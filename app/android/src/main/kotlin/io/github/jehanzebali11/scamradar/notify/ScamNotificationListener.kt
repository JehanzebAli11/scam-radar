package io.github.jehanzebali11.scamradar.notify

import android.app.Notification
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import io.github.jehanzebali11.scamradar.ScamRadarApp
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Checks new SMS/WhatsApp notifications on the phone and posts a "possible
 * scam" alert for amber/red results. Runs only after the disclosure screen and
 * the user granting notification access, and only while the in-app switch is on.
 * Message text stays in memory for the check and is never stored or sent.
 */
class ScamNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val recent = RecentMessages()

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val app = application as ScamRadarApp
        if (!app.prefs.watchNotifications) return
        if (sbn.packageName == packageName) return
        if (!NotificationMessage.isWatched(sbn.packageName, Telephony.Sms.getDefaultSmsPackage(this))) return
        val msg = NotificationMessage.extract(fields(sbn)) ?: return
        if (recent.checkAndAdd(sbn.packageName, msg)) return

        scope.launch {
            try {
                val verdict = app.classifiers.checker().check(msg.text, msg.sender)
                if (verdict.severity != Severity.GREEN) {
                    AlertNotifier.show(this@ScamNotificationListener, verdict, msg)
                }
            } catch (e: Exception) {
                Log.w(TAG, "check failed", e)
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun fields(sbn: StatusBarNotification): NotificationFields {
        val n = sbn.notification
        val extras = n.extras
        return NotificationFields(
            packageName = sbn.packageName,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            messages = messagingStyle(n),
            isGroupSummary = n.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            isOngoing = n.flags and Notification.FLAG_ONGOING_EVENT != 0,
        )
    }

    /** MessagingStyle via NotificationCompat, which handles every API level from minSdk 26. */
    private fun messagingStyle(n: Notification): List<Pair<String?, String>> =
        NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(n)?.messages.orEmpty().mapNotNull { m ->
            val text = m.text?.toString() ?: return@mapNotNull null
            m.person?.name?.toString() to text
        }

    private companion object {
        const val TAG = "ScamListener"
    }
}
