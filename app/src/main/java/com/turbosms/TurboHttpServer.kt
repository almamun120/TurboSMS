package com.turbosms

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.usermodel.DataFormatter
import java.security.SecureRandom
import java.util.concurrent.CopyOnWriteArraySet
import java.io.*
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class TurboHttpServer(
    port: Int,
    private val service: SmsServerService
) : NanoHTTPD(port), SmsSentReceiver.SmsResultCallback {

    companion object {
        private const val TAG = "TurboSMS"
    }

    private val gson = Gson()
    private val devices = ConcurrentHashMap<String, DeviceInfo>()
    private val smsQueue = Collections.synchronizedList(mutableListOf<SmsTask>())
    private var smsJob: Job? = null
    @Volatile private var isPaused = false
    @Volatile private var isStopped = true

    // Use AtomicInteger for thread-safe counting
    private val totalSent = AtomicInteger(0)
    private val totalFailed = AtomicInteger(0)
    @Volatile private var currentIndex = 0
    private var smsInterval = 1000L

    // Track pending SMS results
    private val pendingSmsResults = ConcurrentHashMap<Int, SmsTask>()

    // SMS ID generator
    private val smsIdGenerator = AtomicInteger(0)

    private val logs = Collections.synchronizedList(mutableListOf<LogEntry>())
    private var uploadedData: List<List<String>>? = null
    private var headerRow: List<String>? = null
    private var hasHeader: Boolean = false

    private var persistentFilePath: String? = null
    private var uploadedFileName: String? = null
    private var availableSheets: List<String> = emptyList()
    private var currentSheetIndex = 0

    // SIM configuration
    private var selectedSim: Int = 0
    private var simRatios: Map<Int, Int> = mapOf(1 to 1, 2 to 0)
    private var currentSimIndex = 0
    private var simDistribution: List<Int> = listOf(1)

    // Row range
    private var rowRange: String = ""

    // Cache SIM subscription info
    private var simSubscriptionCache: Map<Int, Int> = emptyMap()

    private val cacheDir: File by lazy {
        File(service.cacheDir, "uploads").apply { mkdirs() }
    }

    private val subscriptionManager: SubscriptionManager? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            service.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        } else {
            null
        }
    }

    private val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // SMS Sent Receiver
    private val smsSentReceiver = SmsSentReceiver()
    private var isReceiverRegistered = false

    init {
        refreshSimCache()
        registerSmsReceiver()
    }

    /**
     * Register the SMS sent broadcast receiver
     */
    private fun registerSmsReceiver() {
        try {
            if (!isReceiverRegistered) {
                val filter = IntentFilter(SmsSentReceiver.ACTION_SMS_SENT)
                ContextCompat.registerReceiver(
                    service,
                    smsSentReceiver,
                    filter,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                SmsSentReceiver.smsResultCallback = this
                isReceiverRegistered = true
                android.util.Log.d(TAG, "SMS Sent Receiver registered")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error registering SMS receiver: ${e.message}")
        }
    }

    /**
     * Unregister the SMS sent broadcast receiver
     */
    private fun unregisterSmsReceiver() {
        try {
            if (isReceiverRegistered) {
                service.unregisterReceiver(smsSentReceiver)
                SmsSentReceiver.smsResultCallback = null
                isReceiverRegistered = false
                android.util.Log.d(TAG, "SMS Sent Receiver unregistered")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error unregistering SMS receiver: ${e.message}")
        }
    }

    /** একটি SMS-এর সব পার্টের ফলাফল জমা রাখে */
    private class PartState(val total: Int) {
        var received = 0
        var firstError: String? = null
    }
    private val partStates = ConcurrentHashMap<Int, PartState>()

    /**
     * প্রতিটি পার্টের sent-ফলাফল এখানে আসে। সব পার্ট এলে তবেই চূড়ান্ত
     * SENT/FAILED গোনা হয় (আগে শুধু শেষ পার্ট দেখা হতো — ভুল গণনা হতো)।
     */
    override fun onSmsPartResult(
        smsId: Int, partIndex: Int, totalParts: Int, success: Boolean, errorMessage: String?
    ) {
        val state = partStates.getOrPut(smsId) { PartState(totalParts) }
        var finished = false
        var error: String? = null
        synchronized(state) {
            state.received++
            if (!success && state.firstError == null) state.firstError = errorMessage ?: "Unknown error"
            if (state.received >= state.total) {
                finished = true
                error = state.firstError
            }
        }
        if (!finished) return
        partStates.remove(smsId)

        val task = pendingSmsResults.remove(smsId) ?: return
        if (error == null) {
            task.status = SmsStatus.SENT
            totalSent.incrementAndGet()
            addLog(task.number, task.sim, task.subscriptionId, "SENT", "Message sent successfully via SIM ${task.sim}")
        } else {
            task.status = SmsStatus.FAILED
            task.errorMessage = error
            totalFailed.incrementAndGet()
            addLog(task.number, task.sim, task.subscriptionId, "FAILED", error ?: "Unknown error")
        }
        service.broadcastSmsStats(totalSent.get(), totalFailed.get(), pendingCount())
    }

    private fun pendingCount(): Int = (smsQueue.size - currentIndex).coerceAtLeast(0)

    private fun refreshSimCache() {
        simSubscriptionCache = getSimSubscriptionMap()
        android.util.Log.d(TAG, "SIM Cache refreshed: $simSubscriptionCache")
    }

    private fun getSimSubscriptionMap(): Map<Int, Int> {
        val result = mutableMapOf<Int, Int>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            return result
        }

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            android.util.Log.e(TAG, "READ_PHONE_STATE permission not granted!")
            return result
        }

        try {
            val subscriptionInfoList = subscriptionManager?.activeSubscriptionInfoList

            if (subscriptionInfoList.isNullOrEmpty()) {
                return result
            }

            for (info in subscriptionInfoList) {
                val slotIndex = info.simSlotIndex
                val subscriptionId = info.subscriptionId
                result[slotIndex + 1] = subscriptionId
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting subscriptions: ${e.message}", e)
        }

        return result
    }

    private fun getAvailableSims(): List<Map<String, Any>> {
        val sims = mutableListOf<Map<String, Any>>()

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP_MR1) {
            sims.add(mapOf(
                "slot" to 1,
                "subscriptionId" to -1,
                "carrier" to "Default SIM",
                "displayName" to "Default SIM",
                "available" to true
            ))
            return sims
        }

        if (ContextCompat.checkSelfPermission(service, Manifest.permission.READ_PHONE_STATE)
            != PackageManager.PERMISSION_GRANTED) {
            sims.add(mapOf("slot" to 1, "subscriptionId" to -1, "carrier" to "SIM 1", "displayName" to "SIM 1", "available" to false))
            sims.add(mapOf("slot" to 2, "subscriptionId" to -1, "carrier" to "SIM 2", "displayName" to "SIM 2", "available" to false))
            return sims
        }

        try {
            val subscriptionInfoList = subscriptionManager?.activeSubscriptionInfoList

            if (subscriptionInfoList.isNullOrEmpty()) {
                sims.add(mapOf("slot" to 1, "subscriptionId" to -1, "carrier" to "No SIM", "displayName" to "No SIM", "available" to false))
                return sims
            }

            for (info in subscriptionInfoList) {
                sims.add(mapOf(
                    "slot" to (info.simSlotIndex + 1),
                    "subscriptionId" to info.subscriptionId,
                    "carrier" to (info.carrierName?.toString() ?: "Unknown"),
                    "displayName" to (info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}"),
                    "number" to (info.number ?: ""),
                    "available" to true
                ))
            }

            sims.sortBy { it["slot"] as Int }

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting SIM info: ${e.message}", e)
        }

        return sims
    }

    // ── PIN সুরক্ষা ───────────────────────────────────────────────────────
    // আগে LAN-এর যে কেউ ব্রাউজার খুলে আপনার ফোন থেকে SMS পাঠাতে পারত।
    // এখন অ্যাপে দেখানো PIN দিয়ে লগইন করতে হয়।
    private val STATUS_429 = object : Response.IStatus {
        override fun getRequestStatus(): Int = 429
        override fun getDescription(): String = "429 Too Many Requests"
    }
    private val reqCodeGen = AtomicInteger(1)
    private fun nextReqCode(): Int = reqCodeGen.incrementAndGet()

    private val authTokens = CopyOnWriteArraySet<String>()
    private val failedAttempts = ConcurrentHashMap<String, MutableList<Long>>()
    private val random = SecureRandom()

    private fun newToken(): String {
        val b = ByteArray(24); random.nextBytes(b)
        return b.joinToString("") { "%02x".format(it) }
    }

    private fun cookieValue(session: IHTTPSession, name: String): String? {
        val header = session.headers["cookie"] ?: return null
        return header.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
    }

    private fun isAuthorized(session: IHTTPSession): Boolean {
        val token = cookieValue(session, "turbo_auth") ?: return false
        return authTokens.contains(token)
    }

    private fun handleLogin(session: IHTTPSession, ip: String): Response {
        val now = System.currentTimeMillis()
        val attempts = failedAttempts.getOrPut(ip) { mutableListOf() }
        synchronized(attempts) {
            attempts.removeAll { now - it > 60_000 }
            if (attempts.size >= 5) {
                val r = newFixedLengthResponse(
                    STATUS_429, "application/json",
                    gson.toJson(mapOf("success" to false, "error" to "Too many attempts. Try again in a minute."))
                )
                return r
            }
        }
        val json = try { parseJsonFromSession(session) } catch (e: Exception) { JsonObject() }
        val given = json.get("pin")?.asString?.trim() ?: ""
        val expected = PinStore.get(service)
        val ok = java.security.MessageDigest.isEqual(given.toByteArray(), expected.toByteArray())
        if (!ok) {
            synchronized(attempts) { attempts.add(now) }
            return newFixedLengthResponse(
                Response.Status.UNAUTHORIZED, "application/json",
                gson.toJson(mapOf("success" to false, "error" to "Wrong PIN"))
            )
        }
        failedAttempts.remove(ip)
        val token = newToken()
        authTokens.add(token)
        val r = jsonResponse(mapOf("success" to true))
        r.addHeader("Set-Cookie", "turbo_auth=$token; Path=/; HttpOnly; SameSite=Lax; Max-Age=86400")
        return r
    }

    private fun handleLogout(session: IHTTPSession): Response {
        cookieValue(session, "turbo_auth")?.let { authTokens.remove(it) }
        val r = jsonResponse(mapOf("success" to true))
        r.addHeader("Set-Cookie", "turbo_auth=; Path=/; Max-Age=0")
        return r
    }

    /** অ্যাপে PIN বদলালে পুরনো সব সেশন বাতিল */
    fun revokeAllSessions() { authTokens.clear() }

    override fun serve(session: IHTTPSession): Response {
        val clientIp = session.remoteIpAddress
        trackDevice(clientIp)

        return try {
            val uri = session.uri
            if (uri == "/api/login" && session.method == Method.POST) return handleLogin(session, clientIp)
            if (uri == "/api/logout") return handleLogout(session)
            if (!isAuthorized(session)) {
                return if (uri.startsWith("/api/")) {
                    newFixedLengthResponse(
                        Response.Status.UNAUTHORIZED, "application/json",
                        gson.toJson(mapOf("success" to false, "error" to "Unauthorized", "unauthorized" to true))
                    )
                } else {
                    newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", WebContent.getLoginPage())
                }
            }
            when {
                session.uri == "/" || session.uri == "/index.html" -> serveHtml()
                session.uri == "/api/send-sms" && session.method == Method.POST -> handleSendSms(session)
                session.uri == "/api/upload-file" && session.method == Method.POST -> handleFileUpload(session)
                session.uri == "/api/select-sheet" && session.method == Method.POST -> handleSelectSheet(session)
                session.uri == "/api/set-header" && session.method == Method.POST -> handleSetHeader(session)
                session.uri == "/api/control" && session.method == Method.POST -> handleControl(session)
                session.uri == "/api/status" -> handleStatus()
                session.uri == "/api/logs" -> handleLogs()
                session.uri == "/api/export-logs" -> handleExportLogs()
                session.uri == "/api/sims" -> handleGetSims()
                session.uri == "/api/refresh-sims" -> handleRefreshSims()
                session.uri == "/api/set-sim-config" && session.method == Method.POST -> handleSetSimConfig(session)
                session.uri.startsWith("/static/") -> serveStatic(session.uri)
                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "serve error", e)
            jsonResponse(mapOf("success" to false, "error" to (e.message ?: "Server error")))
        }
    }

    private fun trackDevice(ip: String) {
        val device = devices.getOrPut(ip) {
            DeviceInfo(ip, 0, System.currentTimeMillis())
        }
        device.requestCount++
        device.lastAccess = System.currentTimeMillis()
        service.broadcastDeviceUpdate(devices.values.toList())
    }

    private fun serveHtml(): Response {
        val html = WebContent.getMainPage()
        return newFixedLengthResponse(Response.Status.OK, "text/html; charset=UTF-8", html)
    }

    private fun serveStatic(uri: String): Response {
        return when {
            uri.endsWith(".css") -> {
                newFixedLengthResponse(Response.Status.OK, "text/css; charset=UTF-8", WebContent.getCss())
            }
            uri.endsWith(".js") -> {
                newFixedLengthResponse(Response.Status.OK, "application/javascript; charset=UTF-8", WebContent.getJs())
            }
            else -> {
                newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not Found")
            }
        }
    }

    private fun readPostBodyAsUtf8(session: IHTTPSession): String {
        try {
            val contentLength = session.headers["content-length"]?.toIntOrNull() ?: 0
            if (contentLength == 0) {
                val files = mutableMapOf<String, String>()
                session.parseBody(files)
                val postData = files["postData"] ?: ""
                return fixEncoding(postData)
            }

            val inputStream = session.inputStream
            val buffer = ByteArray(contentLength)
            var totalRead = 0

            while (totalRead < contentLength) {
                val read = inputStream.read(buffer, totalRead, contentLength - totalRead)
                if (read == -1) break
                totalRead += read
            }

            return String(buffer, 0, totalRead, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            val files = mutableMapOf<String, String>()
            session.parseBody(files)
            return fixEncoding(files["postData"] ?: "")
        }
    }

    private fun fixEncoding(text: String): String {
        return try {
            val bytes = text.toByteArray(StandardCharsets.ISO_8859_1)
            String(bytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            text
        }
    }

    private fun parseJsonFromSession(session: IHTTPSession): JsonObject {
        val postData = readPostBodyAsUtf8(session)
        return gson.fromJson(postData, JsonObject::class.java)
    }

    private fun handleGetSims(): Response {
        refreshSimCache()
        val sims = getAvailableSims()

        return jsonResponse(mapOf(
            "success" to true,
            "sims" to sims,
            "subscriptionMap" to simSubscriptionCache,
            "currentConfig" to mapOf(
                "selectedSim" to selectedSim,
                "simRatios" to simRatios
            ),
            "androidVersion" to Build.VERSION.SDK_INT,
            "dualSimSupported" to (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1)
        ))
    }

    private fun handleRefreshSims(): Response {
        refreshSimCache()
        return handleGetSims()
    }

    private fun handleSetSimConfig(session: IHTTPSession): Response {
        try {
            val json = parseJsonFromSession(session)

            selectedSim = json.get("selectedSim")?.asInt ?: 0

            val ratiosJson = json.getAsJsonObject("simRatios")
            simRatios = mapOf(
                1 to (ratiosJson?.get("1")?.asInt ?: 1),
                2 to (ratiosJson?.get("2")?.asInt ?: 0)
            )

            buildSimDistribution()

            return jsonResponse(mapOf(
                "success" to true,
                "selectedSim" to selectedSim,
                "simRatios" to simRatios,
                "distribution" to simDistribution
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            return jsonResponse(mapOf("success" to false, "error" to e.message))
        }
    }

    private fun buildSimDistribution() {
        val distribution = mutableListOf<Int>()

        if (selectedSim > 0) {
            distribution.add(selectedSim)
        } else {
            repeat(simRatios[1] ?: 0) { distribution.add(1) }
            repeat(simRatios[2] ?: 0) { distribution.add(2) }
        }

        simDistribution = if (distribution.isEmpty()) listOf(1) else distribution
        currentSimIndex = 0
    }

    private fun getNextSim(): Int {
        if (simDistribution.isEmpty()) return 1
        val sim = simDistribution[currentSimIndex % simDistribution.size]
        currentSimIndex++
        return sim
    }

    private fun handleSendSms(session: IHTTPSession): Response {
        try {
            val json = parseJsonFromSession(session)

            if (!isStopped && smsJob?.isActive == true) {
                return jsonResponse(mapOf("success" to false,
                    "error" to "Sending is in progress. Stop or reset before creating a new queue."))
            }

            val numbers = json.get("numbers")?.asString ?: ""
            val message = json.get("message")?.asString ?: ""
            val interval = (json.get("interval")?.asInt ?: 1).coerceIn(0, 3600)
            rowRange = json.get("rowRange")?.asString ?: ""

            if (message.isBlank()) {
                return jsonResponse(mapOf("success" to false, "error" to "Message is empty"))
            }
            if (numbers.isBlank()) {
                return jsonResponse(mapOf("success" to false, "error" to "No phone numbers provided"))
            }

            json.get("selectedSim")?.let { selectedSim = it.asInt }
            json.getAsJsonObject("simRatios")?.let { ratios ->
                simRatios = mapOf(
                    1 to (ratios.get("1")?.asInt ?: 1),
                    2 to (ratios.get("2")?.asInt ?: 0)
                )
            }
            buildSimDistribution()
            refreshSimCache()

            smsInterval = interval * 1000L

            val processedNumbers = processNumbers(numbers)
            if (processedNumbers.isEmpty()) {
                return jsonResponse(mapOf("success" to false, "error" to "No valid numbers found"))
            }
            smsQueue.clear()
            pendingSmsResults.clear()
            partStates.clear()
            currentIndex = 0
            totalSent.set(0)
            totalFailed.set(0)
            logs.clear()
            currentSimIndex = 0
            smsIdGenerator.set(0)

            for (i in processedNumbers.indices) {
                val rawNumber = processedNumbers[i].first
                val rowIndex = processedNumbers[i].second
                val number = cleanNumber(rawNumber)
                val invalid = !isValidNumber(number)
                val finalMessage = processMessageVariables(message, rowIndex)
                val sim = getNextSim()
                val subscriptionId = simSubscriptionCache[sim] ?: -1
                val smsId = smsIdGenerator.incrementAndGet()

                smsQueue.add(SmsTask(
                    id = smsId,
                    number = if (invalid) rawNumber else number,
                    message = finalMessage,
                    sim = sim,
                    subscriptionId = subscriptionId,
                    rowIndex = rowIndex,
                    status = if (invalid) SmsStatus.FAILED else SmsStatus.PENDING,
                    errorMessage = if (invalid) "Invalid phone number" else null
                ))
            }

            return jsonResponse(mapOf(
                "success" to true,
                "totalTasks" to smsQueue.size,
                "message" to "SMS queue created with ${smsQueue.size} messages",
                "simDistribution" to simDistribution.take(10).joinToString(","),
                "subscriptionMap" to simSubscriptionCache
            ))

        } catch (e: Exception) {
            e.printStackTrace()
            return jsonResponse(mapOf(
                "success" to false,
                "error" to e.message
            ))
        }
    }

    private fun parseRowRange(range: String, maxRows: Int): Set<Int> {
        if (range.isBlank()) {
            return (0 until maxRows).toSet()
        }

        val indices = mutableSetOf<Int>()
        val parts = range.split(",").map { it.trim() }

        for (part in parts) {
            if (part.contains("-")) {
                val rangeParts = part.split("-").map { it.trim() }
                if (rangeParts.size == 2) {
                    val start = rangeParts[0].toIntOrNull()?.minus(1) ?: continue
                    val end = rangeParts[1].toIntOrNull()?.minus(1) ?: continue
                    val actualStart = minOf(start, end).coerceIn(0, maxRows - 1)
                    val actualEnd = maxOf(start, end).coerceIn(0, maxRows - 1)
                    for (i in actualStart..actualEnd) {
                        indices.add(i)
                    }
                }
            } else {
                val index = part.toIntOrNull()?.minus(1) ?: continue
                if (index in 0 until maxRows) {
                    indices.add(index)
                }
            }
        }

        return indices
    }

    private fun handleFileUpload(session: IHTTPSession): Response {
        try {
            val files = mutableMapOf<String, String>()
            session.parseBody(files)

            val params = session.parameters
            val separator = params["separator"]?.firstOrNull() ?: "\t"
            val tempFilePath = files["file"]
            hasHeader = params["hasHeader"]?.firstOrNull()?.toBoolean() ?: false

            if (tempFilePath != null) {
                val tempFile = File(tempFilePath)
                val fileName = params["filename"]?.firstOrNull() ?: "uploaded_file"

                cleanupOldFile()

                val persistentFile = copyToPersistentStorage(tempFile, fileName)
                persistentFilePath = persistentFile.absolutePath
                uploadedFileName = fileName

                val isExcel = isExcelName(fileName)

                if (isExcel) {
                    availableSheets = getExcelSheetNames(persistentFile)
                    currentSheetIndex = 0
                    parseAndStoreData(persistentFile, isExcel = true, sheetIndex = 0)
                } else {
                    availableSheets = emptyList()
                    parseAndStoreData(persistentFile, isExcel = false, separator = separator)
                }

                return buildFileResponse(isExcel)
            }

            return jsonResponse(mapOf("success" to false, "error" to "No file uploaded"))

        } catch (e: Exception) {
            e.printStackTrace()
            return jsonResponse(mapOf("success" to false, "error" to e.message))
        }
    }

    private fun handleSelectSheet(session: IHTTPSession): Response {
        try {
            val json = parseJsonFromSession(session)
            val sheetIndex = json.get("sheetIndex")?.asInt ?: 0

            if (persistentFilePath == null) {
                return jsonResponse(mapOf("success" to false, "error" to "No file uploaded"))
            }

            val file = File(persistentFilePath!!)
            if (!file.exists()) {
                persistentFilePath = null
                return jsonResponse(mapOf("success" to false, "error" to "File not found"))
            }

            currentSheetIndex = sheetIndex
            parseAndStoreData(file, isExcel = true, sheetIndex = sheetIndex)

            return buildFileResponse(isExcel = true)

        } catch (e: Exception) {
            e.printStackTrace()
            return jsonResponse(mapOf("success" to false, "error" to e.message))
        }
    }

    private fun handleSetHeader(session: IHTTPSession): Response {
        try {
            val json = parseJsonFromSession(session)
            hasHeader = json.get("hasHeader")?.asBoolean ?: false

            if (persistentFilePath != null) {
                val file = File(persistentFilePath!!)
                if (file.exists()) {
                    val isExcel = uploadedFileName?.let { isExcelName(it) } ?: false

                    if (isExcel) {
                        parseAndStoreData(file, isExcel = true, sheetIndex = currentSheetIndex)
                    } else {
                        parseAndStoreData(file, isExcel = false, separator = "\t")
                    }

                    return buildFileResponse(isExcel)
                }
            }

            return jsonResponse(mapOf("success" to true, "hasHeader" to hasHeader))

        } catch (e: Exception) {
            e.printStackTrace()
            return jsonResponse(mapOf("success" to false, "error" to e.message))
        }
    }

    private fun parseAndStoreData(file: File, isExcel: Boolean, sheetIndex: Int = 0, separator: String = "\t") {
        val allData = if (isExcel) {
            parseExcelFile(file, sheetIndex)
        } else {
            parseTextFile(file, separator)
        }

        if (hasHeader && allData.isNotEmpty()) {
            headerRow = allData.first()
            uploadedData = allData.drop(1)
        } else {
            headerRow = null
            uploadedData = allData
        }
    }

    private fun buildFileResponse(isExcel: Boolean): Response {
        val allData = if (hasHeader && headerRow != null) {
            listOf(headerRow!!) + (uploadedData ?: emptyList())
        } else {
            uploadedData ?: emptyList()
        }

        val columns = allData.firstOrNull()?.size ?: 0
        val dataRows = uploadedData?.size ?: 0

        // Send ALL data for preview (not just first 6 rows)
        // Limit to 500 rows for performance, but can be increased
        val maxPreviewRows = 500
        val previewData = allData.take(maxPreviewRows)

        return jsonResponse(mapOf(
            "success" to true,
            "columns" to columns,
            "rows" to dataRows,
            "totalRows" to allData.size,
            "preview" to previewData,  // Send more rows
            "previewLimited" to (allData.size > maxPreviewRows),
            "sheets" to availableSheets,
            "currentSheet" to currentSheetIndex,
            "isExcel" to isExcel,
            "hasHeader" to hasHeader,
            "headerRow" to (headerRow ?: emptyList<String>())
        ))
    }

    private fun copyToPersistentStorage(tempFile: File, originalFileName: String): File {
        val extension = originalFileName.substringAfterLast('.', "")
        val timestamp = System.currentTimeMillis()
        val persistentFileName = "upload_${timestamp}.$extension"
        val persistentFile = File(cacheDir, persistentFileName)

        tempFile.inputStream().use { input ->
            persistentFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }

        return persistentFile
    }

    private fun cleanupOldFile() {
        persistentFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        persistentFilePath = null
        uploadedFileName = null
        availableSheets = emptyList()
        currentSheetIndex = 0
        uploadedData = null
        headerRow = null
        hasHeader = false
    }

    fun cleanup() {
        cleanupOldFile()
        unregisterSmsReceiver()
        try {
            cacheDir.listFiles()?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleControl(session: IHTTPSession): Response {
        try {
            val json = parseJsonFromSession(session)
            val action = json.get("action")?.asString ?: ""

            when (action) {
                "start" -> startSmsSending()
                "pause" -> pauseSmsSending()
                "resume" -> resumeSmsSending()
                "stop" -> stopSmsSending()
                "reset" -> resetSmsSending()
            }

            return jsonResponse(mapOf(
                "success" to true,
                "action" to action,
                "status" to getStatusData()
            ))

        } catch (e: Exception) {
            return jsonResponse(mapOf("success" to false, "error" to e.message))
        }
    }

    private fun handleStatus(): Response {
        return jsonResponse(getStatusData())
    }

    private fun handleLogs(): Response {
        return jsonResponse(mapOf(
            "logs" to logs.takeLast(100)
        ))
    }

    private fun handleExportLogs(): Response {
        val csv = buildString {
            appendLine("Timestamp,Number,SIM,SubscriptionId,Status,Message")
            logs.forEach { log ->
                val escapedMessage = log.message.replace("\"", "\"\"")
                appendLine("\"${log.timestamp}\",\"${log.number}\",\"SIM ${log.sim}\",\"${log.subscriptionId}\",\"${log.status}\",\"${escapedMessage}\"")
            }
        }

        val bom = "\uFEFF"
        val response = newFixedLengthResponse(Response.Status.OK, "text/csv; charset=UTF-8", bom + csv)
        response.addHeader("Content-Disposition", "attachment; filename=\"sms_logs.csv\"")
        return response
    }

    private fun getStatusData(): Map<String, Any> {
        return mapOf(
            "isRunning" to !isStopped,
            "isPaused" to isPaused,
            "total" to smsQueue.size,
            "current" to currentIndex,
            "sent" to totalSent.get(),
            "failed" to totalFailed.get(),
            "progress" to if (smsQueue.isNotEmpty()) (currentIndex * 100 / smsQueue.size) else 0,
            "simConfig" to mapOf(
                "selectedSim" to selectedSim,
                "simRatios" to simRatios,
                "subscriptionMap" to simSubscriptionCache
            )
        )
    }

    private fun startSmsSending() {
        if (smsQueue.isEmpty()) return
        if (smsJob?.isActive == true) {          // ডাবল-স্টার্ট প্রতিরোধ
            isPaused = false
            return
        }
        if (currentIndex >= smsQueue.size) currentIndex = 0

        isStopped = false
        isPaused = false

        smsJob = serverScope.launch {
            while (currentIndex < smsQueue.size && !isStopped) {
                if (isPaused) {
                    delay(100)
                    continue
                }

                val task = smsQueue[currentIndex]
                if (task.status == SmsStatus.FAILED) {
                    // অবৈধ নম্বর — না পাঠিয়ে ব্যর্থ হিসেবে গণ্য
                    totalFailed.incrementAndGet()
                    addLog(task.number, task.sim, task.subscriptionId, "FAILED", task.errorMessage ?: "Invalid")
                    currentIndex++
                    service.broadcastSmsStats(totalSent.get(), totalFailed.get(), pendingCount())
                    continue
                }
                task.status = SmsStatus.SENDING
                sendSmsWithCallback(task)
                currentIndex++

                service.broadcastSmsStats(totalSent.get(), totalFailed.get(), pendingCount())

                if (currentIndex < smsQueue.size && smsInterval > 0) {
                    // বিরতির সময়ও pause/stop সাড়া দেয়
                    var waited = 0L
                    while (waited < smsInterval && !isStopped) {
                        delay(minOf(200L, smsInterval - waited))
                        waited += 200L
                    }
                }
            }

            // সর্বোচ্চ ১৫ সেকেন্ড অপেক্ষা — pending ফলাফলের জন্য
            var waitCount = 0
            while (pendingSmsResults.isNotEmpty() && waitCount < 150 && !isStopped) {
                delay(100)
                waitCount++
            }

            isStopped = true
            service.broadcastSmsStats(totalSent.get(), totalFailed.get(), pendingCount())
        }
    }

    private fun pauseSmsSending() {
        isPaused = true
    }

    private fun resumeSmsSending() {
        isPaused = false
    }

    private fun stopSmsSending() {
        isStopped = true
        isPaused = false
        smsJob?.cancel()
        smsJob = null
    }

    private fun resetSmsSending() {
        stopSmsSending()
        currentIndex = 0
        totalSent.set(0)
        totalFailed.set(0)
        currentSimIndex = 0
        pendingSmsResults.clear()
        partStates.clear()
        logs.clear()
        service.broadcastSmsStats(0, 0, smsQueue.size)
    }

    /**
     * Send SMS with PendingIntent callback for delivery tracking
     */
    private fun sendSmsWithCallback(task: SmsTask) {
        try {
            android.util.Log.d(TAG, "=== SENDING SMS ===")
            android.util.Log.d(TAG, "ID: ${task.id}, To: ${task.number}, SIM: ${task.sim}, SubId: ${task.subscriptionId}")

            val smsManager = getSmsManagerForSubscription(task.subscriptionId, task.sim)
            val message = task.message

            // Create sent intent
            val sentIntent = Intent(SmsSentReceiver.ACTION_SMS_SENT).apply {
                putExtra(SmsSentReceiver.EXTRA_SMS_ID, task.id)
                putExtra(SmsSentReceiver.EXTRA_PHONE_NUMBER, task.number)
                putExtra(SmsSentReceiver.EXTRA_SIM_SLOT, task.sim)
                setPackage(service.packageName)
            }

            // Divide message for multipart
            val parts: ArrayList<String> = smsManager.divideMessage(message)
            android.util.Log.d(TAG, "Message parts: ${parts.size}")

            // Store task for callback
            pendingSmsResults[task.id] = task

            if (parts.size == 1) {
                // Single part SMS
                val pendingIntent = PendingIntent.getBroadcast(
                    service,
                    nextReqCode(),
                    sentIntent.apply {
                        putExtra(SmsSentReceiver.EXTRA_PART_INDEX, 0)
                        putExtra(SmsSentReceiver.EXTRA_TOTAL_PARTS, 1)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                smsManager.sendTextMessage(
                    task.number,
                    null,
                    message,
                    pendingIntent,
                    null
                )
            } else {
                // Multipart SMS
                val sentIntents = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    val partIntent = PendingIntent.getBroadcast(
                        service,
                        nextReqCode(), // প্রতি পার্টের জন্য ইউনিক রিকোয়েস্ট কোড
                        Intent(SmsSentReceiver.ACTION_SMS_SENT).apply {
                            putExtra(SmsSentReceiver.EXTRA_SMS_ID, task.id)
                            putExtra(SmsSentReceiver.EXTRA_PHONE_NUMBER, task.number)
                            putExtra(SmsSentReceiver.EXTRA_SIM_SLOT, task.sim)
                            putExtra(SmsSentReceiver.EXTRA_PART_INDEX, i)
                            putExtra(SmsSentReceiver.EXTRA_TOTAL_PARTS, parts.size)
                            setPackage(service.packageName)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    sentIntents.add(partIntent)
                }

                smsManager.sendMultipartTextMessage(
                    task.number,
                    null,
                    parts,
                    sentIntents,
                    null
                )
            }

            android.util.Log.d(TAG, "SMS submitted for sending via SIM ${task.sim}")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "✗ SMS send error: ${e.message}", e)
            pendingSmsResults.remove(task.id)
            task.status = SmsStatus.FAILED
            task.errorMessage = e.message
            totalFailed.incrementAndGet()
            addLog(task.number, task.sim, task.subscriptionId, "FAILED", e.message ?: "Unknown error")
            service.broadcastSmsStats(totalSent.get(), totalFailed.get(), pendingCount())
        }
    }

    /**
     * Get SmsManager for specific subscription ID
     */
    private fun getSmsManagerForSubscription(subscriptionId: Int, simSlot: Int): SmsManager {
        if (subscriptionId <= 0) {
            return getDefaultSmsManager()
        }

        return try {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    service.getSystemService(SmsManager::class.java)
                        .createForSubscriptionId(subscriptionId)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
                }
                else -> {
                    getSmsManagerByReflection(subscriptionId) ?: getDefaultSmsManager()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error getting SmsManager: ${e.message}")
            getDefaultSmsManager()
        }
    }

    private fun getSmsManagerByReflection(subscriptionId: Int): SmsManager? {
        return try {
            val reflectMethod = SmsManager::class.java.getMethod(
                "getSmsManagerForSubscriptionId",
                Int::class.javaPrimitiveType
            )
            reflectMethod.invoke(null, subscriptionId) as? SmsManager
        } catch (e: Exception) {
            null
        }
    }

    private fun getDefaultSmsManager(): SmsManager {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
    }

    private fun addLog(number: String, sim: Int, subscriptionId: Int, status: String, message: String) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        logs.add(LogEntry(timestamp, number, sim, subscriptionId, status, message))
    }

    private fun processNumbers(numbers: String): List<Pair<String, Int>> {
        val result = mutableListOf<Pair<String, Int>>()
        val dataRows = uploadedData ?: emptyList()
        val maxRows = dataRows.size

        val allowedIndices = parseRowRange(rowRange, maxRows)

        if (numbers.contains("{col-")) {
            dataRows.forEachIndexed { index, row ->
                if (index in allowedIndices) {
                    var processedNumbers = numbers
                    row.forEachIndexed { colIndex, value ->
                        processedNumbers = processedNumbers.replace("{col-${colIndex + 1}}", value)
                    }
                    processedNumbers.split(",", "|", "\t", "\n")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.contains("{col-") }
                        .forEach { num ->
                            result.add(Pair(num, index))
                        }
                }
            }
        } else {
            numbers.split(",", "|", "\t", "\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { num ->
                    result.add(Pair(num, -1))
                }
        }

        return result
    }

    private fun processMessageVariables(message: String, rowIndex: Int): String {
        var result = message

        val now = Date()
        result = result.replace("{timestamp}", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(now))
        result = result.replace("{date}", SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now))
        result = result.replace("{time}", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now))
        result = result.replace("{day}", SimpleDateFormat("EEEE", Locale.getDefault()).format(now))
        result = result.replace("{month}", SimpleDateFormat("MMMM", Locale.getDefault()).format(now))
        result = result.replace("{year}", SimpleDateFormat("yyyy", Locale.getDefault()).format(now))
        result = result.replace("{today}", SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(now))
        result = result.replace("{random}", (1000..9999).random().toString())
        result = result.replace("{rand}", (100000..999999).random().toString())
        result = result.replace("{index}", (rowIndex + 1).toString())
        result = result.replace("{uuid}", UUID.randomUUID().toString())

        val randPattern = Regex("\\{rand:(\\d+)\\}")
        result = randPattern.replace(result) { matchResult ->
            val digits = matchResult.groupValues[1].toIntOrNull() ?: 6
            val max = "9".repeat(digits).toLongOrNull() ?: 999999
            val min = "1${"0".repeat(digits - 1)}".toLongOrNull() ?: 100000
            (min..max).random().toString()
        }

        if (rowIndex >= 0) {
            uploadedData?.getOrNull(rowIndex)?.forEachIndexed { index, value ->
                result = result.replace("{col-${index + 1}}", value)
            }
        }

        return result
    }

    private fun isXls(name: String) = name.lowercase().endsWith(".xls")
    private fun isExcelName(name: String): Boolean {
        val n = name.lowercase()
        return n.endsWith(".xlsx") || n.endsWith(".xlsm") || n.endsWith(".xls")
    }

    private fun getExcelSheetNames(file: File): List<String> {
        return try {
            if (isXls(file.name)) {
                FileInputStream(file).use { ins ->
                    HSSFWorkbook(ins).use { wb -> (0 until wb.numberOfSheets).map { wb.getSheetName(it) } }
                }
            } else {
                XlsxReader.sheetNames(file)
            }
        } catch (e: Throwable) {
            android.util.Log.e(TAG, "sheet names error: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseExcelFile(file: File, sheetIndex: Int = 0): List<List<String>> {
        return try {
            if (isXls(file.name)) parseXls(file, sheetIndex) else XlsxReader.readSheet(file, sheetIndex)
        } catch (e: Throwable) {
            throw IllegalStateException("Cannot read Excel file: ${e.message}. Try saving it as .xlsx or CSV.")
        }
    }

    private fun parseXls(file: File, sheetIndex: Int): List<List<String>> {
        val data = mutableListOf<List<String>>()
        FileInputStream(file).use { ins ->
            HSSFWorkbook(ins).use { wb ->
                val sheet = wb.getSheetAt(sheetIndex.coerceIn(0, wb.numberOfSheets - 1))
                val fmt = DataFormatter()
                val eval = wb.creationHelper.createFormulaEvaluator()
                var width = 0
                val rows = mutableListOf<List<String>>()
                for (r in 0..sheet.lastRowNum) {
                    val row = sheet.getRow(r) ?: continue
                    val last = row.lastCellNum.toInt()
                    if (last <= 0) continue
                    val vals = MutableList(last) { "" }
                    for (c in 0 until last) {
                        val cell = row.getCell(c) ?: continue
                        vals[c] = try {
                            if (cell.cellType == org.apache.poi.ss.usermodel.CellType.NUMERIC &&
                                !org.apache.poi.ss.usermodel.DateUtil.isCellDateFormatted(cell)
                            ) java.math.BigDecimal.valueOf(cell.numericCellValue).stripTrailingZeros().toPlainString()
                            else fmt.formatCellValue(cell, eval)
                        } catch (e: Exception) { cell.toString() }
                    }
                    if (vals.any { it.isNotEmpty() }) {
                        rows.add(vals); width = maxOf(width, vals.size)
                    }
                }
                rows.forEach { r -> data.add(if (r.size < width) r + List(width - r.size) { "" } else r) }
            }
        }
        return data
    }

    /** সংখ্যা/ফরম্যাটিং ঝাড়ামোছা: "+880 1712-345678", "1712345678.0" → "+8801712345678", "1712345678" */
    private fun cleanNumber(raw: String): String {
        var x = raw.trim().replace(Regex("\\.0+$"), "")
        val plus = x.startsWith("+")
        x = x.replace(Regex("[^0-9]"), "")
        return if (plus) "+$x" else x
    }

    private fun isValidNumber(n: String): Boolean = Regex("^\\+?[0-9]{3,15}$").matches(n)

    private fun parseTextFile(file: File, separator: String): List<List<String>> {
        val lines = file.readLines(StandardCharsets.UTF_8)
            .map { it.removePrefix("\uFEFF") }
            .filter { it.isNotBlank() }
        // ট্যাব না থাকলে (যেমন CSV) কমা/সেমিকোলন অটো-ডিটেক্ট
        val sep = when {
            separator.isNotEmpty() && lines.any { it.contains(separator) } -> separator
            lines.any { it.contains("\t") } -> "\t"
            lines.any { it.contains(";") } -> ";"
            lines.any { it.contains(",") } -> ","
            else -> separator.ifEmpty { "\t" }
        }
        return lines.map { line -> line.split(sep).map { it.trim().trim('"') } }
    }

    private fun jsonResponse(data: Any): Response {
        val json = gson.toJson(data)
        val response = newFixedLengthResponse(Response.Status.OK, "application/json", json)
        response.addHeader("Content-Type", "application/json; charset=UTF-8")
        return response
    }

    fun shutdown() {
        serverScope.cancel()
        cleanup()
        stop()
    }
}