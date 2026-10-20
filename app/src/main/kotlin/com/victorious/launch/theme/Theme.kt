package com.victorious.launch.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Charcoal = Color(0xFF0B0C12)
val CharcoalElevated = Color(0xFF161821)
val Cyan = Color(0xFF00E5FF)
val Magenta = Color(0xFFE840FF)
val OnDark = Color(0xFFF5F7FF)
val Muted = Color(0xFF9AA3B5)
val Tile = Color(0xCC1A1D2B)
val Danger = Color(0xFFFF6B8A)
val StaleAmber = Color(0xFFFFC14D)

private val scheme =
    darkColorScheme(
        primary = Cyan,
        onPrimary = Charcoal,
        secondary = Magenta,
        onSecondary = Charcoal,
        background = Charcoal,
        onBackground = OnDark,
        surface = CharcoalElevated,
        onSurface = OnDark,
        surfaceVariant = Tile,
        onSurfaceVariant = Muted,
        error = Danger,
        onError = Charcoal,
        outline = Color(0xFF3A3F55),
    )

@Composable
fun VictorLaunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
