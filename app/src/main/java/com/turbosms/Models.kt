package com.turbosms

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Device info for connected web clients
 */
@Parcelize
data class DeviceInfo(
    val ipAddress: String,
    var requestCount: Int,
    var lastAccess: Long
) : Parcelable

/**
 * SMS Task for the sending queue
 */
data class SmsTask(
    val id: Int,  // Added unique ID
    val number: String,
    val message: String,
    val sim: Int = 1,
    val subscriptionId: Int = -1,
    val rowIndex: Int = -1,
    var status: SmsStatus = SmsStatus.PENDING,  // Track status
    var errorMessage: String? = null  // Track error
)

/**
 * SMS Status enum
 */
enum class SmsStatus {
    PENDING,
    SENDING,
    SENT,
    FAILED
}

/**
 * Log entry for SMS results
 */
data class LogEntry(
    val timestamp: String,
    val number: String,
    val sim: Int,
    val subscriptionId: Int,
    val status: String,
    val message: String
)