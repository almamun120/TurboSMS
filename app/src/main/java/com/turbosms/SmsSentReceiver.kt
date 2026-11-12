package com.turbosms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

/**
 * SMS "sent" স্ট্যাটাসের Broadcast receiver (শুধু কোড থেকে রেজিস্টার হয়)।
 * প্রতিটি পার্টের ফলাফল [SmsResultCallback.onSmsPartResult]-এ পাঠায়।
 */
class SmsSentReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsSentReceiver"
        const val ACTION_SMS_SENT = "com.turbosms.SMS_SENT"
        const val EXTRA_SMS_ID = "sms_id"
        const val EXTRA_PHONE_NUMBER = "phone_number"
        const val EXTRA_SIM_SLOT = "sim_slot"
        const val EXTRA_PART_INDEX = "part_index"
        const val EXTRA_TOTAL_PARTS = "total_parts"

        @Volatile
        var smsResultCallback: SmsResultCallback? = null

        fun describe(resultCode: Int): String? = when (resultCode) {
            Activity.RESULT_OK -> null
            SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
            SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
            SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
            SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off"
            SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS limit exceeded"
            SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED -> "Short code not allowed"
            SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Short code never allowed"
            else -> "Unknown error (code: $resultCode)"
        }
    }

    interface SmsResultCallback {
        fun onSmsPartResult(
            smsId: Int, partIndex: Int, totalParts: Int, success: Boolean, errorMessage: String?
        )
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action != ACTION_SMS_SENT) return

        val smsId = intent.getIntExtra(EXTRA_SMS_ID, -1)
        val partIndex = intent.getIntExtra(EXTRA_PART_INDEX, 0)
        val totalParts = intent.getIntExtra(EXTRA_TOTAL_PARTS, 1).coerceAtLeast(1)
        val error = describe(resultCode)

        if (error == null) Log.d(TAG, "✓ part ${partIndex + 1}/$totalParts sent (id=$smsId)")
        else Log.e(TAG, "✗ part ${partIndex + 1}/$totalParts failed (id=$smsId): $error")

        smsResultCallback?.onSmsPartResult(smsId, partIndex, totalParts, error == null, error)
    }
}
