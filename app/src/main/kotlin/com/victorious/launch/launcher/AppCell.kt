package com.victorious.launch.launcher

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dock
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shop
import androidx.compose.material.icons.filled.VerticalSplit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.theme.LocalFleetColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppCell(
    app: LaunchableApp,
    catalog: AppCatalog,
    iconPack: IconPackManager,
    iconPackPackage: String?,
    iconStyle: FleetIconStyle = FleetIconStyle.SYSTEM,
    compact: Boolean = false,
    onClick: () -> Unit,
    onLong: () -> Unit,
    onDock: (() -> Unit)? = null,
    onFolder: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
    extraMenu: List<Pair<String, () -> Unit>> = emptyList(),
    iconScalePct: Int = 100,
    showLabel: Boolean = true,
    labelSp: Int = 11,
) {
    val outerDp = ((if (compact) 44 else 52) * iconScalePct + 50) / 100
    val innerDp = ((if (compact) 40 else 48) * iconScalePct + 50) / 100
    val colors = LocalFleetColors.current
    val image =
        remember(app.component, iconPackPackage, iconStyle, colors.primary) {
            val packed = iconPack.loadComponentIcon(iconPackPackage, app.component)
            val raw = packed ?: catalog.loadIcon(app)
            raw?.toThemedImageBitmap(iconStyle, colors.primary.toArgb())
        }
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // Loaded only when the menu opens, so scrolling the drawer stays cheap.
    val shortcuts = remember(menu, app.component) { if (menu) AppActions.shortcuts(context, app) else emptyList() }
    val uninstall = remember(menu, app.component) { if (menu) AppActions.uninstallKind(context, app) else UninstallKind.NONE }
    var inspect by remember { mutableStateOf(false) }
    if (inspect) {
        com.victorious.launch.inspect.AppInspectorDialog(app.packageName, app.label) { inspect = false }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menu = true
                    },
                )
                .padding(6.dp)
                .then(if (compact) Modifier.width((outerDp + 20).dp) else Modifier),
    ) {
        Box(
            Modifier
                .size(outerDp.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.35f), colors.secondary.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = app.label,
                    modifier = Modifier.size(innerDp.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(app.label.take(1), color = colors.onBackground, fontWeight = FontWeight.Bold)
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(4.dp))
            Text(
                app.label,
                color = colors.onBackground,
                fontSize = labelSp.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            val close = { menu = false }
            Text(
                app.label + if (app.isWorkProfile) "  · Island" else "",
                color = colors.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).widthIn(max = 240.dp),
            )
            shortcuts.forEach { sc -> MenuItem(sc.label, Icons.AutoMirrored.Filled.ArrowForward, close) { AppActions.startShortcut(context, sc) } }
            if (shortcuts.isNotEmpty()) HorizontalDivider()
            MenuItem("Pin to workspace", Icons.Filled.PushPin, close) { onLong() }
            if (onDock != null) MenuItem("Pin to dock", Icons.Filled.Dock, close) { onDock() }
            if (onFolder != null) MenuItem("New folder", Icons.Filled.CreateNewFolder, close) { onFolder() }
            MenuItem("Split screen", Icons.Filled.VerticalSplit, close) { AppActions.openSplit(context, app) }
            extraMenu.forEach { (label, action) -> MenuItem(label, Icons.Filled.MoreHoriz, close) { action() } }
            HorizontalDivider()
            MenuItem("App info", Icons.Filled.Info, close) { AppActions.appInfo(context, app) }
            MenuItem("Inspect / extract APK", Icons.Filled.Search, close) { inspect = true }
            MenuItem("Open in store", Icons.Filled.Shop, close) { AppActions.openInPlayStore(context, app) }
            if (onRemove != null) MenuItem("Remove from here", Icons.Filled.RemoveCircleOutline, close) { onRemove() }
            when (uninstall) {
                UninstallKind.UNINSTALL -> MenuItem("Uninstall", Icons.Filled.Delete, close, danger = true) { AppActions.uninstall(context, app) }
                UninstallKind.UNINSTALL_UPDATES -> MenuItem("Uninstall updates", Icons.Filled.Delete, close, danger = true) { AppActions.uninstall(context, app) }
                UninstallKind.NONE -> {}
            }
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: ImageVector, close: () -> Unit, danger: Boolean = false, action: () -> Unit) {
    val colors = LocalFleetColors.current
    val tint = if (danger) colors.danger else colors.primary
    DropdownMenuItem(
        text = { Text(label, color = if (danger) colors.danger else colors.onBackground) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = { close(); action() },
    )
}
