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
    const val TASKBAR = "com.victorious.taskbar"
    const val ROOTFORGE_DEBUG = "ai.rootforge.os.debug"
    const val IDENTITYSHIELD = "com.identityshield.companion"
    const val KEYBOARD_DEBUG = "com.victorious.keyboard.debug"
    const val CONTROLLER = "ai.droidcommand.app"
    const val SPOOF = "com.victorious.spoof"
    const val PENTESTSWARM = "ai.pentestswarm.companion"
    const val EDLCOMMANDER = "ai.edlcommander.companion"
    const val FILEMANAGER = "ai.victorious.filemanager"
    const val FILEMANAGER_DEBUG = "ai.victorious.filemanager.debug"
    const val CAM = "com.victorious.cam"
    const val TERMUX = "com.termux"

    const val HACKERAI_DEBUG =
        "AF:1C:8F:C5:44:9F:58:4A:46:C8:96:CC:C9:7F:35:B0:7B:35:7E:96:EE:66:5F:3A:BF:CB:0D:28:4D:F5:23:55"
    const val HACKERAI_RELEASE = FleetPins.BOUND_COMPANION_RELEASE
    const val MESH_CERT =
        "F4:C9:25:B8:E8:0B:55:AB:83:E6:04:13:EE:49:86:57:DF:85:03:BB:30:D8:1F:9F:17:43:F2:2B:F5:36:B9:F4"
    const val MANGO_CERT =
        "08:02:D9:4C:BB:CB:C3:F8:0C:9D:2F:C5:30:57:9E:5B:BD:E2:26:AC:7C:40:F4:52:31:0C:A1:0A:DA:EC:FE:55"
    const val VICTORYPED_CERT =
        "63:AA:41:BB:C5:0A:06:27:A0:4F:55:96:F1:11:12:E0:36:E4:51:B5:9F:4C:26:0F:AB:FB:92:E1:78:D2:F0:79"
    const val TASKBAR_CERT =
        "4F:58:A5:C5:75:93:5E:10:3B:1F:73:C0:7A:D2:E0:0A:FF:64:57:76:27:71:1B:77:96:79:37:D5:FC:3B:C2:92"
    const val ROOTFORGE_DEBUG_CERT =
        "77:7A:C7:00:87:05:A8:B4:B8:93:AC:EF:0E:03:E9:1C:7F:49:52:C9:52:FE:B2:6E:06:D7:90:B9:14:86:64:6F"
    const val IDENTITYSHIELD_CERT =
        "22:4F:C1:64:D6:8A:28:88:B9:FE:8F:10:C7:B6:B5:E5:78:67:2B:D2:F8:97:05:06:68:4B:23:A9:43:FA:44:1D"
    const val KEYBOARD_DEBUG_CERT =
        "47:1E:AD:6B:3F:96:7E:F3:C8:63:EF:49:08:74:8B:3B:E3:63:F9:1D:75:0B:FD:B1:E9:28:05:BC:F1:6B:AF:4F"
    const val CONTROLLER_DEBUG = FleetPins.CONTROLLER_DEBUG
    const val SPOOF_CERT =
        "A4:84:62:2B:7E:86:E1:D9:F8:04:56:6E:B7:89:2D:EE:3C:64:EB:8E:B9:13:9E:04:25:BC:65:F3:EF:D0:FF:EE"
    const val PENTESTSWARM_CERT =
        "5E:3A:B1:23:1F:7E:DA:18:78:B8:FF:D8:9E:AC:B1:F7:2E:EB:66:AF:3D:BA:2E:01:33:51:24:68:42:23:30:C7"
    const val EDLCOMMANDER_CERT =
        "E5:60:B6:92:BD:E6:51:9E:B7:CB:B5:2C:E4:DC:91:5A:19:E4:41:A0:1A:5D:03:68:82:71:AC:99:78:8A:58:C8"
    const val FILEMANAGER_CERT =
        "8C:1F:E3:C9:E5:34:F2:71:05:C0:68:8F:45:28:D7:7E:47:11:AE:0A:79:DD:08:D1:E8:59:30:B9:E9:48:85:F2"
    const val CAM_CERT =
        "8B:56:F8:18:93:B2:4F:02:BC:6C:93:39:39:36:32:3E:C5:74:86:D9:34:E2:9B:73:7C:E1:A7:B4:24:C5:B6:0D"
    const val TERMUX_CERT =
        "67:17:C5:5A:BE:9E:0D:F8:C6:03:C9:A0:F3:F3:67:B6:CA:BD:B0:1F:3E:58:3A:4F:F6:44:E5:DF:3D:4C:31:D3"

    val PINS: Map<String, Set<String>> =
        mapOf(
            HACKERAI to setOf(HACKERAI_DEBUG, HACKERAI_RELEASE),
            MESH to setOf(MESH_CERT),
            MANGO to setOf(MANGO_CERT),
            VICTORYPED to setOf(VICTORYPED_CERT),
            TASKBAR to setOf(TASKBAR_CERT),
            ROOTFORGE_DEBUG to setOf(ROOTFORGE_DEBUG_CERT),
            IDENTITYSHIELD to setOf(IDENTITYSHIELD_CERT),
            KEYBOARD_DEBUG to setOf(KEYBOARD_DEBUG_CERT),
            CONTROLLER to setOf(CONTROLLER_DEBUG),
            SPOOF to setOf(SPOOF_CERT),
            PENTESTSWARM to setOf(PENTESTSWARM_CERT),
            EDLCOMMANDER to setOf(EDLCOMMANDER_CERT),
            FILEMANAGER to setOf(FILEMANAGER_CERT),
            FILEMANAGER_DEBUG to setOf(FILEMANAGER_CERT),
            CAM to setOf(CAM_CERT),
            TERMUX to setOf(TERMUX_CERT),
        )

    fun isPinned(packageName: String, certs: List<String>): Boolean {
        val allowed = PINS[packageName] ?: return false
        if (certs.isEmpty()) return false
        val incoming = certs.map { FleetPins.normalize(it) }.toSet()
        return allowed.any { FleetPins.normalize(it) in incoming }
    }
}
