package com.victorious.launch.launcher

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.island.IslandManager
import com.victorious.launch.lockdown.BiometricGate
import com.victorious.launch.status.ControllerClient
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
            VictorLaunchTheme {
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
