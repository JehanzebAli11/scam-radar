package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import io.github.jehanzebali11.scamradar.llm.ModelState

data class SettingsState(
    val modelState: ModelState,
    val listenerAccessGranted: Boolean,
    val watchNotifications: Boolean,
    val canPostAlerts: Boolean,
    val reportEnabled: Boolean,
)

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
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onBack) { Text("Back") }
        }

        Text("Model", style = MaterialTheme.typography.titleMedium)
        Text(
            when (val m = s.modelState) {
                ModelState.Loading -> "Loading…"
                is ModelState.Ready -> "On-device model loaded (${m.backend})."
                is ModelState.Missing ->
                    "No model file. Push scamradar.litertlm to one of:\n" + m.lookedIn.joinToString("\n")
                is ModelState.Failed -> "Model failed to load: ${m.message}"
            },
            modifier = Modifier.testTag("model-status"),
        )
        OutlinedButton(onClick = onReloadModel) { Text("Look for the model again") }

        HorizontalDivider()
        Text("Check SMS and WhatsApp notifications", style = MaterialTheme.typography.titleMedium)
        if (!s.listenerAccessGranted) {
            Text("Off. Scam Radar needs notification access for this.")
            Button(onClick = onSetUpWatching, modifier = Modifier.testTag("setup-watching")) { Text("Set up…") }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Check new messages", modifier = Modifier.weight(1f))
                Switch(checked = s.watchNotifications, onCheckedChange = onWatchChange, modifier = Modifier.testTag("watch-switch"))
            }
            if (!s.canPostAlerts) Text("Allow notifications for Scam Radar so it can warn you.")
        }

        HorizontalDivider()
        Text("Reporting", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Show \"Report this scam\" on warnings. It only prepares a message for you to review and send yourself.",
                modifier = Modifier.weight(1f),
            )
            Switch(checked = s.reportEnabled, onCheckedChange = onReportChange, modifier = Modifier.testTag("report-switch"))
        }

        HorizontalDivider()
        Text(
            "Scam Radar has no internet permission. Messages are checked on this phone and are never stored or uploaded.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
