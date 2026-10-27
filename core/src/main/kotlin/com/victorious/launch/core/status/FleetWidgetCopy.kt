package com.victorious.launch.core.status

enum class FleetWidgetKind {
    MESH_VPN,
    BROWSER,
    MESSAGING,
    HEALTH,
}

enum class WidgetAccent {
    OK,
    STALE,
    DOWN,
    UNKNOWN,
}

data class FleetWidgetCopy(
    val kind: FleetWidgetKind,
    val title: String,
    val line1: String,
    val line2: String,
    val accent: WidgetAccent,
)

object FleetWidgetCopyFactory {
    fun from(tray: PublishedTray?, kind: FleetWidgetKind, nowMs: Long): FleetWidgetCopy {
        if (tray == null) {
            return empty(kind, "Waiting for the controller")
        }
        val age = nowMs - tray.pulledAtMs
        if (age > StatusFreshness.STALE_MS) {
            return empty(kind, "Drop — last pull older than 5 min")
        }
        return when (kind) {
            FleetWidgetKind.MESH_VPN -> {
                val mesh = tile(tray, "mesh")
                val vpn = tile(tray, "vpn")
                FleetWidgetCopy(
                    kind = kind,
                    title = "Mesh / VPN",
                    line1 = "Mesh ${label(mesh)}",
                    line2 = "VPN ${label(vpn)}",
                    accent = accent(listOfNotNull(mesh, vpn), age),
                )
            }
            FleetWidgetKind.BROWSER -> {
                val browser = tile(tray, "browser")
                FleetWidgetCopy(
                    kind = kind,
                    title = "Browser",
                    line1 = label(browser),
                    line2 = browser?.detail ?: "no data",
                    accent = accent(listOfNotNull(browser), age),
                )
            }
            FleetWidgetKind.MESSAGING -> {
                val sms = tile(tray, "sms")
                FleetWidgetCopy(
                    kind = kind,
                    title = "Messaging",
                    line1 = label(sms),
                    line2 = sms?.detail ?: "VictorSMS",
                    accent = accent(listOfNotNull(sms), age),
                )
            }
            FleetWidgetKind.HEALTH -> {
                val worst =
                    tray.tiles.minByOrNull { rank(it.state) }
                FleetWidgetCopy(
                    kind = kind,
                    title = "Fleet health",
                    line1 = "${tray.pairedNodeCount} paired node(s)",
                    line2 = worst?.let { "${it.id} ${label(it)}" } ?: "no data",
                    accent = accent(tray.tiles, age),
                )
            }
        }
    }

    private fun empty(kind: FleetWidgetKind, reason: String): FleetWidgetCopy {
        val title =
            when (kind) {
                FleetWidgetKind.MESH_VPN -> "Mesh / VPN"
                FleetWidgetKind.BROWSER -> "Browser"
                FleetWidgetKind.MESSAGING -> "Messaging"
                FleetWidgetKind.HEALTH -> "Fleet health"
            }
        return FleetWidgetCopy(kind, title, "no data", reason, WidgetAccent.UNKNOWN)
    }

    private fun tile(tray: PublishedTray, id: String): TrayTile? =
        tray.tiles.firstOrNull { it.id == id && it.freshness != Freshness.EXPIRED }

    private fun label(tile: TrayTile?): String {
        if (tile == null) return "no data"
        val state =
            when (tile.state) {
                StatusTrayState.OK -> "ok"
                StatusTrayState.DEGRADED -> "degraded"
                StatusTrayState.DOWN -> "down"
                StatusTrayState.UNKNOWN -> "no data"
                StatusTrayState.OFF -> "off"
            }
        return if (tile.freshness == Freshness.STALE) "$state · stale" else state
    }

    private fun accent(tiles: List<TrayTile>, ageMs: Long): WidgetAccent {
        if (tiles.isEmpty()) return WidgetAccent.UNKNOWN
        if (tiles.any { it.state == StatusTrayState.DOWN }) return WidgetAccent.DOWN
        if (ageMs > StatusFreshness.FRESH_MS || tiles.any { it.freshness == Freshness.STALE }) {
            return WidgetAccent.STALE
        }
        if (tiles.any { it.state == StatusTrayState.OK || it.state == StatusTrayState.DEGRADED }) {
            return WidgetAccent.OK
        }
        return WidgetAccent.UNKNOWN
    }

    private fun rank(state: StatusTrayState): Int =
        when (state) {
            StatusTrayState.DOWN -> 0
            StatusTrayState.DEGRADED -> 1
            StatusTrayState.OFF -> 2
            StatusTrayState.UNKNOWN -> 3
            StatusTrayState.OK -> 4
        }
}
