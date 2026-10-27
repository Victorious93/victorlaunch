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
import com.victorious.launch.core.launcher.SearchableApp
import com.victorious.launch.island.IslandManager
import com.victorious.launch.theme.Cyan
import com.victorious.launch.theme.Magenta
import com.victorious.launch.theme.Muted

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
    onTab: (Int) -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onPinWorkspace: (LaunchableApp) -> Unit,
    onPinDock: (LaunchableApp) -> Unit,
    onCreateFolder: (LaunchableApp) -> Unit,
    onClose: () -> Unit,
    island: IslandManager,
    activity: Activity,
    onOpenIsland: () -> Unit,
) {
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
    val source = if (tab == 0) apps else islandApps
    val filtered =
        remember(source, query) {
            val hits =
                AppSearch.filter(
                    source.map { SearchableApp(it.packageName, it.label, it.component) },
                    query,
                ).map { it.packageName }.toSet()
            source.filter { it.packageName in hits }
        }
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", color = Color.White, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = Cyan) }
        }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth().focusRequester(focus).padding(bottom = 8.dp),
            placeholder = { Text("Search apps", color = Muted) },
            singleLine = true,
            colors =
                TextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    cursorColor = Cyan,
                ),
        )
        Row {
            TextButton(onClick = { onTab(0) }) {
                Text("Personal", color = if (tab == 0) Cyan else Muted)
            }
            TextButton(onClick = { onTab(1) }) {
                Text("Island", color = if (tab == 1) Magenta else Muted)
            }
        }
        if (tab == 1) {
            IslandHeader(island, activity, onOpenIsland)
        }
        if (tab == 1 && islandLocked && islandApps.isEmpty()) {
            Text("Island tab is locked. Authenticate to open it.", color = Muted)
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
                    onClick = { onLaunch(app) },
                    onLong = { onPinWorkspace(app) },
                    onDock = { onPinDock(app) },
                    onFolder = { onCreateFolder(app) },
                )
            }
        }
    }
}

@Composable
fun IslandHeader(island: IslandManager, activity: Activity, onOpenIsland: () -> Unit) {
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
                TextButton(onClick = { activity.startActivity(island.provisionIntent()) }) {
                    Text("Create Island", color = Magenta)
                }
            } else {
                TextButton(onClick = { island.pauseWorkProfile() }) { Text("Pause", color = Muted) }
                TextButton(onClick = { island.resumeWorkProfile() }) { Text("Resume", color = Cyan) }
            }
            TextButton(onClick = onOpenIsland) { Text("Manage Island", color = Magenta) }
            TextButton(onClick = { activity.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS)) }) {
                Text("System settings", color = Muted)
            }
        }
    }
}
