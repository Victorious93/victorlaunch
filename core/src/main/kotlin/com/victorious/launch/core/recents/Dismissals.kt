package com.victorious.launch.core.recents

/**
 * Rows the user swiped away inside VictorLaunch's Recents panel. Dismissing hides a row in
 * this panel only; it does not remove the task from Android (removeTask needs a signature
 * permission VictorLaunch does not request).
 *
 * - A usage row stays hidden until the app is used again after the dismissal.
 * - A live task row stays hidden until it becomes the most recent task again
 *   (public task info has no last-active time); see [refreshForTasks].
 */
data class Dismissals(
    val packages: Map<String, Long> = emptyMap(),
    val tasks: Set<Int> = emptySet(),
) {
    fun isHidden(e: RecentEntry): Boolean {
        val tid = e.taskId
        if (tid != null) return tid in tasks
        val at = packages[e.packageName] ?: return false
        val last = e.lastActiveMs ?: return true
        return last <= at
    }

    fun dismiss(e: RecentEntry, nowMs: Long): Dismissals {
        val tid = e.taskId
        return if (tid != null) copy(tasks = tasks + tid) else copy(packages = packages + (e.packageName to nowMs))
    }

    fun dismissAll(entries: List<RecentEntry>, nowMs: Long): Dismissals =
        entries.fold(this) { acc, e -> acc.dismiss(e, nowMs) }

    /** With a fresh newest-first live list: forget gone task ids, and un-hide the top task. */
    fun refreshForTasks(liveNewestFirst: List<RecentEntry>): Dismissals {
        val ids = liveNewestFirst.mapNotNull { it.taskId }
        if (ids.isEmpty()) return if (tasks.isEmpty()) this else copy(tasks = emptySet())
        val alive = tasks.filterTo(HashSet()) { it in ids }
        alive.remove(ids.first())
        return if (alive == tasks) this else copy(tasks = alive)
    }

    /** Drops package dismissals older than [maxAgeMs] so the store cannot grow without bound. */
    fun prune(nowMs: Long, maxAgeMs: Long = 7L * 24 * 60 * 60 * 1000): Dismissals {
        val kept = packages.filterValues { nowMs - it <= maxAgeMs }
        return if (kept.size == packages.size) this else copy(packages = kept)
    }

    companion object {
        private val PKG = Regex("^[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)+$")

        fun isPackageName(s: String): Boolean = PKG.matches(s)

        /** Line format: `p <package> <ms>` or `t <taskId>`. */
        fun encode(d: Dismissals): String =
            (
                d.packages.entries.sortedBy { it.key }.map { "p ${it.key} ${it.value}" } +
                    d.tasks.sorted().map { "t $it" }
            ).joinToString("\n")

        /** Tolerant decoder: malformed lines are skipped. */
        fun decode(raw: String?): Dismissals {
            if (raw.isNullOrBlank()) return Dismissals()
            val pk = LinkedHashMap<String, Long>()
            val tk = LinkedHashSet<Int>()
            raw.lineSequence().forEach { line ->
                val parts = line.trim().split(' ').filter { it.isNotEmpty() }
                when {
                    parts.size == 3 && parts[0] == "p" && isPackageName(parts[1]) ->
                        parts[2].toLongOrNull()?.let { pk[parts[1]] = it }
                    parts.size == 2 && parts[0] == "t" -> parts[1].toIntOrNull()?.let { tk += it }
                }
            }
            return Dismissals(pk, tk)
        }
    }
}
