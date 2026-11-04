package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.verdict.Severity

val UrduStyle = TextStyle(textDirection = TextDirection.Rtl, textAlign = TextAlign.Right)

/** Urdu line above its English line. Urdu is right-aligned and a little larger. */
@Composable
fun BilingualText(
    ur: String,
    en: String,
    color: Color,
    urSize: Int,
    enSize: Int,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
) {
    val weight = if (bold) FontWeight.Bold else FontWeight.Normal
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            ur,
            color = color,
            fontSize = urSize.sp,
            lineHeight = (urSize * 1.65).sp,
            fontWeight = weight,
            style = UrduStyle,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(en, color = color, fontSize = enSize.sp, lineHeight = (enSize * 1.35).sp, fontWeight = weight)
    }
}

/** The app mark: radar rings with a sweep, drawn so it needs no image asset. */
@Composable
fun RadarLogo(size: Dp = 52.dp) {
    Canvas(Modifier.size(size).clip(RoundedCornerShape(size / 3.5f)).background(Navy)) {
        val c = center
        val r = this.size.minDimension / 2
        drawCircle(Color.White.copy(alpha = 0.9f), r * 0.62f, c, style = Stroke(r * 0.09f))
        drawCircle(Color.White.copy(alpha = 0.6f), r * 0.32f, c, style = Stroke(r * 0.09f))
        drawLine(Sun, c, Offset(c.x + r * 0.5f, c.y - r * 0.5f), r * 0.12f, StrokeCap.Round)
        drawCircle(Color(0xFFEE964B), r * 0.1f, Offset(c.x + r * 0.32f, c.y + r * 0.3f))
    }
}

/** Round badge with a check (green), exclamation (amber) or cross (red). */
@Composable
fun SeverityIcon(severity: Severity, size: Dp = 56.dp) {
    val colors = severityColors(severity)
    Canvas(Modifier.size(size).clip(CircleShape).background(colors.onStrong)) {
        val s = this.size.minDimension
        val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        when (severity) {
            Severity.GREEN -> {
                val p = Path().apply {
                    moveTo(s * 0.28f, s * 0.52f)
                    lineTo(s * 0.44f, s * 0.67f)
                    lineTo(s * 0.73f, s * 0.36f)
                }
                drawPath(p, colors.strong, style = stroke)
            }
            Severity.AMBER -> {
                drawLine(colors.accent, Offset(s / 2, s * 0.26f), Offset(s / 2, s * 0.56f), s * 0.11f, StrokeCap.Round)
                drawCircle(colors.accent, s * 0.065f, Offset(s / 2, s * 0.73f))
            }
            Severity.RED -> {
                drawLine(colors.strong, Offset(s * 0.33f, s * 0.33f), Offset(s * 0.67f, s * 0.67f), s * 0.11f, StrokeCap.Round)
                drawLine(colors.strong, Offset(s * 0.67f, s * 0.33f), Offset(s * 0.33f, s * 0.67f), s * 0.11f, StrokeCap.Round)
            }
        }
    }
}

/** Small rounded label with a coloured dot, e.g. "Offline" or "Demo mode". */
@Composable
fun StatusPill(text: String, dot: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 1.dp) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dot))
            Text(text, Modifier.padding(start = 8.dp), fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/** White rounded panel used for every section. */
@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp,
        content = content,
    )
}

/** Numbered circle used in lists (disclosure points, advice). */
@Composable
fun NumberBadge(n: Int, bg: Color, fg: Color) {
    Box(Modifier.size(28.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text("$n", color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
