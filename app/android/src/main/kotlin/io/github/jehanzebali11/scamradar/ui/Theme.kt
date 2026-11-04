package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.jehanzebali11.scamradar.core.verdict.Severity

val Navy = Color(0xFF0D3B66)
val NavyDark = Color(0xFF082848)
val Sun = Color(0xFFF4D35E)
val Ink = Color(0xFF1A1C22)

/**
 * Traffic-light colours. [strong] is the header band, [tint] the card body,
 * [onStrong]/[onTint] the text on each. Same in light and dark mode so a red
 * card always looks like a red card.
 */
data class SeverityColors(val strong: Color, val onStrong: Color, val tint: Color, val onTint: Color, val accent: Color)

fun severityColors(s: Severity): SeverityColors =
    when (s) {
        Severity.RED -> SeverityColors(Color(0xFFC62828), Color.White, Color(0xFFFDECEA), Ink, Color(0xFFB71C1C))
        Severity.AMBER -> SeverityColors(Color(0xFFF59E0B), Ink, Color(0xFFFFF5DC), Ink, Color(0xFF9A5B00))
        Severity.GREEN -> SeverityColors(Color(0xFF2E7D32), Color.White, Color(0xFFE8F5E9), Ink, Color(0xFF1B5E20))
    }

private val AppTypography =
    Typography().run {
        copy(
            headlineSmall = headlineSmall.copy(fontWeight = FontWeight.Bold),
            titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
            titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
            labelLarge = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        )
    }

@Composable
fun ScamRadarTheme(content: @Composable () -> Unit) {
    val scheme =
        if (isSystemInDarkTheme()) {
            darkColorScheme(
                primary = Color(0xFF9CC3EE),
                onPrimary = NavyDark,
                primaryContainer = Color(0xFF173E63),
                background = Color(0xFF0E1420),
                surface = Color(0xFF151C29),
                surfaceVariant = Color(0xFF1F2836),
            )
        } else {
            lightColorScheme(
                primary = Navy,
                onPrimary = Color.White,
                primaryContainer = Color(0xFFDCE8F6),
                onPrimaryContainer = NavyDark,
                background = Color(0xFFF3F6FA),
                surface = Color.White,
                surfaceVariant = Color(0xFFEDF1F7),
                onSurfaceVariant = Color(0xFF4A5565),
                outline = Color(0xFFC5CCD6),
            )
        }
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}
