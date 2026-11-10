package com.victorious.launch.core

import com.victorious.launch.core.launcher.IconPackContract
import com.victorious.launch.core.theme.FleetThemeCodec
import com.victorious.launch.core.theme.FleetThemeMode
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.core.theme.FleetThemeSnapshot
import com.victorious.launch.core.theme.FleetIconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeGalleryTest {
    @Test
    fun neonRingUsesRequestedCharcoalCyanMagenta() {
        val p = FleetThemePalettes.RING
        assertEquals("#FF0B0F1A", p.background)
        assertEquals("#FF00E5FF", p.primary)
        assertEquals("#FFFF2BD6", p.secondary)
        assertEquals(p, FleetThemePalettes.paletteFor(FleetThemeMode.NEON_RING))
        assertEquals(FleetThemeMode.NEON_RING, FleetThemeMode.parse("neon-ring"))
        assertEquals(FleetThemeMode.NEON_RING, FleetThemeMode.parse("NEON_RING"))
    }

    @Test
    fun existingNeonFleetPaletteIsUnchanged() {
        assertEquals("#FF0B0C12", FleetThemePalettes.NEON.background)
        assertEquals("#FF00E5FF", FleetThemePalettes.NEON.primary)
        assertEquals("#FFE840FF", FleetThemePalettes.NEON.secondary)
        assertEquals(FleetThemePalettes.NEON, FleetThemePalettes.paletteFor(FleetThemeMode.NEON_FLEET))
    }

    @Test
    fun unknownModeFallsBackWithoutBreakingSchemaOne() {
        assertEquals(FleetThemeMode.NEON_FLEET, FleetThemeMode.parse("DOES_NOT_EXIST"))
        val snap =
            FleetThemeSnapshot(
                mode = FleetThemeMode.NEON_RING,
                iconStyle = FleetIconStyle.ICON_PACK,
                iconPackPackage = "com.example.pack",
                palette = FleetThemePalettes.RING,
                updatedAtMs = 1L,
            )
        val decoded = FleetThemeCodec.decode(FleetThemeCodec.encode(snap))
        assertEquals(1, decoded.schemaVersion)
        assertEquals(FleetThemeMode.NEON_RING, decoded.mode)
        assertEquals("#FF0B0F1A", decoded.palette.background)
        assertEquals("com.example.pack", decoded.iconPackPackage)
    }

    @Test
    fun iconPackPreviewPicksKnownComponentsThenFills() {
        val filter =
            mapOf(
                "ComponentInfo{com.android.settings/com.android.settings.Settings}" to "settings",
                "com.android.chrome/com.google.android.apps.chrome.Main" to "chrome",
                "com.other/.Other" to "other",
                "com.android.dialer/.DialtactsActivity" to "dialer",
            )
        assertEquals(listOf("settings", "chrome", "dialer", "other"), IconPackContract.previewDrawables(filter, 4))
        assertEquals(
            "com.android.settings/com.android.settings.settings",
            IconPackContract.normalizeComponent("ComponentInfo{com.android.settings/com.android.settings.Settings}"),
        )
        assertTrue(IconPackContract.isKnownThemeAction(IconPackContract.NOVA_THEME))
        assertFalse(IconPackContract.isKnownThemeAction("android.intent.action.MAIN"))
    }
}
