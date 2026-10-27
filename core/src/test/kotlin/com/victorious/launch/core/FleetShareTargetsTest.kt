package com.victorious.launch.core

import com.victorious.launch.core.fleet.FleetPins
import com.victorious.launch.core.share.FleetShareCatalog
import com.victorious.launch.core.share.ShareTargetState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetShareTargetsTest {
    @Test
    fun controllerReadyOnlyWhenDebugPinMatches() {
        val installed = mapOf("ai.droidcommand.app" to listOf(FleetPins.CONTROLLER_DEBUG))
        val ready = FleetShareCatalog.ready(installed, emptyMap())
        assertEquals(listOf("controller"), ready.map { it.spec.id })
    }

    @Test
    fun smsUntrustedUntilUserPinIsRecorded() {
        val installed = mapOf("com.victorious.sms" to listOf("AA:BB"))
        val missingPin = FleetShareCatalog.status(FleetShareCatalog.ALL.first(), installed, emptyMap())
        assertEquals(ShareTargetState.UNTRUSTED, missingPin.state)
        val trusted =
            FleetShareCatalog.status(
                FleetShareCatalog.ALL.first(),
                installed,
                extraPins = mapOf("com.victorious.sms" to listOf("aa:bb")),
            )
        assertEquals(ShareTargetState.READY, trusted.state)
    }

    @Test
    fun missingAppIsNotOffered() {
        assertTrue(FleetShareCatalog.ready(emptyMap(), emptyMap()).isEmpty())
    }
}
