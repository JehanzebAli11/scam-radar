package io.github.jehanzebali11.scamradar.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Everything here opens another app with text filled in. The user reviews it and
 * presses send there; Scam Radar never sends anything itself.
 */
object ShareIntents {
    /**
     * Short code for SMS scam reports. UNVERIFIED: left null until the official
     * number (e.g. PTA's complaint short code) is confirmed. While null, the report
     * is offered through Copy and the share sheet only.
     */
    val REPORT_SMS_NUMBER: String? = null

    fun shareText(context: Context, text: String, chooserTitle: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun copy(context: Context, label: String, text: String) {
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** Opens the SMS app with recipient and body filled in. Does not send. */
    fun openSmsComposer(context: Context, number: String, body: String) {
        val intent =
            Intent(Intent.ACTION_SENDTO, "smsto:$number".toUri())
                .putExtra("sms_body", body)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
