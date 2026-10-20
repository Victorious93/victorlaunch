package com.victorious.launch.island

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

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

    companion object {
        const val ACTION = "com.victorious.launch.action.ISLAND_CMD"
        const val EXTRA_CMD = "cmd"
        const val EXTRA_PACKAGE = "package"
        const val CMD_CLONE = "clone"
        const val CMD_FREEZE = "freeze"
        const val CMD_UNFREEZE = "unfreeze"
        const val CMD_HIDE = "hide"
        const val CMD_UNHIDE = "unhide"
    }
}
