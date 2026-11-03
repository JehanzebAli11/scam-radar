package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.jehanzebali11.scamradar.share.ShareIntents

/** Shows the prepared report. Copy / share / open SMS app; never sends by itself. */
@Composable
fun ReportDialog(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val sms = ShareIntents.REPORT_SMS_NUMBER
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report this scam · رپورٹ") },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text("Scam Radar only prepares this text. Nothing is sent unless you send it yourself.")
                Text("سکیم ریڈار صرف یہ تحریر تیار کرتا ہے۔ جب تک آپ خود نہ بھیجیں، کچھ نہیں جاتا۔")
                Text("\n" + report)
            }
        },
        confirmButton = {
            if (sms != null) {
                TextButton(onClick = { ShareIntents.openSmsComposer(context, sms, report); onDismiss() }) { Text("Open SMS app") }
            } else {
                TextButton(onClick = { ShareIntents.shareText(context, report, "Report with…"); onDismiss() }) { Text("Share…") }
            }
        },
        dismissButton = {
            TextButton(onClick = { ShareIntents.copy(context, "Scam report", report); onDismiss() }) { Text("Copy") }
        },
    )
}
