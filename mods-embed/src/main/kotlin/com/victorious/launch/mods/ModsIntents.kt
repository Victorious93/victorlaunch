package com.victorious.launch.mods

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

object ModsIntents {
    fun installedPackages(pm: PackageManager, names: Collection<String>): Set<String> {
        val out = mutableSetOf<String>()
        for (name in names) {
            try {
                pm.getPackageInfo(name, 0)
                out.add(name)
            } catch (_: Exception) {
            }
        }
        return out
    }

    fun launch(context: Context, packageName: String): Intent? {
        return context.packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun firstInstalledLauncher(context: Context, tool: ModsTool): Intent? {
        for (pkg in tool.stockPackages) {
            launch(context, pkg)?.let { return it }
        }
        return null
    }
}
