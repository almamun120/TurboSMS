package com.turbosms

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import kotlinx.coroutines.*

class SmsServerService : Service() {

    companion object {
        private const val TAG = "SmsServerService"
        const val ACTION_START = "com.turbosms.START"
        const val ACTION_STOP = "com.turbosms.STOP"
        const val ACTION_STATUS_UPDATE = "com.turbosms.STATUS_UPDATE"
        const val ACTION_DEVICE_UPDATE = "com.turbosms.DEVICE_UPDATE"
        const val ACTION_SMS_STATS_UPDATE = "com.turbosms.SMS_STATS_UPDATE"
        const val ACTION_PIN_CHANGED = "com.turbosms.PIN_CHANGED"

        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "TurboSMSChannel"

        @Volatile
        var isRunning = false
            private set

        @Volatile
        var instance: SmsServerService? = null
            private set
    }

    private var httpServer: TurboHttpServer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var currentPort = 9000

    private val prefs by lazy { getSharedPreferences("TurboSMS", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // startForegroundService() ডাকার ৫ সেকেন্ডের মধ্যে startForeground() বাধ্যতামূলক —
                // সার্ভার চালু হোক বা না হোক আগে সেটা করি (আগে এখানে ক্র্যাশ হতো)
                val port = intent.getIntExtra("port", prefs.getInt("port", 9000))
                enterForeground("Starting on port $port…")
                startServer(port)
            }
            ACTION_STOP -> stopServer()
            null -> {
                // সিস্টেম প্রসেস কিল করে রিস্টার্ট করলে
                if (prefs.getBoolean("wasRunning", false)) {
                    val port = prefs.getInt("port", 9000)
                    enterForeground("Restoring server on port $port…")
                    startServer(port)
                } else {
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun enterForeground(text: String) {
        val notification = createNotification(text)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed: ${e.message}", e)
        }
    }

    private fun startServer(port: Int) {
        if (isRunning) {
            broadcastStatus(true, currentPort)
            return
        }
        if (port !in 1024..65535) {
            fail("Port must be between 1024 and 65535")
            return
        }
        currentPort = port

        try {
            httpServer = TurboHttpServer(port, this).apply { start() }
            isRunning = true
            prefs.edit().putBoolean("wasRunning", true).putInt("port", port).apply()
            acquireWakeLock()
            updateNotification("Server running on port $port")
            broadcastStatus(true, port)
        } catch (e: Exception) {
            Log.e(TAG, "Server start failed", e)
            httpServer?.shutdown()
            httpServer = null
            fail(e.message ?: "Could not start server (port in use?)")
        }
    }

    private fun fail(message: String) {
        isRunning = false
        prefs.edit().putBoolean("wasRunning", false).apply()
        val i = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("isRunning", false)
            putExtra("port", 0)
            putExtra("error", message)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(i)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopServer() {
        httpServer?.shutdown()      // আগে stop() ডাকা হতো — receiver ও ফাইল cleanup হতো না
        httpServer = null
        isRunning = false
        prefs.edit().putBoolean("wasRunning", false).apply()
        releaseWakeLock()

        broadcastStatus(false, 0)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopPendingIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SmsServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Turbo SMS Server")
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_stop, "Stop Server", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TurboSMS::ServerWakeLock").apply {
            setReferenceCounted(false)
            acquire()
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun broadcastStatus(isRunning: Boolean, port: Int) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra("isRunning", isRunning)
            putExtra("port", port)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun broadcastDeviceUpdate(devices: List<DeviceInfo>) {
        val intent = Intent(ACTION_DEVICE_UPDATE).apply {
            // নতুন কপি — মূল অবজেক্ট থ্রেডের মধ্যে শেয়ার না করতে
            putParcelableArrayListExtra("devices", ArrayList(devices.map { it.copy() }))
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun broadcastSmsStats(sent: Int, failed: Int, pending: Int) {
        val intent = Intent(ACTION_SMS_STATS_UPDATE).apply {
            putExtra("sent", sent)
            putExtra("failed", failed)
            putExtra("pending", pending)
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }

    fun updateNotification(content: String) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, createNotification(content))
    }

    /** অ্যাপে PIN বদলালে সব লগইন সেশন বাতিল */
    fun onPinChanged() {
        httpServer?.revokeAllSessions()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        releaseWakeLock()
        httpServer?.shutdown()
        httpServer = null
        isRunning = false
        instance = null
    }
}
