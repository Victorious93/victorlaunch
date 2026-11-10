package com.victorious.launch.settings

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetPalette
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.launcher.IconPackManager
import com.victorious.launch.launcher.toImageBitmap
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors

@Composable
fun ThemeGalleryScreen(
    settings: AppSettings,
    activity: Activity,
    onClose: () -> Unit,
    onDockIcons: () -> Unit = {},
) {
    val colors = LocalFleetColors.current
    val packs = remember { IconPackManager(activity.packageManager).installedPacks() }
    val packManager = remember { IconPackManager(activity.packageManager) }
    var mode by remember { mutableStateOf(settings.themeMode()) }
    var pack by remember { mutableStateOf(settings.iconPackPackage()) }
    var note by remember { mutableStateOf<String?>(null) }

    fun applyMode(next: FleetThemeMode, label: String) {
        settings.setThemeMode(next)
        mode = next
        note = "Theme: $label"
    }

    fun applyPack(packageName: String?) {
        settings.setIconPackPackage(packageName)
        pack = packageName
        note = if (packageName == null) "Using system icons" else "Icon pack applied"
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Theme gallery", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        Text(
            "One tap applies a palette to VictorLaunch and publishes it to other fleet apps over the existing theme provider (schema v1). Icon packs are previewed from the pack's appfilter; nothing is vendored.",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Text("Palettes", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 8.dp))
        PresetRow("Neon ring", "Charcoal #0B0F1A, cyan #00E5FF, magenta #FF2BD6", FleetThemePalettes.RING, mode == FleetThemeMode.NEON_RING) {
            applyMode(FleetThemeMode.NEON_RING, "Neon ring")
        }
        PresetRow("Neon fleet", "Current default neon", FleetThemePalettes.NEON, mode == FleetThemeMode.NEON_FLEET) {
            applyMode(FleetThemeMode.NEON_FLEET, "Neon fleet")
        }
        PresetRow("Dark", "Neutral dark", FleetThemePalettes.DARK, mode == FleetThemeMode.DARK) {
            applyMode(FleetThemeMode.DARK, "Dark")
        }
        PresetRow("Light", "Light surfaces", FleetThemePalettes.LIGHT, mode == FleetThemeMode.LIGHT) {
            applyMode(FleetThemeMode.LIGHT, "Light")
        }
        PresetRow(
            "Material You",
            "System accents on Android 12+, otherwise neon",
            FleetThemePalettes.paletteFor(FleetThemeMode.MATERIAL_YOU, com.victorious.launch.theme.FleetThemeResolver.materialYouPalette(activity)),
            mode == FleetThemeMode.MATERIAL_YOU,
        ) { applyMode(FleetThemeMode.MATERIAL_YOU, "Material You") }

        Text("Icon packs", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        PackRow("System icons", "Launcher default", emptyList(), pack == null, packIcon = null) { applyPack(null) }
        packs.forEach { installed ->
            val previews =
                remember(installed.packageName) {
                    packManager.previewIcons(installed.packageName).map { it.toImageBitmap() }
                }
            val icon = remember(installed.packageName) { packManager.packIcon(installed.packageName)?.toImageBitmap() }
            PackRow(installed.label, installed.packageName, previews, pack == installed.packageName, icon) {
                applyPack(installed.packageName)
            }
        }
        if (packs.isEmpty()) {
            Text("No ADW/GO/Nova/Atom icon packs installed.", color = colors.muted, fontSize = 12.sp)
        }
        TextButton(onClick = onDockIcons, modifier = Modifier.padding(top = 8.dp)) {
            Text("Dock & icon size…", color = colors.primary)
        }
        note?.let { Text(it, color = colors.secondary, modifier = Modifier.padding(top = 12.dp)) }
    }
}

@Composable
private fun PresetRow(title: String, subtitle: String, palette: FleetPalette, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFleetColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colorOf(palette.tile))
                .then(if (selected) Modifier.border(2.dp, colorOf(palette.primary), RoundedCornerShape(12.dp)) else Modifier)
                .clickable(onClick = onClick)
                .padding(12.dp),
    ) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Swatch(palette.background)
            Swatch(palette.primary)
            Swatch(palette.secondary)
            Column(Modifier.padding(start = 12.dp)) {
                Text(if (selected) "● $title" else title, color = colorOf(palette.onBackground), fontSize = 15.sp)
                Text(subtitle, color = colorOf(palette.muted), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun PackRow(
    title: String,
    subtitle: String,
    previews: List<androidx.compose.ui.graphics.ImageBitmap>,
    selected: Boolean,
    packIcon: androidx.compose.ui.graphics.ImageBitmap?,
    onClick: () -> Unit,
) {
    val colors = LocalFleetColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.tile)
                .then(if (selected) Modifier.border(2.dp, colors.primary, RoundedCornerShape(12.dp)) else Modifier)
                .clickable(onClick = onClick)
                .padding(12.dp),
    ) {
        if (packIcon != null) {
            Image(packIcon, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
            Text(if (selected) "● $title" else title, color = colors.onBackground, fontSize = 15.sp)
            Text(subtitle, color = colors.muted, fontSize = 11.sp)
        }
        previews.forEach { bmp ->
            Image(bmp, contentDescription = null, modifier = Modifier.size(32.dp).padding(start = 4.dp).clip(CircleShape), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
private fun Swatch(hex: String) {
    Box(
        Modifier
            .padding(end = 6.dp)
            .size(22.dp)
            .clip(CircleShape)
            .background(colorOf(hex)),
    )
}

private fun colorOf(hex: String): Color = Color(FleetThemePalettes.parseArgb(hex))
