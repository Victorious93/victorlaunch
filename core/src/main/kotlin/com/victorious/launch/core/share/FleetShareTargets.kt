package com.victorious.launch.core.share

import com.victorious.launch.core.fleet.FleetPins

data class ShareTargetSpec(
    val id: String,
    val label: String,
    val packageNames: List<String>,
    val pins: Set<String>,
)

enum class ShareTargetState {
    MISSING,
    UNTRUSTED,
    READY,
}

data class ShareTargetStatus(
    val spec: ShareTargetSpec,
    val installedPackage: String?,
    val state: ShareTargetState,
)

object FleetShareCatalog {
    val ALL: List<ShareTargetSpec> =
        listOf(
            ShareTargetSpec(
                id = "victorsms",
                label = "VictorSMS",
                packageNames = listOf("com.victorious.sms"),
                pins = emptySet(),
            ),
            ShareTargetSpec(
                id = "browser",
                label = "Victorious Browser",
                packageNames = listOf("com.victorious.browser"),
                pins = emptySet(),
            ),
            ShareTargetSpec(
                id = "filemanager",
                label = "File Manager",
                packageNames = listOf("ai.victorious.filemanager", "ai.victorious.filemanager.debug"),
                pins = emptySet(),
            ),
            ShareTargetSpec(
                id = "controller",
                label = "DroidCommand AI",
                packageNames = listOf("ai.droidcommand.app"),
                pins = setOf(FleetPins.CONTROLLER_DEBUG),
            ),
        )

    fun status(
        spec: ShareTargetSpec,
        installed: Map<String, List<String>>,
        extraPins: Map<String, List<String>>,
    ): ShareTargetStatus {
        val pkg = spec.packageNames.firstOrNull { it in installed }
        if (pkg == null) return ShareTargetStatus(spec, null, ShareTargetState.MISSING)
        val certs = installed[pkg].orEmpty()
        val pins = spec.pins + extraPins[pkg].orEmpty()
        val trusted = certs.any { cert -> pins.any { FleetPins.normalize(it) == FleetPins.normalize(cert) } }
        return ShareTargetStatus(
            spec = spec,
            installedPackage = pkg,
            state = if (trusted) ShareTargetState.READY else ShareTargetState.UNTRUSTED,
        )
    }

    fun ready(
        installed: Map<String, List<String>>,
        extraPins: Map<String, List<String>>,
    ): List<ShareTargetStatus> =
        ALL.map { status(it, installed, extraPins) }.filter { it.state == ShareTargetState.READY }
}
