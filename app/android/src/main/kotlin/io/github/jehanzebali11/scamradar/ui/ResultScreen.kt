package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.verdict.Verdict

/**
 * Full-screen result for a message that arrived on its own (automatic warning
 * or "Share to Scam Radar"): who sent it, what it said, and the big card.
 */
@Composable
fun ResultScreen(
    verdict: Verdict,
    message: String,
    sender: String?,
    origin: Origin,
    reportEnabled: Boolean,
    onSendToFamily: () -> Unit,
    onReport: () -> Unit,
    onDone: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp).testTag("result-page"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarLogo(40.dp)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(
                    if (origin == Origin.ALERT) "New message checked" else "Shared message checked",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    if (origin == Origin.ALERT) "نیا پیغام چیک ہوا" else "شیئر کیا گیا پیغام چیک ہوا",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    sender?.let { "From $it · بھیجنے والا" } ?: "The message · پیغام",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(message, fontSize = 17.sp, lineHeight = 25.sp)
            }
        }
        ResultCard(verdict = verdict, onSendToFamily = onSendToFamily, onReport = if (reportEnabled) onReport else null)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp).testTag("done"), shape = RoundedCornerShape(18.dp)) {
            Text("Done · ٹھیک ہے", fontSize = 17.sp)
        }
    }
}
