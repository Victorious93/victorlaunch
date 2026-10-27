package com.victorious.launch.core

import com.victorious.launch.core.fleet.FleetPins
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.theme.FleetIconStyle
import com.victorious.launch.core.theme.FleetThemeAccess
import com.victorious.launch.core.theme.FleetThemeCodec
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
    fun contractConstantsAreStable() {
        assertEquals(1, FleetThemeContract.SCHEMA_VERSION)
        assertEquals("com.victorious.launch.theme", FleetThemeContract.AUTHORITY)
        assertEquals("com.victorious.launch.permission.READ_FLEET_THEME", FleetThemeContract.PERMISSION)
        assertEquals("com.victorious.launch.action.FLEET_THEME_CHANGED", FleetThemeContract.ACTION_CHANGED)
        assertEquals("content://com.victorious.launch.theme/current", FleetThemeContract.contentUri())
    }
}
