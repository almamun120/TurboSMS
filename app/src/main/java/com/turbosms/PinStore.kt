package com.turbosms

import android.content.Context
import java.security.SecureRandom

/** ওয়েব ইন্টারফেসের লগইন PIN — প্রথমবার র‍্যান্ডম ৬ অঙ্কের PIN তৈরি হয়। */
object PinStore {
    private const val PREFS = "TurboSMS"
    private const val KEY = "pin"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, null)
        if (!existing.isNullOrBlank()) return existing
        return regenerate(context)
    }

    fun regenerate(context: Context): String {
        val pin = (100000 + SecureRandom().nextInt(900000)).toString()
        set(context, pin)
        return pin
    }

    fun set(context: Context, pin: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, pin).apply()
    }
}
