package com.victorious.launch.core

import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.notify.NotificationBadges
import com.victorious.launch.core.notify.NotificationPreview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBadgesTest {
    private fun item(
        pkg: String,
        key: String,
        title: String = "Title $key",
        text: String = "Body $key",
        at: Long = 1L,
        ongoing: Boolean = false,
        summary: Boolean = false,
    ) = NotificationPreview(key, pkg, title, text, at, ongoing, summary)

    @Test
    fun optedOutPublishesNothing() {
        val active = listOf(item("com.a", "1"), item("com.b", "2"))
        assertTrue(NotificationBadges.summarize(active, optedIn = false).isEmpty())
    }

    @Test
    fun groupsCountsAndDropsOngoingAndSummaries() {
        val active =
            listOf(
                item("com.a", "1", at = 3),
                item("com.a", "2", at = 2),
                item("com.a", "3", at = 1),
                item("com.a", "4", at = 4, ongoing = true),
                item("com.a", "5", at = 5, summary = true),
                item("com.b", "6", at = 9),
            )
        val badges = NotificationBadges.summarize(active, optedIn = true, maxPreviews = 2)
        assertEquals(3, badges["com.a"]!!.count)
        assertEquals(listOf("1", "2"), badges["com.a"]!!.previews.map { it.key })
        assertEquals(1, badges["com.b"]!!.count)
        assertFalse(badges.containsKey("com.c"))
    }

    @Test
    fun badgeLabelCaps() {
        assertEquals("", NotificationBadges.badgeLabel(0))
        assertEquals("3", NotificationBadges.badgeLabel(3))
        assertEquals("9+", NotificationBadges.badgeLabel(10))
        assertEquals("99+", NotificationBadges.badgeLabel(120))
    }

    @Test
    fun emptyPackageIsIgnored() {
        val badges = NotificationBadges.summarize(
            listOf(item("", "1"), item("com.a", "", title = "", text = "")),
            optedIn = true,
        )
        assertTrue(badges.isEmpty())
    }

    @Test
    fun backupExportDoesNotGrowNotificationFields() {
        val json = ConfigBackupCodec.exportJson(LauncherPreferences(dock = listOf("com.a")))
        assertFalse(json.contains("notification", ignoreCase = true))
        assertFalse(json.contains("preview", ignoreCase = true))
        assertFalse(json.contains("Title 1"))
    }
}
