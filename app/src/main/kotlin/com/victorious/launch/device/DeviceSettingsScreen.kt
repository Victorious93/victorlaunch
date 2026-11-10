package com.victorious.launch.device

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.device.DeviceCopy
import com.victorious.launch.core.device.DeviceWidgetKind
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors
import com.victorious.launch.widgets.DeviceWidgets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DeviceSettingsScreen(
    settings: AppSettings,
    activity: Activity,
    onClose: () -> Unit,
    onOpenDevPanel: () -> Unit = {},
) {
    val colors = LocalFleetColors.current
    var readings by remember { mutableStateOf<com.victorious.launch.core.device.DeviceReadings?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun load(updateWidgets: Boolean) {
        val next =
            withContext(Dispatchers.IO) {
                runCatching { DeviceSnapshotter.read(activity, settings, confirmSu = updateWidgets) }.getOrNull()
            }
        readings = next
        if (updateWidgets && next != null) {
            withContext(Dispatchers.IO) { DeviceWidgets.updateAll(activity, next) }
        }
        note = if (next == null) "Could not read device status" else if (updateWidgets) "Widgets updated" else null
    }

    LaunchedEffect(Unit) { load(updateWidgets = false) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Device widgets", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        Text(
            "Home-screen widgets for device info, root/Magisk/KernelSU status, and live resources (CPU, RAM, battery, thermal). " +
                "They refresh about once a minute and when you tap Refresh. Nothing here talks to the network.",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Root checks look at well-known file paths and package names only. " +
                "`su` is never started unless you turn on Dev panel → Allow root (currently " +
                if (settings.isRootShellAllowed()) "on)." else "off).",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        TextButton(onClick = onOpenDevPanel) { Text("Open Dev panel…", color = colors.primary) }
        val current = readings
        if (current != null) {
            DeviceWidgetKind.entries.forEach { kind ->
                val copy = DeviceCopy.of(kind, current)
                Text(copy.title, color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                Text(copy.line1, color = colors.onBackground, fontSize = 14.sp)
                Text(copy.line2, color = colors.muted, fontSize = 13.sp)
            }
        } else {
            Text("No reading yet.", color = colors.muted, modifier = Modifier.padding(top = 16.dp))
        }
        Button(onClick = { scope.launch { load(updateWidgets = true) } }, modifier = Modifier.padding(top = 16.dp)) { Text("Refresh now") }
        note?.let { Text(it, color = colors.secondary, modifier = Modifier.padding(top = 12.dp)) }
    }
}
