package com.victorious.launch.network

/**
 * On-device radio monitor. Pure data + heuristics — no Android types,
 * no network I/O. Unexpected 2G is a **heuristic** for a fake BTS / IMSI
 * catcher, not a detection claim.
 */
enum class NetworkRat {
    NONE,
    GSM,
    GPRS,
    EDGE,
    CDMA,
    UMTS,
    HSPA,
    LTE,
    LTE_CA,
    NR_NSA,
    NR,
    UNKNOWN,
    ;

    val wire: String
        get() =
            when (this) {
                NONE -> "none"
                GSM -> "gsm"
                GPRS -> "gprs"
                EDGE -> "edge"
                CDMA -> "cdma"
                UMTS -> "umts"
                HSPA -> "hspa"
                LTE -> "lte"
                LTE_CA -> "lte_ca"
                NR_NSA -> "nr_nsa"
                NR -> "nr"
                UNKNOWN -> "unknown"
            }

    /** 0 none, 2 = 2G, 3 = 3G, 4 = LTE, 5 = NR. */
    val generation: Int
        get() =
            when (this) {
                NONE -> 0
                GSM, GPRS, EDGE, CDMA -> 2
                UMTS, HSPA -> 3
                LTE, LTE_CA -> 4
                NR_NSA, NR -> 5
                UNKNOWN -> 0
            }

    val isTwoG: Boolean
        get() = generation == 2

    companion object {
        fun fromWire(raw: String): NetworkRat = entries.firstOrNull { it.wire == raw } ?: UNKNOWN

        /**
         * [android.telephony.TelephonyManager] NETWORK_TYPE_* integers
         * (stable AOSP values). Unknown numbers map to [UNKNOWN].
         */
        fun fromTelephonyNetworkType(type: Int): NetworkRat =
            when (type) {
                0 -> NONE
                1 -> GPRS
                2 -> EDGE
                3 -> UMTS
                4, 7 -> CDMA
                8, 9, 10, 15 -> HSPA
                13 -> LTE
                16 -> GSM
                19 -> LTE_CA
                20 -> NR
                else -> UNKNOWN
            }

        /**
         * [android.telephony.TelephonyDisplayInfo] OVERRIDE_NETWORK_TYPE_*.
         */
        fun fromDisplayOverride(overrideType: Int, fallback: NetworkRat): NetworkRat =
            when (overrideType) {
                1, 2 -> LTE_CA
                3, 4 -> NR_NSA
                5 -> NR
                else -> fallback
            }
    }
}

enum class NetworkAlertKind {
    DOWNGRADE,
    UNEXPECTED_2G,
    SUDDEN_CELL_CHANGE,
    SIGNAL_DROP,
    ;

    val wire: String
        get() =
            when (this) {
                DOWNGRADE -> "downgrade"
                UNEXPECTED_2G -> "unexpected_2g"
                SUDDEN_CELL_CHANGE -> "sudden_cell_change"
                SIGNAL_DROP -> "signal_drop"
            }

    companion object {
        fun fromWire(raw: String): NetworkAlertKind? = entries.firstOrNull { it.wire == raw }
    }
}

enum class NetworkDataState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    SUSPENDED,
    UNKNOWN,
    ;

    val wire: String
        get() =
            when (this) {
                DISCONNECTED -> "disconnected"
                CONNECTING -> "connecting"
                CONNECTED -> "connected"
                SUSPENDED -> "suspended"
                UNKNOWN -> "unknown"
            }

    companion object {
        fun fromTelephony(state: Int): NetworkDataState =
            when (state) {
                0 -> DISCONNECTED
                1 -> CONNECTING
                2 -> CONNECTED
                3 -> SUSPENDED
                else -> UNKNOWN
            }
    }
}

data class NetworkSample(
    val atMs: Long,
    val rat: NetworkRat,
    val rsrp: Int? = null,
    val rsrq: Int? = null,
    val sinr: Int? = null,
    val cellId: String? = null,
    val registered: Boolean = false,
    val dataState: NetworkDataState = NetworkDataState.UNKNOWN,
) {
    fun validate(): String? {
        if (atMs < 0) return "invalid timestamp"
        if (cellId != null && !CELL.matches(cellId)) return "invalid cellId"
        return null
    }

    companion object {
        val CELL = Regex("^[A-Za-z0-9._:-]{1,64}$")
    }
}

data class NetworkAlert(
    val kind: NetworkAlertKind,
    val atMs: Long,
    val detail: String,
    val fromRat: NetworkRat? = null,
    val toRat: NetworkRat? = null,
    val cellId: String? = null,
)

object NetworkGate {
    fun canMonitor(enabled: Boolean, phonePermission: Boolean, locationPermission: Boolean): Boolean =
        enabled && phonePermission && locationPermission

    fun canLock(enabled: Boolean, lockEnabled: Boolean, privilegedReady: Boolean): Boolean =
        enabled && lockEnabled && privilegedReady
}

object NetworkThresholds {
    const val SIGNAL_DROP_DB = 12
    const val STRONG_RSRP_DBM = -105
    const val SUDDEN_CELL_WINDOW_MS = 30_000L
    const val HISTORY_CAP = 40
    const val ALERT_CAP = 50
    const val RECOVER_COOLDOWN_MS = 60_000L
    const val RECOVER_WINDOW_MS = 600_000L
    const val RECOVER_MAX_IN_WINDOW = 3
}

object NetworkAutoRecover {
    fun shouldRecover(
        enabled: Boolean,
        autoRecover: Boolean,
        privilegedReady: Boolean,
        nowMs: Long,
        lastRecoverMs: Long?,
        recoverAtMs: List<Long>,
        latestAlert: NetworkAlert?,
    ): Boolean {
        if (!enabled || !autoRecover || !privilegedReady) return false
        if (latestAlert == null) return false
        when (latestAlert.kind) {
            NetworkAlertKind.UNEXPECTED_2G,
            NetworkAlertKind.SIGNAL_DROP,
            NetworkAlertKind.DOWNGRADE,
            -> Unit
            NetworkAlertKind.SUDDEN_CELL_CHANGE -> return false
        }
        if (lastRecoverMs != null && nowMs - lastRecoverMs < NetworkThresholds.RECOVER_COOLDOWN_MS) {
            return false
        }
        val recent = recoverAtMs.count { nowMs - it <= NetworkThresholds.RECOVER_WINDOW_MS }
        return recent < NetworkThresholds.RECOVER_MAX_IN_WINDOW
    }
}

class NetworkMonitorEngine {
    private val samples = ArrayDeque<NetworkSample>()
    private val alerts = ArrayDeque<NetworkAlert>()

    @Synchronized
    fun reset() {
        samples.clear()
        alerts.clear()
    }

    @Synchronized
    fun ingest(enabled: Boolean, sample: NetworkSample): List<NetworkAlert> {
        if (!enabled) return emptyList()
        sample.validate()?.let { throw IllegalArgumentException(it) }
        val previous = samples.lastOrNull()
        samples.addLast(sample)
        while (samples.size > NetworkThresholds.HISTORY_CAP) samples.removeFirst()
        val fresh = evaluate(previous, sample)
        fresh.forEach { alert ->
            alerts.addLast(alert)
            while (alerts.size > NetworkThresholds.ALERT_CAP) alerts.removeFirst()
        }
        return fresh
    }

    @Synchronized
    fun history(): List<NetworkSample> = samples.toList()

    @Synchronized
    fun alerts(): List<NetworkAlert> = alerts.toList()

    @Synchronized
    fun latest(): NetworkSample? = samples.lastOrNull()

    @Synchronized
    fun latestAlert(): NetworkAlert? = alerts.lastOrNull()

    private fun evaluate(previous: NetworkSample?, current: NetworkSample): List<NetworkAlert> {
        if (previous == null) return emptyList()
        val out = mutableListOf<NetworkAlert>()
        val fromGen = previous.rat.generation
        val toGen = current.rat.generation
        if (toGen == 2 && fromGen >= 3) {
            out.add(
                NetworkAlert(
                    kind = NetworkAlertKind.UNEXPECTED_2G,
                    atMs = current.atMs,
                    detail = "Dropped ${previous.rat.wire} → ${current.rat.wire}. Unexpected 2G/GSM can indicate a fake base station.",
                    fromRat = previous.rat,
                    toRat = current.rat,
                    cellId = current.cellId,
                ),
            )
        } else if (toGen in 1..5 && fromGen in 1..5 && toGen < fromGen) {
            out.add(
                NetworkAlert(
                    kind = NetworkAlertKind.DOWNGRADE,
                    atMs = current.atMs,
                    detail = "Radio downgrade ${previous.rat.wire} → ${current.rat.wire}",
                    fromRat = previous.rat,
                    toRat = current.rat,
                    cellId = current.cellId,
                ),
            )
        }
        val prevCell = previous.cellId
        val curCell = current.cellId
        if (
            !prevCell.isNullOrBlank() &&
            !curCell.isNullOrBlank() &&
            prevCell != curCell &&
            current.atMs - previous.atMs <= NetworkThresholds.SUDDEN_CELL_WINDOW_MS &&
            (previous.rsrp == null || previous.rsrp >= NetworkThresholds.STRONG_RSRP_DBM)
        ) {
            out.add(
                NetworkAlert(
                    kind = NetworkAlertKind.SUDDEN_CELL_CHANGE,
                    atMs = current.atMs,
                    detail = "Serving cell $prevCell → $curCell",
                    fromRat = previous.rat,
                    toRat = current.rat,
                    cellId = curCell,
                ),
            )
        }
        val prevRsrp = previous.rsrp
        val curRsrp = current.rsrp
        if (prevRsrp != null && curRsrp != null && prevRsrp - curRsrp >= NetworkThresholds.SIGNAL_DROP_DB) {
            out.add(
                NetworkAlert(
                    kind = NetworkAlertKind.SIGNAL_DROP,
                    atMs = current.atMs,
                    detail = "RSRP $prevRsrp → $curRsrp dBm",
                    fromRat = previous.rat,
                    toRat = current.rat,
                    cellId = current.cellId,
                ),
            )
        }
        return out
    }
}

object NetworkLimits {
    const val TEXT =
        "Not universal. TelephonyCallback needs API 31+ (PhoneStateListener below that). " +
            "Cell info needs fine location and the location toggle. " +
            "setAllowedNetworkTypes / Allow 2G need shell, root, or a carrier privilege — a normal app cannot lock the RAT. " +
            "preferred_network_mode integers differ by OEM. Cell/band lock is modem-specific and is not written here. " +
            "Radio toggle (airplane) may drop calls. Unexpected 2G is a heuristic, not IMSI-catcher proof."
}
