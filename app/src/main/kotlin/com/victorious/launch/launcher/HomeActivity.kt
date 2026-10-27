package com.victorious.launch.launcher

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.island.IslandManager
import com.victorious.launch.lockdown.BiometricGate
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.status.ControllerClient
import com.victorious.launch.theme.FleetThemeResolver
import com.victorious.launch.theme.VictorLaunchTheme

class HomeActivity : FragmentActivity() {
    private lateinit var widgets: WidgetHostHolder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = VictorLaunchApp.instance.settings
        val catalog = AppCatalog(this)
        val island = IslandManager(this)
        val client = ControllerClient(this, settings)
        val gate = BiometricGate(this)
        val iconPack = IconPackManager(packageManager)
        widgets = WidgetHostHolder(this)
        widgets.start()
        setContent {
            var themeTick by remember { mutableIntStateOf(0) }
            DisposableEffect(settings) {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                        if (key != null && key in AppSettings.THEME_KEYS) {
                            themeTick++
                        }
                    }
                settings.register(listener)
                onDispose { settings.unregister(listener) }
            }
            val snapshot = remember(themeTick) { FleetThemeResolver.current(this, settings) }
            VictorLaunchTheme(snapshot) {
                HomeScreen(
                    settings = settings,
                    catalog = catalog,
                    island = island,
                    controller = client,
                    biometric = gate,
                    activity = this,
                    iconPack = iconPack,
                    widgets = widgets,
                )
            }
        }
    }

    override fun onDestroy() {
        if (this::widgets.isInitialized) {
            widgets.stop()
        }
        super.onDestroy()
    }
}
