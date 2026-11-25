package com.victorious.launch.core.recents

/**
 * Pure model for the VictorLaunch Recents panel (no Android types, tested in :core).
 *
 * VictorLaunch has no Quickstep, so this is not the system Overview. It is a launcher
 * panel with two data sources, chosen at runtime from [RecentsCapabilities]:
 *  - [RecentsSource.LIVE_TASKS]: ActivityManager.getRecentTasks. Other apps' tasks are
 *    only returned when REAL_GET_TASKS (signature|privileged) is granted, which the
 *    optional root module's priv-app install provides. Tap switches with moveTaskToFront.
 *  - [RecentsSource.USAGE_HISTORY]: UsageStatsManager foreground events, which need the
 *    user-granted Usage access special permission.
 * With neither, the panel explains how to enable one. Nothing else depends on it.
 */
enum class RecentsSource { LIVE_TASKS, USAGE_HISTORY, NONE }

data class RecentsCapabilities(
    /** android.permission.REAL_GET_TASKS (signature|privileged) is granted. */
    val realGetTasks: Boolean,
    /** android.permission.REORDER_TASKS (normal) is granted. */
    val reorderTasks: Boolean,
    /** Usage access app-op is allowed. */
    val usageAccess: Boolean,
    /** The app is installed as a privileged system app. */
    val privApp: Boolean,
    /** android.permission.KILL_BACKGROUND_PROCESSES (normal) is granted. */
    val killBackground: Boolean = false,
)

/** One row in the panel. [taskId] is set only for live tasks. */
data class RecentEntry(
    val packageName: String,
    val taskId: Int? = null,
    val lastActiveMs: Long? = null,
)

/** A foreground/background transition reduced from UsageEvents. */
data class UsageEventLite(val packageName: String, val timeMs: Long, val foreground: Boolean)

object RecentsPolicy {
    fun source(c: RecentsCapabilities): RecentsSource =
        when {
            c.realGetTasks -> RecentsSource.LIVE_TASKS
            c.usageAccess -> RecentsSource.USAGE_HISTORY
            else -> RecentsSource.NONE
        }

    /** Switching to an existing task needs a live task id, REAL_GET_TASKS and REORDER_TASKS. */
    fun canSwitchTask(c: RecentsCapabilities, entry: RecentEntry): Boolean =
        c.realGetTasks && c.reorderTasks && entry.taskId != null

    fun canStopApp(c: RecentsCapabilities): Boolean = c.killBackground

    fun headline(source: RecentsSource): String =
        when (source) {
            RecentsSource.LIVE_TASKS -> "Live tasks"
            RecentsSource.USAGE_HISTORY -> "Recent apps"
            RecentsSource.NONE -> "Recents unavailable"
        }

    fun explanation(c: RecentsCapabilities): String =
        when (source(c)) {
            RecentsSource.LIVE_TASKS ->
                if (c.reorderTasks) {
                    "Privileged mode: tap a task to switch back to it."
                } else {
                    "Privileged mode: tasks are listed; tapping opens the app."
                }
            RecentsSource.USAGE_HISTORY ->
                if (c.privApp) {
                    "Installed as a system app, but task access is not granted. Showing apps from usage history."
                } else {
                    "Showing apps used in the last 24 hours. The optional root module adds live tasks."
                }
            RecentsSource.NONE ->
                "Grant Usage access to see recent apps, or install the optional root module for live tasks."
        }

    /** Offer a button to the system Usage access screen? */
    fun offerUsageAccess(c: RecentsCapabilities): Boolean = !c.realGetTasks && !c.usageAccess
}

object RecentsModel {
    const val DEFAULT_LIMIT = 24
    const val USAGE_WINDOW_MS = 24L * 60 * 60 * 1000

    /** Latest foreground time per package, newest first. Old and background events are ignored. */
    fun fromUsageEvents(events: List<UsageEventLite>, sinceMs: Long): List<RecentEntry> {
        val last = HashMap<String, Long>()
        for (e in events) {
            if (!e.foreground || e.timeMs < sinceMs || e.packageName.isBlank()) continue
            val prev = last[e.packageName]
            if (prev == null || e.timeMs > prev) last[e.packageName] = e.timeMs
        }
        return last.entries
            .sortedWith(compareByDescending<Map.Entry<String, Long>> { it.value }.thenBy { it.key })
            .map { RecentEntry(packageName = it.key, lastActiveMs = it.value) }
    }

    /**
     * Filters raw entries (newest first) for display: drops [exclude] (own package, System UI),
     * drops packages not in [launchable] when it is non-null, de-duplicates (task id for live
     * tasks, package for usage rows), hides dismissed rows, and caps at [limit].
     */
    fun visible(
        raw: List<RecentEntry>,
        exclude: Set<String>,
        launchable: Set<String>?,
        dismissals: Dismissals,
        limit: Int = DEFAULT_LIMIT,
    ): List<RecentEntry> {
        val seenPackages = HashSet<String>()
        val seenTasks = HashSet<Int>()
        val out = ArrayList<RecentEntry>()
        for (e in raw) {
            if (out.size >= limit) break
            if (e.packageName.isBlank() || e.packageName in exclude) continue
            if (launchable != null && e.packageName !in launchable) continue
            val tid = e.taskId
            if (tid != null) {
                if (!seenTasks.add(tid)) continue
            } else if (!seenPackages.add(e.packageName)) {
                continue
            }
            if (dismissals.isHidden(e)) continue
            out += e
        }
        return out
    }
}
