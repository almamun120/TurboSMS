package com.turbosms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != "android.intent.action.QUICKBOOT_POWERON"
        ) return

        val prefs = context.getSharedPreferences("TurboSMS", Context.MODE_PRIVATE)
        // "wasRunning" এখন সার্ভিস নিজেই লিখে রাখে (আগে কেউ লিখত না, তাই অটো-স্টার্ট কাজ করত না)
        if (!prefs.getBoolean("wasRunning", false)) return

        val serviceIntent = Intent(context, SmsServerService::class.java).apply {
            action = SmsServerService.ACTION_START
            putExtra("port", prefs.getInt("port", 9000))
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            // Android 15+ বুটের সময় dataSync ফোরগ্রাউন্ড সার্ভিস চালাতে দেয় না
            Log.w("BootReceiver", "Auto-start blocked by system: ${e.message}")
        }
    }
}
