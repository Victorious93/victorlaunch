package com.victorious.launch.network

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
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
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

@Composable
fun NetworkScreen(
    settings: AppSettings,
    activity: Activity,
    onClose: () -> Unit,
) {
    var enabled by remember { mutableStateOf(settings.isNetworkMonitorEnabled()) }
    var block2g by remember { mutableStateOf(settings.isNetworkBlock2g()) }
    var lockLteNr by remember { mutableStateOf(settings.isNetworkLockLteNr()) }
    var autoRecover by remember { mutableStateOf(settings.isNetworkAutoRecover()) }
    var confirmEnable by remember { mutableStateOf(false) }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var confirmRecover by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            val ok = granted.values.all { it }
            note =
                if (ok) {
                    "Phone and location permission granted. Cell ID still needs the system location toggle."
                } else {
                    "Permissions denied. Monitor cannot read serving-cell info."
                }
            VictorLaunchApp.instance.network.refresh()
        }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Network Monitor", color = Cyan, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        }
        Text(
            "Off by default. Reads RAT, RSRP/RSRQ/SINR, cell ID and registration on this phone only. No telemetry. Unexpected 2G is a heuristic for a fake BTS, not proof. Nothing here was tested on a device.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
            Text("Enable monitor", color = Cyan, modifier = Modifier.weight(1f))
            Switch(
                checked = enabled,
                onCheckedChange = { on ->
                    if (on) confirmEnable = true
                    else {
                        enabled = false
                        settings.setNetworkMonitorEnabled(false)
                        VictorLaunchApp.instance.network.stop()
                    }
                },
            )
        }
        val json = remember(enabled, note, block2g, lockLteNr) { VictorLaunchApp.instance.network.snapshotJson() }
        Text("Current: ${json["rat"]} gen=${json["generation"]} rsrp=${json["rsrp"]} cell=${json["cellId"]}", color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        Text("Registered=${json["registered"]} data=${json["dataState"]} lockActive=${json["lockActive"]}", color = Muted, fontSize = 12.sp)
        OutlinedButton(
            onClick = {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.READ_PHONE_STATE,
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Grant phone + location") }

        Text("Alerts (this device)", color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
        val alerts = VictorLaunchApp.instance.network.latestAlerts().takeLast(8).asReversed()
        if (alerts.isEmpty()) {
            Text("No alerts yet. History stays on-device.", color = Muted, fontSize = 12.sp)
        } else {
            alerts.forEach { alert ->
                Text("${alert.kind.wire}: ${alert.detail}", color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Text("Prevent drops (non-root)", color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
        Text(
            "A normal app cannot lock the RAT. On Android 12+ Shizuku/shell can call setAllowedNetworkTypes / the Allow-2G toggle. Otherwise open the system screens. Sticky-network: stay on LTE/NR if the carrier allows it. If the radio drops to 2G, prefer Wi-Fi.",
            color = Muted,
            fontSize = 12.sp,
        )
        OutlinedButton(
            onClick = { activity.startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)) },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Open Allow 2G / operator settings") }
        OutlinedButton(
            onClick = { activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS)) },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Preferred network / wireless settings") }
        OutlinedButton(
            onClick = { activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Wi-Fi fallback") }
        if (Build.VERSION.SDK_INT >= 28) {
            OutlinedButton(
                onClick = { activity.startActivity(Intent(Settings.ACTION_DATA_ROAMING_SETTINGS)) },
                modifier = Modifier.padding(top = 4.dp),
            ) { Text("Mobile network settings") }
        }

        Text("Root / Shizuku lock", color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
        Text(
            "Locks preferred_network_mode and allowed_2g, and tries TelephonyManager.setAllowedNetworkTypesForReason as shell. OEM integers differ. Cell/band lock is not written.",
            color = Muted,
            fontSize = 12.sp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Block 2G", color = Cyan, modifier = Modifier.weight(1f))
            Switch(
                checked = block2g,
                enabled = enabled,
                onCheckedChange = { on ->
                    if (on) confirmBlock = true
                    else {
                        block2g = false
                        settings.setNetworkBlock2g(false)
                    }
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Lock LTE/NR only", color = Cyan, modifier = Modifier.weight(1f))
            Switch(
                checked = lockLteNr,
                enabled = enabled,
                onCheckedChange = { on ->
                    if (on) confirmLock = true
                    else {
                        lockLteNr = false
                        settings.setNetworkLockLteNr(false)
                    }
                },
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            Text("Auto-recover radio on drop", color = Cyan, modifier = Modifier.weight(1f))
            Switch(
                checked = autoRecover,
                enabled = enabled,
                onCheckedChange = {
                    autoRecover = it
                    settings.setNetworkAutoRecover(it)
                },
            )
        }
        OutlinedButton(
            onClick = { note = VictorLaunchApp.instance.network.applyLocks() },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Apply lock now (Shizuku/root)") }
        OutlinedButton(
            onClick = { confirmRecover = true },
            modifier = Modifier.padding(top = 4.dp),
        ) { Text("Toggle radio now") }

        Text("Limits", color = Magenta, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp))
        Text(NetworkLimits.TEXT, color = Muted, fontSize = 12.sp)
        val current = note
        if (current != null) {
            Text(current, color = Magenta, modifier = Modifier.padding(top = 12.dp))
        }
        if (confirmEnable) {
            AlertDialog(
                onDismissRequest = { confirmEnable = false },
                title = { Text("Enable Network Monitor?") },
                text = {
                    Text(
                        "This uses READ_PHONE_STATE and fine location to read serving-cell ID and signal. History stays on this phone. Backup import cannot turn this on.",
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmEnable = false
                            enabled = true
                            settings.setNetworkMonitorEnabled(true)
                            permissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.READ_PHONE_STATE,
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                ),
                            )
                            VictorLaunchApp.instance.network.refresh()
                        },
                    ) { Text("Enable") }
                },
                dismissButton = { TextButton(onClick = { confirmEnable = false }) { Text("Cancel") } },
            )
        }
        if (confirmBlock) {
            AlertDialog(
                onDismissRequest = { confirmBlock = false },
                title = { Text("Block 2G?") },
                text = { Text("Tries allowed_2g=0 via Shizuku/root. Rural coverage that needs GSM will fail. Not universal.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmBlock = false
                            block2g = true
                            settings.setNetworkBlock2g(true)
                            note = VictorLaunchApp.instance.network.applyLocks()
                        },
                    ) { Text("Block 2G") }
                },
                dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } },
            )
        }
        if (confirmLock) {
            AlertDialog(
                onDismissRequest = { confirmLock = false },
                title = { Text("Lock LTE/NR?") },
                text = {
                    Text("Tries setAllowedNetworkTypes LTE+NR and preferred_network_mode=23. Samsung/OEM values differ. 3G/2G will be unavailable if the modem honors it.")
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmLock = false
                            lockLteNr = true
                            settings.setNetworkLockLteNr(true)
                            note = VictorLaunchApp.instance.network.applyLocks()
                        },
                    ) { Text("Lock") }
                },
                dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel") } },
            )
        }
        if (confirmRecover) {
            AlertDialog(
                onDismissRequest = { confirmRecover = false },
                title = { Text("Toggle airplane mode?") },
                text = { Text("Flips airplane on then off via Shizuku to re-register. Drops the current call. Cooldown applies to auto-recover.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            confirmRecover = false
                            note = VictorLaunchApp.instance.network.recoverNow()
                        },
                    ) { Text("Toggle radio") }
                },
                dismissButton = { TextButton(onClick = { confirmRecover = false }) { Text("Cancel") } },
            )
        }
    }
}
