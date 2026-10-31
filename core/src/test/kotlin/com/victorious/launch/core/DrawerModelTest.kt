package com.victorious.launch.core

import com.victorious.launch.core.launcher.AppCategory
import com.victorious.launch.core.launcher.AppSearch
import com.victorious.launch.core.launcher.DrawerModel
import com.victorious.launch.core.launcher.SearchableApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DrawerModelTest {
    private val apps =
        listOf(
            SearchableApp("com.google.android.youtube", "YouTube"),
            SearchableApp("com.victorious.sms", "VictorSMS"),
            SearchableApp("com.android.settings", "Settings"),
            SearchableApp("com.google.android.apps.maps", "Google Maps"),
            SearchableApp("org.telegram.messenger", "Telegram"),
        )

    @Test
    fun fuzzyRanksPrefixThenInitialsThenSubsequence() {
        assertEquals("YouTube", AppSearch.fuzzy(apps, "you").first().label)
        assertEquals("Google Maps", AppSearch.fuzzy(apps, "gm").first().label)
        assertEquals("Telegram", AppSearch.fuzzy(apps, "tlgr").first().label)
        assertEquals("Google Maps", AppSearch.fuzzy(apps, "maps").first().label)
        assertTrue(AppSearch.fuzzy(apps, "zzzz").isEmpty())
        assertEquals(apps, AppSearch.fuzzy(apps, " "))
    }

    @Test
    fun categorizeUsesFleetThenAndroidCategoryThenKeywords() {
        assertEquals(AppCategory.FLEET, DrawerModel.categorize("com.victorious.sms", 0))
        assertEquals(AppCategory.GAMES, DrawerModel.categorize("com.foo.bar", 0))
        assertEquals(AppCategory.SOCIAL, DrawerModel.categorize("org.telegram.messenger", null))
        assertEquals(AppCategory.SYSTEM, DrawerModel.categorize("com.vendor.thing", null, isSystem = true))
        assertEquals(AppCategory.OTHER, DrawerModel.categorize("com.vendor.thing", -1))
        assertEquals(
            listOf(AppCategory.FLEET, AppCategory.OTHER),
            DrawerModel.presentCategories(listOf(AppCategory.OTHER, AppCategory.FLEET, AppCategory.OTHER)),
        )
    }

    @Test
    fun hiddenAndShowHidden() {
        val pk = listOf("a", "b", "c")
        assertEquals(listOf("a", "c"), DrawerModel.visible(pk, setOf("b"), showHidden = false))
        assertEquals(pk, DrawerModel.visible(pk, setOf("b"), showHidden = true))
        assertEquals(setOf("b"), DrawerModel.toggleHidden(emptySet(), "b"))
        assertEquals(emptySet<String>(), DrawerModel.toggleHidden(setOf("b"), "b"))
    }

    @Test
    fun userTabsRoundTripAndEdit() {
        var tabs = DrawerModel.addTab(emptyList(), "t1", " Daily ")
        tabs = DrawerModel.addTab(tabs, "t1", "dup")
        tabs = DrawerModel.toggleMembership(tabs, "t1", "com.a")
        tabs = DrawerModel.toggleMembership(tabs, "t1", "com.b")
        tabs = DrawerModel.toggleMembership(tabs, "t1", "com.a")
        tabs = DrawerModel.renameTab(tabs, "t1", "Work")
        assertEquals(1, tabs.size)
        assertEquals("Work", tabs[0].title)
        assertEquals(listOf("com.b"), tabs[0].packages)
        assertEquals(tabs, DrawerModel.decodeTabs(DrawerModel.encodeTabs(tabs)))
        assertTrue(DrawerModel.decodeTabs("not json").isEmpty())
        assertTrue(DrawerModel.removeTab(tabs, "t1").isEmpty())
    }
}
