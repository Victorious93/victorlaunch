package com.victorious.launch.core.launcher

/**
 * Public icon-pack intents used by ADW / GO / Nova / Lawnchair.
 * We query these; we do not vendor any pack.
 */
object IconPackContract {
    const val ADW_THEMES = "org.adw.launcher.THEMES"
    const val ADW_PICK_ICON = "org.adw.launcher.icons.ACTION_PICK_ICON"
    const val GO_THEME = "com.gau.go.launcherex.theme"
    const val NOVA_THEME = "com.novalauncher.THEME"
    const val TURBO_THEME = "com.dlto.atom.launcher.THEME"

    val THEME_ACTIONS: List<String> = listOf(ADW_THEMES, GO_THEME, NOVA_THEME, TURBO_THEME)

    fun isKnownThemeAction(action: String): Boolean = THEME_ACTIONS.contains(action)

    /** Common launcher components used to preview an installed pack. */
    val PREVIEW_COMPONENTS: List<String> =
        listOf(
            "com.android.settings/com.android.settings.Settings",
            "com.android.chrome/com.google.android.apps.chrome.Main",
            "com.google.android.gm/com.google.android.gm.ConversationListActivityGmail",
            "com.google.android.apps.messaging/com.google.android.apps.messaging.ui.ConversationListActivity",
            "com.android.camera2/com.android.camera.CameraLauncher",
            "com.android.dialer/.DialtactsActivity",
            "com.android.mms/.ui.ConversationList",
            "com.android.browser/.BrowserActivity",
        )

    fun normalizeComponent(raw: String): String {
        val trimmed = raw.trim()
        val inner =
            if (trimmed.startsWith("ComponentInfo{") && trimmed.endsWith("}")) {
                trimmed.removePrefix("ComponentInfo{").removeSuffix("}")
            } else {
                trimmed
            }
        return inner.lowercase()
    }

    fun previewDrawables(appFilter: Map<String, String>, limit: Int = 4): List<String> {
        val lookup = appFilter.mapKeys { normalizeComponent(it.key) }
        val names = LinkedHashSet<String>()
        PREVIEW_COMPONENTS.forEach { component ->
            lookup[normalizeComponent(component)]?.let { names += it }
            if (names.size >= limit) return names.toList()
        }
        lookup.values.forEach { value ->
            if (value.isNotBlank()) names += value
            if (names.size >= limit) return names.toList()
        }
        return names.toList().take(limit)
    }
}
