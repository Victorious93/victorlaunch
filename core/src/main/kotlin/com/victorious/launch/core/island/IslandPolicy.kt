package com.victorious.launch.core.island

/**
 * Destructive Island actions never run unless [confirmed] is true.
 * Offline mode is a set of public DPM user restrictions — not a packet filter.
 */
object IslandPolicy {
    const val RESTRICTION_WIFI = "no_config_wifi"
    const val RESTRICTION_MOBILE = "no_config_mobile_networks"
    const val RESTRICTION_VPN = "no_config_vpn"
    const val RESTRICTION_SHARE_IN = "no_sharing_into_profile"

    val OFFLINE_RESTRICTIONS: List<String> =
        listOf(RESTRICTION_WIFI, RESTRICTION_MOBILE, RESTRICTION_VPN, RESTRICTION_SHARE_IN)

    fun canWipe(confirmed: Boolean): Boolean = confirmed

    fun canLockdown(confirmed: Boolean): Boolean = confirmed

    fun describeOffline(nonRoot: Boolean): String =
        if (nonRoot) {
            "Profile-owner path: DPM user restrictions (${OFFLINE_RESTRICTIONS.joinToString()}). " +
                "This does not drop packets. Quiet-mode pause is the reliable offline switch."
        } else {
            "Root/Shizuku path: freeze every work-profile package and enable quiet mode. " +
                "Per-UID netpolicy reject is not a public API from this APK."
        }
}
