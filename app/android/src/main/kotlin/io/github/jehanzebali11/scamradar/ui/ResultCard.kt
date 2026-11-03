package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.text.AdviceText
import io.github.jehanzebali11.scamradar.core.text.Bilingual
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Verdict

private val UrduStyle = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right)

@Composable
private fun BilingualText(b: Bilingual, color: Color, urSize: Int, enSize: Int, bold: Boolean = false) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Text(
        b.ur,
        color = color,
        fontSize = urSize.sp,
        lineHeight = (urSize * 1.6).sp,
        fontWeight = weight,
        style = UrduStyle,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(b.en, color = color, fontSize = enSize.sp, lineHeight = (enSize * 1.3).sp, fontWeight = weight)
}

/** The big green / amber / red card. Every word on it is pre-written in AdviceText. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(
    verdict: Verdict,
    onSendToFamily: () -> Unit,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val card = AdviceText.card(verdict)
    val colors = severityColors(verdict.severity)
    Card(
        modifier = modifier.fillMaxWidth().testTag("result-${verdict.severity.name.lowercase()}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = colors.container, contentColor = colors.content),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BilingualText(card.headline, colors.content, urSize = 34, enSize = 28, bold = true)
            card.title?.let { BilingualText(it, colors.content, urSize = 24, enSize = 20, bold = true) }

            if (card.flagChips.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.flagChips.forEach { chip ->
                        Surface(shape = RoundedCornerShape(50), color = colors.content.copy(alpha = 0.18f), contentColor = colors.content) {
                            Text("${chip.ur} · ${chip.en}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 15.sp)
                        }
                    }
                }
            }

            HorizontalDivider(color = colors.content.copy(alpha = 0.3f))
            card.advice.forEach { BilingualText(it, colors.content, urSize = 20, enSize = 17) }

            if (verdict.links.isNotEmpty() && verdict.severity != Severity.GREEN) {
                Text("Links in this message (don't open): " + verdict.links.joinToString { it.host }, color = colors.content, fontSize = 15.sp)
            }
            if (!verdict.usedRealModel) {
                BilingualText(AdviceText.DEMO_MODE, colors.content, urSize = 15, enSize = 14)
            }

            Button(
                onClick = onSendToFamily,
                modifier = Modifier.fillMaxWidth().testTag("send-to-family"),
                colors = ButtonDefaults.buttonColors(containerColor = colors.content, contentColor = colors.container),
            ) { Text("گھر والوں کو بھیجیں · Send to family", fontSize = 18.sp) }

            if (onReport != null && verdict.severity != Severity.GREEN) {
                OutlinedButton(onClick = onReport, modifier = Modifier.fillMaxWidth().testTag("report")) {
                    Text("رپورٹ تیار کریں · Report this scam", color = colors.content, fontSize = 16.sp)
                }
            }

            verdict.prediction?.let {
                Text("model: ${it.toLine()}", color = colors.content.copy(alpha = 0.7f), fontSize = 12.sp)
            }
        }
    }
}
