package com.victorious.launch.launcher

import android.app.Activity
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.launcher.AppSearch
import com.victorious.launch.core.launcher.DrawerModel
import com.victorious.launch.core.launcher.DrawerTab
import com.victorious.launch.core.launcher.SearchableApp
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.island.IslandManager
import com.victorious.launch.theme.LocalFleetColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AllAppsDrawer(
    apps: List<LaunchableApp>,
    islandApps: List<LaunchableApp>,
    tab: Int,
    islandLocked: Boolean,
    searchFocused: Boolean,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    iconPackPackage: String?,
    iconStyle: FleetIconStyle = FleetIconStyle.SYSTEM,
    onTab: (Int) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onPinWorkspace: (LaunchableApp) -> Unit,
    onPinDock: (LaunchableApp) -> Unit,
    onCreateFolder: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
    island: IslandManager,
    activity: Activity,
    onOpenIsland: () -> Unit,
    settings: com.victorious.launch.prefs.AppSettings? = null,
) {
    val colors = LocalFleetColors.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searchFocused) {
        if (searchFocused) {
            try {
                focus.requestFocus()
            } catch (_: Exception) {
            }
        }
    }
    var rev by remember { mutableStateOf(0) }
    val hidden = remember(rev) { settings?.launcherPreferences()?.hiddenPackages?.toSet().orEmpty() }
    var showHidden by remember { mutableStateOf(settings?.isShowHiddenInDrawer() ?: false) }
    val userTabs = remember(rev) { settings?.drawerTabs().orEmpty() }
    // "all", "user:<id>" or "cat:<CATEGORY>"
    var section by remember { mutableStateOf("all") }
    var newTabName by remember { mutableStateOf<String?>(null) }
    val categories = remember(apps) { DrawerModel.presentCategories(apps.map { it.category }) }
    val source0 = if (tab == 0) apps else islandApps
    val source =
        remember(source0, hidden, showHidden) {
            val keep = DrawerModel.visible(source0.map { it.packageName }, hidden, showHidden).toSet()
            source0.filter { it.packageName in keep }
        }
    val filtered =
        remember(source, query, section, userTabs) {
            if (query.isNotBlank()) {
                // Instant fuzzy search across every visible app, ignoring the selected tab.
                val byPkg = source.associateBy { it.component }
                AppSearch.fuzzy(source.map { SearchableApp(it.packageName, it.label, it.component) }, query)
                    .mapNotNull { byPkg[it.component] }
            } else if (tab == 1 || section == "all") {
                source
            } else if (section.startsWith("user:")) {
                val pk = userTabs.firstOrNull { "user:" + it.id == section }?.packages.orEmpty().toSet()
                source.filter { it.packageName in pk }
            } else {
                source.filter { "cat:" + it.category.name == section }
            }
        }
    fun saveTabs(next: List<DrawerTab>) {
        settings?.setDrawerTabs(next)
        rev++
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = colors.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = 8.dp),
            placeholder = { Text("Search apps", color = colors.muted) },
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = colors.onBackground,
                    unfocusedTextColor = colors.onBackground,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = colors.primary,
                ),
        )
        Row {
            TextButton(onClick = { onTab(0) }) {
                Text("Personal", color = if (tab == 0) colors.primary else colors.muted)
            }
            TextButton(onClick = { onTab(1) }) {
                Text("Island", color = if (tab == 1) colors.secondary else colors.muted)
            }
        }
        if (tab == 0) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                item { Chip("All", section == "all") { section = "all" } }
                items(userTabs, key = { "u" + it.id }) { t ->
                    Chip(t.title, section == "user:" + t.id) { section = "user:" + t.id }
                }
                items(categories, key = { "c" + it.name }) { c ->
                    Chip(c.title, section == "cat:" + c.name) { section = "cat:" + c.name }
                }
                if (settings != null) {
                    item { Chip("+ Tab", false) { newTabName = "" } }
                    item {
                        Chip(if (showHidden) "Hidden: shown" else "Hidden: off", showHidden) {
                            showHidden = !showHidden
                            settings.setShowHiddenInDrawer(showHidden)
                        }
                    }
                }
            }
            val name = newTabName
            if (name != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { newTabName = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Tab name", color = colors.muted) },
                        singleLine = true,
                    )
                    TextButton(onClick = {
                        val id = java.util.UUID.randomUUID().toString()
                        saveTabs(DrawerModel.addTab(userTabs, id, name))
                        section = "user:$id"
                        newTabName = null
                    }) { Text("Add", color = colors.primary) }
                    TextButton(onClick = { newTabName = null }) { Text("Cancel", color = colors.muted) }
                }
            }
            if (section.startsWith("user:")) {
                val id = section.removePrefix("user:")
                TextButton(onClick = {
                    saveTabs(DrawerModel.removeTab(userTabs, id))
                    section = "all"
                }) { Text("Delete this tab (apps stay installed)", color = colors.muted, fontSize = 12.sp) }
                if (filtered.isEmpty() && query.isBlank()) {
                    Text("Empty tab. Long-press an app in All and choose \"Add to …\".", color = colors.muted, fontSize = 12.sp)
                }
            }
        }
        if (tab == 1) {
            IslandHeader(island, activity, onOpenIsland)
        }
        if (tab == 1 && islandLocked && islandApps.isEmpty()) {
            Text("Island tab is locked. Authenticate to open it.", color = colors.muted)
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(filtered, key = { it.component }) { app ->
                AppCell(
                    app = app,
                    catalog = catalog,
                    iconPack = iconPack,
                    iconPackPackage = iconPackPackage,
                    iconStyle = iconStyle,
                    onClick = { onLaunch(app) },
                    onLong = { onPinWorkspace(app) },
                    onDock = { onPinDock(app) },
                    onFolder = { onCreateFolder(app) },
                    extraMenu =
                        if (settings == null) {
                            emptyList()
                        } else {
                            buildList {
                                add(
                                    (if (app.packageName in hidden) "Unhide in drawer" else "Hide from drawer") to {
                                        settings.setHidden(DrawerModel.toggleHidden(hidden, app.packageName))
                                        rev++
                                    },
                                )
                                userTabs.forEach { t ->
                                    val inTab = app.packageName in t.packages
                                    add(
                                        (if (inTab) "Remove from ${t.title}" else "Add to ${t.title}") to {
                                            saveTabs(DrawerModel.toggleMembership(userTabs, t.id, app.packageName))
                                        },
                                    )
                                }
                            }
                        },
                )
            }
        }
    }
}

@Composable
fun IslandHeader(island: IslandManager, activity: Activity, onOpenIsland: () -> Unit) {
    val colors = LocalFleetColors.current
    Column(Modifier.padding(bottom = 8.dp)) {
        val has = island.hasWorkProfile()
        Text(
            if (has) {
                if (island.isQuietMode()) "Island is paused" else "Island work profile is running"
            } else {
                "No work profile yet"
            },
            color = colors.muted,
            fontSize = 12.sp,
        )
        Row {
            if (!has) {
                TextButton(onClick = { activity.startActivity(island.provisionIntent()) }) {
                    Text("Create Island", color = colors.secondary)
                }
            } else {
                TextButton(onClick = { island.pauseWorkProfile() }) { Text("Pause", color = colors.muted) }
                TextButton(onClick = { island.resumeWorkProfile() }) { Text("Resume", color = colors.primary) }
            }
            TextButton(onClick = onOpenIsland) { Text("Manage Island", color = colors.secondary) }
            TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }) {
                Text("System settings", color = colors.muted)
            }
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalFleetColors.current
    TextButton(onClick = onClick) {
        Text(label, color = if (selected) colors.primary else colors.muted, fontSize = 13.sp)
    }
}
