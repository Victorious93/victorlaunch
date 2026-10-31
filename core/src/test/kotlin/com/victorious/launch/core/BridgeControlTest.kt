package com.victorious.launch.core

import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.bridge.BridgeDispatcher
import com.victorious.launch.core.bridge.InstalledApp
import com.victorious.launch.core.bridge.LauncherHost
import com.victorious.launch.core.bridge.LayoutPatch
import com.victorious.launch.core.bridge.PairedNode
import com.victorious.launch.core.bridge.RunningResult
import com.victorious.launch.core.bridge.ownNodeHealth
import com.victorious.launch.core.protocol.BridgeResponse
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.network.NetworkStatusCodec
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Auth, replay, ttl and end-to-end behaviour of the eight control ops. */
class BridgeControlTest {
    private val nodeId = "victorlaunch-bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
    private var counter = 0

    private class Host(val id: String, var now: Long) : LauncherHost {
        var launched: String? = null
        var panel: String? = null
        var prefs = LauncherPreferences(dock = listOf("com.a.one"))
        var running: RunningResult = RunningResult.Unavailable(ErrorCategory.PERMISSION)

        override fun nodeId() = id

        override fun nowMs() = now

        override fun ownHealth(): NodeStatusSnapshot = ownNodeHealth(id, now)

        override fun preferences() = prefs

        override fun applyPreferences(prefs: LauncherPreferences) {
            this.prefs = prefs
        }

        override fun installedPackages() = listOf(InstalledApp("com.a.one", "One", 0))

        override fun layout(): JsonObject =
            buildJsonObject {
                putJsonArray("dock") { prefs.dock.forEach { add(JsonPrimitive(it)) } }
                put("gridColumns", prefs.gridColumns)
            }

        override fun setLayout(patch: LayoutPatch): JsonObject {
            prefs = patch.applyTo(prefs)
            return layout()
        }

        override fun fleetPairs() = listOf(PairedNode(TrustedPeers.CONTROLLER_PACKAGE, TrustedPeers.CONTROLLER_DEBUG_CERT_SHA256))

        override fun runningPackages() = running

        override fun launchApp(packageName: String): Boolean {
            if (packageName != "com.a.one") return false
            launched = packageName
            return true
        }

        override fun openPanel(panel: String): Boolean {
            this.panel = panel
            return true
        }

        override fun networkStatus(): JsonObject = NetworkStatusCodec.disabledJson()
    }

    private val host = Host(nodeId, 50_000_000L)
    private val d = BridgeDispatcher(host)

    private fun nonce(): String = (++counter).toString(16).padStart(32, '0')

    private fun env(
        op: String,
        params: String = "",
        nonce: String = nonce(),
        ts: Long = host.now,
        ttl: Long = 120_000,
        target: String = nodeId,
    ) = """{"op":"$op","requestId":"r$counter","targetNodeRef":"$target","actionRef":"$op","params":{$params},"nonce":"$nonce","timestampMs":$ts,"ttlMs":$ttl}"""

    private fun controller(raw: String, killSwitchOn: Boolean = true): BridgeResponse =
        d.handle(raw, "ai.droidcommand.app", callerPaired = true, externalControlEnabled = false,
            callerIsPinnedController = true, controllerFullControlEnabled = killSwitchOn)

    private fun peer(raw: String, external: Boolean): BridgeResponse =
        d.handle(raw, "com.other.peer", callerPaired = true, externalControlEnabled = external)

    // ---- auth ----

    @Test
    fun unpairedCallerIsNotPairedEvenForControllerPackageName() {
        val r = d.handle(env("capabilities.get"), "ai.droidcommand.app", callerPaired = false, externalControlEnabled = true)
        assertEquals(ErrorCategory.NOT_PAIRED, r.error?.category)
    }

    @Test
    fun pairedPeerReadsAnswerDirectlyWithSwitchOff() {
        host.running = RunningResult.Ok(listOf("com.a.one"))
        for (op in listOf("capabilities.get", "fleet.list", "layout.get", "installed.list", "running.list")) {
            assertTrue(op, peer(env(op), external = false).ok)
        }
        val caps = peer(env("capabilities.get"), external = false)
        assertTrue(caps.result.toString().contains("\"externalControlEnabled\":false"))
    }

    @Test
    fun pairedPeerActionsStillNeedSwitch() {
        for (raw in listOf(env("app.launch", "\"packageName\":\"com.a.one\""), env("layout.set", "\"gridColumns\":5"), env("panel.open", "\"panel\":\"drawer\""))) {
            assertEquals(raw, ErrorCategory.EXTERNAL_CONTROL_DISABLED, peer(raw, external = false).error?.category)
        }
    }

    @Test
    fun unpairedReadsAreNotPaired() {
        for (op in listOf("capabilities.get", "fleet.list", "layout.get", "installed.list", "running.list")) {
            val r = d.handle(env(op), "com.other.peer", callerPaired = false, externalControlEnabled = true)
            assertEquals(op, ErrorCategory.NOT_PAIRED, r.error?.category)
        }
    }

    @Test
    fun payloadCannotClaimControllerIdentity() {
        // Identity comes from the caller's package+cert (callerIsPinnedController), never the envelope.
        val raw = env("app.launch", "\"packageName\":\"com.a.one\",\"sourceNodeRef\":\"ai.droidcommand.app\"")
        assertEquals(ErrorCategory.EXTERNAL_CONTROL_DISABLED, peer(raw, external = false).error?.category)
        assertNull(host.launched)
        assertFalse(TrustedPeers.isPinnedController("ai.droidcommand.app", listOf("00:11")))
    }

    @Test
    fun controllerFullControlRunsAllEightOpsWithoutConfirm() {
        assertTrue(controller(env("app.launch", "\"packageName\":\"com.a.one\"")).ok)
        assertEquals("com.a.one", host.launched)
        val set = controller(env("layout.set", "\"dock\":[\"com.b.two\",\"com.a.one\"],\"gridColumns\":5"))
        assertTrue(set.error?.message ?: "", set.ok)
        assertEquals(listOf("com.b.two", "com.a.one"), host.prefs.dock)
        assertEquals(5, host.prefs.gridColumns)
        assertTrue(controller(env("panel.open", "\"panel\":\"settings\"")).ok)
        assertEquals("settings", host.panel)
        for (op in listOf("capabilities.get", "fleet.list", "layout.get", "installed.list")) {
            assertTrue(op, controller(env(op)).ok)
        }
        assertTrue(controller(env("fleet.list")).result.toString().contains("ai.droidcommand.app"))
    }

    @Test
    fun killSwitchOffFallsBackToGenericRules() {
        assertEquals(
            ErrorCategory.EXTERNAL_CONTROL_DISABLED,
            controller(env("app.launch", "\"packageName\":\"com.a.one\""), killSwitchOn = false).error?.category,
        )
    }

    @Test
    fun otherPeerLayoutSetParksForConfirmAndRunsOnlyWhenApproved() {
        val o = d.evaluate(env("layout.set", "\"dock\":[\"com.b.two\"]"), "com.other.peer", true, externalControlEnabled = true)
        assertTrue(o is BridgeDispatcher.Outcome.NeedsConfirm)
        val p = (o as BridgeDispatcher.Outcome.NeedsConfirm).pending
        assertEquals(listOf("com.a.one"), host.prefs.dock)
        val r = d.complete(p, approved = true)
        assertTrue(r.ok)
        assertEquals(listOf("com.b.two"), host.prefs.dock)
        assertEquals(ErrorCategory.REPLAY_DETECTED, d.complete(p, approved = true).error?.category)
    }

    @Test
    fun declinedConfirmDoesNothing() {
        val o = d.evaluate(env("layout.set", "\"gridColumns\":6"), "com.other.peer", true, externalControlEnabled = true)
        val r = d.complete((o as BridgeDispatcher.Outcome.NeedsConfirm).pending, approved = false)
        assertEquals(ErrorCategory.CONFIRMATION_REQUIRED, r.error?.category)
        assertEquals(4, host.prefs.gridColumns)
    }

    @Test
    fun otherPeerAppLaunchAndPanelNeedOnlyTheSwitch() {
        assertTrue(peer(env("app.launch", "\"packageName\":\"com.a.one\""), external = true).ok)
        assertTrue(peer(env("panel.open", "\"panel\":\"drawer\""), external = true).ok)
    }

    // ---- validation / errors ----

    @Test
    fun badParamsMapToExistingCategories() {
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("app.launch")).error?.category)
        assertEquals(ErrorCategory.NOT_FOUND, controller(env("app.launch", "\"packageName\":\"com.nope.app\"")).error?.category)
        assertEquals(ErrorCategory.NOT_FOUND, controller(env("panel.open", "\"panel\":\"kitchen\"")).error?.category)
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("layout.set", "\"gridColumns\":42")).error?.category)
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("layout.set", "\"dock\":\"x\"")).error?.category)
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("layout.set")).error?.category)
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("layout.set", "\"evil\":1")).error?.category)
        assertEquals(ErrorCategory.NOT_FOUND, controller(env("layout.get", target = "someone-else")).error?.category)
        assertEquals(ErrorCategory.UNSUPPORTED_OP, controller(env("shell.exec")).error?.category)
    }

    @Test
    fun runningListDegrades() {
        assertEquals(ErrorCategory.PERMISSION, controller(env("running.list")).error?.category)
        host.running = RunningResult.Unavailable(ErrorCategory.NOT_AVAILABLE)
        assertEquals(ErrorCategory.NOT_AVAILABLE, controller(env("running.list")).error?.category)
        host.running = RunningResult.Ok(listOf("com.a.one"))
        assertTrue(controller(env("running.list")).result.toString().contains("com.a.one"))
    }

    // ---- replay ----

    @Test
    fun duplicateNonceFromSameSourceIsReplay() {
        val n = nonce()
        assertTrue(controller(env("layout.get", nonce = n)).ok)
        assertEquals(ErrorCategory.REPLAY_DETECTED, controller(env("layout.get", nonce = n)).error?.category)
        // Same nonce from a different source is a different key.
        assertTrue(peer(env("layout.get", nonce = n), external = true).ok)
    }

    @Test
    fun replayIsCheckedBeforeActionRuns() {
        val n = nonce()
        controller(env("app.launch", "\"packageName\":\"com.a.one\"", nonce = n))
        host.launched = null
        assertEquals(ErrorCategory.REPLAY_DETECTED, controller(env("app.launch", "\"packageName\":\"com.a.one\"", nonce = n)).error?.category)
        assertNull(host.launched)
    }

    @Test
    fun shortNonceIsInvalid() {
        assertEquals(ErrorCategory.INVALID_PARAMETERS, controller(env("layout.get", nonce = "abcd")).error?.category)
    }

    // ---- ttl / skew ----

    @Test
    fun expiredEnvelopeRejected() {
        assertEquals(ErrorCategory.EXPIRED, controller(env("layout.get", ts = host.now - 120_001)).error?.category)
        assertTrue(controller(env("layout.get", ts = host.now - 119_000)).ok)
    }

    @Test
    fun futureSkewOver120sRejected() {
        assertEquals(ErrorCategory.EXPIRED, controller(env("layout.get", ts = host.now + 120_001)).error?.category)
        assertTrue(controller(env("layout.get", ts = host.now + 119_000)).ok)
    }

    @Test
    fun ttlIsClampedToMax300s() {
        assertEquals(ErrorCategory.EXPIRED, controller(env("layout.get", ts = host.now - 300_001, ttl = 3_600_000)).error?.category)
        assertTrue(controller(env("layout.get", ts = host.now - 299_000, ttl = 3_600_000)).ok)
    }

    @Test
    fun confirmAfterTtlElapsedIsExpired() {
        val o = d.evaluate(env("layout.set", "\"gridColumns\":6"), "com.other.peer", true, externalControlEnabled = true)
        host.now += 120_001
        assertEquals(ErrorCategory.EXPIRED, d.complete((o as BridgeDispatcher.Outcome.NeedsConfirm).pending, true).error?.category)
        assertEquals(4, host.prefs.gridColumns)
    }
}
