package com.victorious.launch

import android.app.Application
import com.victorious.launch.core.bridge.ActionAuditLog
import com.victorious.launch.network.NetworkMonitorController
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.shizuku.PrivilegedOps
import com.victorious.launch.share.FleetSharePublisher
import com.victorious.launch.shizuku.ShizukuSession

class VictorLaunchApp : Application() {
    lateinit var settings: AppSettings
        private set
    lateinit var shizuku: ShizukuSession
        private set
    lateinit var privileged: PrivilegedOps
        private set
    lateinit var network: NetworkMonitorController
        private set
    val actionAudit = ActionAuditLog()

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = AppSettings(this)
        settings.nodeId()
        shizuku =
            ShizukuSession(
                userEnabled = { settings.isShizukuEnabled() },
                onChanged = { privileged.refreshBinding() },
            )
        privileged = PrivilegedOps(this, settings, shizuku)
        network = NetworkMonitorController(this, settings, privileged, shizuku)
        shizuku.start()
        if (settings.isShizukuEnabled()) {
            privileged.refreshBinding()
        }
        if (settings.isNetworkMonitorEnabled()) {
            network.start()
        }
        FleetSharePublisher.publish(this, settings)
    }

    companion object {
        lateinit var instance: VictorLaunchApp
            private set
    }
}
