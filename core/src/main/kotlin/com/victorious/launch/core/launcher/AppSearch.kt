package com.victorious.launch.core.launcher

data class SearchableApp(
    val packageName: String,
    val label: String,
    val component: String = "",
)

object AppSearch {
    fun filter(apps: List<SearchableApp>, query: String): List<SearchableApp> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return apps
        return apps.filter { app ->
            app.label.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.component.lowercase().contains(q)
        }
    }
}
