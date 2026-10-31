package com.victorious.launch.core.launcher

data class SearchableApp(
    val packageName: String,
    val label: String,
    val component: String = "",
)

object AppSearch {
    /** Substring filter kept for callers that want exact containment. */
    fun filter(apps: List<SearchableApp>, query: String): List<SearchableApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.filter { app ->
            app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.component.lowercase().contains(q)
        }
    }

    /**
     * Fuzzy score, higher is better, 0 = no match. Order of preference:
     * label prefix > word prefix > initials > label substring > subsequence > package hit.
     */
    fun score(app: SearchableApp, query: String): Int {
        val q = query.trim().lowercase().filterNot { it.isWhitespace() }
        if (q.isEmpty()) return 1
        val label = app.label.lowercase()
        val compact = label.filterNot { it.isWhitespace() }
        if (compact.startsWith(q)) return 1000 - compact.length
        val words = label.split(' ', '-', '_', '.').filter { it.isNotEmpty() }
        if (words.any { it.startsWith(q) }) return 800 - compact.length
        val initials = words.joinToString("") { it.take(1) }
        if (q.length >= 2 && initials.startsWith(q)) return 700 - compact.length
        if (compact.contains(q)) return 600 - compact.indexOf(q)
        val gaps = subsequenceGaps(compact, q)
        if (gaps >= 0) return (500 - gaps * 10 - compact.length).coerceAtLeast(101)
        if (app.packageName.lowercase().contains(q)) return 100
        return 0
    }

    /** Ranked fuzzy results; blank query returns the input order. */
    fun fuzzy(apps: List<SearchableApp>, query: String): List<SearchableApp> {
        if (query.isBlank()) return apps
        return apps
            .map { it to score(it, query) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<SearchableApp, Int>> { it.second }.thenBy { it.first.label.lowercase() })
            .map { it.first }
    }

    /** -1 when [q] is not a subsequence of [s]; otherwise the number of skipped chars between hits. */
    private fun subsequenceGaps(s: String, q: String): Int {
        var i = 0
        var gaps = 0
        var started = false
        for (c in s) {
            if (i < q.length && c == q[i]) {
                i++
                started = true
            } else if (started && i < q.length) {
                gaps++
            }
        }
        return if (i == q.length) gaps else -1
    }
}
