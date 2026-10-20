package com.victorious.launch.bridge

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.bridge.InstalledApp
import com.victorious.launch.core.bridge.LauncherHost
import com.victorious.launch.core.bridge.PairedNode
import com.victorious.launch.core.bridge.RunningResult
import com.victorious.launch.core.bridge.ownNodeHealth
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.prefs.AppSettings
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

class AndroidHost(
    private val context: Context,
    private val settings: AppSettings,
    private val catalog: AppCatalog,
) : LauncherHost {
    override fun nodeId(): String = settings.nodeId()

    override fun nowMs(): Long = System.currentTimeMillis()

    override fun ownHealth(): NodeStatusSnapshot =
        ownNodeHealth(settings.nodeId(), nowMs(), detail = "launcher process alive")

    override fun preferences(): LauncherPreferences = settings.launcherPreferences()

    override fun applyPreferences(prefs: LauncherPreferences) {
        settings.applyPreferences(prefs)
        settings.setExternalControlEnabled(false)
    }

    override fun installedPackages(): List<InstalledApp> =
        catalog.allLaunchable().map {
            InstalledApp(it.packageName, it.label, it.userSerial)
        }

    override fun layout(): JsonObject {
        val prefs = settings.launcherPreferences()
        return buildJsonObject {
            putJsonArray("dock") { prefs.dock.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("workspace") {
                prefs.workspace.forEach { item ->
                    add(
                        buildJsonObject {
                            put("packageName", item.packageName)
                            put("x", item.x)
                            put("y", item.y)
                        },
                    )
                }
            }
            put("gridColumns", prefs.gridColumns)
        }
    }

    override fun setLayout(params: JsonObject): JsonObject {
        val dock =
            (params["dock"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: preferences().dock
        settings.setDock(dock)
        return layout()
    }

    override fun fleetPairs(): List<PairedNode> {
        val pairing = settings.pairedController() ?: return emptyList()
        return listOf(PairedNode(pairing.packageName, pairing.certSha256))
    }

    override fun runningPackages(): RunningResult {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        val mode =
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
        if (mode != AppOpsManager.MODE_ALLOWED) {
            return RunningResult.Unavailable(ErrorCategory.PERMISSION)
        }
        val usm = context.getSystemService(UsageStatsManager::class.java)
            ?: return RunningResult.Unavailable(ErrorCategory.NOT_AVAILABLE)
        val end = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, end - 60_000L, end)
        if (stats == null) return RunningResult.Unavailable(ErrorCategory.NOT_AVAILABLE)
        val packages = stats.map { it.packageName }.distinct()
        return RunningResult.Ok(packages)
    }

    override fun launchApp(packageName: String): Boolean = catalog.launchPackage(packageName)

    override fun openPanel(panel: String): Boolean =
        panel == "drawer" || panel == "island" || panel == "settings" || panel == "help"
}
