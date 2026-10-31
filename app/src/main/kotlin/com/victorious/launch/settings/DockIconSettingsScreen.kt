package com.victorious.launch.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.launcher.HomeLayoutConfig
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors

/** Dock, icon and grid customization. Changes apply when you close this screen. */
@Composable
fun DockIconSettingsScreen(
    settings: AppSettings,
    onClose: () -> Unit,
) {
    val colors = LocalFleetColors.current
    var cfg by remember { mutableStateOf(settings.homeLayout()) }
    var homeCols by remember { mutableIntStateOf(settings.launcherPreferences().gridColumns.coerceIn(3, 6)) }

    fun save(next: HomeLayoutConfig) {
        cfg = next.clamped()
        settings.setHomeLayout(cfg)
    }

    @Composable
    fun Stepper(label: String, value: Int, range: IntRange, step: Int = 1, suffix: String = "", onChange: (Int) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(label, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = { onChange((value - step).coerceIn(range)) }, enabled = value > range.first) {
                Text("−", color = colors.primary, fontSize = 18.sp)
            }
            Text("$value$suffix", color = colors.primary, fontSize = 14.sp)
            TextButton(onClick = { onChange((value + step).coerceIn(range)) }, enabled = value < range.last) {
                Text("+", color = colors.primary, fontSize = 18.sp)
            }
        }
    }

    @Composable
    fun Toggle(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
            Text(label, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(checked = value, onCheckedChange = onChange)
        }
    }

    @Composable
    fun Header(text: String) {
        Text(text, color = colors.secondary, fontSize = 13.sp, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dock & icons", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }

        Header("Dock")
        Stepper("Icons per row", cfg.dockIcons, HomeLayoutConfig.DOCK_ICONS) { save(cfg.copy(dockIcons = it)) }
        Stepper("Rows", cfg.dockRows, HomeLayoutConfig.DOCK_ROWS) { save(cfg.copy(dockRows = it)) }
        Toggle("Swipe-up dock (hidden behind a handle)", cfg.dockSwipeUp) { save(cfg.copy(dockSwipeUp = it)) }
        Text(
            "Dock holds ${cfg.dockCapacity} apps. Pinning past that drops the oldest.",
            color = colors.muted,
            fontSize = 12.sp,
        )

        Header("Icons")
        Stepper("Icon size", cfg.iconScalePct, HomeLayoutConfig.ICON_SCALE, step = 10, suffix = "%") { save(cfg.copy(iconScalePct = it)) }
        Toggle("Show labels", cfg.showLabels) { save(cfg.copy(showLabels = it)) }
        if (cfg.showLabels) {
            Stepper("Label size", cfg.labelSizeSp, HomeLayoutConfig.LABEL_SP, suffix = "sp") { save(cfg.copy(labelSizeSp = it)) }
        }

        Header("Home grid")
        Stepper("Columns", homeCols, 3..6) {
            homeCols = it
            settings.setGridColumns(it)
        }
        Stepper("Rows", cfg.homeRows, HomeLayoutConfig.HOME_ROWS) { save(cfg.copy(homeRows = it)) }

        Header("App drawer grid")
        Stepper("Columns", cfg.drawerColumns, HomeLayoutConfig.DRAWER_COLUMNS) { save(cfg.copy(drawerColumns = it)) }
        Stepper("Rows per screen", cfg.drawerRows, HomeLayoutConfig.DRAWER_ROWS) { save(cfg.copy(drawerRows = it)) }

        TextButton(onClick = {
            save(HomeLayoutConfig())
            homeCols = 4
            settings.setGridColumns(4)
        }, modifier = Modifier.padding(top = 12.dp)) {
            Text("Reset to defaults", color = colors.muted)
        }
    }
}
