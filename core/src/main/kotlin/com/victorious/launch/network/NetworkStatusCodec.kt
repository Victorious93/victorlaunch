package com.victorious.launch.network

import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.core.status.StatusTrayState
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

object NetworkStatusCodec {
    fun encode(
        enabled: Boolean,
        sample: NetworkSample?,
        alerts: List<NetworkAlert>,
        permissionsReady: Boolean,
        lockActive: Boolean,
        limits: String = NetworkLimits.TEXT,
    ): JsonObject =
        buildJsonObject {
            put("enabled", enabled)
            put("permissionsReady", permissionsReady)
            put("lockActive", lockActive)
            put("limits", limits.take(200))
            if (sample == null) {
                put("rat", "none")
                put("generation", 0)
            } else {
                put("rat", sample.rat.wire)
                put("generation", sample.rat.generation)
                sample.rsrp?.let { put("rsrp", it) }
                sample.rsrq?.let { put("rsrq", it) }
                sample.sinr?.let { put("sinr", it) }
                sample.cellId?.let { put("cellId", it.take(64)) }
                put("registered", sample.registered)
                put("dataState", sample.dataState.wire)
                put("updatedAtMs", sample.atMs)
            }
            putJsonArray("alerts") {
                alerts.takeLast(10).forEach { alert ->
                    add(
                        buildJsonObject {
                            put("kind", alert.kind.wire)
                            put("atMs", alert.atMs)
                            put("detail", alert.detail.take(200))
                            alert.fromRat?.let { put("fromRat", it.wire) }
                            alert.toRat?.let { put("toRat", it.wire) }
                        },
                    )
                }
            }
        }

    fun domainStatus(
        enabled: Boolean,
        permissionsReady: Boolean,
        sample: NetworkSample?,
        latestAlert: NetworkAlert?,
    ): StatusTrayDomainStatus {
        if (!enabled) {
            return StatusTrayDomainStatus(StatusTrayDomain.NETWORK, StatusTrayState.OFF, "monitor off")
        }
        if (!permissionsReady) {
            return StatusTrayDomainStatus(
                StatusTrayDomain.NETWORK,
                StatusTrayState.UNKNOWN,
                "phone/location permission missing",
            )
        }
        val state =
            when (latestAlert?.kind) {
                NetworkAlertKind.UNEXPECTED_2G -> StatusTrayState.DOWN
                NetworkAlertKind.DOWNGRADE,
                NetworkAlertKind.SIGNAL_DROP,
                NetworkAlertKind.SUDDEN_CELL_CHANGE,
                -> StatusTrayState.DEGRADED
                null -> if (sample?.registered == true) StatusTrayState.OK else StatusTrayState.UNKNOWN
            }
        val detail =
            buildString {
                if (sample != null) {
                    append(sample.rat.wire)
                    sample.rsrp?.let { append(" ").append(it).append("dBm") }
                }
                latestAlert?.let {
                    if (isNotEmpty()) append(" · ")
                    append(it.kind.wire)
                }
            }.ifBlank { null }?.take(200)
        return StatusTrayDomainStatus(StatusTrayDomain.NETWORK, state, detail)
    }

    fun disabledJson(): JsonObject =
        encode(
            enabled = false,
            sample = null,
            alerts = emptyList(),
            permissionsReady = false,
            lockActive = false,
        )
}