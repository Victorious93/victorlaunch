package com.victorious.launch.fleet

import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.fleet.DashboardInstall
import com.victorious.launch.core.fleet.FleetDashboardFactory
import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors

@Composable
fun FleetDashboardPage(
    settings: AppSettings,
    catalog: AppCatalog,
    activity: Activity,
    tray: PublishedTray?,
    trayNote: String,
    onLaunchPackage: (String) -> Unit,
    onOpenInstall: () -> Unit,
) {
    val colors = LocalFleetColors.current
    val dash =
        remember(tray, trayNote, settings.lastFleetManifestJson()) {
            FleetDashboardFactory.build(
                manifest = FleetApps.loadManifest(settings),
                installed = FleetApps.installedMap(activity.packageManager),
                running = FleetApps.runningPackages(activity),
                tray = tray,
                extraPins = FleetApps.extraPins(settings),
                nowMs = System.currentTimeMillis(),
                trayNote = trayNote,
            )
        }
    val freshnessColor =
        when (dash.trayFreshness) {
            Freshness.FRESH -> colors.primary
            Freshness.STALE -> colors.stale
            Freshness.EXPIRED -> colors.danger
        }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text("Fleet dashboard", color = colors.primary, fontSize = 20.sp)
        Text(
            dash.trayNote.ifBlank {
                when (dash.trayFreshness) {
                    Freshness.FRESH -> "Tray is fresh"
                    Freshness.STALE -> "Tray is stale (older than 30s)"
                    Freshness.EXPIRED -> "Tray dropped (older than 5 min) or no controller"
                }
            },
            color = freshnessColor,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
        )
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            dash.tiles.forEach { tile ->
                val color =
                    when {
                        tile.freshness == Freshness.EXPIRED || tile.state == StatusTrayState.UNKNOWN -> colors.muted
                        tile.state == StatusTrayState.DOWN -> colors.danger
                        tile.freshness == Freshness.STALE || tile.state == StatusTrayState.DEGRADED -> colors.stale
                        else -> colors.primary
                    }
                Text(
                    FleetDashboardFactory.tileLabel(tile),
                    color = color,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f).padding(end = 4.dp),
                )
            }
        }
        if (!dash.hasManifest) {
            Text(
                "Load a local fleet-manifest.json in Install / Update to compare versions and offer updates. Fleet install does not use the network.",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        if (!dash.usageAccess) {
            Text(
                "Grant usage access in Settings to show which fleet apps are running. Until then, running state is hidden.",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        dash.rows.forEach { row ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = row.canLaunch) { onLaunchPackage(row.packageName) }
                    .padding(vertical = 8.dp),
            ) {
                Text(row.name, color = colors.onBackground, fontSize = 15.sp)
                val version =
                    buildString {
                        if (row.installedVersionName != null) append("v${row.installedVersionName}")
                        else append("not on device")
                        if (row.manifestVersionName != null) append(" · manifest ${row.manifestVersionName}")
                    }
                Text(
                    "${FleetDashboardFactory.installLabel(row.install)} · $version",
                    color = if (row.install == DashboardInstall.UPDATE_AVAILABLE) colors.stale else colors.muted,
                    fontSize = 12.sp,
                )
                val running = FleetDashboardFactory.runningLabel(row.running, dash.usageAccess)
                val trayState = row.trayState
                val tray =
                    when {
                        trayState == null -> null
                        row.kind == com.victorious.launch.core.fleet.FleetKind.OTHER ||
                            row.kind == com.victorious.launch.core.fleet.FleetKind.CONTROLLER -> null
                        else -> " · " + trayState.name.lowercase()
                    }
                Text(running + (tray ?: ""), color = colors.muted, fontSize = 12.sp)
                if (row.canInstallOrUpdate) {
                    OutlinedButton(
                        onClick = onOpenInstall,
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.Start),
                    ) {
                        Text(if (row.install == DashboardInstall.NOT_INSTALLED) "Install…" else "Update…")
                    }
                }
            }
        }
        OutlinedButton(onClick = onOpenInstall, modifier = Modifier.padding(top = 8.dp)) {
            Text("Install / Update fleet apps")
        }
    }
}
