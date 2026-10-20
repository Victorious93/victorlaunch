package com.victorious.launch.core.protocol

enum class OpKind {
    READ,
    ACTION,
    DESTRUCTIVE,
    ;

    val wire: String
        get() =
            when (this) {
                READ -> "read"
                ACTION -> "action"
                DESTRUCTIVE -> "destructive"
            }
}

data class OpDescriptor(
    val name: String,
    val kind: OpKind,
    val remoteAllowed: Boolean,
    val requiresConfirm: Boolean,
)

object Ops {
    const val CAPABILITIES_GET = "capabilities.get"
    const val NODE_STATUS_GET = "node.status.get"
    const val CONFIG_EXPORT = "config.export"
    const val CONFIG_IMPORT = "config.import"
    const val FLEET_LIST = "fleet.list"
    const val LAYOUT_GET = "layout.get"
    const val LAYOUT_SET = "layout.set"
    const val INSTALLED_LIST = "installed.list"
    const val RUNNING_LIST = "running.list"
    const val APP_LAUNCH = "app.launch"
    const val PANEL_OPEN = "panel.open"
    const val NETWORK_STATUS = "network.status"

    val ALL: List<OpDescriptor> =
        listOf(
            OpDescriptor(CAPABILITIES_GET, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(NODE_STATUS_GET, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(NETWORK_STATUS, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(FLEET_LIST, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(LAYOUT_GET, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(INSTALLED_LIST, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(RUNNING_LIST, OpKind.READ, remoteAllowed = true, requiresConfirm = false),
            OpDescriptor(CONFIG_EXPORT, OpKind.ACTION, remoteAllowed = false, requiresConfirm = false),
            OpDescriptor(CONFIG_IMPORT, OpKind.ACTION, remoteAllowed = false, requiresConfirm = true),
            OpDescriptor(APP_LAUNCH, OpKind.ACTION, remoteAllowed = false, requiresConfirm = false),
            OpDescriptor(LAYOUT_SET, OpKind.ACTION, remoteAllowed = false, requiresConfirm = true),
            OpDescriptor(PANEL_OPEN, OpKind.ACTION, remoteAllowed = false, requiresConfirm = false),
        )

    fun descriptor(name: String): OpDescriptor? = ALL.firstOrNull { it.name == name }
}
