package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.text.AdviceText
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.notify.ProtectionLog

data class ProtectionUi(val on: Boolean, val checked: Int, val warnings: List<ProtectionLog.Warning>)

/**
 * The main panel: is automatic protection on, and what has it caught. When
 * off, one big button leads to the disclosure screen.
 */
@Composable
fun ProtectionPanel(p: ProtectionUi, onTurnOn: () -> Unit, onOpenWarning: (ProtectionLog.Warning) -> Unit) {
    val green = Color(0xFF2E7D32)
    Surface(
        Modifier.fillMaxWidth().testTag(if (p.on) "protection-on" else "protection-off"),
        shape = RoundedCornerShape(28.dp),
        color = if (p.on) green else Navy,
        contentColor = Color.White,
        shadowElevation = 3.dp,
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (p.on) SeverityIcon(Severity.GREEN, 52.dp) else RadarLogo(52.dp)
                Spacer(Modifier.width(14.dp))
                BilingualText(
                    ur = if (p.on) "خودکار حفاظت آن ہے" else "خودکار حفاظت بند ہے",
                    en = if (p.on) "Automatic protection is ON" else "Automatic protection is off",
                    color = Color.White,
                    urSize = 24,
                    enSize = 21,
                    bold = true,
                    modifier = Modifier.weight(1f),
                )
            }
            if (p.on) {
                BilingualText(
                    ur = "نئے ایس ایم ایس اور واٹس ایپ پیغامات آتے ہی چیک ہوتے ہیں۔ فراڈ لگے تو آپ کو وارننگ ملے گی۔",
                    en = "New SMS and WhatsApp messages are checked as they arrive. You get a warning if one looks like a scam.",
                    color = Color.White.copy(alpha = 0.95f),
                    urSize = 17,
                    enSize = 15,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("${p.checked}", "checked · چیک ہوئے", Modifier.weight(1f))
                    Stat("${p.warnings.size}", "warnings · وارننگز", Modifier.weight(1f))
                }
            } else {
                BilingualText(
                    ur = "ہر نیا پیغام آپ کے فون پر خود بخود چیک ہو گا۔ آپ کو کچھ پیسٹ کرنے کی ضرورت نہیں۔",
                    en = "Every new SMS and WhatsApp message is checked on your phone automatically. Nothing to paste.",
                    color = Color.White.copy(alpha = 0.95f),
                    urSize = 17,
                    enSize = 15,
                )
                Button(
                    onClick = onTurnOn,
                    modifier = Modifier.fillMaxWidth().height(58.dp).testTag("turn-on-protection"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Sun, contentColor = Ink),
                ) { Text("Turn on protection · حفاظت آن کریں", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
    if (p.on && p.warnings.isNotEmpty()) {
        Panel {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text(
                    "Recent warnings · حالیہ وارننگز",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
                p.warnings.take(3).forEachIndexed { i, w ->
                    WarningRow(w, Modifier.testTag("warning-$i").clickable { onOpenWarning(w) })
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = Color.White.copy(alpha = 0.16f), contentColor = Color.White) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 13.sp)
        }
    }
}

@Composable
private fun WarningRow(w: ProtectionLog.Warning, modifier: Modifier) {
    val c = severityColors(w.verdict.severity)
    val title = AdviceText.card(w.verdict).let { it.title ?: it.headline }
    Row(modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(c.strong))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text("${title.en} · ${title.ur}", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(
                (w.sender?.let { "$it: " }.orEmpty()) + w.text,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text("Open ›", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}
