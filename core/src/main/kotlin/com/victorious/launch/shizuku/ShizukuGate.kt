package com.victorious.launch.shizuku

enum class ShizukuStartupMode {
    WIRELESS_DEBUG,
    ADB_COMMAND,
    ROOT,
}

data class ShizukuSnapshot(
    val userEnabled: Boolean,
    val binderAlive: Boolean,
    val permissionGranted: Boolean,
    val uid: Int? = null,
) {
    val ready: Boolean
        get() = ShizukuGate.canUsePrivileged(userEnabled, binderAlive, permissionGranted)
}

object ShizukuGate {
    const val STOCK_PACKAGE = "moe.shizuku.privileged.api"

    fun canUsePrivileged(
        userEnabled: Boolean,
        binderAlive: Boolean,
        permissionGranted: Boolean,
    ): Boolean = userEnabled && binderAlive && permissionGranted
}
