package com.victorious.launch.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.victorious.launch.core.launcher.GestureAction
import com.victorious.launch.core.launcher.GestureBinding
import com.victorious.launch.core.launcher.GestureMap
import com.victorious.launch.core.launcher.HomeGesture
import com.victorious.launch.launcher.LaunchableApp
import com.victorious.launch.launcher.LockScreenAccessibilityService
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors

/** Nova-style gesture assignment. */
@Composable
fun GestureSettingsScreen(
    settings: AppSettings,
    apps: List<LaunchableApp>,
    activity: android.app.Activity,
    onClose: () -> Unit,
) {
    val colors = LocalFleetColors.current
    var bindings by remember { mutableStateOf(settings.gestureBindings()) }
    var editing by remember { mutableStateOf<HomeGesture?>(null) }
    var pickingApp by remember { mutableStateOf(false) }

    fun save(g: HomeGesture, b: GestureBinding) {
        settings.setGestureBinding(g, b)
        bindings = settings.gestureBindings()
        editing = null
        pickingApp = false
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Home gestures", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        Text(
            "Swipe left/right always turns pages. Lock screen needs the optional \"VictorLaunch gesture lock\" accessibility service (" +
                (if (LockScreenAccessibilityService.isEnabled()) "on" else "off") + ").",
            color = colors.muted,
            fontSize = 12.sp,
        )
        TextButton(
            onClick = {
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
        ) { Text("Open Accessibility settings", color = colors.secondary) }

        val gesture = editing
        if (gesture == null) {
            GestureMap.ASSIGNABLE.forEach { g ->
                val b = GestureMap.resolve(g, bindings)
                val label =
                    if (b.action == GestureAction.LAUNCH_APP) {
                        "Launch " + (apps.firstOrNull { it.packageName == b.packageName }?.label ?: b.packageName)
                    } else {
                        b.action.label
                    }
                TextButton(onClick = { editing = g }) {
                    Text("${g.label}: $label", color = colors.primary)
                }
            }
        } else if (!pickingApp) {
            Text("${gesture.label} →", color = colors.secondary, modifier = Modifier.padding(top = 12.dp))
            val current = GestureMap.resolve(gesture, bindings).action
            GestureMap.USER_ACTIONS.forEach { a ->
                TextButton(
                    onClick = {
                        if (a == GestureAction.LAUNCH_APP) pickingApp = true else save(gesture, GestureBinding(a))
                    },
                ) {
                    Text(if (a == current) "● ${a.label}" else "○ ${a.label}", color = if (a == current) colors.primary else colors.muted)
                }
            }
            TextButton(onClick = { editing = null }) { Text("Back", color = colors.muted) }
        } else {
            Text("Pick an app for ${gesture.label}", color = colors.secondary, modifier = Modifier.padding(top = 12.dp))
            TextButton(onClick = { pickingApp = false }) { Text("Back", color = colors.muted) }
            apps.sortedBy { it.label.lowercase() }.forEach { app ->
                TextButton(onClick = { save(gesture, GestureBinding(GestureAction.LAUNCH_APP, app.packageName)) }) {
                    Text(app.label, color = colors.primary)
                }
            }
        }
    }
}
