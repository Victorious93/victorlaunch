package com.victorious.launch.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.victorious.launch.core.theme.FleetPalette
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.core.theme.FleetThemeSnapshot

val Charcoal = Color(0xFF0B0C12)
val CharcoalElevated = Color(0xFF161821)
val Cyan = Color(0xFF00E5FF)
val Magenta = Color(0xFFE840FF)
val OnDark = Color(0xFFF5F7FF)
val Muted = Color(0xFF9AA3B5)
val Tile = Color(0xCC1A1D2B)
val Danger = Color(0xFFFF6B8A)
val StaleAmber = Color(0xFFFFC14D)

data class FleetUiColors(
    val background: Color,
    val surface: Color,
    val primary: Color,
    val secondary: Color,
    val onBackground: Color,
    val muted: Color,
    val danger: Color,
    val stale: Color,
    val tile: Color,
) {
    companion object {
        val Neon = from(FleetThemePalettes.NEON)

        fun from(palette: FleetPalette): FleetUiColors =
            FleetUiColors(
                background = palette.background.toComposeColor(),
                surface = palette.surface.toComposeColor(),
                primary = palette.primary.toComposeColor(),
                secondary = palette.secondary.toComposeColor(),
                onBackground = palette.onBackground.toComposeColor(),
                muted = palette.muted.toComposeColor(),
                danger = palette.danger.toComposeColor(),
                stale = palette.stale.toComposeColor(),
                tile = palette.tile.toComposeColor(),
            )
    }
}

val LocalFleetColors = staticCompositionLocalOf { FleetUiColors.Neon }

fun String.toComposeColor(): Color = Color(FleetThemePalettes.parseArgb(this))

@Composable
fun VictorLaunchTheme(
    snapshot: FleetThemeSnapshot? = null,
    content: @Composable () -> Unit,
) {
    val palette = snapshot?.palette ?: FleetThemePalettes.NEON
    val ui = FleetUiColors.from(palette)
    val light = snapshot?.mode == FleetThemeMode.LIGHT
    val scheme =
        if (light) {
            lightColorScheme(
                primary = ui.primary,
                onPrimary = ui.background,
                secondary = ui.secondary,
                onSecondary = ui.background,
                background = ui.background,
                onBackground = ui.onBackground,
                surface = ui.surface,
                onSurface = ui.onBackground,
                surfaceVariant = ui.tile,
                onSurfaceVariant = ui.muted,
                error = ui.danger,
                onError = ui.background,
                outline = ui.muted,
            )
        } else {
            darkColorScheme(
                primary = ui.primary,
                onPrimary = ui.background,
                secondary = ui.secondary,
                onSecondary = ui.background,
                background = ui.background,
                onBackground = ui.onBackground,
                surface = ui.surface,
                onSurface = ui.onBackground,
                surfaceVariant = ui.tile,
                onSurfaceVariant = ui.muted,
                error = ui.danger,
                onError = ui.background,
                outline = ui.muted,
            )
        }
    CompositionLocalProvider(LocalFleetColors provides ui) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
