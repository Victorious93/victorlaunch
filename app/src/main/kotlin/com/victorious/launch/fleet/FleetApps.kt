package com.victorious.launch.fleet

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import com.victorious.launch.core.fleet.FleetManifest
import com.victorious.launch.core.fleet.FleetManifestCodec
import com.victorious.launch.core.theme.FleetThemeConsumers
import com.victorious.launch.prefs.AppSettings

object FleetApps {
    @Suppress("DEPRECATION")
    fun installedMap(pm: PackageManager): Map<String, Pair<Int, String>> {
        val packages = pm.getInstalledPackages(0)
        return packages.associate { info ->
            val code =
                if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else info.versionCode
            info.packageName to (code to (info.versionName ?: ""))
        }
    }

    fun extraPins(settings: AppSettings): List<String> {
        val out = mutableListOf<String>()
        FleetThemeConsumers.PINS.values.forEach { out += it }
        settings.sharePins().values.forEach { out += it }
        settings.themeExtraPins().values.forEach { out += it }
        return out.distinct()
    }

    fun loadManifest(settings: AppSettings): FleetManifest? {
        val raw = settings.lastFleetManifestJson() ?: return null
        return try {
            FleetManifestCodec.parse(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode =
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Recently used packages, or null when usage access is not granted. */
    fun runningPackages(context: Context): Set<String>? {
        if (!hasUsageAccess(context)) return null
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000L, end) ?: return emptySet()
        return stats.map { it.packageName }.toSet()
    }
}
