package com.victorious.launch.core

import com.victorious.launch.core.fleet.FleetActionKind
import com.victorious.launch.core.fleet.FleetPublicActions
import com.victorious.launch.core.fleet.FleetVpnToggles
import com.victorious.launch.core.fleet.KnownFleetApps
import com.victorious.launch.core.fleet.PublishedCapability
import com.victorious.launch.core.protocol.Ops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetPublicActionsTest {
    @Test
    fun nonFleetAppsGetNoExtras() {
        assertFalse(FleetPublicActions.isFleetPackage("com.android.settings"))
        assertTrue(FleetPublicActions.extras("com.android.settings").isEmpty())
    }

    @Test
    fun meshAndVpnHideTogglesWhenNothingResolves() {
        val mesh = FleetPublicActions.extras("com.victorious.mesh")
        assertEquals(listOf("Open"), mesh.map { it.label })
        assertTrue(FleetVpnToggles.actionsFor("com.victorious.mesh", PublishedCapability()).isEmpty())
        val mango = FleetPublicActions.extras("com.victorious.mango")
        assertEquals(listOf("Open"), mango.map { it.label })
        assertTrue(FleetVpnToggles.actionsFor("com.victorious.mango", PublishedCapability()).isEmpty())
    }

    @Test
    fun prefersShortcutOverExplicitActionAndHidesTheRest() {
        val cap =
            PublishedCapability(
                shortcutIds = setOf(FleetVpnToggles.MESH_CONNECT_SHORTCUT),
                activities =
                    mapOf(
                        FleetVpnToggles.MESH_CONNECT_ACTION to listOf("com.victorious.mesh.ConnectActivity"),
                        FleetVpnToggles.MESH_DISCONNECT_ACTION to listOf("com.victorious.mesh.DisconnectActivity"),
                    ),
            )
        val actions = FleetVpnToggles.actionsFor(FleetVpnToggles.MESH_PACKAGE, cap)
        assertEquals(listOf("mesh_on", "mesh_off"), actions.map { it.id })
        val on = actions.first { it.id == "mesh_on" }
        assertEquals(FleetActionKind.SHORTCUT, on.kind)
        assertEquals(FleetVpnToggles.MESH_CONNECT_SHORTCUT, on.shortcutId)
        assertEquals(FleetVpnToggles.MESH_ON_LABEL, on.label)
        val off = actions.first { it.id == "mesh_off" }
        assertEquals(FleetActionKind.EXPLICIT, off.kind)
        assertEquals(FleetVpnToggles.MESH_DISCONNECT_ACTION, off.action)
        assertEquals("com.victorious.mesh.DisconnectActivity", off.activityClass)
        assertEquals(setOf(FleetVpnToggles.MESH_CONNECT_SHORTCUT), FleetPublicActions.claimedShortcutIds(actions))
    }

    @Test
    fun hidesUnresolvedDirection() {
        val cap =
            PublishedCapability(
                activities = mapOf(FleetVpnToggles.MESH_CONNECT_ACTION to listOf("com.victorious.mesh.ConnectActivity")),
            )
        val actions = FleetVpnToggles.actionsFor(FleetVpnToggles.MESH_PACKAGE, cap)
        assertEquals(listOf("mesh_on"), actions.map { it.id })
        assertEquals(FleetActionKind.EXPLICIT, actions.single().kind)
    }

    @Test
    fun mangoPrefersOnOffAndFallsBackToToggle() {
        val onOff =
            FleetVpnToggles.actionsFor(
                FleetVpnToggles.MANGO_PACKAGE,
                PublishedCapability(
                    shortcutIds = setOf(FleetVpnToggles.MANGO_ON_SHORTCUT, FleetVpnToggles.MANGO_OFF_SHORTCUT),
                    activities =
                        mapOf(
                            FleetVpnToggles.MANGO_TOGGLE_ACTION to listOf(FleetVpnToggles.MANGO_ACTIVITY_CLASS),
                        ),
                ),
            )
        assertEquals(listOf("mango_on", "mango_off"), onOff.map { it.id })
        assertTrue(onOff.all { it.kind == FleetActionKind.SHORTCUT })
        assertEquals(listOf(FleetVpnToggles.MANGO_ON_LABEL, FleetVpnToggles.MANGO_OFF_LABEL), onOff.map { it.label })

        val toggleOnly =
            FleetVpnToggles.actionsFor(
                FleetVpnToggles.MANGO_PACKAGE,
                PublishedCapability(
                    activities = mapOf(FleetVpnToggles.MANGO_TOGGLE_ACTION to listOf(FleetVpnToggles.MANGO_ACTIVITY_CLASS)),
                ),
            )
        assertEquals(listOf("mango_toggle"), toggleOnly.map { it.id })
        assertEquals(FleetVpnToggles.MANGO_TOGGLE_LABEL, toggleOnly.single().label)
        assertEquals(FleetVpnToggles.MANGO_ACTIVITY_CLASS, toggleOnly.single().activityClass)
        assertEquals(FleetActionKind.EXPLICIT, toggleOnly.single().kind)
    }

    @Test
    fun mangoPrefersAnnouncedActivityClassWhenSeveralResolve() {
        val cap =
            PublishedCapability(
                activities =
                    mapOf(
                        FleetVpnToggles.MANGO_ON_ACTION to
                            listOf("com.victorious.mango.Other", FleetVpnToggles.MANGO_ACTIVITY_CLASS),
                    ),
            )
        val actions = FleetVpnToggles.actionsFor(FleetVpnToggles.MANGO_PACKAGE, cap)
        assertEquals(FleetVpnToggles.MANGO_ACTIVITY_CLASS, actions.single().activityClass)
    }

    @Test
    fun extrasIncludeResolvedTogglesAndTrayPackagesMap() {
        val cap =
            PublishedCapability(
                shortcutIds = setOf(FleetVpnToggles.MESH_DISCONNECT_SHORTCUT),
            )
        val extras = FleetPublicActions.extras(FleetVpnToggles.MESH_PACKAGE, capability = cap)
        assertEquals(listOf("Open", FleetVpnToggles.MESH_OFF_LABEL), extras.map { it.label })
        assertEquals(FleetVpnToggles.MESH_PACKAGE, FleetVpnToggles.packageForTrayDomain("mesh"))
        assertEquals(FleetVpnToggles.MANGO_PACKAGE, FleetVpnToggles.packageForTrayDomain("vpn"))
        assertEquals(null, FleetVpnToggles.packageForTrayDomain("browser"))
    }

    @Test
    fun smsComposeAndBrowserViewArePublicNoPermissionIntents() {
        val sms = FleetPublicActions.extras("com.victorious.sms")
        assertEquals(listOf("Open", "Compose SMS"), sms.map { it.label })
        val compose = sms.first { it.id == "sms_compose" }
        assertEquals(FleetActionKind.SENDTO, compose.kind)
        assertEquals("android.intent.action.SENDTO", compose.action)
        assertEquals("smsto:", compose.dataUri)
        val browser = FleetPublicActions.extras("com.victorious.browser")
        assertEquals("android.intent.action.VIEW", browser.first { it.id == "browser_view" }.action)
        assertEquals("https://", browser.first { it.id == "browser_view" }.dataUri)
    }

    @Test
    fun publishedShortcutsAreNotDuplicated() {
        val sms = FleetPublicActions.extras("com.victorious.sms", shortcutLabels = listOf("Compose"))
        assertEquals(listOf("Open"), sms.map { it.label })
        val browser = FleetPublicActions.extras("com.victorious.browser", shortcutLabels = listOf("Open new tab"))
        assertEquals(listOf("Open"), browser.map { it.label })
    }

    @Test
    fun everyKnownFleetAppAtLeastGetsOpenAndCatalogStaysPublic() {
        val announced =
            setOf(
                FleetVpnToggles.MESH_CONNECT_ACTION,
                FleetVpnToggles.MESH_DISCONNECT_ACTION,
                FleetVpnToggles.MANGO_ON_ACTION,
                FleetVpnToggles.MANGO_OFF_ACTION,
                FleetVpnToggles.MANGO_TOGGLE_ACTION,
            )
        KnownFleetApps.ALL.flatMap { it.packages }.forEach { pkg ->
            val extras = FleetPublicActions.extras(pkg)
            assertTrue(pkg, extras.any { it.id == "open" })
            extras.forEach { action ->
                val ok =
                    action.action.startsWith("android.intent.action.") ||
                        action.action == "android.settings.APPLICATION_DETAILS_SETTINGS" ||
                        action.action in announced
                assertTrue(action.action, ok)
            }
        }
    }

    @Test
    fun togglesAreNotBridgeOps() {
        val names = Ops.ALL.map { it.name }
        listOf("mesh.toggle", "vpn.toggle", "mesh.connect", "mango.vpn").forEach { banned ->
            assertFalse("bridge leaked $banned", banned in names)
        }
    }
}
