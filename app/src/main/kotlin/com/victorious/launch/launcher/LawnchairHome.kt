package com.victorious.launch.launcher

import android.appwidget.AppWidgetManager
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.launcher.GestureAction
import com.victorious.launch.core.launcher.GestureBinding
import com.victorious.launch.core.launcher.HomeGesture
import com.victorious.launch.core.launcher.GestureMap
import com.victorious.launch.core.launcher.WorkspacePages
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors
import java.util.UUID
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LawnchairWorkspace(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    widgets: WidgetHostHolder,
    activity: android.app.Activity,
    layoutRev: Int,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenDrawer: () -> Unit,
    onOpenSearch: () -> Unit,
    onGesture: (GestureBinding) -> Unit = { b ->
        when (b.action) {
            GestureAction.OPEN_DRAWER -> onOpenDrawer()
            GestureAction.OPEN_SEARCH -> onOpenSearch()
            else -> Unit
        }
    },
) {
    val gestureBindings = settings.gestureBindings().also { layoutRev }
    val colors = LocalFleetColors.current
    val iconStyle = settings.iconStyle()
    val columns = settings.launcherPreferences().gridColumns.coerceIn(3, 6)
    val hidden = settings.launcherPreferences().hiddenPackages.toSet()
    val items = settings.launcherPreferences().workspace.also { layoutRev }
    val pages = WorkspacePages.pageCount(items).coerceAtLeast(1)
    val pagerState = rememberPagerState(pageCount = { pages })
    val scope = rememberCoroutineScope()
    var picker by remember { mutableStateOf(false) }
    var openFolder by remember { mutableStateOf<WorkspaceItem?>(null) }
    var pendingWidgetId by remember { mutableIntStateOf(AppWidgetManager.INVALID_APPWIDGET_ID) }
    val bindWidget =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pendingWidgetId
            if (result.resultCode == android.app.Activity.RESULT_OK && id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                placeWidget(settings, id)
            } else if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
                widgets.deleteId(id)
            }
            pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
        }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(pages, layoutRev) {
                val threshold = 48.dp.toPx()
                var lastTapUpMs = 0L
                var lastTapPos = androidx.compose.ui.geometry.Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var maxPointers = 1
                    var startSpan = 0f
                    var endSpan = 0f
                    var startCy = 0f
                    var endCy = 0f
                    var drag = androidx.compose.ui.geometry.Offset.Zero
                    var childConsumed = false
                    var upConsumed = false
                    var upTime = down.uptimeMillis
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (event.changes.any { it.isConsumed }) childConsumed = true
                        if (pressed.isEmpty()) {
                            upConsumed = event.changes.any { it.isConsumed }
                            upTime = event.changes.maxOf { it.uptimeMillis }
                            break
                        }
                        if (pressed.size >= 2) {
                            val cy = pressed.map { it.position.y }.average().toFloat()
                            val span = (pressed[0].position - pressed[1].position).getDistance()
                            if (maxPointers < 2) {
                                startSpan = span
                                startCy = cy
                            }
                            endSpan = span
                            endCy = cy
                        } else if (maxPointers == 1) {
                            drag += pressed[0].positionChange()
                        }
                        maxPointers = maxOf(maxPointers, pressed.size)
                    }
                    val gesture =
                        if (maxPointers >= 2) {
                            GestureMap.classifyMultiTouch(startSpan, endSpan, endCy - startCy, threshold)
                        } else if (!childConsumed) {
                            GestureMap.classifyDrag(drag.x, drag.y, threshold)
                                ?: run {
                                    if (upConsumed) return@run null
                                    val d = down.position - lastTapPos
                                    if (GestureMap.isDoubleTap(lastTapUpMs, down.uptimeMillis, d.x, d.y, slop = threshold * 2)) {
                                        lastTapUpMs = 0L
                                        HomeGesture.DOUBLE_TAP
                                    } else {
                                        lastTapUpMs = upTime
                                        lastTapPos = down.position
                                        null
                                    }
                                }
                        } else {
                            null
                        }
                    if (gesture == null) return@awaitEachGesture
                    val binding = GestureMap.resolve(gesture, gestureBindings)
                    when (binding.action) {
                        GestureAction.NEXT_PAGE ->
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(pages - 1))
                            }
                        GestureAction.PREV_PAGE ->
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage - 1).coerceAtLeast(0))
                            }
                        GestureAction.NOOP -> Unit
                        else -> onGesture(binding)
                    }
                }
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                WorkspacePage(
                    page = page,
                    columns = columns,
                    items = WorkspacePages.itemsOnPage(items, page),
                    apps = apps,
                    hidden = hidden,
                    catalog = catalog,
                    iconPack = iconPack,
                    iconPackPackage = settings.iconPackPackage(),
                    iconStyle = iconStyle,
                    widgets = widgets,
                    onLaunch = onLaunch,
                    onOpenFolder = { openFolder = it },
                    onRemove = { item ->
                        if (item.kind == WorkspaceItem.KIND_WIDGET) {
                            item.widgetId?.let { widgets.deleteId(it) }
                        }
                        settings.setWorkspace(WorkspacePages.removeItem(items, item.itemId()))
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("p.${pagerState.currentPage + 1}/$pages", color = colors.muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                IconButton(onClick = { picker = true }) {
                    Icon(Icons.Outlined.Widgets, contentDescription = "Add widget", tint = colors.secondary)
                }
            }
            DockRow(
                settings = settings,
                apps = apps,
                catalog = catalog,
                iconPack = iconPack,
                onLaunch = onLaunch,
                onOpenDrawer = onOpenDrawer,
            )
        }
        if (picker) {
            WidgetPicker(
                widgets = widgets,
                onClose = { picker = false },
                onPick = { info ->
                    picker = false
                    val id = widgets.allocateId()
                    val allowed = widgets.manager.bindAppWidgetIdIfAllowed(id, info.provider)
                    if (allowed) {
                        val configure = info.configure
                        if (configure != null) {
                            pendingWidgetId = id
                            bindWidget.launch(
                                Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                                    component = configure
                                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                },
                            )
                        } else {
                            placeWidget(settings, id)
                        }
                    } else {
                        pendingWidgetId = id
                        bindWidget.launch(
                            Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                            },
                        )
                    }
                },
            )
        }
        val folder = openFolder
        if (folder != null) {
            FolderSheet(
                folder = folder,
                apps = apps,
                catalog = catalog,
                iconPack = iconPack,
                iconPackPackage = settings.iconPackPackage(),
                iconStyle = iconStyle,
                onLaunch = onLaunch,
                onClose = { openFolder = null },
            )
        }
    }
}

@Composable
private fun WorkspacePage(
    page: Int,
    columns: Int,
    items: List<WorkspaceItem>,
    apps: List<LaunchableApp>,
    hidden: Set<String>,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    iconPackPackage: String?,
    iconStyle: FleetIconStyle,
    widgets: WidgetHostHolder,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenFolder: (WorkspaceItem) -> Unit,
    onRemove: (WorkspaceItem) -> Unit,
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Swipe up for the drawer. Long-press an app to pin it, or tap the widget button.",
                color = LocalFleetColors.current.muted,
                modifier = Modifier.padding(24.dp),
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items,
            key = { it.itemId() },
            span = { item ->
                if (item.kind == WorkspaceItem.KIND_WIDGET) {
                    GridItemSpan(item.spanX.coerceIn(1, columns))
                } else {
                    GridItemSpan(1)
                }
            },
        ) { item ->
            when (item.kind) {
                WorkspaceItem.KIND_WIDGET -> {
                    val id = item.widgetId
                    if (id != null) {
                        val info = widgets.manager.getAppWidgetInfo(id)
                        if (info != null) {
                            AndroidView(
                                factory = { ctx -> widgets.host.createView(ctx, id, info) },
                                modifier = Modifier.fillMaxWidth().height((item.spanY.coerceIn(1, 4) * 80).dp),
                            )
                        }
                    }
                }
                WorkspaceItem.KIND_FOLDER -> {
                    FolderCell(item, onClick = { onOpenFolder(item) }, onRemove = { onRemove(item) })
                }
                else -> {
                    val app = apps.firstOrNull { it.packageName == item.packageName && it.packageName !in hidden }
                    if (app != null) {
                        AppCell(
                            app = app,
                            catalog = catalog,
                            iconPack = iconPack,
                            iconPackPackage = iconPackPackage,
                            iconStyle = iconStyle,
                            onClick = { onLaunch(app) },
                            onLong = { },
                            onRemove = { onRemove(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderCell(item: WorkspaceItem, onClick: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalFleetColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.tile,
        modifier = Modifier.padding(6.dp),
        onClick = onClick,
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(item.title ?: "Folder", color = colors.primary, fontSize = 12.sp)
            Text("${item.children.size} apps", color = colors.muted, fontSize = 10.sp)
            TextButton(onClick = onRemove) { Text("Remove", color = colors.secondary, fontSize = 10.sp) }
        }
    }
}

@Composable
private fun DockRow(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    onLaunch: (LaunchableApp) -> Unit,
    onOpenDrawer: () -> Unit,
) {
    val colors = LocalFleetColors.current
    val hidden = settings.launcherPreferences().hiddenPackages.toSet()
    val dock =
        settings.launcherPreferences().dock.mapNotNull { pkg ->
            apps.firstOrNull { it.packageName == pkg && pkg !in hidden }
        }
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(28.dp),
        color = colors.tile,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dock.take(5).forEach { app ->
                AppCell(
                    app = app,
                    catalog = catalog,
                    iconPack = iconPack,
                    iconPackPackage = settings.iconPackPackage(),
                    iconStyle = settings.iconStyle(),
                    compact = true,
                    onClick = { onLaunch(app) },
                    onLong = { },
                )
            }
            IconButton(onClick = onOpenDrawer) {
                Icon(Icons.Outlined.Apps, contentDescription = "App drawer", tint = colors.primary)
            }
        }
    }
}

@Composable
private fun WidgetPicker(
    widgets: WidgetHostHolder,
    onClose: () -> Unit,
    onPick: (android.appwidget.AppWidgetProviderInfo) -> Unit,
) {
    val colors = LocalFleetColors.current
    val providers = remember { widgets.providers() }
    Surface(Modifier.fillMaxSize().padding(24.dp), color = colors.tile, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Widgets", color = colors.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = colors.secondary) }
            }
            Text(
                "System AppWidgetHost. Binding may ask Android for permission. [Likely] not all providers configure cleanly.",
                color = colors.muted,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f)) {
                items(providers.size) { index ->
                    val info = providers[index]
                    val label = info.loadLabel(widgets.packageManager).toString().ifBlank { info.provider.flattenToShortString() }
                    TextButton(onClick = { onPick(info) }) {
                        Text(label, color = colors.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderSheet(
    folder: WorkspaceItem,
    apps: List<LaunchableApp>,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    iconPackPackage: String?,
    iconStyle: FleetIconStyle = FleetIconStyle.SYSTEM,
    onLaunch: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
) {
    val colors = LocalFleetColors.current
    Surface(Modifier.fillMaxSize().padding(32.dp), color = colors.tile, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(folder.title ?: "Folder", color = colors.secondary, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
            }
            val children = folder.children.mapNotNull { pkg -> apps.firstOrNull { it.packageName == pkg } }
            LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.weight(1f)) {
                items(children, key = { it.component }) { app ->
                    AppCell(
                        app = app,
                        catalog = catalog,
                        iconPack = iconPack,
                        iconPackPackage = iconPackPackage,
                        iconStyle = iconStyle,
                        onClick = { onLaunch(app) },
                        onLong = { },
                    )
                }
            }
        }
    }
}

fun pinToDock(settings: AppSettings, app: LaunchableApp) {
    val dock = settings.launcherPreferences().dock.toMutableList()
    if (app.packageName !in dock) {
        if (dock.size >= 5) dock.removeAt(0)
        dock.add(app.packageName)
        settings.setDock(dock)
    }
}

fun pinToWorkspace(settings: AppSettings, app: LaunchableApp) {
    settings.setWorkspace(
        WorkspacePages.pinApp(
            settings.launcherPreferences().workspace,
            app.packageName,
            settings.launcherPreferences().gridColumns,
        ),
    )
}

fun createFolderFromApp(settings: AppSettings, app: LaunchableApp) {
    val workspace = settings.launcherPreferences().workspace
    val other =
        workspace.firstOrNull { it.kind == WorkspaceItem.KIND_APP && it.packageName != app.packageName }
            ?: return
    settings.setWorkspace(
        WorkspacePages.createFolder(
            workspace,
            other.packageName,
            app.packageName,
            UUID.randomUUID().toString(),
            "Folder",
            settings.launcherPreferences().gridColumns,
        ),
    )
}

private fun placeWidget(settings: AppSettings, widgetId: Int) {
    val workspace = settings.launcherPreferences().workspace
    val (page, x, y) = WorkspacePages.nextEmptySlot(workspace, settings.launcherPreferences().gridColumns)
    settings.setWorkspace(
        workspace +
            WorkspaceItem(
                packageName = "",
                x = x,
                y = y,
                kind = WorkspaceItem.KIND_WIDGET,
                page = page,
                widgetId = widgetId,
                spanX = 2,
                spanY = 2,
            ),
    )
}
