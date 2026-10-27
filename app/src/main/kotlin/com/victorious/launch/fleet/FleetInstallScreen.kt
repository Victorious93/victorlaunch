package com.victorious.launch.fleet

import android.app.Activity
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.fleet.FleetManifest
import com.victorious.launch.core.fleet.FleetManifestCodec
import com.victorious.launch.core.fleet.FleetPlanRow
import com.victorious.launch.core.fleet.FleetPlanner
import com.victorious.launch.core.fleet.FleetRowState
import com.victorious.launch.security.SigningCerts
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

/**
 * One-tap fleet install/update. No INTERNET: the user (or the controller)
 * supplies a local fleet-manifest.json and a local APK. PackageInstaller
 * always shows the system confirm sheet.
 */
@Composable
fun FleetInstallScreen(activity: Activity, onClose: () -> Unit) {
    var note by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf<List<FleetPlanRow>>(emptyList()) }
    var pending by remember { mutableStateOf<FleetPlanRow?>(null) }
    val pickManifest =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            try {
                val text = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                    ?: error("empty")
                rows = planFrom(activity, FleetManifestCodec.parse(text))
                note = "Loaded ${rows.size} fleet app(s) from local manifest (schema v1)."
            } catch (e: Exception) {
                note = e.message ?: "manifest failed"
            }
        }
    val pickApk =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val row = pending
            pending = null
            if (uri == null || row == null) return@rememberLauncherForActivityResult
            try {
                val file = FleetApkInstaller.copyToCache(activity, uri)
                val digest = FleetApkInstaller.sha256Hex(file)
                val certs = archiveCerts(activity, file.absolutePath)
                if (!FleetPlanner.apkMatchesRow(row, certs, digest)) {
                    note = "Refused ${row.app.name}: APK cert or SHA-256 does not match the pinned manifest row."
                    file.delete()
                    return@rememberLauncherForActivityResult
                }
                note = FleetApkInstaller.startSession(activity, file)
            } catch (e: Exception) {
                note = e.message ?: "install failed"
            }
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        Text("Install / Update fleet apps", color = Magenta, fontSize = 20.sp)
        Text(
            "Schema is Victorious93/fleet-manifest schemaVersion 1 (see docs/fleet-manifest.md). " +
                "This APK has no INTERNET permission, so it will not poll GitHub. Load a local " +
                "fleet-manifest.json, then pick a local APK. Install always asks the system " +
                "PackageInstaller confirm sheet. Unsigned / unpinned rows stay UNTRUSTED.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
        )
        Row {
            Button(onClick = { pickManifest.launch(arrayOf("application/json", "*/*")) }) {
                Text("Load local manifest")
            }
        }
        note?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(vertical = 8.dp)) }
        rows.forEach { row ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(row.app.name, color = Cyan, fontSize = 16.sp)
                Text(row.app.applicationId, color = Muted, fontSize = 12.sp)
                Text(
                    "manifest ${row.app.versionName} (${row.app.versionCode})  ·  installed " +
                        (row.installedVersionName ?: "—"),
                    color = Muted,
                    fontSize = 12.sp,
                )
                Text(stateLabel(row.state), color = if (row.trusted) Cyan else Magenta, fontSize = 12.sp)
                if (row.trusted && row.state != FleetRowState.UP_TO_DATE) {
                    OutlinedButton(
                        onClick = {
                            pending = row
                            pickApk.launch(arrayOf("application/vnd.android.package-archive", "*/*"))
                        },
                    ) {
                        Text(if (row.state == FleetRowState.NOT_INSTALLED) "Install APK…" else "Update APK…")
                    }
                }
            }
        }
    }
}

private fun stateLabel(state: FleetRowState): String =
    when (state) {
        FleetRowState.NOT_INSTALLED -> "Not installed"
        FleetRowState.UP_TO_DATE -> "Installed — current"
        FleetRowState.UPDATE_AVAILABLE -> "Update available"
        FleetRowState.DOWNGRADE -> "Installed is newer than the manifest"
        FleetRowState.UNTRUSTED -> "Untrusted — no pin match (will not install)"
    }

private fun planFrom(activity: Activity, manifest: FleetManifest) =
    FleetPlanner.plan(manifest, installedMap(activity.packageManager))

@Suppress("DEPRECATION")
private fun installedMap(pm: PackageManager): Map<String, Pair<Int, String>> {
    val packages = pm.getInstalledPackages(0)
    return packages.associate { info ->
        val code =
            if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
        info.packageName to (code to (info.versionName ?: ""))
    }
}

private fun archiveCerts(activity: Activity, path: String): List<String> {
    val pm = activity.packageManager
    val flags =
        if (Build.VERSION.SDK_INT >= 28) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }
    val info = pm.getPackageArchiveInfo(path, flags) ?: return emptyList()
    return try {
        if (Build.VERSION.SDK_INT >= 28) {
            info.signingInfo?.apkContentsSigners.orEmpty().map { SigningCerts.sha256Colon(it.toByteArray()) }
        } else {
            @Suppress("DEPRECATION")
            info.signatures.orEmpty().map { SigningCerts.sha256Colon(it.toByteArray()) }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
