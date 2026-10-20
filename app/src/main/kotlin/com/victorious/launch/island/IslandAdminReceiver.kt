package com.victorious.launch.island

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Profile-owner admin for the managed work profile (Island).
 *
 * Pattern inspired by Island (https://github.com/oasisfeng/island, GPLv3) and
 * Insular. No Island/Insular source is copied here; this uses public
 * DevicePolicyManager / UserManager APIs only.
 */
class IslandAdminReceiver : DeviceAdminReceiver() {
    override fun onProfileProvisioningComplete(context: Context, intent: Intent) {
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = component(context)
        dpm.setProfileName(admin, "Island")
        val filter =
            IntentFilter().apply {
                addAction(IslandCommandReceiver.ACTION)
            }
        dpm.addCrossProfileIntentFilter(
            admin,
            filter,
            DevicePolicyManager.FLAG_PARENT_CAN_ACCESS_MANAGED or
                DevicePolicyManager.FLAG_MANAGED_CAN_ACCESS_PARENT,
        )
        dpm.setProfileEnabled(admin)
    }

    companion object {
        fun component(context: Context): ComponentName =
            ComponentName(context, IslandAdminReceiver::class.java)
    }
}
