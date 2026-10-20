package com.victorious.launch.core

import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.core.status.StatusTrayState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusTrayAggregatorTest {
    private val now = 10_000_000L

    private fun snap(
        id: String,
        ageMs: Long,
        domain: StatusTrayDomain,
        state: StatusTrayState,
    ): NodeStatusSnapshot =
        NodeStatusSnapshot(
            nodeId = id,
            packageName = "com.example.$id",
            connectionClass = "EXTERNAL_NODE",
            updatedAtMs = now - ageMs,
            domains = listOf(StatusTrayDomainStatus(domain, state, "real")),
        )

    @Test
    fun dropsRowsOlderThanFiveMinutes() {
        val tray =
            StatusTrayAggregator { now }.publish(
                listOf(
                    snap("mesh-1", 10_000, StatusTrayDomain.MESH, StatusTrayState.OK),
                    snap("old-1", 400_000, StatusTrayDomain.VPN, StatusTrayState.OK),
                ),
                now,
            )
        assertEquals(1, tray.snapshots.size)
        assertEquals("mesh-1", tray.snapshots.single().nodeId)
        assertEquals(1, tray.pairedNodeCount)
    }

    @Test
    fun marksStaleBetween30sAnd5min() {
        assertEquals(Freshness.FRESH, StatusFreshness.of(now - 20_000, now))
        assertEquals(Freshness.STALE, StatusFreshness.of(now - 60_000, now))
        assertEquals(Freshness.EXPIRED, StatusFreshness.of(now - 301_000, now))
    }

    @Test
    fun missingDomainIsUnknownWithNoFakeDetail() {
        val tray =
            StatusTrayAggregator { now }.publish(
                listOf(snap("mesh-1", 1_000, StatusTrayDomain.MESH, StatusTrayState.OK)),
                now,
            )
        val vpn = tray.tiles.first { it.id == "vpn" }
        assertEquals(StatusTrayState.UNKNOWN, vpn.state)
        assertNull(vpn.detail)
        assertTrue(vpn.nodeId == null)
    }
}
