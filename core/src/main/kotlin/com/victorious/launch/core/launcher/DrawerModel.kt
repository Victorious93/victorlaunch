package com.victorious.launch.core.launcher

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Auto categories for drawer tabs. [androidCategory] mirrors ApplicationInfo.category. */
enum class AppCategory(val title: String) {
    FLEET("Fleet"),
    SOCIAL("Social"),
    MEDIA("Media"),
    GAMES("Games"),
    PRODUCTIVITY("Work"),
    MAPS("Travel"),
    NEWS("News"),
    TOOLS("Tools"),
    SYSTEM("System"),
    OTHER("Other"),
}

/** A user-created drawer tab (acts as a drawer folder). */
@Serializable
data class DrawerTab(val id: String, val title: String, val packages: List<String> = emptyList())

object DrawerModel {
    // android.content.pm.ApplicationInfo.CATEGORY_* values (stable public constants).
    private const val CAT_GAME = 0
    private const val CAT_AUDIO = 1
    private const val CAT_VIDEO = 2
    private const val CAT_IMAGE = 3
    private const val CAT_SOCIAL = 4
    private const val CAT_NEWS = 5
    private const val CAT_MAPS = 6
    private const val CAT_PRODUCTIVITY = 7
    private const val CAT_ACCESSIBILITY = 8

    private val keywordRules: List<Pair<AppCategory, List<String>>> =
        listOf(
            AppCategory.SOCIAL to listOf("whatsapp", "telegram", "signal", "messag", "sms", "mms", "facebook", "instagram", "twitter", "discord", "reddit", "snapchat", "mail", "dialer", "contacts"),
            AppCategory.MEDIA to listOf("music", "spotify", "youtube", "video", "camera", "gallery", "photo", "podcast", "player", "netflix"),
            AppCategory.MAPS to listOf("maps", "navigation", "uber", "lyft", "transit", "waze"),
            AppCategory.PRODUCTIVITY to listOf("docs", "sheets", "office", "calendar", "notes", "drive", "slack", "keep", "todo", "pdf"),
            AppCategory.TOOLS to listOf("calculator", "clock", "files", "filemanager", "termux", "vpn", "browser", "chrome", "firefox", "keyboard", "settings", "shizuku"),
            AppCategory.NEWS to listOf("news", "feed"),
        )

    fun categorize(packageName: String, androidCategory: Int? = null, isSystem: Boolean = false): AppCategory {
        val pkg = packageName.lowercase()
        if (pkg.startsWith("com.victorious.")) return AppCategory.FLEET
        when (androidCategory) {
            CAT_GAME -> return AppCategory.GAMES
            CAT_AUDIO, CAT_VIDEO, CAT_IMAGE -> return AppCategory.MEDIA
            CAT_SOCIAL -> return AppCategory.SOCIAL
            CAT_NEWS -> return AppCategory.NEWS
            CAT_MAPS -> return AppCategory.MAPS
            CAT_PRODUCTIVITY -> return AppCategory.PRODUCTIVITY
            CAT_ACCESSIBILITY -> return AppCategory.TOOLS
        }
        keywordRules.forEach { (cat, words) -> if (words.any { it in pkg }) return cat }
        return if (isSystem) AppCategory.SYSTEM else AppCategory.OTHER
    }

    /** Categories that have at least one app, in enum order. */
    fun presentCategories(categories: Collection<AppCategory>): List<AppCategory> {
        val set = categories.toSet()
        return AppCategory.entries.filter { it in set }
    }

    fun visible(packages: List<String>, hidden: Set<String>, showHidden: Boolean): List<String> =
        if (showHidden) packages else packages.filterNot { it in hidden }

    fun toggleHidden(hidden: Set<String>, pkg: String): Set<String> = if (pkg in hidden) hidden - pkg else hidden + pkg

    fun addTab(tabs: List<DrawerTab>, id: String, title: String): List<DrawerTab> {
        val name = title.trim().ifEmpty { "Tab" }
        if (tabs.any { it.id == id }) return tabs
        return tabs + DrawerTab(id, name)
    }

    fun removeTab(tabs: List<DrawerTab>, id: String): List<DrawerTab> = tabs.filterNot { it.id == id }

    fun renameTab(tabs: List<DrawerTab>, id: String, title: String): List<DrawerTab> =
        tabs.map { if (it.id == id && title.isNotBlank()) it.copy(title = title.trim()) else it }

    fun toggleMembership(tabs: List<DrawerTab>, id: String, pkg: String): List<DrawerTab> =
        tabs.map { tab ->
            if (tab.id != id) tab
            else if (pkg in tab.packages) tab.copy(packages = tab.packages - pkg)
            else tab.copy(packages = tab.packages + pkg)
        }

    private val json = Json { ignoreUnknownKeys = true }

    fun encodeTabs(tabs: List<DrawerTab>): String = json.encodeToString(ListSerializer(DrawerTab.serializer()), tabs)

    fun decodeTabs(raw: String?): List<DrawerTab> =
        if (raw.isNullOrBlank()) {
            emptyList()
        } else {
            try {
                json.decodeFromString(ListSerializer(DrawerTab.serializer()), raw)
            } catch (_: Exception) {
                emptyList()
            }
        }
}
