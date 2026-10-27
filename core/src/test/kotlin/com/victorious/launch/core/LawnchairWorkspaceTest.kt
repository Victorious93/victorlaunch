package com.victorious.launch.core

import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.backup.WorkspaceItem
import com.victorious.launch.core.launcher.AppSearch
import com.victorious.launch.core.launcher.GestureAction
import com.victorious.launch.core.launcher.GestureMap
import com.victorious.launch.core.launcher.HomeGesture
import com.victorious.launch.core.launcher.IconPackContract
import com.victorious.launch.core.launcher.SearchableApp
import com.victorious.launch.core.launcher.WorkspacePages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LawnchairWorkspaceTest {
    @Test
    fun swipeUpOpensDrawer() {
        assertEquals(GestureAction.OPEN_DRAWER, GestureMap.action(HomeGesture.SWIPE_UP))
        assertEquals(GestureAction.OPEN_SEARCH, GestureMap.action(HomeGesture.SWIPE_DOWN))
        assertEquals(HomeGesture.SWIPE_UP, GestureMap.classifyDrag(0f, -80f, 40f))
        assertNull(GestureMap.classifyDrag(1f, 1f, 40f))
    }

    @Test
    fun searchFiltersLabelAndPackage() {
        val apps =
            listOf(
                SearchableApp("com.victorious.sms", "VictorSMS"),
                SearchableApp("com.android.settings", "Settings"),
            )
        assertEquals(listOf("com.victorious.sms"), AppSearch.filter(apps, "sms").map { it.packageName })
        assertEquals(apps, AppSearch.filter(apps, "  "))
    }

    @Test
    fun iconPackIntentsAreThePublicAdwGoNovaSet() {
        assertTrue(IconPackContract.isKnownThemeAction(IconPackContract.ADW_THEMES))
        assertTrue(IconPackContract.isKnownThemeAction(IconPackContract.NOVA_THEME))
        assertFalse(IconPackContract.isKnownThemeAction("android.intent.action.MAIN"))
    }

    @Test
    fun pinAppFillsFirstEmptyCellThenNewPage() {
        var items = emptyList<WorkspaceItem>()
        repeat(4) { i ->
            items = WorkspacePages.pinApp(items, "pkg.$i", columns = 4)
        }
        assertEquals(1, WorkspacePages.pageCount(items))
        items = WorkspacePages.pinApp(items, "pkg.4", columns = 4)
        assertEquals(1, items.last().y)
        assertEquals(0, items.last().x)
        assertEquals("pkg.4", items.last().packageName)
        items = WorkspacePages.pinApp(items, "pkg.4", columns = 4)
        assertEquals(5, items.size)
        repeat(16) { i ->
            items = WorkspacePages.pinApp(items, "pkg.more.$i", columns = 4)
        }
        assertEquals(2, WorkspacePages.pageCount(items))
    }

    @Test
    fun folderMergesTwoAppsAndBackupRoundTrips() {
        val pinned =
            WorkspacePages.pinApp(
                WorkspacePages.pinApp(emptyList(), "a.one", 4),
                "a.two",
                4,
            )
        val folded = WorkspacePages.createFolder(pinned, "a.one", "a.two", "fld-1", "Tools", 4)
        assertEquals(1, folded.size)
        assertEquals(WorkspaceItem.KIND_FOLDER, folded.single().kind)
        assertEquals(listOf("a.one", "a.two"), folded.single().children)
        val withThird = WorkspacePages.addToFolder(folded + WorkspaceItem("a.three", 1, 0), "fld-1", "a.three")
        assertEquals(listOf("a.one", "a.two", "a.three"), withThird.single { it.kind == WorkspaceItem.KIND_FOLDER }.children)
        assertTrue(withThird.none { it.packageName == "a.three" && it.kind == WorkspaceItem.KIND_APP })

        val json = ConfigBackupCodec.exportJson(LauncherPreferences(workspace = folded))
        val decoded = ConfigBackupCodec.decode(json)
        assertEquals("fld-1", decoded.workspace.single().folderId)
        assertEquals(listOf("a.one", "a.two"), decoded.workspace.single().children)
    }

    @Test
    fun oldWorkspaceJsonStillDecodesAsAppsOnPageZero() {
        val incoming =
            """{"schemaVersion":1,"dock":[],"workspace":[{"packageName":"com.android.settings","x":1,"y":2}],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":4}"""
        val prefs = ConfigBackupCodec.decode(incoming)
        assertEquals("com.android.settings", prefs.workspace.single().packageName)
        assertEquals(0, prefs.workspace.single().page)
        assertEquals(WorkspaceItem.KIND_APP, prefs.workspace.single().kind)
    }
}
