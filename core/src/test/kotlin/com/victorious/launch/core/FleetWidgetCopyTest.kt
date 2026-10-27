package com.victorious.launch.core

import com.victorious.launch.core.status.FleetWidgetCopyFactory
import com.victorious.launch.core.status.FleetWidgetKind
import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.TrayTile
import com.victorious.launch.core.status.WidgetAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetWidgetCopyTest {
    @Test
    fun nullTrayIsUnknown() {
        val copy = FleetWidgetCopyFactory.from(null, FleetWidgetKind.HEALTH, 0L)
        assertEquals(WidgetAccent.UNKNOWN, copy.accent)
        assertTrue(copy.line1.contains("no data"))
    }

    @Test
    fun dropsPullsOlderThanFiveMinutes() {
        val tray = tray(pulledAt = 0L, freshness = Freshness.FRESH, state = StatusTrayState.OK)
        val copy = FleetWidgetCopyFactory.from(tray, FleetWidgetKind.BROWSER, 5 * 60 * 1000L + 1)
        assertEquals(WidgetAccent.UNKNOWN, copy.accent)
        assertTrue(copy.line2.contains("5 min"))
    }

    @Test
    fun meshVpnAndHealthUseFreshTiles() {
        val now = 10_000L
        val tray = tray(pulledAt = now, freshness = Freshness.FRESH, state = StatusTrayState.OK)
        val mesh = FleetWidgetCopyFactory.from(tray, FleetWidgetKind.MESH_VPN, now)
        assertEquals(WidgetAccent.OK, mesh.accent)
        assertTrue(mesh.line1.contains("ok"))
        val health = FleetWidgetCopyFactory.from(tray, FleetWidgetKind.HEALTH, now)
        assertTrue(health.line1.contains("1 paired"))
        val stale = FleetWidgetCopyFactory.from(tray, FleetWidgetKind.MESSAGING, now + 31_000L)
        assertEquals(WidgetAccent.STALE, stale.accent)
    }

    private fun tray(pulledAt: Long, freshness: Freshness, state: StatusTrayState): PublishedTray {
        val tiles =
            listOf("mesh", "vpn", "browser", "sms").map { id ->
                TrayTile(
                    id = id,
                    domain = StatusTrayDomain.fromWire(id),
                    state = state,
                    detail = null,
                    freshness = freshness,
                    nodeId = "n1",
                )
            }
        return PublishedTray(snapshots = emptyList(), tiles = tiles, pairedNodeCount = 1, pulledAtMs = pulledAt)
    }
}
