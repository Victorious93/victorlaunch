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
import androidx.compose.material3.DropdownMenu
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
) {
    val colors = LocalFleetColors.current
    val image =
        remember(app.component, iconPackPackage, iconStyle, colors.primary) {
            val packed = iconPack.loadComponentIcon(iconPackPackage, app.component)
            val raw = packed ?: catalog.loadIcon(app)
            raw?.toThemedImageBitmap(iconStyle, colors.primary.toArgb())
        }
    var menu by remember { mutableStateOf(false) }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = {
                        if (onDock != null || onFolder != null || onRemove != null) {
                            menu = true
                        } else {
                            onLong()
                        }
                    },
                )
                .padding(6.dp)
                .then(if (compact) Modifier.width(64.dp) else Modifier),
    ) {
        Box(
            Modifier
                .size(if (compact) 44.dp else 52.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.35f), colors.secondary.copy(alpha = 0.35f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = app.label,
                    modifier = Modifier.size(if (compact) 40.dp else 48.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(app.label.take(1), color = colors.onBackground, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            app.label,
            color = colors.onBackground,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("Pin to workspace") },
                onClick = {
                    menu = false
                    onLong()
                },
            )
            if (onDock != null) {
                DropdownMenuItem(
                    text = { Text("Pin to dock") },
                    onClick = {
                        menu = false
                        onDock()
                    },
                )
            }
            if (onFolder != null) {
                DropdownMenuItem(
                    text = { Text("New folder") },
                    onClick = {
                        menu = false
                        onFolder()
                    },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Remove") },
                    onClick = {
                        menu = false
                        onRemove()
                    },
                )
            }
        }
    }
}
