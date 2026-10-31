package com.victorious.launch.bridge

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.bridge.InstalledApp
import com.victorious.launch.core.bridge.LauncherHost
import com.victorious.launch.core.bridge.LayoutPatch
import com.victorious.launch.core.bridge.Panels
import com.victorious.launch.launcher.HomeActivity
import android.content.Intent
import com.victorious.launch.core.bridge.PairedNode
import com.victorious.launch.core.bridge.RunningResult
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.bridge.ownNodeHealth
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.network.NetworkStatusCodec
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.prefs.AppSettings
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

    override fun ownHealth(): NodeStatusSnapshot {
        val network =
            try {
                VictorLaunchApp.instance.network.domainStatus()
            } catch (_: Exception) {
                NetworkStatusCodec.domainStatus(
                    enabled = settings.isNetworkMonitorEnabled(),
                    permissionsReady = false,
                    sample = null,
                    latestAlert = null,
                )
            }
        return ownNodeHealth(settings.nodeId(), nowMs(), detail = "launcher process alive", network = network)
    }

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
                            put("page", item.page)
                            put("kind", item.kind)
                        },
                    )
                }
            }
            put("gridColumns", prefs.gridColumns)
        }
    }

    override fun setLayout(patch: LayoutPatch): JsonObject {
        patch.dock?.let { settings.setDock(it) }
        patch.workspace?.let { settings.setWorkspace(it) }
        patch.gridColumns?.let { settings.setGridColumns(it) }
        BridgeEvents.layoutRev.value = BridgeEvents.layoutRev.value + 1
        return layout()
    }

    override fun fleetPairs(): List<PairedNode> {
        val out = linkedMapOf<String, PairedNode>()
        TrustedPeers.ALL.filter { it.pinned }.forEach { peer ->
            val cert = peer.certSha256 ?: return@forEach
            out[TrustedPeers.normalize(peer.packageName + cert)] = PairedNode(peer.packageName, cert)
        }
        settings.pairedController()?.let { row ->
            out[TrustedPeers.normalize(row.packageName + row.certSha256)] =
                PairedNode(row.packageName, row.certSha256)
        }
        return out.values.toList()
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

    override fun openPanel(panel: String): Boolean {
        if (!Panels.isKnown(panel)) return false
        BridgeEvents.panel.value = panel
        // Bring home to the front; HomeScreen consumes BridgeEvents.panel.
        return try {
            context.startActivity(
                Intent(context, HomeActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            true
        } catch (_: Exception) {
            // Still delivered next time home is visible.
            true
        }
    }

    override fun networkStatus(): JsonObject =
        try {
            VictorLaunchApp.instance.network.snapshotJson()
        } catch (_: Exception) {
            NetworkStatusCodec.disabledJson()
        }
}
