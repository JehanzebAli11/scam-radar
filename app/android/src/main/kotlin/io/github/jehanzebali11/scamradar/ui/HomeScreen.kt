package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.llm.ModelState

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
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Scam Radar · سکیم ریڈار", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.testTag("open-settings")) { Text("Settings") }
        }
        ModelBanner(modelState)

        OutlinedTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp).testTag("input"),
            label = { Text("Paste the message here · پیغام یہاں پیسٹ کریں") },
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontSize = 18.sp),
            minLines = 4,
        )
        state.sender?.let { Text("From: $it", style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onCheck,
                enabled = !state.checking && state.input.isNotBlank(),
                modifier = Modifier.weight(1f).testTag("check"),
            ) { Text("Check · چیک کریں", fontSize = 18.sp) }
            OutlinedButton(onClick = onPaste, modifier = Modifier.testTag("paste")) { Text("Paste") }
            OutlinedButton(onClick = onClear) { Text("Clear") }
        }

        if (state.checking) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Text("Checking on this phone…")
            }
        }
        state.verdict?.let { v ->
            ResultCard(
                verdict = v,
                onSendToFamily = onSendToFamily,
                onReport = if (reportEnabled) onReport else null,
            )
        }
        Text("Scam Radar works offline. Messages never leave this phone.", style = MaterialTheme.typography.bodySmall)
        Text(
            "سکیم ریڈار آف لائن کام کرتا ہے۔ پیغام اس فون سے باہر نہیں جاتے۔",
            style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ModelBanner(modelState: ModelState) {
    val text =
        when (modelState) {
            ModelState.Loading -> "Loading the model…"
            is ModelState.Ready -> null
            is ModelState.Missing -> "Demo mode: no model file found, using a simple keyword check. See Settings."
            is ModelState.Failed -> "Demo mode: the model could not be loaded (${modelState.message}). See Settings."
        } ?: return
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium) {
        Text(text, Modifier.padding(12.dp).testTag("model-banner"))
    }
}
