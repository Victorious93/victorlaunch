package com.victorious.launch.network

/**
 * Shell builders for radio lock / recover (uid 0 or 2000). Strings only.
 * OEM support is **not** universal — callers must treat failures as
 * "this device ignored the setting".
 */
object NetworkCommands {
    /** AOSP RIL `NT_MODE_NR_LTE` — many Qualcomms; Samsungs often differ. */
    const val MODE_NR_LTE = 23

    /** AOSP `NT_MODE_LTE_ONLY`. */
    const val MODE_LTE_ONLY = 11

    /**
     * TelephonyManager NETWORK_TYPE_BITMASK for LTE + LTE_CA + NR.
     * NETWORK_TYPE_LTE=13, LTE_CA=19, NR=20 → bit (n-1).
     */
    val LTE_NR_BITMASK: Long = bitmask(13) or bitmask(19) or bitmask(20)

    fun setPreferredNetworkMode(mode: Int): List<String> {
        require(mode in 0..33) { "invalid preferred_network_mode" }
        return listOf("settings", "put", "global", "preferred_network_mode", mode.toString())
    }

    fun setAllowed2g(allowed: Boolean): List<String> =
        listOf("settings", "put", "global", "allowed_2g", if (allowed) "1" else "0")

    fun setEnable2g(allowed: Boolean): List<String> =
        listOf("settings", "put", "global", "enable_2g", if (allowed) "1" else "0")

    fun airplaneCmd(on: Boolean): List<String> =
        listOf("cmd", "connectivity", "airplane-mode", if (on) "enable" else "disable")

    fun airplaneSetting(on: Boolean): List<String> =
        listOf("settings", "put", "global", "airplane_mode_on", if (on) "1" else "0")

    fun airplaneBroadcast(on: Boolean): List<String> =
        listOf(
            "am",
            "broadcast",
            "-a",
            "android.intent.action.AIRPLANE_MODE",
            "--ez",
            "state",
            if (on) "true" else "false",
        )

    fun wifiEnable(): List<String> = listOf("svc", "wifi", "enable")

    /**
     * Cell/band lock is OEM NV / Qualcomm debug. This builder only
     * validates integers so tests can pin the contract; the UserService
     * must refuse to run it.
     */
    fun cellLockUnsupported(pci: Int, earfcn: Int): String {
        require(pci in 0..1007) { "invalid pci" }
        require(earfcn in 0..262143) { "invalid earfcn" }
        return "unsupported: cell/band lock is OEM-specific and is not written"
    }

    private fun bitmask(networkType: Int): Long = 1L shl (networkType - 1)
}
