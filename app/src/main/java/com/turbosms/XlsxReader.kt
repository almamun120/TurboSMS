package com.turbosms

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.util.zip.ZipFile

/**
 * হালকা .xlsx রিডার — Apache POI-ooxml ছাড়া (Android-এ StAX নেই বলে POI ক্র্যাশ করত)।
 * Shared strings, inline strings, সংখ্যা ও বুলিয়ান সেল সাপোর্ট করে।
 * ফোন নম্বরের মতো বড় সংখ্যা "1.7E9" হয়ে যায় না।
 */
object XlsxReader {

    data class Sheet(val name: String, val path: String)

    fun sheetNames(file: File): List<String> = ZipFile(file).use { zip -> listSheets(zip).map { it.name } }

    fun readSheet(file: File, sheetIndex: Int): List<List<String>> {
        ZipFile(file).use { zip ->
            val sheets = listSheets(zip)
            if (sheets.isEmpty()) return emptyList()
            val sheet = sheets[sheetIndex.coerceIn(0, sheets.size - 1)]
            val shared = readSharedStrings(zip)
            val entry = zip.getEntry(sheet.path) ?: return emptyList()
            return zip.getInputStream(entry).use { parseSheet(it, shared) }
        }
    }

    // ── workbook.xml + rels ───────────────────────────────────────────────

    private fun listSheets(zip: ZipFile): List<Sheet> {
        val rels = HashMap<String, String>()
        zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            zip.getInputStream(e).use { ins ->
                val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
                while (p.next() != XmlPullParser.END_DOCUMENT) {
                    if (p.eventType == XmlPullParser.START_TAG && p.name == "Relationship") {
                        val id = p.getAttributeValue(null, "Id") ?: continue
                        val target = p.getAttributeValue(null, "Target") ?: continue
                        rels[id] = normalize(target)
                    }
                }
            }
        }
        val result = mutableListOf<Sheet>()
        val wb = zip.getEntry("xl/workbook.xml") ?: return result
        zip.getInputStream(wb).use { ins ->
            val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                if (p.eventType == XmlPullParser.START_TAG && p.name == "sheet") {
                    val name = p.getAttributeValue(null, "name") ?: "Sheet${result.size + 1}"
                    var rid: String? = null
                    for (i in 0 until p.attributeCount) {
                        if (p.getAttributeName(i) == "id") rid = p.getAttributeValue(i)
                    }
                    val path = rid?.let { rels[it] } ?: "xl/worksheets/sheet${result.size + 1}.xml"
                    result.add(Sheet(name, path))
                }
            }
        }
        return result
    }

    private fun normalize(target: String): String {
        val t = target.removePrefix("/")
        return if (t.startsWith("xl/")) t else "xl/$t"
    }

    // ── sharedStrings.xml ─────────────────────────────────────────────────

    private fun readSharedStrings(zip: ZipFile): List<String> {
        val entry = zip.getEntry("xl/sharedStrings.xml") ?: return emptyList()
        val list = ArrayList<String>()
        zip.getInputStream(entry).use { ins ->
            val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
            var sb: StringBuilder? = null
            var inT = false
            var inRPh = false
            while (p.next() != XmlPullParser.END_DOCUMENT) {
                when (p.eventType) {
                    XmlPullParser.START_TAG -> when (p.name) {
                        "si" -> sb = StringBuilder()
                        "rPh" -> inRPh = true          // phonetic runs বাদ
                        "t" -> inT = !inRPh
                    }
                    XmlPullParser.TEXT -> if (inT) sb?.append(p.text)
                    XmlPullParser.END_TAG -> when (p.name) {
                        "t" -> inT = false
                        "rPh" -> inRPh = false
                        "si" -> { list.add(sb?.toString() ?: ""); sb = null }
                    }
                }
            }
        }
        return list
    }

    // ── sheet.xml ─────────────────────────────────────────────────────────

    private fun parseSheet(ins: java.io.InputStream, shared: List<String>): List<List<String>> {
        val rows = ArrayList<List<String>>()
        val p = Xml.newPullParser().apply { setInput(ins, "UTF-8") }
        var current: MutableList<String>? = null
        var colIdx = 0
        var cellType = ""
        var value = StringBuilder()
        var inV = false
        var inT = false
        var inCell = false

        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> when (p.name) {
                    "row" -> current = ArrayList()
                    "c" -> {
                        inCell = true
                        cellType = p.getAttributeValue(null, "t") ?: ""
                        value = StringBuilder()
                        val ref = p.getAttributeValue(null, "r")
                        if (ref != null) colIdx = columnIndex(ref)
                        // মাঝের ফাঁকা সেল পূরণ — কলাম সারিবদ্ধ থাকে
                        while ((current?.size ?: 0) < colIdx) current?.add("")
                    }
                    "v" -> inV = true
                    "t" -> if (inCell) inT = true
                }
                XmlPullParser.TEXT -> if (inV || inT) value.append(p.text)
                XmlPullParser.END_TAG -> when (p.name) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "c" -> {
                        inCell = false
                        current?.add(cellValue(cellType, value.toString(), shared))
                    }
                    "row" -> {
                        current?.let { r ->
                            while (r.isNotEmpty() && r.last().isEmpty()) r.removeAt(r.size - 1)
                            if (r.any { it.isNotEmpty() }) rows.add(r)
                        }
                        current = null
                    }
                }
            }
        }
        val width = rows.maxOfOrNull { it.size } ?: 0
        return rows.map { r -> if (r.size < width) r + List(width - r.size) { "" } else r }
    }

    private fun cellValue(type: String, raw: String, shared: List<String>): String = when (type) {
        "s" -> raw.trim().toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
        "b" -> if (raw.trim() == "1") "TRUE" else "FALSE"
        "inlineStr", "str", "e" -> raw
        else -> formatNumber(raw.trim())
    }

    /** "1.712345678E9" বা "1712345678.0" → "1712345678" */
    private fun formatNumber(s: String): String {
        if (s.isEmpty()) return ""
        s.toLongOrNull()?.let { return it.toString() }
        return try {
            val bd = java.math.BigDecimal(s)
            bd.stripTrailingZeros().toPlainString()
        } catch (e: Exception) {
            s
        }
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c in 'A'..'Z') n = n * 26 + (c - 'A' + 1)
            else if (c in 'a'..'z') n = n * 26 + (c - 'a' + 1)
            else break
        }
        return n - 1
    }
}
