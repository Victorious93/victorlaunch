package com.victorious.launch.dev

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.dev.DevNamespace
import com.victorious.launch.core.dev.DevOptions
import com.victorious.launch.core.dev.DevToggle
import com.victorious.launch.core.dev.LogFilter
import com.victorious.launch.core.dev.LogLevel
import com.victorious.launch.core.dev.LogLine
import com.victorious.launch.core.dev.Logcat
import com.victorious.launch.core.dev.RouteDecision
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors
import java.net.Inet4Address
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DevPanelScreen(
    settings: AppSettings,
    activity: Activity,
    onClose: () -> Unit,
) {
    val colors = LocalFleetColors.current
    val dev = remember { DevSettings(activity, settings) }
    val scope = rememberCoroutineScope()
    var rev by remember { mutableIntStateOf(0) }
    var note by remember { mutableStateOf<String?>(null) }
    var rootAllowed by remember { mutableStateOf(settings.isRootShellAllowed()) }
    var values by remember { mutableStateOf<Map<String, String?>>(emptyMap()) }
    var endpoint by remember { mutableStateOf<String?>(null) }

    val session = remember { LogcatSession() }
    DisposableEffect(Unit) { onDispose { session.stop() } }
    val lines by session.lines.collectAsState()
    val logStatus by session.status.collectAsState()
    var paused by remember { mutableStateOf(false) }
    var tag by remember { mutableStateOf("") }
    var pkg by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(LogLevel.VERBOSE) }
    var pids by remember { mutableStateOf<Set<Int>?>(null) }

    val exportLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val filtered = session.snapshot().filter(LogFilter(tag, level, pids, text)::matches)
            scope.launch {
                note =
                    withContext(Dispatchers.IO) {
                        try {
                            activity.contentResolver.openOutputStream(uri)?.use { it.write(Logcat.export(filtered).toByteArray()) }
                            "Exported ${filtered.size} lines"
                        } catch (e: Exception) {
                            "Export failed: ${e.message}"
                        }
                    }
            }
        }

    fun allToggles(): List<DevToggle> = listOf(DevOptions.WIRELESS_ADB, DevOptions.USB_DEBUGGING) + DevOptions.QUICK_TOGGLES

    LaunchedEffect(rev) {
        values =
            withContext(Dispatchers.IO) {
                val m = mutableMapOf<String, String?>()
                allToggles().forEach { m[it.id] = dev.read(it.namespace, it.key) }
                DevOptions.ANIMATION_KEYS.forEach { m[it] = dev.read(DevNamespace.GLOBAL, it) }
                m
            }
        endpoint =
            withContext(Dispatchers.IO) {
                val port = dev.readProp("service.adb.tls.port")
                    ?: dev.shell(DevOptions.readArgv(DevNamespace.PROP, "service.adb.tls.port")).takeIf { it.ok }?.detail
                DevOptions.wirelessEndpoint(wifiIpv4(activity), port)
            }
    }

    fun write(toggle: DevToggle, value: String) {
        scope.launch {
            val r = withContext(Dispatchers.IO) { dev.write(toggle.namespace, toggle.key, value, toggle.needsShell) }
            note = if (r.ok) "${toggle.title}: set" else "${toggle.title}: ${r.detail}"
            rev++
        }
    }

    val cap = dev.capability()
    val filter = LogFilter(tag, level, pids, text)
    val shown = remember(lines, filter) { lines.filter(filter::matches).takeLast(1500) }

    LazyColumn(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 16.dp)) {
                Text("Developer panel", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
            }
            Text(
                "Shizuku: ${if (cap.shizukuReady) "ready" else "not ready"} · " +
                    "WRITE_SECURE_SETTINGS: ${if (cap.hasWriteSecureSettings) "granted" else "no"} · " +
                    "READ_LOGS: ${if (dev.hasReadLogs()) "granted" else "no"}",
                color = colors.muted,
                fontSize = 12.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Allow root (runs su only for actions you tap here)",
                    color = colors.onBackground,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(checked = rootAllowed, onCheckedChange = {
                    rootAllowed = it
                    settings.setRootShellAllowed(it)
                    rev++
                })
            }
            Row {
                if (!cap.hasWriteSecureSettings) {
                    TextButton(onClick = {
                        scope.launch {
                            val r = withContext(Dispatchers.IO) { dev.grantToSelf(Manifest.permission.WRITE_SECURE_SETTINGS) }
                            note = if (r.ok) "WRITE_SECURE_SETTINGS granted" else "Grant failed: ${r.detail}"
                            rev++
                        }
                    }) { Text("Grant WSS", color = colors.secondary, fontSize = 12.sp) }
                }
                TextButton(onClick = {
                    activity.startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }) { Text("Developer options", color = colors.secondary, fontSize = 12.sp) }
            }
            note?.let { Text(it, color = colors.secondary, fontSize = 12.sp) }
        }

        item {
            Section("Wireless ADB")
            val t = DevOptions.WIRELESS_ADB
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                Text("Wireless debugging needs Android 11+.", color = colors.muted, fontSize = 12.sp)
            } else {
                ToggleRow(t, values[t.id], dev.decision(t.needsShell)) { on -> write(t, if (on) t.onValue else t.offValue) }
                Text(
                    if (t.isOn(values[t.id])) "adb connect ${endpoint ?: "<ip>:<port> (port not readable — see Developer options → Wireless debugging)"}"
                    else "Off",
                    color = colors.muted,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            val usb = DevOptions.USB_DEBUGGING
            Text(
                "USB debugging: ${if (usb.isOn(values[usb.id])) "on" else if (values[usb.id] == null) "unknown" else "off"} (change it in Developer options)",
                color = colors.onBackground,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        item {
            Section("Quick toggles")
            DevOptions.QUICK_TOGGLES.forEach { t ->
                ToggleRow(t, values[t.id], dev.decision(t.needsShell)) { on -> write(t, if (on) t.onValue else t.offValue) }
            }
            Section("Animation scales")
            val d = dev.decision(false)
            DevOptions.ANIMATION_KEYS.forEach { key ->
                Text(key.replace('_', ' '), color = colors.onBackground, fontSize = 13.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DevOptions.ANIMATION_SCALES.forEach { v ->
                        val cur = values[key]?.toFloatOrNull() ?: 1f
                        FilterChip(
                            selected = cur == v.toFloat(),
                            enabled = d is RouteDecision.Use,
                            onClick = {
                                write(DevToggle(key, key, DevNamespace.GLOBAL, key, v, v, needsShell = false), v)
                            },
                            label = { Text("${v}x", fontSize = 11.sp) },
                        )
                    }
                }
            }
            if (d is RouteDecision.Unavailable) Text(d.reason, color = colors.stale, fontSize = 11.sp)
        }

        item {
            Section("Logcat")
            Text(
                if (dev.hasReadLogs()) {
                    "READ_LOGS granted: all apps visible (Android 13+ may also ask you to allow log access once)."
                } else {
                    "Without READ_LOGS only VictorLaunch's own lines show. Grant it (needs Shizuku/root, or " +
                        "adb shell pm grant com.victorious.launch android.permission.READ_LOGS), then force-stop and reopen VictorLaunch."
                },
                color = colors.muted,
                fontSize = 12.sp,
            )
            if (!dev.hasReadLogs()) {
                TextButton(onClick = {
                    scope.launch {
                        val r = withContext(Dispatchers.IO) { dev.grantToSelf(Manifest.permission.READ_LOGS) }
                        note = if (r.ok) "READ_LOGS granted. Restart VictorLaunch to apply." else "Grant failed: ${r.detail}"
                    }
                }) { Text("Grant READ_LOGS", color = colors.secondary, fontSize = 12.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(tag, { tag = it }, label = { Text("Tag") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(text, { text = it }, label = { Text("Text") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(pkg, { pkg = it.trim() }, label = { Text("Package") }, singleLine = true, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (pkg.isBlank()) {
                        pids = null
                        return@TextButton
                    }
                    scope.launch {
                        pids =
                            withContext(Dispatchers.IO) {
                                val own = runCatching {
                                    val p = ProcessBuilder("pidof", pkg).start()
                                    Logcat.parsePidof(p.inputStream.bufferedReader().readText()).also { p.waitFor() }
                                }.getOrDefault(emptySet())
                                own.ifEmpty { dev.shell(listOf("pidof", pkg)).takeIf { it.ok }?.let { Logcat.parsePidof(it.detail) } ?: emptySet() }
                            }
                        note = if (pids.isNullOrEmpty()) "$pkg is not running (or its PID is hidden)" else "Filtering PIDs ${pids}"
                    }
                }) { Text(if (pkg.isBlank()) "All" else "Apply", color = colors.secondary) }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                LogLevel.entries.forEach { l ->
                    FilterChip(selected = level == l, onClick = { level = l }, label = { Text(l.letter.toString()) })
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { session.start() }) { Text("Start") }
                TextButton(onClick = {
                    paused = !paused
                    session.paused = paused
                }) { Text(if (paused) "Resume" else "Pause", color = colors.primary) }
                TextButton(onClick = { session.stop() }) { Text("Stop", color = colors.primary) }
                TextButton(onClick = { session.clear() }) { Text("Clear", color = colors.primary) }
                TextButton(onClick = {
                    val body = Logcat.export(session.snapshot().filter(filter::matches)).takeLast(200_000)
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body)
                    activity.startActivity(Intent.createChooser(send, "Share logcat"))
                }) { Text("Share", color = colors.primary) }
                TextButton(onClick = { exportLauncher.launch("logcat-${System.currentTimeMillis()}.txt") }) {
                    Text("Export", color = colors.primary)
                }
            }
            Text("$logStatus · ${shown.size} shown${if (paused) " · paused" else ""}", color = colors.muted, fontSize = 11.sp)
        }

        items(shown.asReversed()) { line -> LogRow(line) }
    }
}

@Composable
private fun Section(title: String) {
    Text(title, color = LocalFleetColors.current.secondary, fontSize = 15.sp, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
}

@Composable
private fun ToggleRow(toggle: DevToggle, current: String?, decision: RouteDecision, onChange: (Boolean) -> Unit) {
    val colors = LocalFleetColors.current
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(toggle.title, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Switch(
                checked = toggle.isOn(current),
                enabled = !toggle.readOnly && decision is RouteDecision.Use,
                onCheckedChange = onChange,
            )
        }
        if (decision is RouteDecision.Unavailable) {
            Text(decision.reason, color = colors.stale, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LogRow(line: LogLine) {
    val colors = LocalFleetColors.current
    val c =
        when (line.level) {
            LogLevel.ERROR, LogLevel.FATAL, LogLevel.ASSERT -> colors.danger
            LogLevel.WARN -> colors.stale
            LogLevel.INFO -> colors.onBackground
            null -> colors.muted
            else -> Color.Gray
        }
    Text(line.raw, color = c, fontSize = 10.sp, fontFamily = FontFamily.Monospace, lineHeight = 12.sp)
}

private fun wifiIpv4(context: Context): String? =
    try {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        cm.getLinkProperties(cm.activeNetwork)
            ?.linkAddresses
            ?.map { it.address }
            ?.firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    } catch (_: Exception) {
        null
    }
