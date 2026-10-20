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
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = VictorLaunchApp.instance.settings
        val catalog = AppCatalog(this)
        val island = IslandManager(this)
        val client = ControllerClient(this, settings)
        val gate = BiometricGate(this)
        setContent {
            VictorLaunchTheme {
                HomeScreen(
                    settings = settings,
                    catalog = catalog,
                    island = island,
                    controller = client,
                    biometric = gate,
                    activity = this,
                )
            }
        }
    }
}
