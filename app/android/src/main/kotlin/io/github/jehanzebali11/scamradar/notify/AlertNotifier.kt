package io.github.jehanzebali11.scamradar.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.jehanzebali11.scamradar.R
import io.github.jehanzebali11.scamradar.core.text.AdviceText
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Verdict
import io.github.jehanzebali11.scamradar.ui.MainActivity

/** Posts our own "possible scam" notification; tapping it opens the full card. */
object AlertNotifier {
    private const val CHANNEL_ID = "scam_alerts"

    fun createChannel(context: Context) {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alert_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.alert_channel_description) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(context: Context, verdict: Verdict, msg: IncomingMessage) {
        if (!canPost(context)) return
        val card = AdviceText.card(verdict)
        val title =
            when (verdict.severity) {
                Severity.RED -> "⚠ ${card.headline.en} · ${card.headline.ur}"
                else -> "${card.headline.en} · ${card.headline.ur}"
            }
        val from = msg.sender?.let { "From $it: " }.orEmpty()
        val what = card.title?.en ?: card.advice.firstOrNull()?.en.orEmpty()
        val subtitle = "$from$what. Don't reply yet."
        val big =
            listOfNotNull(
                from + what,
                card.title?.ur,
                "Don't reply, send money or share codes. Tap to see why.",
                "جواب نہ دیں، پیسے یا کوڈ نہ بھیجیں۔ وجہ دیکھنے کے لیے ٹیپ کریں۔",
            ).joinToString("\n")
        val open =
            Intent(context, MainActivity::class.java)
                .setAction(MainActivity.ACTION_CHECK)
                .putExtra(Intent.EXTRA_TEXT, msg.text)
                .putExtra(MainActivity.EXTRA_SENDER, msg.sender)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val id = (msg.sender + msg.text).hashCode()
        val pending = PendingIntent.getActivity(context, id, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_alert)
                .setContentTitle(title)
                .setContentText(subtitle)
                .setStyle(NotificationCompat.BigTextStyle().bigText(big))
                .setColor(if (verdict.severity == Severity.RED) 0xFFB71C1C.toInt() else 0xFFFFA000.toInt())
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setContentIntent(pending)
                .setAutoCancel(true)
                .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the post.
        }
    }
}
