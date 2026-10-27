package com.victorious.launch.core.theme

import com.victorious.launch.core.fleet.FleetPins

/**
 * Package+cert pairs allowed to **read** the shared fleet theme.
 *
 * This list does **not** grant bridge pairing, fleet-install trust, or
 * remote-control ops (`app.launch` / `layout.set` / `panel.open`).
 * A package may have more than one allowed cert.
 */
object FleetThemeConsumers {
    const val HACKERAI = "ai.hackerai.companion"
    const val MESH = "com.victorious.mesh"
    const val MANGO = "com.victorious.mango"
    const val VICTORYPED = "com.victorious.victoryped"

    const val HACKERAI_DEBUG =
        "AF:1C:8F:C5:44:9F:58:4A:46:C8:96:CC:C9:7F:35:B0:7B:35:7E:96:EE:66:5F:3A:BF:CB:0D:28:4D:F5:23:55"
    const val HACKERAI_RELEASE = FleetPins.BOUND_COMPANION_RELEASE
    const val MESH_CERT =
        "F4:C9:25:B8:E8:0B:55:AB:83:E6:04:13:EE:49:86:57:DF:85:03:BB:30:D8:1F:9F:17:43:F2:2B:F5:36:B9:F4"
    const val MANGO_CERT =
        "08:02:D9:4C:BB:CB:C3:F8:0C:9D:2F:C5:30:57:9E:5B:BD:E2:26:AC:7C:40:F4:52:31:0C:A1:0A:DA:EC:FE:55"
    const val VICTORYPED_CERT =
        "63:AA:41:BB:C5:0A:06:27:A0:4F:55:96:F1:11:12:E0:36:E4:51:B5:9F:4C:26:0F:AB:FB:92:E1:78:D2:F0:79"

    val PINS: Map<String, Set<String>> =
        mapOf(
            HACKERAI to setOf(HACKERAI_DEBUG, HACKERAI_RELEASE),
            MESH to setOf(MESH_CERT),
            MANGO to setOf(MANGO_CERT),
            VICTORYPED to setOf(VICTORYPED_CERT),
        )

    fun isPinned(packageName: String, certs: List<String>): Boolean {
        val allowed = PINS[packageName] ?: return false
        if (certs.isEmpty()) return false
        val incoming = certs.map { FleetPins.normalize(it) }.toSet()
        return allowed.any { FleetPins.normalize(it) in incoming }
    }
}
