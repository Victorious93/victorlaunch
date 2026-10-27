package com.victorious.launch.theme

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.victorious.launch.core.theme.FleetPalette
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.core.theme.FleetThemeSnapshot
import com.victorious.launch.prefs.AppSettings

object FleetThemeResolver {
    fun current(context: Context, settings: AppSettings): FleetThemeSnapshot {
        val mode = settings.themeMode()
        val you = if (mode == FleetThemeMode.MATERIAL_YOU) materialYouPalette(context) else null
        return FleetThemeSnapshot(
            mode = mode,
            iconStyle = settings.iconStyle(),
            iconPackPackage = settings.iconPackPackage(),
            palette = FleetThemePalettes.paletteFor(mode, you),
            updatedAtMs = settings.themeUpdatedAtMs(),
        )
    }

    fun materialYouPalette(context: Context): FleetPalette? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return try {
            val night =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            fun color(id: Int): String = "#%08X".format(context.getColor(id))
            if (night) {
                FleetPalette(
                    background = color(android.R.color.system_neutral1_900),
                    surface = color(android.R.color.system_neutral1_800),
                    primary = color(android.R.color.system_accent1_200),
                    secondary = color(android.R.color.system_accent2_200),
                    onBackground = color(android.R.color.system_neutral1_50),
                    muted = color(android.R.color.system_neutral2_200),
                    danger = FleetThemePalettes.DARK.danger,
                    stale = color(android.R.color.system_accent3_200),
                    tile = color(android.R.color.system_neutral1_700),
                )
            } else {
                FleetPalette(
                    background = color(android.R.color.system_neutral1_10),
                    surface = color(android.R.color.system_neutral1_0),
                    primary = color(android.R.color.system_accent1_600),
                    secondary = color(android.R.color.system_accent2_600),
                    onBackground = color(android.R.color.system_neutral1_900),
                    muted = color(android.R.color.system_neutral2_600),
                    danger = FleetThemePalettes.LIGHT.danger,
                    stale = color(android.R.color.system_accent3_600),
                    tile = color(android.R.color.system_neutral1_50),
                )
            }
        } catch (_: Exception) {
            null
        }
    }
}
