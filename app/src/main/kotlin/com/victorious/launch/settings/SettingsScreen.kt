package com.victorious.launch.settings

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import com.victorious.launch.core.backup.BackupConflictPolicy
import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.ImportReport
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.island.IslandCommandReceiver
import com.victorious.launch.island.IslandManager
import com.victorious.launch.core.share.FleetShareCatalog
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.launcher.IconPackManager
import com.victorious.launch.share.FleetSharePublisher
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.security.SigningCerts
import com.victorious.launch.shizuku.EmbeddedShizukuServer
import com.victorious.launch.shizuku.ShizukuStarters
import com.victorious.launch.shizuku.ShizukuStartupMode
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

@Composable
fun SettingsScreen(
    settings: AppSettings,
    catalog: AppCatalog,
    island: IslandManager,
    activity: Activity,
    onHelp: () -> Unit,
    onFleet: () -> Unit,
    onMods: () -> Unit,
    onNetwork: () -> Unit,
    onIsland: () -> Unit,
    onClose: () -> Unit,
) {
    var external by remember { mutableStateOf(settings.isExternalControlEnabled()) }
    var controllerFull by remember { mutableStateOf(settings.isControllerFullControlEnabled()) }
    var shizukuOn by remember { mutableStateOf(settings.isShizukuEnabled()) }
    var confirmShizuku by remember { mutableStateOf(false) }
    var islandLocked by remember { mutableStateOf(settings.launcherPreferences().islandTabLocked) }
    var note by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<ImportReport?>(null) }
    val pairing = settings.pairedController()
    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: run {
                        note = "Could not read file"
                        return@rememberLauncherForActivityResult
                    }
                pendingImport =
                    ConfigBackupCodec.import(
                        live = settings.launcherPreferences(),
                        incomingJson = text,
                        dryRun = true,
                        policy = BackupConflictPolicy.SKIP,
                        confirmed = false,
                    )
                note = "Dry-run complete. Confirm to apply."
            } catch (e: Exception) {
                note = e.message ?: "import failed"
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Settings", color = Cyan, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        }
        Section("Fleet theme") {
            Text(
                "VictorLaunch is the source of truth. Other fleet apps read the current palette from the exported ContentProvider and a change broadcast. Callers are checked by package+cert (the fleet does not share a signing key). See docs/specs/FLEET_THEME.md.",
                color = Muted,
                fontSize = 13.sp,
            )
            Text("Mode: ${settings.themeMode().name}", color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            ThemeChoice("Neon fleet palette", settings.themeMode() == FleetThemeMode.NEON_FLEET) {
                settings.setThemeMode(FleetThemeMode.NEON_FLEET)
                note = "Theme: neon fleet"
            }
            ThemeChoice("Dark", settings.themeMode() == FleetThemeMode.DARK) {
                settings.setThemeMode(FleetThemeMode.DARK)
                note = "Theme: dark"
            }
            ThemeChoice("Light", settings.themeMode() == FleetThemeMode.LIGHT) {
                settings.setThemeMode(FleetThemeMode.LIGHT)
                note = "Theme: light"
            }
            ThemeChoice("Material You accent", settings.themeMode() == FleetThemeMode.MATERIAL_YOU) {
                settings.setThemeMode(FleetThemeMode.MATERIAL_YOU)
                note = "Theme: Material You (falls back to neon below Android 12)"
            }
            Text("App icons: ${settings.iconStyle().name}", color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
            ThemeChoice("System icons", settings.iconStyle() == FleetIconStyle.SYSTEM) {
                settings.setIconStyle(FleetIconStyle.SYSTEM)
                note = "Icons: system"
            }
            ThemeChoice("Monochrome / themed", settings.iconStyle() == FleetIconStyle.MONOCHROME) {
                settings.setIconStyle(FleetIconStyle.MONOCHROME)
                note = "Icons: monochrome"
            }
            ThemeChoice("Tinted with accent", settings.iconStyle() == FleetIconStyle.TINTED) {
                settings.setIconStyle(FleetIconStyle.TINTED)
                note = "Icons: tinted"
            }
            ThemeChoice("Icon pack", settings.iconStyle() == FleetIconStyle.ICON_PACK) {
                settings.setIconStyle(FleetIconStyle.ICON_PACK)
                note = "Icons: icon pack"
            }
        }
        Section("Home (Lawnchair lineage)") {
            Text(
                "Paged workspace, folders, AppWidgetHost widgets, swipe-up drawer, swipe-down search, and ADW/GO/Nova icon packs. This is not a vendored Lawnchair AOSP tree — see docs/LAWNCHAIR_REBASE.md.",
                color = Muted,
                fontSize = 13.sp,
            )
            val packs = remember { IconPackManager(activity.packageManager).installedPacks() }
            val selected = settings.iconPackPackage()
            Text(
                if (selected == null) "Icon pack: system icons" else "Icon pack: $selected",
                color = Cyan,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(
                onClick = {
                    settings.setIconPackPackage(null)
                    note = "Using system icons"
                },
            ) { Text("System icons", color = Muted) }
            packs.forEach { pack ->
                TextButton(
                    onClick = {
                        settings.setIconPackPackage(pack.packageName)
                        note = "Icon pack ${pack.label}"
                    },
                ) { Text(pack.label, color = Cyan) }
            }
            if (packs.isEmpty()) {
                Text("No ADW/GO/Nova icon packs installed.", color = Muted, fontSize = 12.sp)
            }
        }
        Section("Default launcher") {
            Text(
                "VictorLaunch must be the Home app to show the workspace, dock and status tray.",
                color = Muted,
                fontSize = 13.sp,
            )
            Button(
                onClick = {
                    activity.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Open default-home settings") }
        }
        Section("Pair controller") {
            Text(
                buildString {
                    append("Pinned debug controller ${TrustedPeers.CONTROLLER_PACKAGE}\n")
                    append("SHA-256 ${TrustedPeers.CONTROLLER_DEBUG_CERT_SHA256}\n")
                    append("Release cert: TBD (not trusted until filled in).\n")
                    if (pairing == null) {
                        append("No extra pairing row. Status pulls if the installed APK matches the debug pin.")
                    } else {
                        append("Extra pairing: ${pairing.packageName}\nSHA-256 ${pairing.certSha256}")
                    }
                },
                color = Muted,
                fontSize = 13.sp,
            )
            Button(
                onClick = {
                    val certs = SigningCerts.forPackage(activity.packageManager, Protocol.CONTROLLER_PACKAGE)
                    if (certs.isEmpty()) {
                        note = "DroidCommand AI is not installed"
                    } else {
                        settings.pairController(Protocol.CONTROLLER_PACKAGE, certs.first())
                        note = "Paired ${Protocol.CONTROLLER_PACKAGE}"
                    }
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Pair DroidCommand AI") }
            TextButton(onClick = {
                settings.unpairController()
                note = "Unpaired"
            }) { Text("Clear pairing", color = Magenta) }
        }
        Section("DroidCommand AI full control") {
            Text(
                "Don’s choice: the pinned debug controller (ai.droidcommand.app + the debug SHA-256) may run app.launch, layout.set and panel.open with no on-device confirmation. This is ON by default and is a kill switch — turn it off to revoke that privilege. It is not granted to any other peer. Backup import cannot turn it on. This deviates from the fleet rule that destructive ops stay gated.",
                color = Muted,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Allow controller full control", color = Cyan, modifier = Modifier.weight(1f))
                Switch(
                    checked = controllerFull,
                    onCheckedChange = {
                        controllerFull = it
                        settings.setControllerFullControlEnabled(it)
                    },
                )
            }
            val auditLines =
                remember(note, controllerFull, external) {
                    VictorLaunchApp.instance.actionAudit.snapshot().takeLast(5)
                }
            if (auditLines.isNotEmpty()) {
                Text("Recent remote actions", color = Magenta, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                auditLines.forEach { event ->
                    Text(
                        "${event.op} ${if (event.ok) "ok" else event.detail} ← ${event.sourceNodeRef}",
                        color = Muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
        Section("External control (other peers)") {
            Text(
                "Off by default. When off, app.launch, layout.set and panel.open from non-controller peers are refused. layout.set still needs on-device confirmation even when this is on. A backup import never turns this on.",
                color = Muted,
                fontSize = 13.sp,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Allow other peers", color = Cyan, modifier = Modifier.weight(1f))
                Switch(
                    checked = external,
                    onCheckedChange = {
                        external = it
                        settings.setExternalControlEnabled(it)
                    },
                )
            }
        }
        Section("Shizuku (elevated, optional)") {
            Text(
                "Off by default. When you turn this on, VictorLaunch may use a Shizuku binder (stock Shizuku app today; built-in server later) for freeze/hide, usage-access grant, Island ops without being device owner, and force-stop. That is ADB-level or root-level access. Only enable it if you trust this phone and this APK.",
                color = Muted,
                fontSize = 13.sp,
            )
            val snap = remember(shizukuOn, note) { VictorLaunchApp.instance.shizuku.snapshot() }
            Text(
                when {
                    !shizukuOn -> "Status: disabled"
                    !snap.binderAlive -> "Status: waiting for Shizuku binder"
                    !snap.permissionGranted -> "Status: binder up, permission not granted"
                    else -> "Status: ready (uid ${snap.uid})"
                },
                color = Cyan,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Enable Shizuku", color = Cyan, modifier = Modifier.weight(1f))
                Switch(
                    checked = shizukuOn,
                    onCheckedChange = { on ->
                        if (on) {
                            confirmShizuku = true
                        } else {
                            shizukuOn = false
                            settings.setShizukuEnabled(false)
                            VictorLaunchApp.instance.privileged.unbind()
                        }
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    VictorLaunchApp.instance.shizuku.requestPermission()
                    note = "Requested Shizuku permission"
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Grant Shizuku permission") }
            Text("Start the server", color = Magenta, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp))
            Text(ShizukuStarters.modeHelp(ShizukuStartupMode.WIRELESS_DEBUG), color = Muted, fontSize = 12.sp)
            TextButton(
                onClick = {
                    val embedded = EmbeddedShizukuServer.start(ShizukuStartupMode.WIRELESS_DEBUG)
                    try {
                        activity.startActivity(ShizukuStarters.wirelessDebuggingIntent())
                        note = embedded.detail
                    } catch (_: Exception) {
                        activity.startActivity(ShizukuStarters.developerSettingsIntent())
                        note = embedded.detail
                    }
                },
            ) { Text("Open wireless debugging", color = Cyan) }
            Text(ShizukuStarters.modeHelp(ShizukuStartupMode.ADB_COMMAND), color = Muted, fontSize = 12.sp)
            Text(ShizukuStarters.modeHelp(ShizukuStartupMode.ROOT), color = Muted, fontSize = 12.sp)
            val stock = ShizukuStarters.stockManagerIntent(activity)
            if (stock != null) {
                TextButton(onClick = { activity.startActivity(stock) }) { Text("Open stock Shizuku", color = Cyan) }
            } else {
                Text("Stock Shizuku is not installed. Install it, or wait for the embedded server.", color = Muted, fontSize = 12.sp)
            }
        }
        Section("Island") {
            Text(
                if (island.hasWorkProfile()) {
                    "Work profile present. Pause, resume, clone, freeze, offline and lockdown prefer Shizuku when you enabled it, then DevicePolicyManager. Wipe never runs without an in-app confirm. Inspired by Island/Insular; no third-party Island source is bundled."
                } else {
                    "Create a managed work profile. VictorLaunch becomes the profile owner of that profile."
                },
                color = Muted,
                fontSize = 13.sp,
            )
            Button(onClick = onIsland, modifier = Modifier.padding(top = 8.dp)) { Text("Open Island screen") }
            Row(modifier = Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Lock Island tab", color = Cyan, modifier = Modifier.weight(1f))
                Switch(
                    checked = islandLocked,
                    onCheckedChange = {
                        islandLocked = it
                        settings.setIslandTabLocked(it)
                    },
                )
            }
            if (!island.hasWorkProfile()) {
                Button(onClick = { activity.startActivity(island.provisionIntent()) }) {
                    Text("Create work profile")
                }
                OutlinedButton(
                    onClick = {
                        note = VictorLaunchApp.instance.privileged.createManagedProfile("Island").detail
                    },
                    modifier = Modifier.padding(top = 8.dp),
                ) { Text("Create Island via Shizuku (no device owner)") }
            } else {
                OutlinedButton(onClick = { island.pauseWorkProfile() }) { Text("Pause Island") }
                OutlinedButton(onClick = { island.resumeWorkProfile() }) { Text("Resume Island") }
            }
            Text(
                "To remove the work profile: Settings → Accounts (or Users) → Work → Remove work profile. If this copy is the profile owner, you can also wipe the profile from the Island screen.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Section("Lockdown") {
            Text(
                "Hidden apps stay out of the drawer. Locked apps, folders and the Island tab ask for BiometricPrompt first.",
                color = Muted,
                fontSize = 13.sp,
            )
            val hidden = settings.launcherPreferences().hiddenPackages.toMutableSet()
            val locked = settings.launcherPreferences().lockedPackages.toMutableSet()
            catalog.personal().take(12).forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(app.label, color = Cyan, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = {
                        if (app.packageName in hidden) hidden.remove(app.packageName) else hidden.add(app.packageName)
                        settings.setHidden(hidden)
                        val systemHide = app.packageName in hidden
                        val priv = VictorLaunchApp.instance.privileged.hide(app.packageName, systemHide)
                        note = if (priv.ok) "Hidden=$systemHide (${priv.detail})" else "Drawer hide updated; system hide: ${priv.detail}"
                    }) { Text(if (app.packageName in hidden) "Unhide" else "Hide", color = Muted) }
                    TextButton(onClick = {
                        if (app.packageName in locked) locked.remove(app.packageName) else locked.add(app.packageName)
                        settings.setLocked(locked)
                        note = "Updated locked apps"
                    }) { Text(if (app.packageName in locked) "Unlock" else "Lock", color = Muted) }
                    TextButton(onClick = {
                        note = VictorLaunchApp.instance.privileged.freeze(app.packageName, true).detail
                    }) { Text("Freeze", color = Muted) }
                    TextButton(onClick = {
                        note = VictorLaunchApp.instance.privileged.freeze(app.packageName, false).detail
                    }) { Text("Unfreeze", color = Muted) }
                    if (island.hasWorkProfile()) {
                        TextButton(onClick = {
                            island.sendCommand(IslandCommandReceiver.CMD_CLONE, app.packageName)
                            note = "Clone requested for ${app.packageName}"
                        }) { Text("Clone", color = Magenta) }
                        TextButton(onClick = {
                            island.sendCommand(IslandCommandReceiver.CMD_FREEZE, app.packageName)
                            note = "Island freeze requested for ${app.packageName}"
                        }) { Text("Island freeze", color = Magenta) }
                        TextButton(onClick = {
                            island.sendCommand(IslandCommandReceiver.CMD_UNFREEZE, app.packageName)
                            note = "Island unfreeze requested for ${app.packageName}"
                        }) { Text("Island thaw", color = Magenta) }
                    }
                    TextButton(onClick = {
                        note = VictorLaunchApp.instance.privileged.forceStop(app.packageName).detail
                    }) { Text("Force stop", color = Muted) }
                }
            }
            OutlinedButton(
                onClick = {
                    note = VictorLaunchApp.instance.privileged.grantUsageAccess(activity.packageName).detail
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Grant usage access to VictorLaunch (Shizuku)") }
        }
        Section("Backup") {
            Text(
                "VictorLaunch can export its own launcher preferences (dock, workspace, hidden/locked apps). It never holds the fleet wrapping key. Ask the controller to build or restore a fleet bundle.",
                color = Muted,
                fontSize = 13.sp,
            )
            OutlinedButton(
                onClick = {
                    val json = ConfigBackupCodec.exportJson(settings.launcherPreferences())
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, json)
                        putExtra(Intent.EXTRA_TITLE, "victorlaunch-config.json")
                    }
                    activity.startActivity(Intent.createChooser(send, "Export launcher preferences"))
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Export launcher preferences") }
            OutlinedButton(
                onClick = { openDocument.launch(arrayOf("application/json", "text/plain", "*/*")) },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Import (dry-run then confirm)") }
            Text(
                "Import always dry-runs first. Confirming still forces external control off. VictorLaunch never stores the fleet wrapping key — ask DroidCommand AI to export or import the encrypted fleet bundle.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Section("Network Monitor") {
            Text(
                "Off by default. TelephonyCallback / PhoneStateListener for RAT, signal, cell ID. Alerts on 2G drops, downgrades, sudden cell changes. Lock 2G/LTE-NR via Shizuku when the OEM allows it; otherwise open system settings. Backup import cannot turn this on. Not universal.",
                color = Muted,
                fontSize = 13.sp,
            )
            Button(onClick = onNetwork, modifier = Modifier.padding(top = 8.dp)) { Text("Open Network Monitor") }
        }
        Section("Mods") {
            Text(
                "Off by default. LSPosed, LSPatch, Geto (JackEblan/Geto, 90% confidence) and MMRL. Each tool has a root path and a non-root path. Full engines are not vendored — this opens the stock GPLv3 apps and uses Shizuku helpers when you enabled Shizuku. Backup import cannot turn Mods on.",
                color = Muted,
                fontSize = 13.sp,
            )
            Button(onClick = onMods, modifier = Modifier.padding(top = 8.dp)) { Text("Open Mods") }
        }
        Section("Share targets") {
            Text(
                "Direct-share shortcuts and ChooserTargets only list VictorSMS, Victorious Browser, File Manager, and DroidCommand AI when that package is installed and its signing cert matches a pin. The controller debug cert is pre-pinned. Other fleet apps stay hidden until you trust the installed cert (does not invent a pin).",
                color = Muted,
                fontSize = 13.sp,
            )
            Button(
                onClick = {
                    val installed = FleetSharePublisher.installedCerts(activity)
                    var n = 0
                    FleetShareCatalog.ALL.forEach { spec ->
                        spec.packageNames.forEach { pkg ->
                            installed[pkg]?.firstOrNull()?.let {
                                settings.putSharePin(pkg, it)
                                n++
                            }
                        }
                    }
                    FleetSharePublisher.publish(activity, settings)
                    note = "Trusted $n installed share-target cert(s)"
                },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Trust installed fleet share apps") }
        }
        Section("Fleet apps") {
            Text(
                "Load a local fleet-manifest.json (schema v1). No INTERNET — this will not poll GitHub. Unpinned certs stay untrusted. Install uses PackageInstaller confirm.",
                color = Muted,
                fontSize = 13.sp,
            )
            Button(onClick = onFleet, modifier = Modifier.padding(top = 8.dp)) { Text("Install / Update fleet apps") }
        }
        Section("Help") {
            Button(onClick = onHelp) { Text("Open the manual") }
        }
        val currentNote = note
        if (currentNote != null) {
            Text(currentNote, color = Magenta, modifier = Modifier.padding(top = 12.dp))
        }
        if (confirmShizuku) {
            AlertDialog(
                onDismissRequest = { confirmShizuku = false },
                title = { Text("Enable Shizuku?") },
                text = {
                    Text(
                        "This lets VictorLaunch run freeze, hide, force-stop, usage-access grants and some Island commands as ADB (or root if you started Shizuku that way). It is off until you agree. The embedded Shizuku server is not in this build — the stock Shizuku app must be running.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmShizuku = false
                            shizukuOn = true
                            settings.setShizukuEnabled(true)
                            VictorLaunchApp.instance.privileged.refreshBinding()
                            VictorLaunchApp.instance.shizuku.requestPermission()
                        },
                    ) { Text("Enable") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmShizuku = false }) { Text("Cancel") }
                },
            )
        }
        val report = pendingImport
        if (report != null) {
            AlertDialog(
                onDismissRequest = { pendingImport = null },
                title = { Text("Apply launcher backup?") },
                text = {
                    Text(report.notes.joinToString("\n").ifBlank { "Dry-run succeeded. External control will stay off." })
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val prefs = report.preferences?.copy(externalControlEnabled = false)
                            if (prefs != null) {
                                settings.applyPreferences(prefs)
                            }
                            settings.setExternalControlEnabled(false)
                            external = false
                            pendingImport = null
                            note = "Import applied; external control remains off"
                        },
                    ) { Text("Apply") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingImport = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Text(title, color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
    content()
}

@Composable
private fun ThemeChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(if (selected) "● $label" else "○ $label", color = if (selected) Cyan else Muted)
    }
}
