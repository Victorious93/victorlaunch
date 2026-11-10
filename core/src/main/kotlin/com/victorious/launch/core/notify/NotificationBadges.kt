package com.victorious.launch.core.notify

/**
 * In-memory badge and preview models. Notification content must never be
 * written to preferences, backups, or logs by callers.
 */
data class NotificationPreview(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val postedAtMs: Long,
    val ongoing: Boolean = false,
    val groupSummary: Boolean = false,
)

data class NotificationBadge(
    val packageName: String,
    val count: Int,
    val previews: List<NotificationPreview>,
)

object NotificationBadges {
    const val MAX_PREVIEWS = 3

    fun summarize(
        active: List<NotificationPreview>,
        optedIn: Boolean,
        maxPreviews: Int = MAX_PREVIEWS,
    ): Map<String, NotificationBadge> {
        if (!optedIn) return emptyMap()
        return active
            .filter { shouldCount(it) }
            .groupBy { it.packageName }
            .mapValues { (pkg, items) ->
                val ordered = items.sortedByDescending { it.postedAtMs }
                NotificationBadge(
                    packageName = pkg,
                    count = ordered.size,
                    previews = ordered.take(maxPreviews),
                )
            }
    }

    fun shouldCount(item: NotificationPreview): Boolean {
        if (item.packageName.isBlank()) return false
        if (item.ongoing || item.groupSummary) return false
        return item.title.isNotBlank() || item.text.isNotBlank() || item.key.isNotBlank()
    }

    fun badgeLabel(count: Int): String =
        when {
            count <= 0 -> ""
            count < 10 -> count.toString()
            count < 100 -> "9+"
            else -> "99+"
        }
}
