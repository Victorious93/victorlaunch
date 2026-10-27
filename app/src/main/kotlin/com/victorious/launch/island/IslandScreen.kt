package com.victorious.launch.island

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import com.victorious.launch.core.island.IslandPolicy
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

@Composable
fun IslandScreen(
    island: IslandManager,
    catalog: AppCatalog,
    activity: Activity,
    onClose: () -> Unit,
) {
    var note by remember { mutableStateOf<String?>(null) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmLock by remember { mutableStateOf(false) }
    var offline by remember { mutableStateOf(false) }
    val personalApps = remember { catalog.personal() }
    val workApps = remember { catalog.island() }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        Text("Island work profile", color = Magenta, fontSize = 20.sp)
        Text(
            if (island.hasWorkProfile()) {
                if (island.isQuietMode()) "Island is paused (quiet mode)." else "Island is running."
            } else {
                "No work profile yet. Create one to clone / freeze / offline / lockdown."
            },
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        if (!island.hasWorkProfile()) {
            Button(onClick = { activity.startActivity(island.provisionIntent()) }) {
                Text("Create work profile")
            }
        } else {
            OutlinedButton(onClick = { island.pauseWorkProfile(); note = "Paused" }) { Text("Pause") }
            OutlinedButton(
                onClick = { island.resumeWorkProfile(); note = "Resumed" },
                modifier = Modifier.padding(top = 8.dp),
            ) { Text("Resume") }
            Text("Clone into Island", color = Cyan, modifier = Modifier.padding(top = 12.dp))
            personalApps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = {
                        island.sendCommand(IslandCommandReceiver.CMD_CLONE, app.packageName)
                        note = "Clone ${app.packageName}"
                    }) { Text("Clone", color = Cyan) }
                }
            }
            Text("Freeze / unfreeze Island apps", color = Cyan, modifier = Modifier.padding(top = 12.dp))
            if (workApps.isEmpty()) {
                Text("No work-profile apps yet. Clone one, then freeze it here.", color = Muted, fontSize = 12.sp)
            }
            workApps.forEach { app ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(app.label, color = Muted, modifier = Modifier.weight(1f), fontSize = 13.sp)
                    TextButton(onClick = {
                        island.sendCommand(IslandCommandReceiver.CMD_FREEZE, app.packageName)
                        note = "Freeze ${app.packageName}"
                    }) { Text("Freeze", color = Magenta) }
                    TextButton(onClick = {
                        island.sendCommand(IslandCommandReceiver.CMD_UNFREEZE, app.packageName)
                        note = "Unfreeze ${app.packageName}"
                    }) { Text("Unfreeze", color = Cyan) }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 12.dp)) {
                Text("Offline / private", color = Cyan, modifier = Modifier.weight(1f))
                Switch(
                    checked = offline,
                    onCheckedChange = {
                        offline = it
                        note = island.setOfflineMode(it, workApps.map { app -> app.packageName })
                    },
                )
            }
            Text(IslandPolicy.describeOffline(nonRoot = !island.isProfileOwner()), color = Muted, fontSize = 12.sp)
            Button(
                onClick = { confirmLock = true },
                modifier = Modifier.padding(top = 12.dp),
            ) { Text("Lockdown — freeze all Island apps") }
            OutlinedButton(onClick = {
                note = island.lockdown(workApps.map { it.packageName }, enabled = false, confirmed = true)
            }) { Text("Lift lockdown") }
            TextButton(onClick = { confirmWipe = true }) {
                Text("Remove work profile…", color = Magenta)
            }
        }
        note?.let { Text(it, color = Cyan, fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp)) }
    }
    if (confirmLock) {
        AlertDialog(
            onDismissRequest = { confirmLock = false },
            title = { Text("Freeze every Island app?") },
            text = { Text("This suspends work-profile apps and pauses the profile. It does not wipe. Confirm to continue.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLock = false
                    note = island.lockdown(workApps.map { it.packageName }, enabled = true, confirmed = true)
                }) { Text("Freeze all", color = Magenta) }
            },
            dismissButton = { TextButton(onClick = { confirmLock = false }) { Text("Cancel") } },
        )
    }
    if (confirmWipe) {
        AlertDialog(
            onDismissRequest = { confirmWipe = false },
            title = { Text("Wipe the work profile?") },
            text = { Text("This deletes the Island user and every cloned app. VictorLaunch in the personal profile stays. This cannot be undone from here.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmWipe = false
                    note = island.wipeProfile(confirmed = true)
                }) { Text("Wipe Island", color = Magenta) }
            },
            dismissButton = { TextButton(onClick = { confirmWipe = false }) { Text("Cancel") } },
        )
    }
}
