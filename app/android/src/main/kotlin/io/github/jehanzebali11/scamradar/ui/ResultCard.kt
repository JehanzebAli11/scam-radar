package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.text.AdviceText
import io.github.jehanzebali11.scamradar.core.verdict.Severity
import io.github.jehanzebali11.scamradar.core.verdict.Verdict

/**
 * The big green / amber / red result. A solid header band carries the verdict;
 * the tinted body carries the advice. Every word comes from AdviceText.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultCard(
    verdict: Verdict,
    onSendToFamily: () -> Unit,
    onReport: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val card = AdviceText.card(verdict)
    val c = severityColors(verdict.severity)
    Card(
        modifier = modifier.fillMaxWidth().testTag("result-${verdict.severity.name.lowercase()}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = c.tint, contentColor = c.onTint),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
    ) {
        // Header band
        Column(
            Modifier.fillMaxWidth().background(c.strong).padding(horizontal = 20.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SeverityIcon(verdict.severity, size = 60.dp)
                Spacer(Modifier.width(16.dp))
                BilingualText(card.headline.ur, card.headline.en, c.onStrong, urSize = 30, enSize = 24, bold = true, modifier = Modifier.weight(1f))
            }
            card.title?.let {
                Surface(shape = RoundedCornerShape(16.dp), color = c.onStrong.copy(alpha = 0.16f), contentColor = c.onStrong) {
                    BilingualText(it.ur, it.en, c.onStrong, urSize = 21, enSize = 18, bold = true, modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
        }

        // Body
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (card.flagChips.isNotEmpty()) {
                Text("What we noticed · ہم نے کیا دیکھا", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    card.flagChips.forEach { chip ->
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White,
                            contentColor = c.accent,
                            border = BorderStroke(1.dp, c.accent.copy(alpha = 0.35f)),
                        ) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(c.strong))
                                Text("  ${chip.en} · ${chip.ur}", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }

            Text("What to do · کیا کریں", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
            card.advice.forEachIndexed { i, advice ->
                Row(verticalAlignment = Alignment.Top) {
                    NumberBadge(i + 1, c.strong, c.onStrong)
                    Spacer(Modifier.width(12.dp))
                    BilingualText(advice.ur, advice.en, c.onTint, urSize = 19, enSize = 16, modifier = Modifier.weight(1f))
                }
            }

            if (verdict.links.isNotEmpty() && verdict.severity != Severity.GREEN) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, c.accent.copy(alpha = 0.3f))) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Links found. Don't open them · لنک نہ کھولیں", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.accent)
                        verdict.links.forEach { Text(it.host, fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = Ink) }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onSendToFamily,
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("send-to-family"),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (verdict.severity == Severity.AMBER) Ink else c.strong, contentColor = Color.White),
                ) { Text("Send to family · گھر والوں کو بھیجیں", fontSize = 17.sp) }

                if (onReport != null && verdict.severity != Severity.GREEN) {
                    OutlinedButton(
                        onClick = onReport,
                        modifier = Modifier.fillMaxWidth().height(52.dp).testTag("report"),
                        shape = RoundedCornerShape(18.dp),
                        border = BorderStroke(1.5.dp, c.accent.copy(alpha = 0.6f)),
                    ) { Text("Report this scam · رپورٹ تیار کریں", color = c.accent, fontSize = 16.sp) }
                }
            }

            if (!verdict.usedRealModel) {
                Text(AdviceText.DEMO_MODE.en, fontSize = 12.sp, color = c.onTint.copy(alpha = 0.6f))
            }
            verdict.prediction?.let {
                Text(it.toLine(), fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = c.onTint.copy(alpha = 0.5f))
            }
        }
    }
}
