package com.victorious.launch.core.fleet

/**
 * Extra long-press (and dashboard-tile) actions for fleet apps, limited to
 * **public** intents and to shortcuts the target app itself publishes.
 *
 * Mesh / Mango VPN toggles are resolved at runtime: a LauncherApps shortcut
 * is preferred, then a package+action activity, otherwise the item is hidden.
 * VictorLaunch never toggles silently and does not add a bridge op for this.
 */
enum class FleetActionKind {
    OPEN,
    INFO,
    SENDTO,
    VIEW,
    SHORTCUT,
    EXPLICIT,
}

data class FleetPublicAction(
    val id: String,
    val label: String,
    val packageName: String,
    val kind: FleetActionKind,
    val action: String,
    val dataUri: String? = null,
    val mimeType: String? = null,
    val shortcutId: String? = null,
    val activityClass: String? = null,
)

/** What the device currently exposes for one package. Empty means hide toggles. */
data class PublishedCapability(
    val shortcutIds: Set<String> = emptySet(),
    /** Action string → resolved activity class names (possibly empty if hidden). */
    val activities: Map<String, List<String>> = emptyMap(),
) {
    fun hasShortcut(id: String): Boolean = id in shortcutIds

    fun activityClasses(action: String): List<String> = activities[action].orEmpty()

    fun hasActivity(action: String): Boolean = activityClasses(action).isNotEmpty()
}

/** Announced public contracts for Victorious Mesh and VictorMango. Not a bridge op. */
object FleetVpnToggles {
    const val MESH_PACKAGE = "com.victorious.mesh"
    const val MANGO_PACKAGE = "com.victorious.mango"

    const val MESH_CONNECT_SHORTCUT = "mesh_connect"
    const val MESH_DISCONNECT_SHORTCUT = "mesh_disconnect"
    const val MESH_CONNECT_ACTION = "com.victorious.mesh.action.MESH_CONNECT"
    const val MESH_DISCONNECT_ACTION = "com.victorious.mesh.action.MESH_DISCONNECT"

    const val MANGO_ON_SHORTCUT = "vpn_on"
    const val MANGO_OFF_SHORTCUT = "vpn_off"
    const val MANGO_ON_ACTION = "com.victorious.mango.action.VPN_ON"
    const val MANGO_OFF_ACTION = "com.victorious.mango.action.VPN_OFF"
    const val MANGO_TOGGLE_ACTION = "com.victorious.mango.action.VPN_TOGGLE"
    const val MANGO_ACTIVITY_CLASS = "com.victorious.mango.VpnToggleActivity"

    const val MESH_ON_LABEL = "Mesh VPN on"
    const val MESH_OFF_LABEL = "Mesh VPN off"
    const val MANGO_ON_LABEL = "Router VPN on"
    const val MANGO_OFF_LABEL = "Router VPN off"
    const val MANGO_TOGGLE_LABEL = "Router VPN on/off"

    val MESH_ACTIONS: List<String> = listOf(MESH_CONNECT_ACTION, MESH_DISCONNECT_ACTION)
    val MANGO_ACTIONS: List<String> = listOf(MANGO_ON_ACTION, MANGO_OFF_ACTION, MANGO_TOGGLE_ACTION)
    val ALL_ACTIONS: List<String> = MESH_ACTIONS + MANGO_ACTIONS

    fun packageForTrayDomain(domain: String): String? =
        when (domain) {
            "mesh" -> MESH_PACKAGE
            "vpn" -> MANGO_PACKAGE
            else -> null
        }

    fun actionsFor(
        packageName: String,
        capability: PublishedCapability,
        preferredClass: String? = if (packageName == MANGO_PACKAGE) MANGO_ACTIVITY_CLASS else null,
    ): List<FleetPublicAction> {
        return when (packageName) {
            MESH_PACKAGE ->
                listOfNotNull(
                    resolve(
                        id = "mesh_on",
                        label = MESH_ON_LABEL,
                        packageName = packageName,
                        shortcutId = MESH_CONNECT_SHORTCUT,
                        action = MESH_CONNECT_ACTION,
                        capability = capability,
                    ),
                    resolve(
                        id = "mesh_off",
                        label = MESH_OFF_LABEL,
                        packageName = packageName,
                        shortcutId = MESH_DISCONNECT_SHORTCUT,
                        action = MESH_DISCONNECT_ACTION,
                        capability = capability,
                    ),
                )
            MANGO_PACKAGE -> {
                val onOff =
                    listOfNotNull(
                        resolve(
                            id = "mango_on",
                            label = MANGO_ON_LABEL,
                            packageName = packageName,
                            shortcutId = MANGO_ON_SHORTCUT,
                            action = MANGO_ON_ACTION,
                            capability = capability,
                            preferredClass = preferredClass,
                        ),
                        resolve(
                            id = "mango_off",
                            label = MANGO_OFF_LABEL,
                            packageName = packageName,
                            shortcutId = MANGO_OFF_SHORTCUT,
                            action = MANGO_OFF_ACTION,
                            capability = capability,
                            preferredClass = preferredClass,
                        ),
                    )
                if (onOff.isNotEmpty()) {
                    onOff
                } else {
                    listOfNotNull(
                        resolve(
                            id = "mango_toggle",
                            label = MANGO_TOGGLE_LABEL,
                            packageName = packageName,
                            shortcutId = null,
                            action = MANGO_TOGGLE_ACTION,
                            capability = capability,
                            preferredClass = preferredClass,
                        ),
                    )
                }
            }
            else -> emptyList()
        }
    }

    /**
     * Prefer a published shortcut id, else a resolvable package+action activity,
     * else hide. [shortcutId] may be null (toggle-only).
     */
    fun resolve(
        id: String,
        label: String,
        packageName: String,
        shortcutId: String?,
        action: String,
        capability: PublishedCapability,
        preferredClass: String? = null,
    ): FleetPublicAction? {
        if (!shortcutId.isNullOrBlank() && capability.hasShortcut(shortcutId)) {
            return FleetPublicAction(
                id = id,
                label = label,
                packageName = packageName,
                kind = FleetActionKind.SHORTCUT,
                action = action,
                shortcutId = shortcutId,
            )
        }
        if (!capability.hasActivity(action)) return null
        val classes = capability.activityClasses(action)
        val chosen =
            when {
                preferredClass != null && preferredClass in classes -> preferredClass
                else -> classes.firstOrNull()
            }
        return FleetPublicAction(
            id = id,
            label = label,
            packageName = packageName,
            kind = FleetActionKind.EXPLICIT,
            action = action,
            activityClass = chosen,
        )
    }
}

object FleetPublicActions {
    const val ACTION_MAIN = "android.intent.action.MAIN"
    const val ACTION_INFO = "android.settings.APPLICATION_DETAILS_SETTINGS"
    const val ACTION_SENDTO = "android.intent.action.SENDTO"
    const val ACTION_VIEW = "android.intent.action.VIEW"

    fun isFleetPackage(packageName: String): Boolean =
        KnownFleetApps.ALL.any { packageName in it.packages }

    /**
     * Safe extras for [packageName]. [shortcutLabels] are already shown in the
     * menu from LauncherApps; matching labels on catalog intents are not
     * duplicated. [capability] is required for Mesh / Mango toggles; when it is
     * missing or empty those items are hidden.
     */
    fun extras(
        packageName: String,
        shortcutLabels: List<String> = emptyList(),
        capability: PublishedCapability = PublishedCapability(),
    ): List<FleetPublicAction> {
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
        val usedShortcutIds = mutableSetOf<String>()
        FleetVpnToggles.actionsFor(packageName, capability).forEach { action ->
            action.shortcutId?.let { usedShortcutIds += it }
            out += action
        }
        return out
    }

    /** Shortcut ids already claimed by a resolved toggle, so the menu can skip them. */
    fun claimedShortcutIds(actions: List<FleetPublicAction>): Set<String> =
        actions.mapNotNull { it.shortcutId }.toSet()

    /** Public, no-permission intents we know about (not including runtime-resolved toggles). */
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
