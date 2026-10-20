package com.victorious.launch.core

import com.victorious.launch.core.protocol.Ops
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.TrustedPeers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrustedPeersTest {
    private val debug =
        "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D"

    @Test
    fun pinsDebugControllerPackageAndCert() {
        assertEquals(Protocol.CONTROLLER_PACKAGE, TrustedPeers.CONTROLLER_PACKAGE)
        assertEquals("ai.droidcommand.app", TrustedPeers.CONTROLLER_DEBUG.packageName)
        assertTrue(TrustedPeers.isTrusted("ai.droidcommand.app", listOf(debug)))
        assertTrue(
            TrustedPeers.isTrusted(
                "ai.droidcommand.app",
                listOf(debug.replace(":", "").lowercase()),
            ),
        )
    }

    @Test
    fun releaseCertSlotIsTbdAndDoesNotAuthorize() {
        assertNull(TrustedPeers.CONTROLLER_RELEASE.certSha256)
        assertEquals("TBD", TrustedPeers.CONTROLLER_RELEASE_CERT_SHA256_TBD)
        assertFalse(TrustedPeers.CONTROLLER_RELEASE.pinned)
        assertFalse(TrustedPeers.isPinnedCert(null))
        assertFalse(TrustedPeers.isPinnedCert("TBD"))
        assertFalse(TrustedPeers.isTrusted("ai.droidcommand.app", listOf("TBD")))
    }

    @Test
    fun rejectsWrongPackageOrCert() {
        assertFalse(TrustedPeers.isTrusted("com.evil.app", listOf(debug)))
        assertFalse(
            TrustedPeers.isTrusted(
                "ai.droidcommand.app",
                listOf("00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"),
            ),
        )
        assertFalse(TrustedPeers.isTrusted("ai.droidcommand.app", emptyList()))
    }

    @Test
    fun extraPairingIsAuthorizedWithoutEnablingActionOps() {
        val extraCert = "AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99"
        assertTrue(
            TrustedPeers.authorize(
                "com.example.node",
                listOf(extraCert),
                extraPairings = listOf("com.example.node" to extraCert),
            ),
        )
        assertFalse(Ops.descriptor(Ops.APP_LAUNCH)!!.remoteAllowed)
        assertFalse(Ops.descriptor(Ops.LAYOUT_SET)!!.remoteAllowed)
        assertFalse(Ops.descriptor(Ops.PANEL_OPEN)!!.remoteAllowed)
        assertFalse(Ops.descriptor(Ops.CONFIG_EXPORT)!!.remoteAllowed)
        assertFalse(Ops.descriptor(Ops.CONFIG_IMPORT)!!.remoteAllowed)
    }
}
