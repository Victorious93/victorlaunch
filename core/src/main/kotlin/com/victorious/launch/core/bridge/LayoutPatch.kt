package com.victorious.launch.core.bridge

import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.protocol.ErrorCategory
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/** Panels `panel.open` may target. Unknown names are `not_found`. */
object Panels {
    val ALL: List<String> = listOf("home", "drawer", "island", "settings", "help", "mods", "network", "fleet", "gestures")

    fun isKnown(panel: String): Boolean = panel in ALL
}

/**
 * Validated `layout.set` params. Every field is optional; absent fields keep
 * the live value. Bad types or ranges throw `invalid_parameters`.
 *
 * Wire: `{"dock":["pkg",...], "workspace":[{"packageName","x","y","page"?}], "gridColumns":3..6}`
 */
data class LayoutPatch(
    val dock: List<String>? = null,
    val workspace: List<WorkspaceItem>? = null,
    val gridColumns: Int? = null,
) {
    val isEmpty: Boolean get() = dock == null && workspace == null && gridColumns == null

    fun applyTo(prefs: LauncherPreferences): LauncherPreferences =
        prefs.copy(
            dock = dock ?: prefs.dock,
            workspace = workspace ?: prefs.workspace,
            gridColumns = gridColumns ?: prefs.gridColumns,
        )

    companion object {
        const val MAX_DOCK = 10
        const val MAX_WORKSPACE = 500
        val GRID_RANGE = 3..6

        private fun bad(msg: String): Nothing = throw GateException(ErrorCategory.INVALID_PARAMETERS, msg)

        private fun pkg(v: Any?, field: String): String {
            val s = (v as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull ?: bad("$field must be a package name")
            if (!Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$").matches(s)) bad("$field is not a valid package name")
            return s
        }

        private fun int(v: Any?, field: String, min: Int, max: Int): Int {
            val n = (v as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull ?: bad("$field must be an integer")
            if (n !in min..max) bad("$field out of range $min..$max")
            return n
        }

        fun parse(params: JsonObject): LayoutPatch {
            val allowed = setOf("dock", "workspace", "gridColumns")
            params.keys.firstOrNull { it !in allowed }?.let { bad("unknown layout field $it") }
            val dock =
                params["dock"]?.let { el ->
                    val arr = el as? JsonArray ?: bad("dock must be an array")
                    if (arr.size > MAX_DOCK) bad("dock holds at most $MAX_DOCK apps")
                    arr.map { pkg(it, "dock[]") }.distinct()
                }
            val workspace =
                params["workspace"]?.let { el ->
                    val arr = el as? JsonArray ?: bad("workspace must be an array")
                    if (arr.size > MAX_WORKSPACE) bad("workspace too large")
                    arr.map { item ->
                        val o = item as? JsonObject ?: bad("workspace[] must be objects")
                        WorkspaceItem(
                            packageName = pkg(o["packageName"], "workspace[].packageName"),
                            x = int(o["x"], "workspace[].x", 0, 11),
                            y = int(o["y"], "workspace[].y", 0, 11),
                            page = o["page"]?.let { int(it, "workspace[].page", 0, 20) } ?: 0,
                        )
                    }
                }
            val grid = params["gridColumns"]?.let { int(it, "gridColumns", GRID_RANGE.first, GRID_RANGE.last) }
            val patch = LayoutPatch(dock, workspace, grid)
            if (patch.isEmpty) bad("layout.set needs dock, workspace or gridColumns")
            return patch
        }
    }
}
