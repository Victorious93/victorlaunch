package com.victorious.launch.prefs

import android.content.Context
import android.content.SharedPreferences
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.launcher.DrawerModel
import com.victorious.launch.core.launcher.DrawerTab
import com.victorious.launch.core.launcher.GestureBinding
import com.victorious.launch.core.launcher.GestureMap
import com.victorious.launch.core.launcher.HomeGesture
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.mods.ModsTool
import com.victorious.launch.theme.FleetThemePublisher
import java.security.SecureRandom
import org.json.JSONArray
import org.json.JSONObject

class AppSettings(context: Context) {
    private val app = context.applicationContext
    private val prefs: SharedPreferences =
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
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

    /** Kill switch for Don's pinned-controller full control. Default ON. */
    fun isControllerFullControlEnabled(): Boolean = prefs.getBoolean(KEY_CONTROLLER_FULL, true)

    fun setControllerFullControlEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CONTROLLER_FULL, enabled).apply()
    }

    fun isShizukuEnabled(): Boolean = prefs.getBoolean(KEY_SHIZUKU, false)

    fun setShizukuEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHIZUKU, enabled).apply()
    }

    /** Opt-in: allow VictorLaunch to run `su` for dev tools. Off by default; su is never run otherwise. */
    fun isRootShellAllowed(): Boolean = prefs.getBoolean("root_shell_allowed", false)

    fun setRootShellAllowed(allowed: Boolean) {
        prefs.edit().putBoolean("root_shell_allowed", allowed).apply()
    }

    fun isModsMasterEnabled(): Boolean = prefs.getBoolean(KEY_MODS_MASTER, false)

    fun setModsMasterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_MODS_MASTER, enabled).apply()
    }

    fun isModEnabled(tool: ModsTool): Boolean = prefs.getBoolean(modKey(tool), false)

    fun setModEnabled(tool: ModsTool, enabled: Boolean) {
        prefs.edit().putBoolean(modKey(tool), enabled).apply()
    }

    fun isNetworkMonitorEnabled(): Boolean = prefs.getBoolean(KEY_NET_MONITOR, false)

    fun setNetworkMonitorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NET_MONITOR, enabled).apply()
    }

    fun isNetworkBlock2g(): Boolean = prefs.getBoolean(KEY_NET_BLOCK_2G, false)

    fun setNetworkBlock2g(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NET_BLOCK_2G, enabled).apply()
    }

    fun isNetworkLockLteNr(): Boolean = prefs.getBoolean(KEY_NET_LOCK_LTE_NR, false)

    fun setNetworkLockLteNr(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NET_LOCK_LTE_NR, enabled).apply()
    }

    fun isNetworkAutoRecover(): Boolean = prefs.getBoolean(KEY_NET_AUTO_RECOVER, false)

    fun setNetworkAutoRecover(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NET_AUTO_RECOVER, enabled).apply()
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

    fun gestureBindings(): Map<HomeGesture, GestureBinding> = GestureMap.decode(prefs.getString(KEY_GESTURES, null))

    fun setGestureBinding(gesture: HomeGesture, binding: GestureBinding) {
        val next = gestureBindings().toMutableMap().apply { put(gesture, binding) }
        prefs.edit().putString(KEY_GESTURES, GestureMap.encode(next)).apply()
    }

    fun drawerTabs(): List<DrawerTab> = DrawerModel.decodeTabs(prefs.getString(KEY_DRAWER_TABS, null))

    fun setDrawerTabs(tabs: List<DrawerTab>) {
        prefs.edit().putString(KEY_DRAWER_TABS, DrawerModel.encodeTabs(tabs)).apply()
    }

    fun isShowHiddenInDrawer(): Boolean = prefs.getBoolean(KEY_SHOW_HIDDEN, false)

    fun setShowHiddenInDrawer(show: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_HIDDEN, show).apply()
    }

    fun homeLayout(): com.victorious.launch.core.launcher.HomeLayoutConfig =
        com.victorious.launch.core.launcher.HomeLayoutConfig.decode(prefs.getString(KEY_HOME_LAYOUT, null))

    fun setHomeLayout(config: com.victorious.launch.core.launcher.HomeLayoutConfig) {
        prefs.edit().putString(KEY_HOME_LAYOUT, config.clamped().encode()).apply()
    }

    fun setGridColumns(columns: Int) {
        prefs.edit().putInt(KEY_GRID, columns.coerceIn(3, 6)).apply()
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

    fun extraPairings(): List<Pair<String, String>> {
        val row = pairedController() ?: return emptyList()
        return listOf(row.packageName to row.certSha256)
    }

    fun sharePins(): Map<String, List<String>> {
        val raw = prefs.getString(KEY_SHARE_PINS, null) ?: return emptyMap()
        val obj = JSONObject(raw)
        val out = LinkedHashMap<String, List<String>>()
        obj.keys().forEach { pkg ->
            val arr = obj.optJSONArray(pkg) ?: return@forEach
            out[pkg] =
                buildList {
                    for (i in 0 until arr.length()) add(arr.getString(i))
                }
        }
        return out
    }

    fun putSharePin(packageName: String, certSha256: String) {
        val obj = JSONObject(prefs.getString(KEY_SHARE_PINS, "{}") ?: "{}")
        val arr = obj.optJSONArray(packageName) ?: JSONArray()
        val existing = buildList {
            for (i in 0 until arr.length()) add(arr.getString(i))
        }
        if (certSha256 !in existing) arr.put(certSha256)
        obj.put(packageName, arr)
        prefs.edit().putString(KEY_SHARE_PINS, obj.toString()).apply()
    }

    fun isCallerPaired(packageName: String, certs: List<String>): Boolean =
        TrustedPeers.authorize(packageName, certs, extraPairings())

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
                val kind = o.optString("kind", WorkspaceItem.KIND_APP)
                val pkg = o.optString("packageName", "")
                if (kind == WorkspaceItem.KIND_APP && pkg.isBlank()) continue
                val children = o.optJSONArray("children")
                add(
                    WorkspaceItem(
                        packageName = pkg,
                        x = o.optInt("x"),
                        y = o.optInt("y"),
                        kind = kind,
                        page = o.optInt("page"),
                        folderId = o.optString("folderId").ifBlank { null },
                        title = o.optString("title").ifBlank { null },
                        children =
                            if (children == null) {
                                emptyList()
                            } else {
                                buildList {
                                    for (c in 0 until children.length()) add(children.getString(c))
                                }
                            },
                        widgetId = if (o.has("widgetId")) o.optInt("widgetId") else null,
                        spanX = o.optInt("spanX", 1),
                        spanY = o.optInt("spanY", 1),
                    ),
                )
            }
        }
    }

    private fun encodeWorkspace(items: List<WorkspaceItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            val obj =
                JSONObject()
                    .put("packageName", item.packageName)
                    .put("x", item.x)
                    .put("y", item.y)
                    .put("kind", item.kind)
                    .put("page", item.page)
                    .put("spanX", item.spanX)
                    .put("spanY", item.spanY)
            if (item.folderId != null) obj.put("folderId", item.folderId)
            if (item.title != null) obj.put("title", item.title)
            if (item.children.isNotEmpty()) obj.put("children", JSONArray(item.children))
            if (item.widgetId != null) obj.put("widgetId", item.widgetId)
            arr.put(obj)
        }
        return arr.toString()
    }

    fun iconPackPackage(): String? = prefs.getString(KEY_ICON_PACK, null)?.ifBlank { null }

    fun setIconPackPackage(packageName: String?) {
        val edit = prefs.edit().putString(KEY_ICON_PACK, packageName.orEmpty()).putLong(KEY_THEME_UPDATED, System.currentTimeMillis())
        if (packageName.isNullOrBlank()) {
            if (iconStyle() == FleetIconStyle.ICON_PACK) {
                edit.putString(KEY_ICON_STYLE, FleetIconStyle.SYSTEM.name)
            }
        } else {
            edit.putString(KEY_ICON_STYLE, FleetIconStyle.ICON_PACK.name)
        }
        edit.apply()
        FleetThemePublisher.publish(app, this)
    }

    fun themeMode(): FleetThemeMode = FleetThemeMode.parse(prefs.getString(KEY_THEME_MODE, null))

    fun setThemeMode(mode: FleetThemeMode) {
        prefs.edit()
            .putString(KEY_THEME_MODE, mode.name)
            .putLong(KEY_THEME_UPDATED, System.currentTimeMillis())
            .apply()
        FleetThemePublisher.publish(app, this)
    }

    fun iconStyle(): FleetIconStyle {
        val raw = prefs.getString(KEY_ICON_STYLE, null)
        if (!raw.isNullOrBlank()) return FleetIconStyle.parse(raw)
        return if (iconPackPackage() != null) FleetIconStyle.ICON_PACK else FleetIconStyle.SYSTEM
    }

    fun setIconStyle(style: FleetIconStyle) {
        prefs.edit()
            .putString(KEY_ICON_STYLE, style.name)
            .putLong(KEY_THEME_UPDATED, System.currentTimeMillis())
            .apply()
        FleetThemePublisher.publish(app, this)
    }

    fun themeUpdatedAtMs(): Long = prefs.getLong(KEY_THEME_UPDATED, 0L)

    fun themeExtraPins(): Map<String, List<String>> {
        val out = LinkedHashMap<String, MutableList<String>>()
        sharePins().forEach { (pkg, certs) -> out[pkg] = certs.toMutableList() }
        pairedController()?.let { row ->
            val list = out.getOrPut(row.packageName) { mutableListOf() }
            if (row.certSha256 !in list) list.add(row.certSha256)
        }
        return out
    }

    fun register(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregister(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    data class Pairing(val packageName: String, val certSha256: String)

    companion object {
        private const val PREFS = "launcher"
        private const val PAIRING = "pairing"
        private const val KEY_NODE_ID = "node_id"
        private const val KEY_EXTERNAL = "external_control_enabled"
        private const val KEY_CONTROLLER_FULL = "controller_full_control"
        private const val KEY_SHIZUKU = "shizuku_enabled"
        private const val KEY_MODS_MASTER = "mods_master_enabled"
        private const val KEY_NET_MONITOR = "network_monitor_enabled"
        private const val KEY_NET_BLOCK_2G = "network_block_2g"
        private const val KEY_NET_LOCK_LTE_NR = "network_lock_lte_nr"
        private const val KEY_NET_AUTO_RECOVER = "network_auto_recover"

        private fun modKey(tool: ModsTool): String = "mods_tool_${tool.id}"
        private const val KEY_GESTURES = "home_gestures"
        private const val KEY_DRAWER_TABS = "drawer_tabs"
        private const val KEY_SHOW_HIDDEN = "drawer_show_hidden"
        private const val KEY_DOCK = "dock"
        private const val KEY_WORKSPACE = "workspace"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_LOCKED = "locked"
        private const val KEY_LOCKED_FOLDERS = "locked_folders"
        private const val KEY_ISLAND_LOCKED = "island_tab_locked"
        private const val KEY_GRID = "grid"
        private const val KEY_HOME_LAYOUT = "home_layout"
        private const val KEY_PAIRED_PKG = "controller_package"
        private const val KEY_PAIRED_CERT = "controller_cert_sha256"
        private const val KEY_ICON_PACK = "icon_pack_package"
        private const val KEY_SHARE_PINS = "share_target_pins"
        private const val KEY_THEME_MODE = "fleet_theme_mode"
        private const val KEY_ICON_STYLE = "fleet_icon_style"
        private const val KEY_THEME_UPDATED = "fleet_theme_updated_at"
        val THEME_KEYS: Set<String> = setOf(KEY_THEME_MODE, KEY_ICON_STYLE, KEY_ICON_PACK, KEY_THEME_UPDATED)
    }
}
