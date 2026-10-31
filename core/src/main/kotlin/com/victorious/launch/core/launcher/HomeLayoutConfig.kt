package com.victorious.launch.core.launcher

/**
 * Dock, icon and grid customization. Pure data + clamping so the UI and the
 * prefs layer agree on limits. Home columns stay in the existing `grid` pref
 * (3..6, shared with layout.set and backups); everything else lives here.
 */
data class HomeLayoutConfig(
    val dockIcons: Int = 5,
    val dockRows: Int = 1,
    /** Dock hidden behind a handle; swipe up on it to reveal. */
    val dockSwipeUp: Boolean = false,
    /** Icon size in percent of the default (52dp home / 44dp dock). */
    val iconScalePct: Int = 100,
    val showLabels: Boolean = true,
    val labelSizeSp: Int = 11,
    val homeRows: Int = WorkspacePages.DEFAULT_ROWS,
    val drawerColumns: Int = 4,
    /** Rows of the drawer visible per screen height (sets cell height). */
    val drawerRows: Int = 6,
) {
    val dockCapacity: Int get() = dockIcons * dockRows

    fun clamped(): HomeLayoutConfig =
        copy(
            dockIcons = dockIcons.coerceIn(DOCK_ICONS),
            dockRows = dockRows.coerceIn(DOCK_ROWS),
            iconScalePct = iconScalePct.coerceIn(ICON_SCALE),
            labelSizeSp = labelSizeSp.coerceIn(LABEL_SP),
            homeRows = homeRows.coerceIn(HOME_ROWS),
            drawerColumns = drawerColumns.coerceIn(DRAWER_COLUMNS),
            drawerRows = drawerRows.coerceIn(DRAWER_ROWS),
        )

    /** Scaled icon size in dp, rounded, from a base (e.g. 52 home, 44 dock). */
    fun iconDp(base: Int): Int = (base * clamped().iconScalePct + 50) / 100

    /** Split the dock package list into rows of [dockIcons], dropping overflow past capacity. */
    fun dockRowsOf(packages: List<String>): List<List<String>> {
        val c = clamped()
        return packages.take(c.dockCapacity).chunked(c.dockIcons)
    }

    /** Pin to dock: no-op if present; when full, the oldest entry falls off. */
    fun pinToDock(dock: List<String>, pkg: String): List<String> {
        if (pkg in dock) return dock
        val cap = clamped().dockCapacity
        return (dock + pkg).takeLast(cap)
    }

    fun encode(): String =
        listOf(
            "dockIcons=$dockIcons", "dockRows=$dockRows", "dockSwipeUp=$dockSwipeUp",
            "iconScalePct=$iconScalePct", "showLabels=$showLabels", "labelSizeSp=$labelSizeSp",
            "homeRows=$homeRows", "drawerColumns=$drawerColumns", "drawerRows=$drawerRows",
        ).joinToString(";")

    companion object {
        val DOCK_ICONS = 3..7
        val DOCK_ROWS = 1..2
        val ICON_SCALE = 70..140
        val LABEL_SP = 8..16
        val HOME_ROWS = 3..8
        val DRAWER_COLUMNS = 3..7
        val DRAWER_ROWS = 4..9

        /** Tolerant: unknown keys and bad values fall back to defaults, then everything is clamped. */
        fun decode(text: String?): HomeLayoutConfig {
            val d = HomeLayoutConfig()
            if (text.isNullOrBlank()) return d
            val m = text.split(';').mapNotNull { kv ->
                val i = kv.indexOf('=')
                if (i <= 0) null else kv.substring(0, i).trim() to kv.substring(i + 1).trim()
            }.toMap()
            fun i(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
            fun b(k: String, def: Boolean) = m[k]?.toBooleanStrictOrNull() ?: def
            return HomeLayoutConfig(
                dockIcons = i("dockIcons", d.dockIcons),
                dockRows = i("dockRows", d.dockRows),
                dockSwipeUp = b("dockSwipeUp", d.dockSwipeUp),
                iconScalePct = i("iconScalePct", d.iconScalePct),
                showLabels = b("showLabels", d.showLabels),
                labelSizeSp = i("labelSizeSp", d.labelSizeSp),
                homeRows = i("homeRows", d.homeRows),
                drawerColumns = i("drawerColumns", d.drawerColumns),
                drawerRows = i("drawerRows", d.drawerRows),
            ).clamped()
        }
    }
}
