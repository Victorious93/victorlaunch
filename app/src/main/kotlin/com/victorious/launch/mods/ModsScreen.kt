package com.victorious.launch.mods

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted
import java.io.File

@Composable
fun ModsScreen(
    settings: AppSettings,
    catalog: AppCatalog,
    activity: Activity,
    onClose: () -> Unit,
) {
    var master by remember { mutableStateOf(settings.isModsMasterEnabled()) }
    var enabledMap by remember {
        mutableStateOf(ModsTool.entries.associateWith { settings.isModEnabled(it) })
    }
    var confirmMaster by remember { mutableStateOf(false) }
    var pendingTool by remember { mutableStateOf<ModsTool?>(null) }
    var pendingRootInstall by remember { mutableStateOf<RootModuleKind?>(null) }
    var pendingZipPath by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    val installed =
        remember {
            ModsIntents.installedPackages(
                activity.packageManager,
                ModsTool.entries.flatMap { it.stockPackages },
            )
        }
    val patchedApkPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            note =
                try {
                    PatchedApkInstaller.startSession(activity, uri)
                } catch (e: Exception) {
                    e.message ?: "install session failed"
                }
        }
    val moduleZipPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val kind = pendingRootInstall
            if (uri == null || kind == null) {
                pendingRootInstall = null
                return@rememberLauncherForActivityResult
            }
            pendingRootInstall = null
            val copied = copyZipForRootInstall(activity, uri)
            if (copied == null) {
                note = "Could not copy the zip into app storage"
                return@rememberLauncherForActivityResult
            }
            val err = RootModuleInstallRequest(kind, copied).validate()
            if (err != null) {
                note = err
                return@rememberLauncherForActivityResult
            }
            pendingZipPath = copied
            pendingRootInstall = kind
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mods", color = Cyan, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        }
        Text(
            "Off by default. LSPosed, LSPatch, Geto and MMRL are GPLv3. This build talks to the stock apps; the full engines are not vendored. Enable the master switch, then each tool. Nothing here was tested on a device.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            "Geto = JackEblan/Geto (com.android.geto), ${GetoIdentification.CONFIDENCE_PERCENT}% confidence. ${GetoIdentification.RATIONALE}",
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text("Enable Mods", color = Cyan, modifier = Modifier.weight(1f))
            Switch(
                checked = master,
                onCheckedChange = { on ->
                    if (on) confirmMaster = true
                    else {
                        master = false
                        settings.setModsMasterEnabled(false)
                    }
                },
            )
        }
        ModsCatalog.ALL.forEach { spec ->
            ToolCard(
                spec = spec,
                master = master,
                enabled = enabledMap[spec.tool] == true,
                installed = ModsCatalog.detectInstalled(spec.tool, installed),
                catalog = catalog,
                activity = activity,
                onToggle = { on ->
                    if (on) pendingTool = spec.tool
                    else {
                        settings.setModEnabled(spec.tool, false)
                        enabledMap = enabledMap + (spec.tool to false)
                    }
                },
                onNote = { note = it },
                onPickPatchedApk = {
                    patchedApkPicker.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                },
                onPickRootZip = { kind ->
                    pendingRootInstall = kind
                    moduleZipPicker.launch(arrayOf("application/zip", "*/*"))
                },
            )
        }
        val current = note
        if (current != null) {
            Text(current, color = Magenta, modifier = Modifier.padding(top = 12.dp))
        }
        if (confirmMaster) {
            AlertDialog(
                onDismissRequest = { confirmMaster = false },
                title = { Text("Enable Mods?") },
                text = {
                    Text(
                        "This unlocks LSPosed, LSPatch, Geto and MMRL controls. They can change other apps and the system. Each tool still needs its own switch. Full engines are not in this APK — stock apps are opened instead.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmMaster = false
                            master = true
                            settings.setModsMasterEnabled(true)
                        },
                    ) { Text("Enable") }
                },
                dismissButton = { TextButton(onClick = { confirmMaster = false }) { Text("Cancel") } },
            )
        }
        val tool = pendingTool
        if (tool != null) {
            val spec = ModsCatalog.spec(tool)
            AlertDialog(
                onDismissRequest = { pendingTool = null },
                title = { Text("Enable ${spec.tool.title}?") },
                text = { Text(spec.risks) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            settings.setModEnabled(tool, true)
                            enabledMap = enabledMap + (tool to true)
                            pendingTool = null
                        },
                    ) { Text("Enable") }
                },
                dismissButton = { TextButton(onClick = { pendingTool = null }) { Text("Cancel") } },
            )
        }
        val kind = pendingRootInstall
        val zip = pendingZipPath
        if (kind != null && zip != null) {
            AlertDialog(
                onDismissRequest = {
                    pendingRootInstall = null
                    pendingZipPath = null
                },
                title = { Text("Install ${kind.title} module?") },
                text = {
                    Text(
                        "This runs ${kind.title} against $zip as ADB/root via Shizuku. A bad zip can bootloop. This does not auto-run; confirm only if you trust the file.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            note = VictorLaunchApp.instance.privileged.installRootModule(kind, zip).detail
                            pendingRootInstall = null
                            pendingZipPath = null
                        },
                    ) { Text("Install") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            pendingRootInstall = null
                            pendingZipPath = null
                        },
                    ) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun ToolCard(
    spec: ModsToolSpec,
    master: Boolean,
    enabled: Boolean,
    installed: Boolean,
    catalog: AppCatalog,
    activity: Activity,
    onToggle: (Boolean) -> Unit,
    onNote: (String) -> Unit,
    onPickPatchedApk: () -> Unit,
    onPickRootZip: (RootModuleKind) -> Unit,
) {
    val gated = ModsGate.canUse(master, enabled)
    Text(spec.tool.title, color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
    Text("${spec.tool.license} · ${spec.tool.upstream}", color = Muted, fontSize = 11.sp)
    Text("Embed: ${spec.embedStatus}", color = Muted, fontSize = 12.sp)
    Text("Stock app ${if (installed) "installed" else "not installed"}", color = Cyan, fontSize = 12.sp)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Text("Enable ${spec.tool.title}", color = Cyan, modifier = Modifier.weight(1f))
        Switch(checked = enabled, enabled = master, onCheckedChange = onToggle)
    }
    Text("Root: ${spec.rootPath}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    Text("Non-root: ${spec.nonRootPath}", color = Muted, fontSize = 12.sp)
    Text("Risks: ${spec.risks}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
    OutlinedButton(
        onClick = {
            if (!gated) {
                onNote("Enable Mods and ${spec.tool.title} first")
                return@OutlinedButton
            }
            val launch = ModsIntents.firstInstalledLauncher(activity, spec.tool)
            if (launch != null) {
                activity.startActivity(launch)
                onNote("Opened ${spec.tool.title}")
            } else {
                onNote("${spec.tool.title} is not installed. Install the stock GPLv3 app, then return.")
            }
        },
        modifier = Modifier.padding(top = 8.dp),
    ) { Text("Open stock ${spec.tool.title}") }
    when (spec.tool) {
        ModsTool.LSPOSED -> {
            OutlinedButton(
                onClick = {
                    if (!gated) {
                        onNote("Enable Mods and LSPosed first")
                        return@OutlinedButton
                    }
                    val listed = VictorLaunchApp.instance.privileged.listAdbModules()
                    val detected = ModsCatalog.detectLsposedModules(listed.detail.split('\n', ' ', '\t'))
                    onNote(
                        "ADB modules (Shizuku/root): ${listed.detail}. LSPosed dir ${if (detected) "detected" else "not detected"}. Module/scope UI stays in the stock manager.",
                    )
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Detect LSPosed / list modules (privileged)") }
        }
        ModsTool.LSPATCH -> {
            var target by remember { mutableStateOf(catalog.personal().firstOrNull()?.packageName ?: "com.android.settings") }
            Text("Target package: $target", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            catalog.personal().take(8).forEach { app ->
                TextButton(onClick = { target = app.packageName }) {
                    Text(app.label, color = if (app.packageName == target) Cyan else Muted, fontSize = 12.sp)
                }
            }
            OutlinedButton(
                onClick = {
                    if (!gated) {
                        onNote("Enable Mods and LSPatch first")
                        return@OutlinedButton
                    }
                    val req = LspatchRequest(target, listOf("org.lsposed.manager"))
                    val err = req.validate()
                    if (err != null) {
                        onNote(err)
                        return@OutlinedButton
                    }
                    val path = VictorLaunchApp.instance.privileged.packagePath(req.targetPackage)
                    onNote(
                        "LSPatch rewriter is not embedded. ${path.detail}. Open stock LSPatch to patch, then use Install patched APK so Android asks you to confirm.",
                    )
                    ModsIntents.firstInstalledLauncher(activity, ModsTool.LSPATCH)?.let { activity.startActivity(it) }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Resolve APK path + open LSPatch") }
            OutlinedButton(
                onClick = {
                    if (!gated) {
                        onNote("Enable Mods and LSPatch first")
                        return@OutlinedButton
                    }
                    onPickPatchedApk()
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Install patched APK (you confirm)") }
            OutlinedButton(
                onClick = {
                    activity.startActivity(
                        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(
                            Uri.parse("package:${activity.packageName}"),
                        ),
                    )
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Unknown-sources permission") }
        }
        ModsTool.GETO -> {
            OutlinedButton(
                onClick = {
                    if (!gated) {
                        onNote("Enable Mods and Geto first")
                        return@OutlinedButton
                    }
                    val req = GetoGrantRequest()
                    val err = req.validate()
                    if (err != null) {
                        onNote(err)
                        return@OutlinedButton
                    }
                    val granted = VictorLaunchApp.instance.privileged.grantWriteSecureSettings(req.packageName)
                    onNote("WRITE_SECURE_SETTINGS: ${granted.detail}")
                    ModsIntents.firstInstalledLauncher(activity, ModsTool.GETO)?.let { activity.startActivity(it) }
                },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Grant WRITE_SECURE_SETTINGS (Shizuku/root)") }
        }
        ModsTool.MMRL -> {
            ModsCatalog.MMRL_READONLY_REPOS.forEach { repo ->
                Text("· $repo", color = Muted, fontSize = 12.sp)
            }
            Text(
                "Read-only names only — this APK has no INTERNET and does not fetch zips.",
                color = Muted,
                fontSize = 11.sp,
            )
            RootModuleKind.entries.forEach { kind ->
                OutlinedButton(
                    onClick = {
                        if (!gated) {
                            onNote("Enable Mods and MMRL first")
                            return@OutlinedButton
                        }
                        onPickRootZip(kind)
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Install zip via ${kind.title} (confirm)") }
            }
        }
    }
}

private fun copyZipForRootInstall(activity: Activity, uri: Uri): String? {
    val dir = activity.getExternalFilesDir("mods") ?: activity.filesDir
    if (!dir.exists() && !dir.mkdirs()) return null
    val dest = File(dir, "module.zip")
    return try {
        activity.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { input.copyTo(it) }
        } ?: return null
        dest.absolutePath
    } catch (_: Exception) {
        null
    }
}
