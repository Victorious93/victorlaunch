package com.victorious.launch.launcher

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CellTower
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.TrayTile
import com.victorious.launch.fleet.FleetInstallScreen
import com.victorious.launch.help.HelpScreen
import com.victorious.launch.island.IslandManager
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.mods.ModsScreen
import com.victorious.launch.network.NetworkScreen
import com.victorious.launch.lockdown.BiometricGate
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.settings.SettingsScreen
import com.victorious.launch.status.ControllerClient
import com.victorious.launch.status.PullResult
import com.victorious.launch.theme.Charcoal
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted
import com.victorious.launch.theme.StaleAmber
import com.victorious.launch.theme.Tile
import kotlinx.coroutines.delay

enum class Overlay {
    NONE,
    DRAWER,
    SETTINGS,
    HELP,
    FLEET,
    MODS,
    NETWORK,
}

@Composable
fun HomeScreen(
    settings: AppSettings,
    catalog: AppCatalog,
    island: IslandManager,
    controller: ControllerClient,
    biometric: BiometricGate,
    activity: Activity,
) {
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var drawerTab by remember { mutableStateOf(0) }
    var apps by remember { mutableStateOf(catalog.personal()) }
    var islandApps by remember { mutableStateOf(catalog.island()) }
    var tray by remember { mutableStateOf<PublishedTray?>(null) }
    var trayNote by remember { mutableStateOf("Waiting for the controller") }
    var radio by remember { mutableStateOf<TrayTile?>(null) }
    val aggregator = remember { StatusTrayAggregator() }

    LaunchedEffect(Unit) {
        while (true) {
            apps = catalog.personal()
            islandApps = catalog.island()
            radio = localRadioTile(settings)
            when (val pull = controller.pullSnapshots()) {
                is PullResult.Ok -> {
                    tray = aggregator.publish(pull.snapshots)
                    trayNote =
                        if (pull.snapshots.isEmpty()) {
                            "Controller answered with an empty tray"
                        } else {
                            "Pulled ${pull.snapshots.size} node(s) from the controller"
                        }
                }
                PullResult.MissingController -> {
                    tray = aggregator.publish(emptyList())
                    trayNote = "DroidCommand AI is not installed"
                }
                PullResult.NotPaired -> {
                    tray = aggregator.publish(emptyList())
                    trayNote = "Not paired to the controller"
                }
                PullResult.Unauthorized -> {
                    tray = aggregator.publish(emptyList())
                    trayNote = "Controller cert does not match the pairing"
                }
                is PullResult.Unreachable -> {
                    tray = aggregator.publish(emptyList())
                    trayNote = "Controller unreachable: ${pull.reason}"
                }
                is PullResult.RemoteError -> {
                    tray = aggregator.publish(emptyList())
                    trayNote = "Controller error: ${pull.category}"
                }
            }
            delay(StatusFreshness.PULL_INTERVAL_MS)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            StatusStrip(tray, trayNote, radio)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { overlay = Overlay.NETWORK }) {
                    Icon(Icons.Outlined.CellTower, contentDescription = "Network Monitor", tint = Cyan)
                }
                IconButton(onClick = { overlay = Overlay.MODS }) {
                    Icon(Icons.Outlined.Extension, contentDescription = "Mods", tint = Magenta)
                }
                IconButton(onClick = { overlay = Overlay.HELP }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Help", tint = Cyan)
                }
                IconButton(onClick = { overlay = Overlay.SETTINGS }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = Cyan)
                }
            }
            WorkspaceGrid(
                modifier = Modifier.weight(1f),
                settings = settings,
                apps = apps,
                onLaunch = { app -> gatedLaunch(settings, biometric, app, catalog) },
                onPin = { app -> pinToWorkspace(settings, app) },
            )
            DockRow(
                settings = settings,
                apps = apps,
                onLaunch = { app -> gatedLaunch(settings, biometric, app, catalog) },
                onOpenDrawer = { overlay = Overlay.DRAWER },
            )
        }
        when (overlay) {
            Overlay.NONE -> Unit
            Overlay.DRAWER ->
                DrawerOverlay(
                    apps = apps,
                    islandApps = islandApps,
                    tab = drawerTab,
                    islandLocked = settings.launcherPreferences().islandTabLocked,
                    onTab = { next ->
                        if (next == 1 && settings.launcherPreferences().islandTabLocked) {
                            biometric.prompt(
                                title = "Unlock Island",
                                onOk = { drawerTab = 1 },
                            )
                        } else {
                            drawerTab = next
                        }
                    },
                    onLaunch = { app -> gatedLaunch(settings, biometric, app, catalog) },
                    onPinDock = { app -> pinToDock(settings, app) },
                    onClose = { overlay = Overlay.NONE },
                    island = island,
                    activity = activity,
                )
            Overlay.SETTINGS ->
                OverlayScrim {
                    SettingsScreen(
                        settings = settings,
                        catalog = catalog,
                        island = island,
                        activity = activity,
                        onHelp = { overlay = Overlay.HELP },
                        onFleet = { overlay = Overlay.FLEET },
                        onMods = { overlay = Overlay.MODS },
                        onNetwork = { overlay = Overlay.NETWORK },
                        onClose = { overlay = Overlay.NONE },
                    )
                }
            Overlay.HELP ->
                OverlayScrim { HelpScreen(onClose = { overlay = Overlay.NONE }) }
            Overlay.FLEET ->
                OverlayScrim { FleetInstallScreen(onClose = { overlay = Overlay.NONE }) }
            Overlay.MODS ->
                OverlayScrim {
                    ModsScreen(
                        settings = settings,
                        catalog = catalog,
                        activity = activity,
                        onClose = { overlay = Overlay.NONE },
                    )
                }
            Overlay.NETWORK ->
                OverlayScrim {
                    NetworkScreen(
                        settings = settings,
                        activity = activity,
                        onClose = { overlay = Overlay.NONE },
                    )
                }
        }
    }
}

private fun gatedLaunch(
    settings: AppSettings,
    biometric: BiometricGate,
    app: LaunchableApp,
    catalog: AppCatalog,
) {
    val locked = settings.launcherPreferences().lockedPackages.contains(app.packageName)
    if (locked) {
        biometric.prompt(
            title = "Unlock ${app.label}",
            onOk = { catalog.launch(app) },
        )
    } else {
        catalog.launch(app)
    }
}

private fun pinToDock(settings: AppSettings, app: LaunchableApp) {
    val dock = settings.launcherPreferences().dock.toMutableList()
    if (app.packageName !in dock) {
        if (dock.size >= 5) dock.removeAt(0)
        dock.add(app.packageName)
        settings.setDock(dock)
    }
}

private fun pinToWorkspace(settings: AppSettings, app: LaunchableApp) {
    val ws = settings.launcherPreferences().workspace.toMutableList()
    if (ws.none { it.packageName == app.packageName }) {
        ws.add(WorkspaceItem(app.packageName, ws.size % 4, ws.size / 4))
        settings.setWorkspace(ws)
    }
}

@Composable
private fun OverlayScrim(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Charcoal.copy(alpha = 0.96f)),
    ) { content() }
}

private fun localRadioTile(settings: AppSettings): TrayTile {
    val domain = VictorLaunchApp.instance.network.domainStatus()
    return TrayTile(
        id = "radio",
        domain = domain.name,
        state = domain.state,
        detail = domain.detail,
        freshness = if (settings.isNetworkMonitorEnabled()) Freshness.FRESH else Freshness.EXPIRED,
        nodeId = settings.nodeId(),
    )
}

@Composable
private fun StatusStrip(tray: PublishedTray?, note: String, radio: TrayTile?) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            "VictorLaunch",
            color = Cyan,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Text(note, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (radio != null) {
                item(key = "radio") { StatusChip(radio) }
            }
            val tiles = tray?.tiles.orEmpty().filterNot { it.id == "network" }
            items(tiles, key = { it.id }) { tile -> StatusChip(tile) }
            item {
                CountChip(tray?.pairedNodeCount ?: 0, tray)
            }
        }
    }
}

@Composable
private fun StatusChip(tile: TrayTile) {
    val label =
        when (tile.id) {
            "mesh" -> "Mesh"
            "vpn" -> "VPN"
            "browser" -> "Browser proxy"
            "sms" -> "VictorSMS"
            "network", "radio" -> "Network"
            else -> tile.id
        }
    val stateText =
        when (tile.state) {
            StatusTrayState.OK -> "ok"
            StatusTrayState.DEGRADED -> "degraded"
            StatusTrayState.DOWN -> "down"
            StatusTrayState.UNKNOWN -> "no data"
            StatusTrayState.OFF -> "off"
        }
    val accent =
        when (tile.state) {
            StatusTrayState.UNKNOWN -> Muted
            StatusTrayState.OK -> if (tile.freshness == Freshness.STALE) StaleAmber else Cyan
            StatusTrayState.DEGRADED -> StaleAmber
            StatusTrayState.DOWN -> Color(0xFFFF6B8A)
            StatusTrayState.OFF -> Muted
        }
    Surface(shape = RoundedCornerShape(14.dp), color = Tile) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(110.dp)) {
            Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(stateText, color = accent, fontSize = 11.sp)
            if (tile.freshness == Freshness.STALE) {
                Text("stale", color = StaleAmber, fontSize = 10.sp)
            }
            val detail = tile.detail
            if (!detail.isNullOrBlank()) {
                Text(detail, color = Muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CountChip(count: Int, tray: PublishedTray?) {
    Surface(shape = RoundedCornerShape(14.dp), color = Tile) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(110.dp)) {
            Text("Paired nodes", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                if (tray == null) "—" else count.toString(),
                color = if (tray == null) Muted else Cyan,
                fontSize = 11.sp,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkspaceGrid(
    modifier: Modifier,
    settings: AppSettings,
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onPin: (LaunchableApp) -> Unit,
) {
    val hidden = settings.launcherPreferences().hiddenPackages.toSet()
    val pinned =
        settings.launcherPreferences().workspace.mapNotNull { item ->
            apps.firstOrNull { it.packageName == item.packageName && it.packageName !in hidden }
        }
    if (pinned.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Long-press an app in the drawer to pin it here.",
                color = Muted,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(settings.launcherPreferences().gridColumns.coerceIn(3, 6)),
        modifier = modifier,
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(pinned, key = { it.component }) { app ->
            AppCell(app, onClick = { onLaunch(app) }, onLong = { onPin(app) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DockRow(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val hidden = settings.launcherPreferences().hiddenPackages.toSet()
    val dock =
        settings.launcherPreferences().dock.mapNotNull { pkg ->
            apps.firstOrNull { it.packageName == pkg && pkg !in hidden }
        }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = Tile,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dock.take(5).forEach { app ->
                AppCell(app, compact = true, onClick = { onLaunch(app) }, onLong = {})
            }
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Apps, contentDescription = "App drawer", tint = Cyan)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: LaunchableApp,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLong: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLong)
                .padding(6.dp)
                .then(if (compact) Modifier.width(64.dp) else Modifier),
    ) {
        Box(
            Modifier
                .size(if (compact) 44.dp else 52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Cyan.copy(alpha = 0.35f), Magenta.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(app.label.take(1), color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            color = Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrawerOverlay(
    apps: List<LaunchableApp>,
    islandApps: List<LaunchableApp>,
    tab: Int,
    islandLocked: Boolean,
    onTab: (Int) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onPinDock: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
    island: IslandManager,
    activity: Activity,
) {
    OverlayScrim {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Apps", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = Cyan) }
            }
            Row {
                TextButton(onClick = { onTab(0) }) {
                    Text("Personal", color = if (tab == 0) Cyan else Muted)
                }
                TextButton(onClick = { onTab(1) }) {
                    Text("Island", color = if (tab == 1) Magenta else Muted)
                }
            }
            if (tab == 1) {
                IslandHeader(island, activity)
            }
            val shown = if (tab == 0) apps else islandApps
            if (tab == 1 && islandLocked && islandApps.isEmpty()) {
                Text("Island tab is locked. Authenticate to open it.", color = Muted)
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.component }) { app ->
                    AppCell(app, onClick = { onLaunch(app) }, onLong = { onPinDock(app) })
                }
            }
        }
    }
}

@Composable
private fun IslandHeader(island: IslandManager, activity: Activity) {
    Column(Modifier.padding(bottom = 8.dp)) {
        val has = island.hasWorkProfile()
        Text(
            if (has) {
                if (island.isQuietMode()) "Island is paused" else "Island work profile is running"
            } else {
                "No work profile yet"
            },
            color = Muted,
            fontSize = 12.sp,
        )
        Row {
            if (!has) {
                TextButton(
                    onClick = {
                        activity.startActivity(island.provisionIntent())
                    },
                ) { Text("Create Island", color = Magenta) }
            } else {
                TextButton(onClick = { island.pauseWorkProfile() }) { Text("Pause", color = Muted) }
                TextButton(onClick = { island.resumeWorkProfile() }) { Text("Resume", color = Cyan) }
            }
            TextButton(
                onClick = {
                    activity.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                },
            ) { Text("System settings", color = Muted) }
        }
    }
}
