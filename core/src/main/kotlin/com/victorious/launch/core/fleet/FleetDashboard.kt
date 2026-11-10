package com.victorious.launch.core.fleet

import com.victorious.launch.core.status.Freshness
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayState
import com.victorious.launch.core.status.TrayTile

enum class FleetKind {
    MESH,
    VPN,
    BROWSER,
    MESSAGING,
    CONTROLLER,
    OTHER,
    ;

    val trayDomain: StatusTrayDomain?
        get() =
            when (this) {
                MESH -> StatusTrayDomain.MESH
                VPN -> StatusTrayDomain.VPN
                BROWSER -> StatusTrayDomain.BROWSER
                MESSAGING -> StatusTrayDomain.SMS
                else -> null
            }
}

enum class DashboardInstall {
    NOT_INSTALLED,
    INSTALLED,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNGRADE,
    UNTRUSTED,
}

enum class RunningKnowledge {
    RUNNING,
    NOT_RUNNING,
    UNKNOWN,
}

data class KnownFleetApp(
    val id: String,
    val name: String,
    val packages: List<String>,
    val kind: FleetKind,
)

data class FleetDashboardRow(
    val id: String,
    val name: String,
    val packageName: String,
    val packages: List<String>,
    val kind: FleetKind,
    val install: DashboardInstall,
    val installedVersionName: String?,
    val installedVersionCode: Int?,
    val manifestVersionName: String?,
    val manifestVersionCode: Int?,
    val running: RunningKnowledge,
    val trayState: StatusTrayState?,
    val trayDetail: String?,
    val trayFreshness: Freshness?,
    val canLaunch: Boolean,
    val canInstallOrUpdate: Boolean,
)

data class FleetDashboard(
    val pulledAtMs: Long,
    val trayFreshness: Freshness,
    val trayNote: String,
    val tiles: List<TrayTile>,
    val rows: List<FleetDashboardRow>,
    val hasManifest: Boolean,
    val usageAccess: Boolean,
)

object KnownFleetApps {
    val ALL: List<KnownFleetApp> =
        listOf(
            KnownFleetApp("mesh", "Mesh", listOf("com.victorious.mesh"), FleetKind.MESH),
            KnownFleetApp("mango", "Mango", listOf("com.victorious.mango"), FleetKind.OTHER),
            KnownFleetApp("browser", "Victorious Browser", listOf("com.victorious.browser"), FleetKind.BROWSER),
            KnownFleetApp("sms", "VictorSMS", listOf("com.victorious.sms"), FleetKind.MESSAGING),
            KnownFleetApp("controller", "DroidCommand AI", listOf("ai.droidcommand.app"), FleetKind.CONTROLLER),
            KnownFleetApp("filemanager", "File Manager", listOf("ai.victorious.filemanager", "ai.victorious.filemanager.debug"), FleetKind.OTHER),
            KnownFleetApp("cam", "Victorious Cam", listOf("com.victorious.cam"), FleetKind.OTHER),
            KnownFleetApp("hackerai", "HackerAI", listOf("ai.hackerai.companion"), FleetKind.OTHER),
            KnownFleetApp("pentestswarm", "PentestSwarm", listOf("ai.pentestswarm.companion"), FleetKind.OTHER),
            KnownFleetApp("edlcommander", "EDL Commander", listOf("ai.edlcommander.companion"), FleetKind.OTHER),
            KnownFleetApp("victoryped", "VictoryPed", listOf("com.victorious.victoryped"), FleetKind.OTHER),
            KnownFleetApp("taskbar", "Taskbar", listOf("com.victorious.taskbar"), FleetKind.OTHER),
            KnownFleetApp("rootforge", "RootForge", listOf("ai.rootforge.os.debug"), FleetKind.OTHER),
            KnownFleetApp("identityshield", "IdentityShield", listOf("com.identityshield.companion"), FleetKind.OTHER),
            KnownFleetApp("keyboard", "Keyboard", listOf("com.victorious.keyboard.debug"), FleetKind.OTHER),
            KnownFleetApp("spoof", "Spoof", listOf("com.victorious.spoof"), FleetKind.OTHER),
            KnownFleetApp("termux", "Termux", listOf("com.termux"), FleetKind.OTHER),
        )

    fun kindForPackage(packageName: String): FleetKind =
        ALL.firstOrNull { packageName in it.packages }?.kind ?: FleetKind.OTHER
}

object FleetDashboardFactory {
    fun build(
        manifest: FleetManifest?,
        installed: Map<String, Pair<Int, String>>,
        running: Set<String>?,
        tray: PublishedTray?,
        extraPins: List<String> = emptyList(),
        nowMs: Long,
        trayNote: String = "",
        known: List<KnownFleetApp> = KnownFleetApps.ALL,
    ): FleetDashboard {
        val trayFreshness =
            if (tray == null) Freshness.EXPIRED else StatusFreshness.of(tray.pulledAtMs, nowMs)
        val tiles =
            listOf("mesh", "vpn", "browser", "sms").map { id ->
                tray?.tiles?.firstOrNull { it.id == id }
                    ?: TrayTile(
                        id = id,
                        domain = StatusTrayDomain.fromWire(id),
                        state = StatusTrayState.UNKNOWN,
                        detail = null,
                        freshness = Freshness.EXPIRED,
                        nodeId = null,
                    )
            }
        val manifestByPkg = manifest?.apps?.associateBy { it.applicationId }.orEmpty()
        val seen = mutableSetOf<String>()
        val rows = mutableListOf<FleetDashboardRow>()

        fun add(knownApp: KnownFleetApp?, manifestApp: FleetApp?) {
            val packages = (knownApp?.packages.orEmpty() + listOfNotNull(manifestApp?.applicationId)).distinct()
            val key = packages.joinToString(",")
            if (key in seen) return
            seen += key
            val installedPkg = packages.firstOrNull { it in installed }
            val pkg = installedPkg ?: manifestApp?.applicationId ?: packages.first()
            val live = installedPkg?.let { installed[it] }
            val plan =
                if (manifestApp != null) {
                    FleetPlanner.plan(
                        FleetManifest(1, manifest?.generatedAt.orEmpty(), listOf(manifestApp)),
                        installed,
                        extraPins,
                    ).first()
                } else {
                    null
                }
            val install =
                when {
                    plan != null ->
                        when (plan.state) {
                            FleetRowState.NOT_INSTALLED -> DashboardInstall.NOT_INSTALLED
                            FleetRowState.UP_TO_DATE -> DashboardInstall.UP_TO_DATE
                            FleetRowState.UPDATE_AVAILABLE -> DashboardInstall.UPDATE_AVAILABLE
                            FleetRowState.DOWNGRADE -> DashboardInstall.DOWNGRADE
                            FleetRowState.UNTRUSTED -> DashboardInstall.UNTRUSTED
                        }
                    live == null -> DashboardInstall.NOT_INSTALLED
                    else -> DashboardInstall.INSTALLED
                }
            val runningState =
                when {
                    running == null -> RunningKnowledge.UNKNOWN
                    live == null -> RunningKnowledge.NOT_RUNNING
                    packages.any { it in running } -> RunningKnowledge.RUNNING
                    else -> RunningKnowledge.NOT_RUNNING
                }
            val kind = knownApp?.kind ?: KnownFleetApps.kindForPackage(pkg)
            val domain = kind.trayDomain
            val tile = domain?.let { d -> tiles.firstOrNull { it.domain == d } }
            rows +=
                FleetDashboardRow(
                    id = knownApp?.id ?: manifestApp?.id ?: pkg,
                    name = knownApp?.name ?: manifestApp?.name ?: pkg,
                    packageName = pkg,
                    packages = packages,
                    kind = kind,
                    install = install,
                    installedVersionName = live?.second,
                    installedVersionCode = live?.first,
                    manifestVersionName = manifestApp?.versionName ?: plan?.app?.versionName,
                    manifestVersionCode = manifestApp?.versionCode ?: plan?.app?.versionCode,
                    running = runningState,
                    trayState = tile?.state,
                    trayDetail = tile?.detail,
                    trayFreshness = tile?.freshness,
                    canLaunch = live != null,
                    canInstallOrUpdate =
                        plan != null &&
                            plan.trusted &&
                            plan.state != FleetRowState.UP_TO_DATE,
                )
        }

        known.forEach { add(it, it.packages.firstNotNullOfOrNull { p -> manifestByPkg[p] }) }
        manifest?.apps?.forEach { app ->
            if (known.none { app.applicationId in it.packages }) {
                add(null, app)
            }
        }

        return FleetDashboard(
            pulledAtMs = tray?.pulledAtMs ?: nowMs,
            trayFreshness = trayFreshness,
            trayNote = trayNote,
            tiles = tiles,
            rows = rows,
            hasManifest = manifest != null,
            usageAccess = running != null,
        )
    }

    fun installLabel(install: DashboardInstall): String =
        when (install) {
            DashboardInstall.NOT_INSTALLED -> "Not installed"
            DashboardInstall.INSTALLED -> "Installed"
            DashboardInstall.UP_TO_DATE -> "Up to date"
            DashboardInstall.UPDATE_AVAILABLE -> "Update available"
            DashboardInstall.DOWNGRADE -> "Installed is newer"
            DashboardInstall.UNTRUSTED -> "Untrusted"
        }

    fun runningLabel(running: RunningKnowledge, usageAccess: Boolean): String =
        when {
            !usageAccess || running == RunningKnowledge.UNKNOWN -> "Running: needs usage access"
            running == RunningKnowledge.RUNNING -> "Running"
            else -> "Not running"
        }

    fun tileLabel(tile: TrayTile): String {
        val name =
            when (tile.domain) {
                StatusTrayDomain.MESH -> "Mesh"
                StatusTrayDomain.VPN -> "VPN"
                StatusTrayDomain.BROWSER -> "Browser"
                StatusTrayDomain.SMS -> "Messaging"
                else -> tile.id
            }
        val state =
            when (tile.state) {
                StatusTrayState.OK -> "up"
                StatusTrayState.DEGRADED -> "degraded"
                StatusTrayState.DOWN -> "down"
                StatusTrayState.OFF -> "off"
                StatusTrayState.UNKNOWN -> "no data"
            }
        val freshness =
            when (tile.freshness) {
                Freshness.FRESH -> null
                Freshness.STALE -> "stale"
                Freshness.EXPIRED -> "drop"
            }
        return listOfNotNull(name, state, freshness).joinToString(" · ")
    }
}
