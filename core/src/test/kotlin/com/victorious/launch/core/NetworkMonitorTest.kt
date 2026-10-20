package com.victorious.launch.core

import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.network.NetworkAlertKind
import com.victorious.launch.network.NetworkAutoRecover
import com.victorious.launch.network.NetworkCommands
import com.victorious.launch.network.NetworkDataState
import com.victorious.launch.network.NetworkGate
import com.victorious.launch.network.NetworkMonitorEngine
import com.victorious.launch.network.NetworkRat
import com.victorious.launch.network.NetworkSample
import com.victorious.launch.network.NetworkStatusCodec
import com.victorious.launch.network.NetworkThresholds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMonitorTest {
    private fun sample(
        at: Long,
        rat: NetworkRat,
        rsrp: Int? = -90,
        cell: String? = "310-260-1",
    ): NetworkSample =
        NetworkSample(
            atMs = at,
            rat = rat,
            rsrp = rsrp,
            rsrq = -10,
            sinr = 8,
            cellId = cell,
            registered = true,
            dataState = NetworkDataState.CONNECTED,
        )

    @Test
    fun monitorStaysOffUntilEnabledAndPermissions() {
        assertFalse(NetworkGate.canMonitor(false, true, true))
        assertFalse(NetworkGate.canMonitor(true, false, true))
        assertFalse(NetworkGate.canMonitor(true, true, false))
        assertTrue(NetworkGate.canMonitor(true, true, true))
        assertFalse(NetworkGate.canLock(true, true, false))
        assertTrue(NetworkGate.canLock(true, true, true))
    }

    @Test
    fun flagsUnexpected2gAndSkipsGenericDowngrade() {
        val engine = NetworkMonitorEngine()
        engine.ingest(true, sample(1_000, NetworkRat.LTE))
        val alerts = engine.ingest(true, sample(2_000, NetworkRat.GSM, rsrp = -70, cell = "310-260-1"))
        assertEquals(listOf(NetworkAlertKind.UNEXPECTED_2G), alerts.map { it.kind })
        assertTrue(alerts.single().detail.contains("fake base station"))
    }

    @Test
    fun flagsLteDowngradeFromNr() {
        val engine = NetworkMonitorEngine()
        engine.ingest(true, sample(1_000, NetworkRat.NR))
        val alerts = engine.ingest(true, sample(2_000, NetworkRat.LTE))
        assertEquals(NetworkAlertKind.DOWNGRADE, alerts.single().kind)
    }

    @Test
    fun flagsSuddenCellChangeWhileSignalStrong() {
        val engine = NetworkMonitorEngine()
        engine.ingest(true, sample(1_000, NetworkRat.LTE, rsrp = -80, cell = "310-260-1"))
        val alerts = engine.ingest(true, sample(5_000, NetworkRat.LTE, rsrp = -82, cell = "310-260-2"))
        assertTrue(alerts.any { it.kind == NetworkAlertKind.SUDDEN_CELL_CHANGE })
    }

    @Test
    fun flagsSignalDrop() {
        val engine = NetworkMonitorEngine()
        engine.ingest(true, sample(1_000, NetworkRat.LTE, rsrp = -80))
        val alerts = engine.ingest(true, sample(2_000, NetworkRat.LTE, rsrp = -80 - NetworkThresholds.SIGNAL_DROP_DB))
        assertTrue(alerts.any { it.kind == NetworkAlertKind.SIGNAL_DROP })
    }

    @Test
    fun disabledIngestIsIgnored() {
        val engine = NetworkMonitorEngine()
        assertTrue(engine.ingest(false, sample(1_000, NetworkRat.GSM)).isEmpty())
        assertTrue(engine.latest() == null)
    }

    @Test
    fun autoRecoverHasCooldownAndIgnoresCellHandover() {
        val drop =
            com.victorious.launch.network.NetworkAlert(
                kind = NetworkAlertKind.SIGNAL_DROP,
                atMs = 10_000,
                detail = "drop",
            )
        val cell =
            drop.copy(kind = NetworkAlertKind.SUDDEN_CELL_CHANGE, detail = "cell")
        assertTrue(
            NetworkAutoRecover.shouldRecover(
                enabled = true,
                autoRecover = true,
                privilegedReady = true,
                nowMs = 10_000,
                lastRecoverMs = null,
                recoverAtMs = emptyList(),
                latestAlert = drop,
            ),
        )
        assertFalse(
            NetworkAutoRecover.shouldRecover(
                enabled = true,
                autoRecover = true,
                privilegedReady = true,
                nowMs = 10_000,
                lastRecoverMs = 9_500,
                recoverAtMs = listOf(9_500),
                latestAlert = drop,
            ),
        )
        assertFalse(
            NetworkAutoRecover.shouldRecover(
                enabled = true,
                autoRecover = true,
                privilegedReady = true,
                nowMs = 10_000,
                lastRecoverMs = null,
                recoverAtMs = emptyList(),
                latestAlert = cell,
            ),
        )
    }

    @Test
    fun commandBuildersRejectUnsafeValues() {
        assertEquals(
            listOf("settings", "put", "global", "preferred_network_mode", "23"),
            NetworkCommands.setPreferredNetworkMode(NetworkCommands.MODE_NR_LTE),
        )
        assertEquals(
            listOf("settings", "put", "global", "allowed_2g", "0"),
            NetworkCommands.setAllowed2g(false),
        )
        assertEquals(
            listOf("cmd", "connectivity", "airplane-mode", "enable"),
            NetworkCommands.airplaneCmd(true),
        )
        assertTrue(NetworkCommands.LTE_NR_BITMASK > 0)
        assertEquals(
            "unsupported: cell/band lock is OEM-specific and is not written",
            NetworkCommands.cellLockUnsupported(100, 1650),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun preferredModeRejectsOutOfRange() {
        NetworkCommands.setPreferredNetworkMode(99)
    }

    @Test
    fun telephonyTypeMapsToRatGenerations() {
        assertEquals(NetworkRat.LTE, NetworkRat.fromTelephonyNetworkType(13))
        assertEquals(NetworkRat.NR, NetworkRat.fromTelephonyNetworkType(20))
        assertEquals(NetworkRat.GSM, NetworkRat.fromTelephonyNetworkType(16))
        assertEquals(5, NetworkRat.fromDisplayOverride(3, NetworkRat.LTE).generation)
        assertEquals(2, NetworkRat.EDGE.generation)
    }

    @Test
    fun statusCodecOffAndAlertMapsToTray() {
        val off = NetworkStatusCodec.disabledJson()
        assertEquals("false", off["enabled"].toString())
        val domain =
            NetworkStatusCodec.domainStatus(
                enabled = true,
                permissionsReady = true,
                sample = sample(1, NetworkRat.GSM),
                latestAlert =
                    com.victorious.launch.network.NetworkAlert(
                        kind = NetworkAlertKind.UNEXPECTED_2G,
                        atMs = 1,
                        detail = "2g",
                    ),
            )
        assertEquals(StatusTrayDomain.NETWORK, domain.name)
        assertEquals(StatusTrayState.DOWN, domain.state)
    }

    @Test
    fun aggregatorExposesNetworkTileWithoutInventingOk() {
        val now = 5_000_000L
        val tray =
            StatusTrayAggregator { now }.publish(
                listOf(
                    NodeStatusSnapshot(
                        nodeId = "n1",
                        packageName = "com.example",
                        connectionClass = "EXTERNAL_NODE",
                        updatedAtMs = now,
                        domains =
                            listOf(
                                StatusTrayDomainStatus(StatusTrayDomain.MESH, StatusTrayState.OK, "ok"),
                            ),
                    ),
                ),
                now,
            )
        val net = tray.tiles.first { it.id == "network" }
        assertEquals(StatusTrayState.UNKNOWN, net.state)
        assertEquals(null, net.detail)
    }
}
