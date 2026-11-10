package com.victorious.launch.fleet

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import com.victorious.launch.core.fleet.PublishedCapability
import com.victorious.launch.core.fleet.FleetVpnToggles

/**
 * Runtime discovery for Mesh / Mango toggle shortcuts and package+action
 * activities. Missing or unresolvable contracts stay hidden.
 */
object FleetToggleResolver {
    fun capability(
        context: Context,
        packageName: String,
        user: UserHandle = Process.myUserHandle(),
    ): PublishedCapability {
        val actions =
            when (packageName) {
                FleetVpnToggles.MESH_PACKAGE -> FleetVpnToggles.MESH_ACTIONS
                FleetVpnToggles.MANGO_PACKAGE -> FleetVpnToggles.MANGO_ACTIONS
                else -> return PublishedCapability()
            }
        return PublishedCapability(
            shortcutIds = shortcutIds(context, packageName, user),
            activities = actions.associateWith { action -> activityClasses(context, packageName, action) },
        )
    }

    fun shortcutIds(context: Context, packageName: String, user: UserHandle = Process.myUserHandle()): Set<String> {
        if (Build.VERSION.SDK_INT < 25) return emptySet()
        val launcher = context.getSystemService(LauncherApps::class.java) ?: return emptySet()
        return try {
            if (!launcher.hasShortcutHostPermission()) return emptySet()
            val query =
                LauncherApps.ShortcutQuery()
                    .setPackage(packageName)
                    .setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                    )
            (launcher.getShortcuts(query, user) ?: emptyList())
                .filter { it.isEnabled }
                .map { it.id }
                .toSet()
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun activityClasses(context: Context, packageName: String, action: String): List<String> {
        val intent = Intent(action).setPackage(packageName)
        val resolved =
            try {
                queryActivities(context.packageManager, intent)
            } catch (_: Exception) {
                emptyList()
            }
        return resolved
            .mapNotNull { it.activityInfo }
            .filter { it.packageName == packageName && it.exported }
            .map { it.name }
    }

    @Suppress("DEPRECATION")
    private fun queryActivities(pm: PackageManager, intent: Intent): List<android.content.pm.ResolveInfo> {
        val flags = PackageManager.MATCH_ALL
        return if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags.toLong()))
        } else {
            pm.queryIntentActivities(intent, flags)
        }
    }
}
