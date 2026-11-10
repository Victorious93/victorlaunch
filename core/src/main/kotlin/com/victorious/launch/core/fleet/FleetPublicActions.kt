package com.victorious.launch.core.fleet

/**
 * Extra long-press actions for fleet apps, limited to **public** intents and
 * to shortcuts the target app itself publishes. This catalog does not invent
 * mesh-up / VPN-toggle APIs; if a fleet app exposes none, only Open and Info
 * are offered.
 */
enum class FleetActionKind {
    OPEN,
    INFO,
    SENDTO,
    VIEW,
}

data class FleetPublicAction(
    val id: String,
    val label: String,
    val packageName: String,
    val kind: FleetActionKind,
    val action: String,
    val dataUri: String? = null,
    val mimeType: String? = null,
)

object FleetPublicActions {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_INFO = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    fun isFleetPackage(packageName: String): Boolean =
        KnownFleetApps.ALL.any { packageName in it.packages }

    /**
     * Safe extras for [packageName]. [shortcutLabels] are already shown in the
     * menu from LauncherApps; matching labels are not duplicated.
     */
    fun extras(packageName: String, shortcutLabels: List<String> = emptyList()): List<FleetPublicAction> {
        if (!isFleetPackage(packageName)) return emptyList()
        val skip = shortcutLabels.map { it.trim().lowercase() }.toSet()
        val out = mutableListOf<FleetPublicAction>()
        out +=
            FleetPublicAction(
                id = "open",
                label = "Open",
                packageName = packageName,
                kind = FleetActionKind.OPEN,
                action = ACTION_MAIN,
            )
        catalogIntents(packageName).forEach { action ->
            val label = action.label.lowercase()
            if (skip.none { s -> s == label || s in label || label in s }) {
                out += action
            }
        }
        return out
    }

    /** Public, no-permission intents we know about. Empty for mesh/VPN/most apps. */
    fun catalogIntents(packageName: String): List<FleetPublicAction> {
        return when (packageName) {
            "com.victorious.sms" ->
                listOf(
                    FleetPublicAction(
                        id = "sms_compose",
                        label = "Compose SMS",
                        packageName = packageName,
                        kind = FleetActionKind.SENDTO,
                        action = ACTION_SENDTO,
                        dataUri = "smsto:",
                    ),
                )
            "com.victorious.browser" ->
                listOf(
                    FleetPublicAction(
                        id = "browser_view",
                        label = "Open new tab",
                        packageName = packageName,
                        kind = FleetActionKind.VIEW,
                        action = ACTION_VIEW,
                        dataUri = "https://",
                    ),
                )
            else -> emptyList()
        }
    }

    fun infoAction(packageName: String): FleetPublicAction =
        FleetPublicAction(
            id = "info",
            label = "App info",
            packageName = packageName,
            kind = FleetActionKind.INFO,
            action = ACTION_INFO,
            dataUri = "package:$packageName",
        )
}
