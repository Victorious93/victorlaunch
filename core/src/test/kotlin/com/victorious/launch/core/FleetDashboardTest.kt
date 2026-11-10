package com.victorious.launch.core

import com.victorious.launch.core.fleet.DashboardInstall
import com.victorious.launch.core.fleet.FleetApp
import com.victorious.launch.core.fleet.FleetDashboardFactory
import com.victorious.launch.core.fleet.FleetKind
import com.victorious.launch.core.fleet.FleetManifest
import com.victorious.launch.core.fleet.KnownFleetApps
import com.victorious.launch.core.fleet.RunningKnowledge
import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.NodeStatusSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetDashboardTest {
    private val known =
        listOf(
            KnownFleetApps.ALL.first { it.id == "mesh" },
            KnownFleetApps.ALL.first { it.id == "browser" },
            KnownFleetApps.ALL.first { it.id == "sms" },
        )
    private val now = 1_000_000L

    @Test
    fun withoutManifestShowsInstalledOrMissingAndUnknownRunning() {
        val dash =
            FleetDashboardFactory.build(
                manifest = null,
                installed = mapOf("com.victorious.mesh" to (10 to "1.0")),
                running = null,
                tray = null,
                nowMs = now,
                trayNote = "Waiting for the controller",
                known = known,
            )
        assertFalse(dash.hasManifest)
        assertFalse(dash.usageAccess)
        assertEquals(Freshness.EXPIRED, dash.trayFreshness)
        assertEquals("Waiting for the controller", dash.trayNote)
        val mesh = dash.rows.first { it.id == "mesh" }
        assertEquals(DashboardInstall.INSTALLED, mesh.install)
        assertEquals("1.0", mesh.installedVersionName)
        assertTrue(mesh.canLaunch)
        assertFalse(mesh.canInstallOrUpdate)
        assertEquals(RunningKnowledge.UNKNOWN, mesh.running)
        assertEquals("Running: needs usage access", FleetDashboardFactory.runningLabel(mesh.running, dash.usageAccess))
        val browser = dash.rows.first { it.id == "browser" }
        assertEquals(DashboardInstall.NOT_INSTALLED, browser.install)
        assertFalse(browser.canLaunch)
    }

    @Test
    fun manifestMarksUpdateAndUntrustedAndMergesUnknownApps() {
        val manifest =
            FleetManifest(
                schemaVersion = 1,
                generatedAt = "t",
                apps =
                    listOf(
                        FleetApp("mesh", "Mesh", "r", "com.victorious.mesh", "2.0", 20, false, "v2", "F4:C9:25:B8:E8:0B:55:AB:83:E6:04:13:EE:49:86:57:DF:85:03:BB:30:D8:1F:9F:17:43:F2:2B:F5:36:B9:F4", emptyList()),
                        FleetApp("browser", "Browser", "r", "com.victorious.browser", "1.0", 1, false, "v1", null, emptyList()),
                        FleetApp("newone", "New Fleet App", "r", "com.example.fleetx", "3.0", 3, false, "v3", "AA:BB", emptyList()),
                    ),
            )
        val dash =
            FleetDashboardFactory.build(
                manifest = manifest,
                installed = mapOf("com.victorious.mesh" to (10 to "1.0"), "com.victorious.browser" to (1 to "1.0")),
                running = setOf("com.victorious.mesh"),
                tray = null,
                extraPins = listOf("F4:C9:25:B8:E8:0B:55:AB:83:E6:04:13:EE:49:86:57:DF:85:03:BB:30:D8:1F:9F:17:43:F2:2B:F5:36:B9:F4"),
                nowMs = now,
                known = known,
            )
        assertTrue(dash.hasManifest)
        val mesh = dash.rows.first { it.id == "mesh" }
        assertEquals(DashboardInstall.UPDATE_AVAILABLE, mesh.install)
        assertTrue(mesh.canInstallOrUpdate)
        assertEquals(RunningKnowledge.RUNNING, mesh.running)
        assertEquals("2.0", mesh.manifestVersionName)
        val browser = dash.rows.first { it.id == "browser" }
        assertEquals(DashboardInstall.UNTRUSTED, browser.install)
        assertFalse(browser.canInstallOrUpdate)
        val extra = dash.rows.first { it.packageName == "com.example.fleetx" }
        assertEquals("New Fleet App", extra.name)
        assertEquals(DashboardInstall.UNTRUSTED, extra.install)
    }

    @Test
    fun trayFreshnessAndTileLabelsHonorStatusTrayWindows() {
        val snap =
            NodeStatusSnapshot(
                nodeId = "n1",
                packageName = "ai.droidcommand.app",
                connectionClass = "local",
                updatedAtMs = now,
                domains =
                    listOf(
                        StatusTrayDomainStatus(StatusTrayDomain.MESH, StatusTrayState.OK, "uplink"),
                        StatusTrayDomainStatus(StatusTrayDomain.VPN, StatusTrayState.DOWN, null),
                        StatusTrayDomainStatus(StatusTrayDomain.BROWSER, StatusTrayState.OK, null),
                        StatusTrayDomainStatus(StatusTrayDomain.SMS, StatusTrayState.DEGRADED, null),
                    ),
            )
        val fresh = StatusTrayAggregator { now }.publish(listOf(snap), now)
        val dash = FleetDashboardFactory.build(null, emptyMap(), emptySet(), fresh, nowMs = now, known = known)
        assertEquals(Freshness.FRESH, dash.trayFreshness)
        assertEquals("Mesh · up", FleetDashboardFactory.tileLabel(dash.tiles.first { it.id == "mesh" }))
        assertEquals("VPN · down", FleetDashboardFactory.tileLabel(dash.tiles.first { it.id == "vpn" }))
        assertEquals("Messaging · degraded", FleetDashboardFactory.tileLabel(dash.tiles.first { it.id == "sms" }))
        assertEquals(StatusTrayState.OK, dash.rows.first { it.id == "mesh" }.trayState)

        val staleAt = now + StatusFreshness.FRESH_MS + 1
        val staleTray = fresh.copy(pulledAtMs = now)
        val stale = FleetDashboardFactory.build(null, emptyMap(), emptySet(), staleTray, nowMs = staleAt, known = known)
        assertEquals(Freshness.STALE, stale.trayFreshness)

        val expiredAt = now + StatusFreshness.STALE_MS + 1
        val expired = FleetDashboardFactory.build(null, emptyMap(), emptySet(), staleTray, nowMs = expiredAt, known = known)
        assertEquals(Freshness.EXPIRED, expired.trayFreshness)
        val dropped = StatusTrayAggregator { expiredAt }.publish(listOf(snap), expiredAt)
        assertTrue(dropped.snapshots.isEmpty())
    }

    @Test
    fun runningKnowledgeWhenUsageAccessGranted() {
        val dash =
            FleetDashboardFactory.build(
                manifest = null,
                installed = mapOf("com.victorious.sms" to (1 to "1")),
                running = emptySet(),
                tray = null,
                nowMs = now,
                known = known,
            )
        assertTrue(dash.usageAccess)
        assertEquals(RunningKnowledge.NOT_RUNNING, dash.rows.first { it.id == "sms" }.running)
        assertEquals("Not running", FleetDashboardFactory.runningLabel(RunningKnowledge.NOT_RUNNING, true))
    }
}
