package com.victorious.launch.core.url

/** Built-in drawer / palette search entry for URL Tools. Not a bridge op. */
object UrlToolsActions {
    const val ID = "url-tools"
    const val TITLE = "URL Tools"
    const val SUBTITLE = "Check, expand, shorten, or clean a link"

    private val KEYWORDS =
        listOf(
            "url", "url tools", "link", "unshorten", "expand", "shorten", "is.gd",
            "tinyurl", "clean link", "utm", "tracking", "virustotal", "virus total",
            "safe browsing", "qr", "https", "http",
        )

    fun matches(query: String): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return false
        if (q.startsWith(">")) return matches(q.drop(1))
        if (TITLE.lowercase().contains(q) || q in KEYWORDS) return true
        return KEYWORDS.any { it.contains(q) || q.contains(it) }
    }
}
