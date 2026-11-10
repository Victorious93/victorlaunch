package com.victorious.launch.core

import com.victorious.launch.core.fleet.FleetActionKind
import com.victorious.launch.core.fleet.FleetPublicActions
import com.victorious.launch.core.fleet.KnownFleetApps
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
    fun meshAndVpnExposeOnlyOpenWhenNoShortcuts() {
        val mesh = FleetPublicActions.extras("com.victorious.mesh")
        assertEquals(listOf("Open"), mesh.map { it.label })
        assertTrue(FleetPublicActions.catalogIntents("com.victorious.mesh").isEmpty())
        val mango = FleetPublicActions.extras("com.victorious.mango")
        assertEquals(listOf("Open"), mango.map { it.label })
        assertTrue(FleetPublicActions.catalogIntents("com.victorious.mango").isEmpty())
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
    fun everyKnownFleetAppAtLeastGetsOpenAndNeverAPrivateAction() {
        KnownFleetApps.ALL.flatMap { it.packages }.forEach { pkg ->
            val extras = FleetPublicActions.extras(pkg)
            assertTrue(pkg, extras.any { it.id == "open" })
            extras.forEach { action ->
                assertTrue(
                    action.action,
                    action.action.startsWith("android.intent.action.") ||
                        action.action == "android.settings.APPLICATION_DETAILS_SETTINGS",
                )
                assertFalse(action.action.contains("com.victorious.", ignoreCase = false) && action.kind != FleetActionKind.OPEN)
            }
        }
    }
}
