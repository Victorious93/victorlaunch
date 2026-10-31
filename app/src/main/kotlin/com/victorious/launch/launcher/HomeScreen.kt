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
import androidx.compose.material.icons.outlined.WorkOutline
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.TrayTile
import com.victorious.launch.fleet.FleetInstallScreen
import com.victorious.launch.help.HelpScreen
import com.victorious.launch.island.IslandManager
import com.victorious.launch.island.IslandScreen
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.mods.ModsScreen
import com.victorious.launch.network.NetworkScreen
import com.victorious.launch.lockdown.BiometricGate
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.settings.GestureSettingsScreen
import com.victorious.launch.settings.SettingsScreen
import com.victorious.launch.core.launcher.GestureAction
import com.victorious.launch.status.ControllerClient
import com.victorious.launch.status.PullResult
import com.victorious.launch.widgets.FleetWidgets
import com.victorious.launch.widgets.TrayCache
import com.victorious.launch.theme.LocalFleetColors
import kotlinx.coroutines.delay

enum class Overlay {
    NONE,
    DRAWER,
    SETTINGS,
    HELP,
    FLEET,
    MODS,
    NETWORK,
    ISLAND,
    GESTURES,
}

@Composable
fun HomeScreen(
    settings: AppSettings,
    catalog: AppCatalog,
    island: IslandManager,
    controller: ControllerClient,
    biometric: BiometricGate,
    activity: Activity,
    iconPack: IconPackManager,
    widgets: WidgetHostHolder,
) {
    var overlay by remember { mutableStateOf(Overlay.NONE) }
    var drawerTab by remember { mutableStateOf(0) }
    var searchFocused by remember { mutableStateOf(false) }
    var layoutRev by remember { mutableStateOf(0) }
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
                    TrayCache.save(tray!!)
                    FleetWidgets.updateAll(activity, tray)
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
                val bar = LocalFleetColors.current
                IconButton(onClick = { overlay = Overlay.ISLAND }) {
                    Icon(Icons.Outlined.WorkOutline, contentDescription = "Island", tint = bar.secondary)
                }
                IconButton(onClick = { overlay = Overlay.NETWORK }) {
                    Icon(Icons.Outlined.CellTower, contentDescription = "Network Monitor", tint = bar.primary)
                }
                IconButton(onClick = { overlay = Overlay.MODS }) {
                    Icon(Icons.Outlined.Extension, contentDescription = "Mods", tint = bar.secondary)
                }
                IconButton(onClick = { overlay = Overlay.HELP }) {
                    Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = "Help", tint = bar.primary)
                }
                IconButton(onClick = { overlay = Overlay.SETTINGS }) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = bar.primary)
                }
            }
            LawnchairWorkspace(
                settings = settings,
                apps = apps,
                catalog = catalog,
                iconPack = iconPack,
                widgets = widgets,
                activity = activity,
                layoutRev = layoutRev,
                onLaunch = { app -> gatedLaunch(settings, biometric, app, catalog) },
                onOpenDrawer = {
                    searchFocused = false
                    overlay = Overlay.DRAWER
                },
                onOpenSearch = {
                    searchFocused = true
                    overlay = Overlay.DRAWER
                },
                onGesture = { binding ->
                    when (binding.action) {
                        GestureAction.OPEN_DRAWER -> {
                            searchFocused = false
                            overlay = Overlay.DRAWER
                        }
                        GestureAction.OPEN_SEARCH -> {
                            searchFocused = true
                            overlay = Overlay.DRAWER
                        }
                        GestureAction.EXPAND_NOTIFICATIONS -> GestureActions.expandNotifications(activity)
                        GestureAction.OPEN_FLEET_TRAY -> overlay = Overlay.FLEET
                        GestureAction.LAUNCH_APP ->
                            binding.packageName?.let { GestureActions.launchPackage(activity, catalog, it) }
                        GestureAction.LOCK_SCREEN -> GestureActions.lockScreen(activity)
                        else -> Unit
                    }
                },
            )
        }
        when (overlay) {
            Overlay.NONE -> Unit
            Overlay.DRAWER ->
                OverlayScrim {
                    AllAppsDrawer(
                        apps = apps,
                        islandApps = islandApps,
                        tab = drawerTab,
                        islandLocked = settings.launcherPreferences().islandTabLocked,
                        searchFocused = searchFocused,
                        catalog = catalog,
                        iconPack = iconPack,
                        iconPackPackage = settings.iconPackPackage(),
                        iconStyle = settings.iconStyle(),
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
                        onPinWorkspace = { app ->
                            pinToWorkspace(settings, app)
                            layoutRev++
                        },
                        onPinDock = { app ->
                            pinToDock(settings, app)
                            layoutRev++
                        },
                        onCreateFolder = { app ->
                            createFolderFromApp(settings, app)
                            layoutRev++
                        },
                        onClose = { overlay = Overlay.NONE },
                        island = island,
                        activity = activity,
                        onOpenIsland = { overlay = Overlay.ISLAND },
                        settings = settings,
                    )
                }
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
                        onIsland = { overlay = Overlay.ISLAND },
                        onGestures = { overlay = Overlay.GESTURES },
                        onClose = { overlay = Overlay.NONE },
                    )
                }
            Overlay.GESTURES ->
                OverlayScrim {
                    GestureSettingsScreen(
                        settings = settings,
                        apps = apps,
                        activity = activity,
                        onClose = {
                            layoutRev++
                            overlay = Overlay.SETTINGS
                        },
                    )
                }
            Overlay.HELP ->
                OverlayScrim { HelpScreen(onClose = { overlay = Overlay.NONE }) }
            Overlay.FLEET ->
                OverlayScrim { FleetInstallScreen(activity = activity, onClose = { overlay = Overlay.NONE }) }
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
            Overlay.ISLAND ->
                OverlayScrim {
                    IslandScreen(
                        island = island,
                        catalog = catalog,
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

@Composable
private fun OverlayScrim(content: @Composable () -> Unit) {
    val colors = LocalFleetColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background.copy(alpha = 0.96f)),
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
    val colors = LocalFleetColors.current
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp)) {
        Text(
            "VictorLaunch",
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
        )
        Text(note, color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
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
    val colors = LocalFleetColors.current
    val accent =
        when (tile.state) {
            StatusTrayState.UNKNOWN -> colors.muted
            StatusTrayState.OK -> if (tile.freshness == Freshness.STALE) colors.stale else colors.primary
            StatusTrayState.DEGRADED -> colors.stale
            StatusTrayState.DOWN -> colors.danger
            StatusTrayState.OFF -> colors.muted
        }
    Surface(shape = RoundedCornerShape(14.dp), color = colors.tile) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(110.dp)) {
            Text(label, color = colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(stateText, color = accent, fontSize = 11.sp)
            if (tile.freshness == Freshness.STALE) {
                Text("stale", color = colors.stale, fontSize = 10.sp)
            }
            val detail = tile.detail
            if (!detail.isNullOrBlank()) {
                Text(detail, color = colors.muted, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CountChip(count: Int, tray: PublishedTray?) {
    val colors = LocalFleetColors.current
    Surface(shape = RoundedCornerShape(14.dp), color = colors.tile) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp).width(110.dp)) {
            Text("Paired nodes", color = colors.onBackground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(
                if (tray == null) "—" else count.toString(),
                color = if (tray == null) colors.muted else colors.primary,
                fontSize = 11.sp,
            )
        }
    }
}

