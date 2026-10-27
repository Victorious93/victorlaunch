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
}
