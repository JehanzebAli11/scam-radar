package io.github.jehanzebali11.scamradar.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.llm.ModelState
import io.github.jehanzebali11.scamradar.notify.ProtectionLog

@Composable
fun HomeScreen(
    state: CheckUiState,
    modelState: ModelState,
    reportEnabled: Boolean,
    onInputChange: (String) -> Unit,
    onPaste: () -> Unit,
    onCheck: () -> Unit,
    onClear: () -> Unit,
    onSendToFamily: () -> Unit,
    onReport: () -> Unit,
    onOpenSettings: () -> Unit,
    protection: ProtectionUi,
    onTurnOnProtection: () -> Unit,
    onOpenWarning: (ProtectionLog.Warning) -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarLogo()
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Scam Radar", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
                Text("سکیم ریڈار", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag("open-settings"), shape = RoundedCornerShape(14.dp)) { Text("Settings") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill("Offline · on this phone", Color(0xFF2E7D32))
            when (modelState) {
                ModelState.Loading -> StatusPill("Loading model…", Color(0xFF9E9E9E))
                is ModelState.Ready -> StatusPill("Model ready (${modelState.backend})", Navy)
                is ModelState.Missing, is ModelState.Failed -> StatusPill("Demo mode", Color(0xFFF59E0B), Modifier.testTag("model-banner"))
            }
        }

        ProtectionPanel(protection, onTurnOnProtection, onOpenWarning)

        // Manual check
        Panel {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Check a message yourself · خود چیک کریں", style = MaterialTheme.typography.titleMedium)
                TextField(
                    value = state.input,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp).testTag("input"),
                    placeholder = { Text("SMS or WhatsApp text…") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp, lineHeight = 27.sp),
                    shape = RoundedCornerShape(18.dp),
                    colors =
                        TextFieldDefaults.colors(
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    minLines = 4,
                )
                state.sender?.let { Text("From: $it", style = MaterialTheme.typography.bodyMedium) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onPaste, modifier = Modifier.testTag("paste")) { Text("Paste") }
                    TextButton(onClick = onClear) { Text("Clear") }
                    Spacer(Modifier.weight(1f))
                    Text("${state.input.length} chars", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(
                    onClick = onCheck,
                    enabled = !state.checking && state.input.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(58.dp).testTag("check"),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Check message · چیک کریں", fontSize = 18.sp, fontWeight = FontWeight.SemiBold) }
                if (state.checking) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Checking on this phone…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Examples
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Try an example · مثال آزمائیں", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EXAMPLES.forEach { ex ->
                    SuggestionChip(onClick = { onInputChange(ex.text) }, label = { Text(ex.label) }, shape = RoundedCornerShape(50))
                }
            }
        }

        AnimatedVisibility(
            visible = state.verdict != null,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 6 },
            exit = fadeOut(tween(150)),
        ) {
            state.verdict?.let { v ->
                ResultCard(verdict = v, onSendToFamily = onSendToFamily, onReport = if (reportEnabled) onReport else null)
            }
        }

        Text(
            "Messages never leave this phone. Scam Radar has no internet access.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            "پیغامات اس فون سے باہر نہیں جاتے۔ سکیم ریڈار کے پاس انٹرنیٹ کی رسائی نہیں ہے۔",
            style = MaterialTheme.typography.bodySmall.copy(textDirection = UrduStyle.textDirection, textAlign = UrduStyle.textAlign),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
