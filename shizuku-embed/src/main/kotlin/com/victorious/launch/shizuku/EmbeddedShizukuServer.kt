package com.victorious.launch.shizuku

/**
 * Hook for a future in-tree Shizuku server (native starter + app_process
 * service + pairing UI). This PR does **not** vendor upstream server
 * code — [isEmbedded] is always false and [start] never launches a
 * binder of our own. The client talks to the stock Shizuku app instead.
 *
 * See `shizuku-embed/README.md` for the embed plan (Apache-2.0, no
 * Shizuku name / icons / `moe.shizuku.privileged.api`).
 */
data class EmbeddedStartAttempt(
    val started: Boolean,
    val detail: String,
)

object EmbeddedShizukuServer {
    fun isEmbedded(): Boolean = false

    fun start(mode: ShizukuStartupMode): EmbeddedStartAttempt =
        EmbeddedStartAttempt(
            started = false,
            detail =
                when (mode) {
                    ShizukuStartupMode.WIRELESS_DEBUG ->
                        "Embedded wireless-debug pairing is not in this build. Use the stock Shizuku app after enabling Wireless debugging."
                    ShizukuStartupMode.ADB_COMMAND ->
                        "Embedded starter binary is not in this build. ${ShizukuStarters.adbCommand()}"
                    ShizukuStartupMode.ROOT ->
                        "Embedded starter binary is not in this build. ${ShizukuStarters.rootCommand()}"
                },
        )
}
