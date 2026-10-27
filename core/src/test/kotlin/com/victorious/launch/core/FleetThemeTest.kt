package com.victorious.launch.core

import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.bridge.BridgeDispatcher
import com.victorious.launch.core.fleet.FleetPins
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.Ops
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.RemoteActionPolicy
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetThemeAccess
import com.victorious.launch.core.theme.FleetThemeCodec
import com.victorious.launch.core.theme.FleetThemeConsumers
import com.victorious.launch.core.theme.FleetThemeContract
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.core.theme.FleetThemeSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetThemeTest {
    @Test
    fun codecRoundTripKeepsSchemaVersionOne() {
        val snap =
            FleetThemeSnapshot(
                mode = FleetThemeMode.LIGHT,
                iconStyle = FleetIconStyle.MONOCHROME,
                iconPackPackage = "com.example.pack",
                palette = FleetThemePalettes.paletteFor(FleetThemeMode.LIGHT),
                updatedAtMs = 42L,
            )
        val decoded = FleetThemeCodec.decode(FleetThemeCodec.encode(snap))
        assertEquals(FleetThemeContract.SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(FleetThemeMode.LIGHT, decoded.mode)
        assertEquals(FleetIconStyle.MONOCHROME, decoded.iconStyle)
        assertEquals("com.example.pack", decoded.iconPackPackage)
        assertEquals(FleetThemePalettes.LIGHT.primary, decoded.palette.primary)
        assertEquals(42L, decoded.updatedAtMs)
    }

    @Test
    fun materialYouFallsBackToNeonWhenAccentMissing() {
        assertEquals(
            FleetThemePalettes.NEON,
            FleetThemePalettes.paletteFor(FleetThemeMode.MATERIAL_YOU, materialYou = null),
        )
    }

    @Test
    fun parseArgbAcceptsRgbAndArgb() {
        assertEquals(0xFF00E5FF.toInt(), FleetThemePalettes.parseArgb("#00E5FF"))
        assertEquals(0xCC1A1D2B.toInt(), FleetThemePalettes.parseArgb("#CC1A1D2B"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun decodeRejectsUnknownSchema() {
        FleetThemeCodec.decode("""{"schemaVersion":2,"mode":"DARK"}""")
    }

    @Test
    fun selfPackageCanAlwaysRead() {
        assertTrue(
            FleetThemeAccess.canRead(
                callerPackage = Protocol.PACKAGE_NAME,
                callerCerts = emptyList(),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun unpinnedCallerIsRejected() {
        assertFalse(
            FleetThemeAccess.canRead(
                callerPackage = "com.evil.app",
                callerCerts = listOf("AA:BB"),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun emptyCertsAreRejectedForOthers() {
        assertFalse(
            FleetThemeAccess.canRead(
                callerPackage = "ai.droidcommand.app",
                callerCerts = emptyList(),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun pinnedControllerCanRead() {
        assertTrue(
            FleetThemeAccess.canRead(
                callerPackage = "ai.droidcommand.app",
                callerCerts = listOf(FleetPins.CONTROLLER_DEBUG),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun sharePinUnlocksUnlistedFleetApp() {
        assertFalse(
            FleetThemeAccess.canRead(
                callerPackage = "com.victorious.sms",
                callerCerts = listOf("11:22:33"),
                extraPins = emptyMap(),
            ),
        )
        assertTrue(
            FleetThemeAccess.canRead(
                callerPackage = "com.victorious.sms",
                callerCerts = listOf("11:22:33"),
                extraPins = mapOf("com.victorious.sms" to listOf("11:22:33")),
            ),
        )
    }

    @Test
    fun broadcastListOmitsVictorLaunchAndIncludesKnownFleet() {
        val pkgs = FleetThemeAccess.broadcastPackages(mapOf("com.victorious.sms" to listOf("AA")))
        assertFalse(pkgs.contains(Protocol.PACKAGE_NAME))
        assertTrue(pkgs.contains("ai.droidcommand.app"))
        assertTrue(pkgs.contains("com.victorious.sms"))
    }

    @Test
    fun themeConsumersCanReadEachPinnedCert() {
        consumerPins().forEach { (pkg, cert) ->
            assertTrue(
                "$pkg + pinned cert should read the theme",
                FleetThemeAccess.canRead(pkg, listOf(cert), extraPins = emptyMap()),
            )
        }
        assertTrue(
            FleetThemeAccess.canRead(
                FleetThemeConsumers.HACKERAI,
                listOf(FleetThemeConsumers.HACKERAI_DEBUG, FleetThemeConsumers.HACKERAI_RELEASE),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun themeConsumersWrongCertIsDenied() {
        consumerPins().forEach { (pkg, _) ->
            assertFalse(
                "$pkg + wrong cert must not read the theme",
                FleetThemeAccess.canRead(pkg, listOf(WRONG_CERT), extraPins = emptyMap()),
            )
        }
        assertFalse(
            FleetThemeAccess.canRead(
                FleetThemeConsumers.HACKERAI,
                listOf(FleetThemeConsumers.MESH_CERT),
                extraPins = emptyMap(),
            ),
        )
        assertFalse(
            "bare ai.rootforge.os must not inherit the .debug pin",
            FleetThemeAccess.canRead(
                "ai.rootforge.os",
                listOf(FleetThemeConsumers.ROOTFORGE_DEBUG_CERT),
                extraPins = emptyMap(),
            ),
        )
    }

    @Test
    fun themeConsumerPinsDoNotGrantFleetInstallTrustExceptPreexistingHackeraiRelease() {
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.MESH, FleetThemeConsumers.MESH_CERT))
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.MANGO, FleetThemeConsumers.MANGO_CERT))
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.VICTORYPED, FleetThemeConsumers.VICTORYPED_CERT))
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.TASKBAR, FleetThemeConsumers.TASKBAR_CERT))
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.ROOTFORGE_DEBUG, FleetThemeConsumers.ROOTFORGE_DEBUG_CERT))
        assertFalse(FleetPins.isTrusted(FleetThemeConsumers.HACKERAI, FleetThemeConsumers.HACKERAI_DEBUG))
        assertTrue(FleetPins.isTrusted(FleetThemeConsumers.HACKERAI, FleetThemeConsumers.HACKERAI_RELEASE))
    }

    @Test
    fun themeConsumersAreNotPinnedControllersAndCannotRunControlOps() {
        val dispatcher = BridgeDispatcher(FakeHost("victorlaunch-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", 9_000_000L, com.victorious.launch.core.bridge.RunningResult.Unavailable(ErrorCategory.PERMISSION), LauncherPreferences()))
        var nonce = 0
        consumerPins().forEach { (pkg, cert) ->
            assertFalse(TrustedPeers.isPinnedController(pkg, listOf(cert)))
            assertFalse(TrustedPeers.isTrusted(pkg, listOf(cert)))
            for (op in RemoteActionPolicy.FULL_CONTROL_OPS) {
                assertFalse(
                    RemoteActionPolicy.allowsAction(
                        op,
                        callerIsPinnedController = TrustedPeers.isPinnedController(pkg, listOf(cert)),
                        controllerFullControlEnabled = true,
                        genericExternalControl = false,
                        isLocalUi = false,
                    ),
                )
            }
            nonce++
            val hex = nonce.toString().padStart(32, 'a')
            val unpaired =
                dispatcher.handle(
                    """{"op":"${Ops.APP_LAUNCH}","requestId":"req-$nonce","targetNodeRef":"victorlaunch-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","actionRef":"${Ops.APP_LAUNCH}","params":{"packageName":"com.android.settings"},"nonce":"$hex","timestampMs":9000000,"ttlMs":120000,"destructive":false}""",
                    sourceNodeRef = pkg,
                    callerPaired = false,
                    externalControlEnabled = false,
                    callerIsPinnedController = TrustedPeers.isPinnedController(pkg, listOf(cert)),
                    controllerFullControlEnabled = true,
                )
            assertFalse(unpaired.ok)
            assertEquals(ErrorCategory.NOT_PAIRED, unpaired.error?.category)

            nonce++
            val hex2 = nonce.toString().padStart(32, 'b')
            val paired =
                dispatcher.handle(
                    """{"op":"${Ops.LAYOUT_SET}","requestId":"req-$nonce","targetNodeRef":"victorlaunch-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","actionRef":"${Ops.LAYOUT_SET}","params":{"dock":[]},"nonce":"$hex2","timestampMs":9000000,"ttlMs":120000,"destructive":false}""",
                    sourceNodeRef = pkg,
                    callerPaired = true,
                    externalControlEnabled = false,
                    confirmed = false,
                    callerIsPinnedController = TrustedPeers.isPinnedController(pkg, listOf(cert)),
                    controllerFullControlEnabled = true,
                )
            assertFalse(paired.ok)
            assertEquals(ErrorCategory.EXTERNAL_CONTROL_DISABLED, paired.error?.category)
        }
    }

    @Test
    fun broadcastListIncludesThemeConsumers() {
        val pkgs = FleetThemeAccess.broadcastPackages(emptyMap())
        FleetThemeConsumers.PINS.keys.forEach { assertTrue(pkgs.contains(it)) }
    }

    @Test
    fun contractConstantsAreStable() {
        assertEquals(1, FleetThemeContract.SCHEMA_VERSION)
        assertEquals("com.victorious.launch.theme", FleetThemeContract.AUTHORITY)
        assertEquals("com.victorious.launch.permission.READ_FLEET_THEME", FleetThemeContract.PERMISSION)
        assertEquals("com.victorious.launch.action.FLEET_THEME_CHANGED", FleetThemeContract.ACTION_CHANGED)
        assertEquals("content://com.victorious.launch.theme/current", FleetThemeContract.contentUri())
    }

    private fun consumerPins(): List<Pair<String, String>> =
        FleetThemeConsumers.PINS.flatMap { (pkg, certs) -> certs.map { pkg to it } }

    companion object {
        private const val WRONG_CERT =
            "00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF"
    }
}
