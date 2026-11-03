package io.github.jehanzebali11.scamradar.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.github.jehanzebali11.scamradar.core.verdict.Severity

data class SeverityColors(val container: Color, val content: Color)

/** High-contrast traffic-light colours, the same in light and dark mode. */
fun severityColors(s: Severity): SeverityColors =
    when (s) {
        Severity.RED -> SeverityColors(Color(0xFFB71C1C), Color.White)
        Severity.AMBER -> SeverityColors(Color(0xFFFFB300), Color(0xFF1A1A1A))
        Severity.GREEN -> SeverityColors(Color(0xFF2E7D32), Color.White)
    }

@Composable
fun ScamRadarTheme(content: @Composable () -> Unit) {
    val scheme =
        if (isSystemInDarkTheme()) {
            darkColorScheme(primary = Color(0xFF90CAF9))
        } else {
            lightColorScheme(primary = Color(0xFF0D3B66))
        }
    MaterialTheme(colorScheme = scheme, content = content)
}
