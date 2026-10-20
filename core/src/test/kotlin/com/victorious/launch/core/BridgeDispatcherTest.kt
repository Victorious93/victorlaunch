package com.victorious.launch.core

import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.bridge.BridgeDispatcher
import com.victorious.launch.core.bridge.InstalledApp
import com.victorious.launch.core.bridge.LauncherHost
import com.victorious.launch.core.bridge.PairedNode
import com.victorious.launch.core.bridge.RunningResult
import com.victorious.launch.core.bridge.ownNodeHealth
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.status.NodeStatusSnapshot
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeDispatcherTest {
    private val now = 8_000_000L
    private val nodeId = "victorlaunch-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"

    private fun host(
        running: RunningResult = RunningResult.Unavailable(ErrorCategory.PERMISSION),
        prefs: LauncherPreferences = LauncherPreferences(),
    ): FakeHost = FakeHost(nodeId, now, running, prefs)

    private fun envelope(
        op: String,
        nonce: String,
        extra: String = "",
        ttl: Long = 120000,
        ts: Long = now,
    ): String =
        """
        {"op":"$op","requestId":"req-1","targetNodeRef":"$nodeId","actionRef":"$op",
         "params":{$extra},"nonce":"$nonce","timestampMs":$ts,"ttlMs":$ttl,"destructive":false}
        """.trimIndent()

    @Test
    fun capabilitiesListsFrozenOps() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("capabilities.get", "11111111111111111111111111111111"),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
            )
        assertTrue(resp.ok)
        val text = resp.result.toString()
        assertTrue(text.contains("node.status.get"))
        assertTrue(text.contains("config.import"))
        assertTrue(text.contains("\"protocolMinor\":1"))
    }

    @Test
    fun unpairedCallerIsRejected() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("capabilities.get", "22222222222222222222222222222222"),
                sourceNodeRef = "evil.pkg",
                callerPaired = false,
                externalControlEnabled = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.NOT_PAIRED, resp.error?.category)
    }

    @Test
    fun actionOpsFailWhenExternalControlOffForOtherPeers() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("app.launch", "33333333333333333333333333333333", extra = """"packageName":"com.android.settings""""),
                sourceNodeRef = "com.other.node",
                callerPaired = true,
                externalControlEnabled = false,
                callerIsPinnedController = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.EXTERNAL_CONTROL_DISABLED, resp.error?.category)
        assertEquals("app.launch", d.audit.snapshot().last().op)
        assertFalse(d.audit.snapshot().last().ok)
    }

    @Test
    fun pinnedControllerRunsActionsWithoutConfirmOrGenericSwitch() {
        val d = BridgeDispatcher(host())
        val launch =
            d.handle(
                envelope("app.launch", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", extra = """"packageName":"com.android.settings""""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            )
        assertTrue(launch.ok)
        val layout =
            d.handle(
                envelope("layout.set", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", extra = """"dock":[]"""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            )
        assertTrue(layout.ok)
        val panel =
            d.handle(
                envelope("panel.open", "cccccccccccccccccccccccccccccccc", extra = """"panel":"drawer""""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            )
        assertTrue(panel.ok)
        val audited = d.audit.snapshot().map { it.op }
        assertTrue(audited.containsAll(listOf("app.launch", "layout.set", "panel.open")))
        assertTrue(d.audit.snapshot().all { it.ok })
    }

    @Test
    fun killSwitchRevokesPinnedControllerActions() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("layout.set", "dddddddddddddddddddddddddddddddd"),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.EXTERNAL_CONTROL_DISABLED, resp.error?.category)
    }

    @Test
    fun otherPeerLayoutSetStillNeedsConfirmWhenGenericSwitchOn() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("layout.set", "eeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee"),
                sourceNodeRef = "com.other.node",
                callerPaired = true,
                externalControlEnabled = true,
                confirmed = false,
                callerIsPinnedController = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.CONFIRMATION_REQUIRED, resp.error?.category)
    }

    @Test
    fun capabilitiesForPinnedControllerSkipConfirmOnActionOps() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("capabilities.get", "ffffffffffffffffffffffffffffffff"),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            )
        assertTrue(resp.ok)
        val text = resp.result.toString()
        assertTrue(
            text.contains("\"name\":\"layout.set\",\"kind\":\"action\",\"remoteAllowed\":true,\"requiresConfirm\":false"),
        )
        assertTrue(
            text.contains("\"name\":\"app.launch\",\"kind\":\"action\",\"remoteAllowed\":true,\"requiresConfirm\":false"),
        )
        assertTrue(
            text.contains("\"name\":\"panel.open\",\"kind\":\"action\",\"remoteAllowed\":true,\"requiresConfirm\":false"),
        )
        assertTrue(
            text.contains("\"name\":\"config.import\",\"kind\":\"action\",\"remoteAllowed\":false,\"requiresConfirm\":true"),
        )
    }

    @Test
    fun replayStillRejectedForPinnedController() {
        val d = BridgeDispatcher(host())
        val first =
            d.handle(
                envelope("app.launch", "12121212121212121212121212121212", extra = """"packageName":"com.android.settings""""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                callerIsPinnedController = true,
            )
        assertTrue(first.ok)
        val replay =
            d.handle(
                envelope("app.launch", "12121212121212121212121212121212", extra = """"packageName":"com.android.settings""""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                callerIsPinnedController = true,
            )
        assertFalse(replay.ok)
        assertEquals(ErrorCategory.REPLAY_DETECTED, replay.error?.category)
    }

    @Test
    fun remoteConfigImportIsCapabilityOff() {
        val d = BridgeDispatcher(host())
        val payload = """{"schemaVersion":1,"dock":[],"workspace":[],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":4}"""
        val escaped = payload.replace("\"", "\\\"")
        val resp =
            d.handle(
                envelope("config.import", "44444444444444444444444444444444", extra = """"bundle":"$escaped""""),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
                callerIsPinnedController = true,
                controllerFullControlEnabled = true,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.CAPABILITY_OFF, resp.error?.category)
    }

    @Test
    fun localImportWithoutConfirmReturnsConfirmationRequired() {
        val d = BridgeDispatcher(host())
        val payload = """{"schemaVersion":1,"dock":[],"workspace":[],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":4}"""
        val escaped = payload.replace("\"", "\\\"")
        val resp =
            d.handle(
                envelope("config.import", "77777777777777777777777777777777", extra = """"bundle":"$escaped""""),
                sourceNodeRef = nodeId,
                callerPaired = true,
                externalControlEnabled = false,
                confirmed = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.CONFIRMATION_REQUIRED, resp.error?.category)
    }

    @Test
    fun runningListWithoutUsageAccess() {
        val d = BridgeDispatcher(host(running = RunningResult.Unavailable(ErrorCategory.PERMISSION)))
        val resp =
            d.handle(
                envelope("running.list", "55555555555555555555555555555555"),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
            )
        assertFalse(resp.ok)
        assertEquals(ErrorCategory.PERMISSION, resp.error?.category)
    }

    @Test
    fun nodeStatusGetReturnsOwnHealth() {
        val d = BridgeDispatcher(host())
        val resp =
            d.handle(
                envelope("node.status.get", "66666666666666666666666666666666"),
                sourceNodeRef = "ai.droidcommand.app",
                callerPaired = true,
                externalControlEnabled = false,
            )
        assertTrue(resp.ok)
        assertTrue(resp.result.toString().contains("node_health"))
        assertTrue(resp.result.toString().contains(Protocol.PACKAGE_NAME))
    }
}

class FakeHost(
    private val id: String,
    private val now: Long,
    private val running: RunningResult,
    private var prefs: LauncherPreferences,
) : LauncherHost {
    override fun nodeId(): String = id

    override fun nowMs(): Long = now

    override fun ownHealth(): NodeStatusSnapshot = ownNodeHealth(id, now)

    override fun preferences(): LauncherPreferences = prefs

    override fun applyPreferences(prefs: LauncherPreferences) {
        this.prefs = prefs.copy(externalControlEnabled = false)
    }

    override fun installedPackages(): List<InstalledApp> = emptyList()

    override fun layout(): JsonObject = JsonObject(emptyMap())

    override fun setLayout(params: JsonObject): JsonObject = params

    override fun fleetPairs(): List<PairedNode> = emptyList()

    override fun runningPackages(): RunningResult = running

    override fun launchApp(packageName: String): Boolean = true

    override fun openPanel(panel: String): Boolean = true
}
