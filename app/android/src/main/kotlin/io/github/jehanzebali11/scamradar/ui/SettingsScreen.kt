package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.llm.ModelState

data class SettingsState(
    val modelState: ModelState,
    val listenerAccessGranted: Boolean,
    val watchNotifications: Boolean,
    val canPostAlerts: Boolean,
    val reportEnabled: Boolean,
)

@Composable
private fun Section(title: String, subtitle: String, content: @Composable () -> Unit) {
    Panel {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
fun SettingsScreen(
    s: SettingsState,
    onBack: () -> Unit,
    onReloadModel: () -> Unit,
    onSetUpWatching: () -> Unit,
    onWatchChange: (Boolean) -> Unit,
    onReportChange: (Boolean) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) { Text("Back") }
        }

        Section("Model", "The fine-tuned model runs on this phone. Nothing is downloaded.") {
            val (dot, text) =
                when (val m = s.modelState) {
                    ModelState.Loading -> Color(0xFF9E9E9E) to "Loading…"
                    is ModelState.Ready -> Color(0xFF2E7D32) to "On-device model loaded (${m.backend}). It is unloaded after a few quiet minutes to save memory."
                    ModelState.Asleep -> Color(0xFF2E7D32) to "Model unloaded to save memory and battery. It loads again when a message needs checking."
                    is ModelState.Missing -> Color(0xFFF59E0B) to "No model file yet, so the app runs in demo mode. Push scamradar.litertlm to one of the paths below."
                    is ModelState.Failed -> Color(0xFFC62828) to "Model failed to load: ${m.message}"
                }
            Row(verticalAlignment = Alignment.Top) {
                StatusPill(if (s.modelState is ModelState.Ready || s.modelState == ModelState.Asleep) "Ready" else "Demo", dot)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("model-status"))
            (s.modelState as? ModelState.Missing)?.lookedIn?.forEach {
                Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onReloadModel, shape = RoundedCornerShape(14.dp)) { Text("Look for the model again") }
        }

        Section("Check SMS and WhatsApp automatically", "Warns you when a new message looks like a scam. Off until you turn it on.") {
            if (!s.listenerAccessGranted) {
                Button(onClick = onSetUpWatching, modifier = Modifier.testTag("setup-watching"), shape = RoundedCornerShape(14.dp)) { Text("Set up…") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Check new messages", modifier = Modifier.weight(1f))
                    Switch(checked = s.watchNotifications, onCheckedChange = onWatchChange, modifier = Modifier.testTag("watch-switch"))
                }
                if (!s.canPostAlerts) Text("Allow notifications for Scam Radar so it can warn you.")
            }
        }

        Section("Report this scam", "Adds a button that prepares a report for you to review and send yourself. Nothing is sent automatically.") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Show the report button", modifier = Modifier.weight(1f))
                Spacer(Modifier.width(12.dp))
                Switch(checked = s.reportEnabled, onCheckedChange = onReportChange, modifier = Modifier.testTag("report-switch"))
            }
        }

        Section("Privacy", "Scam Radar has no internet permission. Messages are checked on this phone and are never stored or uploaded.") {}
    }
}
