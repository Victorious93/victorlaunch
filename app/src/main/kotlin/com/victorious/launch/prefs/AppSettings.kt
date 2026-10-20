package com.victorious.launch.prefs

import android.content.Context
import android.content.SharedPreferences
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.protocol.Protocol
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

class AppSettings(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val pairing: SharedPreferences =
        context.applicationContext.getSharedPreferences(PAIRING, Context.MODE_PRIVATE)

    @Synchronized
    fun nodeId(): String {
        prefs.getString(KEY_NODE_ID, null)?.let { if (Protocol.isValidNodeId(it)) return it }
        val bytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val id = Protocol.NODE_ID_PREFIX + bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_NODE_ID, id).commit()
        return id
    }

    fun isExternalControlEnabled(): Boolean = prefs.getBoolean(KEY_EXTERNAL, false)

    fun setExternalControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EXTERNAL, enabled).apply()
    }

    fun isShizukuEnabled(): Boolean = prefs.getBoolean(KEY_SHIZUKU, false)

    fun setShizukuEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHIZUKU, enabled).apply()
    }

    fun launcherPreferences(): LauncherPreferences =
        LauncherPreferences(
            dock = stringList(KEY_DOCK),
            workspace = workspace(),
            hiddenPackages = stringList(KEY_HIDDEN),
            lockedPackages = stringList(KEY_LOCKED),
            lockedFolders = stringList(KEY_LOCKED_FOLDERS),
            islandTabLocked = prefs.getBoolean(KEY_ISLAND_LOCKED, false),
            gridColumns = prefs.getInt(KEY_GRID, 4),
            externalControlEnabled = false,
            pairedControllerPackage = pairing.getString(KEY_PAIRED_PKG, null),
        )

    fun applyPreferences(incoming: LauncherPreferences) {
        val forced = incoming.copy(externalControlEnabled = false)
        prefs.edit()
            .putString(KEY_DOCK, JSONArray(forced.dock).toString())
            .putString(KEY_WORKSPACE, encodeWorkspace(forced.workspace))
            .putString(KEY_HIDDEN, JSONArray(forced.hiddenPackages).toString())
            .putString(KEY_LOCKED, JSONArray(forced.lockedPackages).toString())
            .putString(KEY_LOCKED_FOLDERS, JSONArray(forced.lockedFolders).toString())
            .putBoolean(KEY_ISLAND_LOCKED, forced.islandTabLocked)
            .putInt(KEY_GRID, forced.gridColumns.coerceIn(3, 6))
            .putBoolean(KEY_EXTERNAL, false)
            .apply()
    }

    fun setDock(packages: List<String>) {
        prefs.edit().putString(KEY_DOCK, JSONArray(packages).toString()).apply()
    }

    fun setWorkspace(items: List<WorkspaceItem>) {
        prefs.edit().putString(KEY_WORKSPACE, encodeWorkspace(items)).apply()
    }

    fun setHidden(packages: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, JSONArray(packages.toList()).toString()).apply()
    }

    fun setLocked(packages: Set<String>) {
        prefs.edit().putString(KEY_LOCKED, JSONArray(packages.toList()).toString()).apply()
    }

    fun setIslandTabLocked(locked: Boolean) {
        prefs.edit().putBoolean(KEY_ISLAND_LOCKED, locked).apply()
    }

    fun pairedController(): Pairing? {
        val pkg = pairing.getString(KEY_PAIRED_PKG, null) ?: return null
        val cert = pairing.getString(KEY_PAIRED_CERT, null) ?: return null
        return Pairing(pkg, cert)
    }

    fun pairController(pkg: String, certSha256: String) {
        pairing.edit().putString(KEY_PAIRED_PKG, pkg).putString(KEY_PAIRED_CERT, certSha256).apply()
    }

    fun unpairController() {
        pairing.edit().clear().apply()
    }

    fun isCallerPaired(packageName: String, certs: List<String>): Boolean {
        val row = pairedController() ?: return false
        return row.packageName == packageName && certs.any { it.equals(row.certSha256, ignoreCase = true) }
    }

    private fun stringList(key: String): List<String> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
    }

    private fun workspace(): List<WorkspaceItem> {
        val raw = prefs.getString(KEY_WORKSPACE, null) ?: return emptyList()
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(WorkspaceItem(o.getString("packageName"), o.optInt("x"), o.optInt("y")))
            }
        }
    }

    private fun encodeWorkspace(items: List<WorkspaceItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("packageName", item.packageName)
                    .put("x", item.x)
                    .put("y", item.y),
            )
        }
        return arr.toString()
    }

    data class Pairing(val packageName: String, val certSha256: String)

    companion object {
        private const val PREFS = "launcher"
        private const val PAIRING = "pairing"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_EXTERNAL = "external_control_enabled"
        private const val KEY_SHIZUKU = "shizuku_enabled"
        private const val KEY_DOCK = "dock"
        private const val KEY_WORKSPACE = "workspace"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_LOCKED = "locked"
        private const val KEY_LOCKED_FOLDERS = "locked_folders"
        private const val KEY_ISLAND_LOCKED = "island_tab_locked"
        private const val KEY_GRID = "grid"
        private const val KEY_PAIRED_PKG = "controller_package"
        private const val KEY_PAIRED_CERT = "controller_cert_sha256"
    }
}
