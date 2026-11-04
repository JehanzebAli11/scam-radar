package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Prominent disclosure shown BEFORE the app sends the user to Android's
 * notification-access settings. Says what is read, what happens to it, and how
 * to turn it off.
 */
@Composable
fun DisclosureScreen(onAgree: () -> Unit, onDecline: () -> Unit) {
    val points =
        listOf(
            "Scam Radar will read the text of new notifications from your SMS and WhatsApp apps." to
                "سکیم ریڈار آپ کی ایس ایم ایس اور واٹس ایپ ایپس کی نئی نوٹیفیکیشنز کا متن پڑھے گا۔",
            "Each message is checked on this phone only. The app has no internet permission, so nothing is uploaded or shared." to
                "ہر پیغام صرف اسی فون پر جانچا جاتا ہے۔ ایپ کے پاس انٹرنیٹ کی اجازت ہی نہیں، اس لیے کچھ بھی اپ لوڈ یا شیئر نہیں ہوتا۔",
            "Messages are not saved. If one looks like a scam, you get a warning notification." to
                "پیغامات محفوظ نہیں کیے جاتے۔ اگر کوئی پیغام فراڈ لگے تو آپ کو وارننگ ملے گی۔",
            "Notifications from other apps are ignored." to "دوسری ایپس کی نوٹیفیکیشنز کو نظر انداز کیا جاتا ہے۔",
            "You can turn this off any time in Scam Radar settings or in Android's notification access settings." to
                "آپ اسے کسی بھی وقت سکیم ریڈار کی سیٹنگز یا اینڈرائیڈ کی نوٹیفیکیشن ایکسیس سیٹنگز سے بند کر سکتے ہیں۔",
        )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp).testTag("disclosure"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarLogo(44.dp)
            Spacer(Modifier.width(12.dp))
            Text("Before you turn this on", style = MaterialTheme.typography.titleLarge)
        }
        Panel {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Check messages automatically?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "کیا پیغامات خود بخود چیک کیے جائیں؟",
                    style = MaterialTheme.typography.headlineSmall.merge(UrduStyle),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Panel {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                points.forEachIndexed { i, (en, ur) ->
                    Row(verticalAlignment = Alignment.Top) {
                        NumberBadge(i + 1, MaterialTheme.colorScheme.primaryContainer, Navy)
                        Spacer(Modifier.width(12.dp))
                        BilingualText(ur, en, MaterialTheme.colorScheme.onSurface, urSize = 18, enSize = 15, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Text(
            "On the next screen, Android will ask you to allow notification access for Scam Radar.",
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            color = Color(0xFF4A5565),
        )
        Button(onClick = onAgree, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("disclosure-agree"), shape = RoundedCornerShape(18.dp)) {
            Text("I understand, continue · سمجھ گیا، آگے چلیں", fontSize = 16.sp)
        }
        OutlinedButton(onClick = onDecline, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("disclosure-decline"), shape = RoundedCornerShape(18.dp)) {
            Text("Not now · ابھی نہیں")
        }
    }
}
