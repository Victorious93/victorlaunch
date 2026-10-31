package com.victorious.launch.core

import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.launcher.HomeLayoutConfig
import com.victorious.launch.core.launcher.WorkspacePages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeLayoutConfigTest {
    @Test
    fun roundTripsAndDefaults() {
        val c = HomeLayoutConfig(dockIcons = 6, dockRows = 2, dockSwipeUp = true, iconScalePct = 120,
            showLabels = false, labelSizeSp = 13, homeRows = 6, drawerColumns = 5, drawerRows = 7)
        assertEquals(c, HomeLayoutConfig.decode(c.encode()))
        assertEquals(HomeLayoutConfig(), HomeLayoutConfig.decode(null))
        assertEquals(HomeLayoutConfig(), HomeLayoutConfig.decode("garbage;dockIcons=abc"))
    }

    @Test
    fun clampsOutOfRange() {
        val c = HomeLayoutConfig.decode("dockIcons=99;dockRows=0;iconScalePct=10;labelSizeSp=40;homeRows=1;drawerColumns=12;drawerRows=1")
        assertEquals(7, c.dockIcons)
        assertEquals(1, c.dockRows)
        assertEquals(70, c.iconScalePct)
        assertEquals(16, c.labelSizeSp)
        assertEquals(3, c.homeRows)
        assertEquals(7, c.drawerColumns)
        assertEquals(4, c.drawerRows)
    }

    @Test
    fun dockRowsAndCapacity() {
        val c = HomeLayoutConfig(dockIcons = 3, dockRows = 2)
        val pk = (1..8).map { "p.$it" }
        assertEquals(listOf(listOf("p.1", "p.2", "p.3"), listOf("p.4", "p.5", "p.6")), c.dockRowsOf(pk))
        assertEquals(listOf("p.2", "p.3", "p.4", "p.5", "p.6", "p.7"), c.pinToDock(pk.take(6), "p.7"))
        assertEquals(pk.take(3), c.pinToDock(pk.take(3), "p.2"))
        assertEquals(listOf("a.b", "c.d"), HomeLayoutConfig().pinToDock(listOf("a.b"), "c.d"))
    }

    @Test
    fun iconScaling() {
        assertEquals(52, HomeLayoutConfig().iconDp(52))
        assertEquals(62, HomeLayoutConfig(iconScalePct = 120).iconDp(52))
        assertEquals(36, HomeLayoutConfig(iconScalePct = 70).iconDp(52))
        assertFalse(HomeLayoutConfig(showLabels = false).showLabels)
    }

    @Test
    fun pinAppRespectsRows() {
        val full = (0 until 3).flatMap { y -> (0 until 4).map { x -> WorkspaceItem("p.$x$y", x, y) } }
        val out = WorkspacePages.pinApp(full, "n.ew", columns = 4, rows = 3)
        val added = out.last()
        assertEquals(1, added.page)
        assertTrue(added.x == 0 && added.y == 0)
        assertEquals(0, WorkspacePages.pinApp(full, "n.ew", columns = 4, rows = 4).last().page)
    }
}
