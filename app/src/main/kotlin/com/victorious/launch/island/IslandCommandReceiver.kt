package com.victorious.launch.island

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.victorious.launch.core.island.IslandPolicy

/**
 * Runs inside the work profile (profile owner) to clone / freeze / hide.
 * Personal-profile UI sends [ACTION] through the cross-profile intent filter.
 */
class IslandCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        val admin = IslandAdminReceiver.component(context)
        if (!dpm.isProfileOwnerApp(context.packageName)) return
        when (intent.getStringExtra(EXTRA_CMD)) {
            CMD_OFFLINE -> applyOffline(dpm, admin, intent.getBooleanExtra(EXTRA_ENABLED, false))
            CMD_WIPE -> {
                if (!intent.getBooleanExtra(EXTRA_CONFIRMED, false)) return
                if (!IslandPolicy.canWipe(confirmed = true)) return
                dpm.wipeData(0)
            }
            else -> {
                val pkg = intent.getStringExtra(EXTRA_PACKAGE) ?: return
                when (intent.getStringExtra(EXTRA_CMD)) {
                    CMD_CLONE -> dpm.installExistingPackage(admin, pkg)
                    CMD_FREEZE -> dpm.setPackagesSuspended(admin, arrayOf(pkg), true)
                    CMD_UNFREEZE -> dpm.setPackagesSuspended(admin, arrayOf(pkg), false)
                    CMD_HIDE -> dpm.setApplicationHidden(admin, pkg, true)
                    CMD_UNHIDE -> dpm.setApplicationHidden(admin, pkg, false)
                    else -> return
                }
            }
        }
    }

    private fun applyOffline(dpm: DevicePolicyManager, admin: android.content.ComponentName, enabled: Boolean) {
        IslandPolicy.OFFLINE_RESTRICTIONS.forEach { key ->
            if (enabled) dpm.addUserRestriction(admin, key) else dpm.clearUserRestriction(admin, key)
        }
    }

    companion object {
        const val ACTION = "com.victorious.launch.action.ISLAND_CMD"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_PACKAGE = "package"
        const val EXTRA_ENABLED = "enabled"
        const val EXTRA_CONFIRMED = "confirmed"
        const val CMD_CLONE = "clone"
        const val CMD_FREEZE = "freeze"
        const val CMD_UNFREEZE = "unfreeze"
        const val CMD_HIDE = "hide"
        const val CMD_UNHIDE = "unhide"
        const val CMD_OFFLINE = "offline"
        const val CMD_WIPE = "wipe"
    }
}
