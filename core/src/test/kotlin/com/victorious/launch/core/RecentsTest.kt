package com.victorious.launch.core

import com.victorious.launch.core.recents.Dismissals
import com.victorious.launch.core.recents.RecentEntry
import com.victorious.launch.core.recents.RecentsCapabilities
import com.victorious.launch.core.recents.RecentsModel
import com.victorious.launch.core.recents.RecentsPolicy
import com.victorious.launch.core.recents.RecentsSource
import com.victorious.launch.core.recents.UsageEventLite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentsTest {
    private fun caps(
        real: Boolean = false,
        reorder: Boolean = false,
        usage: Boolean = false,
        priv: Boolean = false,
        kill: Boolean = false,
    ) = RecentsCapabilities(real, reorder, usage, priv, kill)

    @Test
    fun sourcePrefersLiveTasksThenUsageThenNone() {
        assertEquals(RecentsSource.LIVE_TASKS, RecentsPolicy.source(caps(real = true, usage = true)))
        assertEquals(RecentsSource.USAGE_HISTORY, RecentsPolicy.source(caps(usage = true)))
        assertEquals(RecentsSource.NONE, RecentsPolicy.source(caps()))
        // Privileged install without the grant still falls back.
        assertEquals(RecentsSource.USAGE_HISTORY, RecentsPolicy.source(caps(priv = true, usage = true)))
    }

    @Test
    fun switchNeedsLiveTaskAndReorder() {
        val live = RecentEntry("com.a.b", taskId = 7)
        val usage = RecentEntry("com.a.b", lastActiveMs = 5)
        assertTrue(RecentsPolicy.canSwitchTask(caps(real = true, reorder = true), live))
        assertFalse(RecentsPolicy.canSwitchTask(caps(real = true), live))
        assertFalse(RecentsPolicy.canSwitchTask(caps(real = true, reorder = true), usage))
        assertFalse(RecentsPolicy.canSwitchTask(caps(reorder = true), live))
    }

    @Test
    fun stopAndUsageButtonFollowCapabilities() {
        assertTrue(RecentsPolicy.canStopApp(caps(kill = true)))
        assertFalse(RecentsPolicy.canStopApp(caps()))
        assertTrue(RecentsPolicy.offerUsageAccess(caps()))
        assertFalse(RecentsPolicy.offerUsageAccess(caps(usage = true)))
        assertFalse(RecentsPolicy.offerUsageAccess(caps(real = true)))
    }

    @Test
    fun explanationsDiffer() {
        val texts =
            listOf(
                RecentsPolicy.explanation(caps(real = true, reorder = true)),
                RecentsPolicy.explanation(caps(real = true)),
                RecentsPolicy.explanation(caps(usage = true)),
                RecentsPolicy.explanation(caps(usage = true, priv = true)),
                RecentsPolicy.explanation(caps()),
            )
        assertEquals(texts.size, texts.toSet().size)
        assertEquals("Recents unavailable", RecentsPolicy.headline(RecentsSource.NONE))
    }

    @Test
    fun usageEventsKeepNewestForegroundPerPackage() {
        val events =
            listOf(
                UsageEventLite("com.a.one", 100, true),
                UsageEventLite("com.a.one", 300, true),
                UsageEventLite("com.a.one", 400, false),
                UsageEventLite("com.b.two", 200, true),
                UsageEventLite("com.c.old", 10, true),
                UsageEventLite("", 500, true),
            )
        val out = RecentsModel.fromUsageEvents(events, sinceMs = 50)
        assertEquals(listOf("com.a.one", "com.b.two"), out.map { it.packageName })
        assertEquals(300L, out[0].lastActiveMs)
    }

    @Test
    fun visibleFiltersDedupesAndCaps() {
        val raw =
            listOf(
                RecentEntry("com.victorious.launch", taskId = 1),
                RecentEntry("com.a.one", taskId = 2),
                RecentEntry("com.a.one", taskId = 2),
                RecentEntry("com.a.one", taskId = 3),
                RecentEntry("com.nolauncher", taskId = 4),
                RecentEntry("com.b.two", taskId = 5),
            )
        val out =
            RecentsModel.visible(
                raw,
                exclude = setOf("com.victorious.launch"),
                launchable = setOf("com.a.one", "com.b.two"),
                dismissals = Dismissals(),
            )
        assertEquals(listOf(2, 3, 5), out.map { it.taskId })
        assertEquals(2, RecentsModel.visible(raw, emptySet(), null, Dismissals(), limit = 2).size)
        val usageDup = listOf(RecentEntry("com.a.one", lastActiveMs = 9), RecentEntry("com.a.one", lastActiveMs = 5))
        assertEquals(1, RecentsModel.visible(usageDup, emptySet(), null, Dismissals()).size)
    }

    @Test
    fun usageDismissalHidesUntilUsedAgain() {
        val d = Dismissals().dismiss(RecentEntry("com.a.one", lastActiveMs = 100), nowMs = 200)
        assertTrue(d.isHidden(RecentEntry("com.a.one", lastActiveMs = 150)))
        assertFalse(d.isHidden(RecentEntry("com.a.one", lastActiveMs = 250)))
        assertFalse(d.isHidden(RecentEntry("com.b.two", lastActiveMs = 1)))
    }

    @Test
    fun taskDismissalClearsWhenTaskIsTopAgainOrGone() {
        val d = Dismissals().dismiss(RecentEntry("com.a.one", taskId = 7), 1).dismiss(RecentEntry("com.b.two", taskId = 8), 1)
        assertTrue(d.isHidden(RecentEntry("com.a.one", taskId = 7)))
        val live = listOf(RecentEntry("com.b.two", taskId = 8), RecentEntry("com.a.one", taskId = 7))
        assertEquals(setOf(7), d.refreshForTasks(live).tasks)
        val gone = listOf(RecentEntry("com.c.c", taskId = 9))
        assertTrue(d.refreshForTasks(gone).tasks.isEmpty())
        assertTrue(d.refreshForTasks(emptyList()).tasks.isEmpty())
    }

    @Test
    fun dismissAllAndPrune() {
        val rows = listOf(RecentEntry("com.a.one", lastActiveMs = 1), RecentEntry("com.b.two", taskId = 3))
        val d = Dismissals().dismissAll(rows, nowMs = 1_000)
        assertEquals(setOf(3), d.tasks)
        assertEquals(setOf("com.a.one"), d.packages.keys)
        assertTrue(d.prune(nowMs = 1_000 + 8L * 24 * 3600 * 1000).packages.isEmpty())
        assertEquals(d, d.prune(nowMs = 2_000))
    }

    @Test
    fun codecRoundTripAndTolerance() {
        val d = Dismissals(mapOf("com.a.one" to 5L, "com.b.two" to 9L), setOf(3, 1))
        assertEquals(d, Dismissals.decode(Dismissals.encode(d)))
        val messy = Dismissals.decode("p bad 1\np com.ok.pkg x\nt zz\nq 1\np com.ok.pkg 4\nt 2\n\n")
        assertEquals(Dismissals(mapOf("com.ok.pkg" to 4L), setOf(2)), messy)
        assertEquals(Dismissals(), Dismissals.decode(null))
    }
}
