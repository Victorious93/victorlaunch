package com.victorious.launch.core.status

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

enum class StatusTrayDomain {
    MESH,
    VPN,
    BROWSER,
    SMS,
    NETWORK,
    NODE_HEALTH,
    OTHER,
    ;

    val wire: String
        get() =
            when (this) {
                MESH -> "mesh"
                VPN -> "vpn"
                BROWSER -> "browser"
                SMS -> "sms"
                NETWORK -> "network"
                NODE_HEALTH -> "node_health"
                OTHER -> "other"
            }

    companion object {
        fun fromWire(raw: String): StatusTrayDomain =
            when (raw) {
                "mesh" -> MESH
                "vpn" -> VPN
                "browser" -> BROWSER
                "sms" -> SMS
                "network" -> NETWORK
                "node_health" -> NODE_HEALTH
                "other" -> OTHER
                else -> OTHER
            }
    }
}

enum class StatusTrayState {
    OK,
    DEGRADED,
    DOWN,
    UNKNOWN,
    OFF,
    ;

    val wire: String
        get() =
            when (this) {
                OK -> "ok"
                DEGRADED -> "degraded"
                DOWN -> "down"
                UNKNOWN -> "unknown"
                OFF -> "off"
            }

    companion object {
        fun fromWire(raw: String): StatusTrayState =
            when (raw) {
                "ok" -> OK
                "degraded" -> DEGRADED
                "down" -> DOWN
                "unknown" -> UNKNOWN
                "off" -> OFF
                else -> UNKNOWN
            }
    }
}

enum class Freshness {
    FRESH,
    STALE,
    EXPIRED,
}

data class StatusTrayDomainStatus(
    val name: StatusTrayDomain,
    val state: StatusTrayState,
    val detail: String? = null,
    val rawName: String? = null,
)

data class NodeStatusSnapshot(
    val nodeId: String,
    val packageName: String?,
    val connectionClass: String,
    val updatedAtMs: Long,
    val domains: List<StatusTrayDomainStatus>,
)

object StatusFreshness {
    const val FRESH_MS = 30_000L
    const val STALE_MS = 300_000L
    const val PULL_INTERVAL_MS = 15_000L

    fun of(updatedAtMs: Long, nowMs: Long): Freshness {
        val age = nowMs - updatedAtMs
        return when {
            age <= FRESH_MS -> Freshness.FRESH
            age <= STALE_MS -> Freshness.STALE
            else -> Freshness.EXPIRED
        }
    }
}

data class TrayTile(
    val id: String,
    val domain: StatusTrayDomain,
    val state: StatusTrayState,
    val detail: String?,
    val freshness: Freshness,
    val nodeId: String?,
)

data class PublishedTray(
    val snapshots: List<NodeStatusSnapshot>,
    val tiles: List<TrayTile>,
    val pairedNodeCount: Int,
    val pulledAtMs: Long,
)

/**
 * Controller-aggregate consumer. Drops rows older than 5 minutes. Never
 * invents domain state — missing domains surface as UNKNOWN with no detail.
 */
class StatusTrayAggregator(
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    fun publish(snapshots: List<NodeStatusSnapshot>, nowMs: Long = clock()): PublishedTray {
        val kept =
            snapshots.filter { StatusFreshness.of(it.updatedAtMs, nowMs) != Freshness.EXPIRED }
        val tiles =
            listOf(
                tile("mesh", StatusTrayDomain.MESH, kept, nowMs),
                tile("vpn", StatusTrayDomain.VPN, kept, nowMs),
                tile("browser", StatusTrayDomain.BROWSER, kept, nowMs),
                tile("sms", StatusTrayDomain.SMS, kept, nowMs),
                tile("network", StatusTrayDomain.NETWORK, kept, nowMs),
            )
        return PublishedTray(
            snapshots = kept,
            tiles = tiles,
            pairedNodeCount = kept.size,
            pulledAtMs = nowMs,
        )
    }

    private fun tile(
        id: String,
        domain: StatusTrayDomain,
        snapshots: List<NodeStatusSnapshot>,
        nowMs: Long,
    ): TrayTile {
        val hit =
            snapshots
                .mapNotNull { snap ->
                    val d = snap.domains.firstOrNull { it.name == domain } ?: return@mapNotNull null
                    Triple(snap, d, StatusFreshness.of(snap.updatedAtMs, nowMs))
                }
                .maxByOrNull { it.first.updatedAtMs }
        if (hit == null) {
            return TrayTile(
                id = id,
                domain = domain,
                state = StatusTrayState.UNKNOWN,
                detail = null,
                freshness = Freshness.EXPIRED,
                nodeId = null,
            )
        }
        val (snap, domainStatus, freshness) = hit
        return TrayTile(
            id = id,
            domain = domain,
            state = domainStatus.state,
            detail = domainStatus.detail,
            freshness = freshness,
            nodeId = snap.nodeId,
        )
    }
}

object NodeStatusSnapshotCodec {
    private val FORBIDDEN =
        Regex(
            """(?i)(?<![A-Za-z0-9_])(password|api[_-]?key|api[_-]?token|token|secret|code[_-]?word|whitelist|signing[_-]?key|private[_-]?key)(?![A-Za-z0-9_])""",
        )

    fun encode(snapshot: NodeStatusSnapshot): JsonObject {
        if (snapshot.nodeId.isBlank()) {
            throw InvalidStatusException("nodeId must be non-blank")
        }
        snapshot.domains.forEach { guardDetail(it.detail) }
        return buildJsonObject {
            put("nodeId", snapshot.nodeId)
            if (snapshot.packageName == null) put("packageName", JsonNull)
            else put("packageName", snapshot.packageName)
            put("connectionClass", snapshot.connectionClass)
            put("updatedAtMs", snapshot.updatedAtMs)
            putJsonArray("domains") {
                snapshot.domains.forEach { add(encodeDomain(it)) }
            }
        }
    }

    fun decode(obj: JsonObject): NodeStatusSnapshot {
        val nodeId = obj["nodeId"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (nodeId.isBlank()) throw InvalidStatusException("nodeId must be non-blank")
        val packageName = obj["packageName"]?.jsonPrimitive?.contentOrNull
        val connectionClass =
            obj["connectionClass"]?.jsonPrimitive?.contentOrNull
                ?: throw InvalidStatusException("connectionClass is required")
        val updatedAtMs =
            obj["updatedAtMs"]?.jsonPrimitive?.long
                ?: throw InvalidStatusException("updatedAtMs is required")
        val domainsEl = obj["domains"]
        val domains =
            if (domainsEl is JsonArray) {
                domainsEl.map { decodeDomain(it.jsonObject) }
            } else {
                emptyList()
            }
        return NodeStatusSnapshot(
            nodeId = nodeId,
            packageName = packageName,
            connectionClass = connectionClass,
            updatedAtMs = updatedAtMs,
            domains = domains,
        )
    }

    fun decodeTray(result: JsonObject): List<NodeStatusSnapshot> {
        val snapshotsEl = result["snapshots"]
        if (snapshotsEl is JsonArray) {
            return snapshotsEl.map { decode(it.jsonObject) }
        }
        if (result.containsKey("nodeId")) {
            return listOf(decode(result))
        }
        return emptyList()
    }

    private fun encodeDomain(domain: StatusTrayDomainStatus): JsonObject =
        buildJsonObject {
            put("name", domain.rawName ?: domain.name.wire)
            put("state", domain.state.wire)
            val detail = domain.detail
            if (!detail.isNullOrBlank()) put("detail", detail.take(200))
        }

    private fun decodeDomain(obj: JsonObject): StatusTrayDomainStatus {
        val raw = obj["name"]?.jsonPrimitive?.contentOrNull ?: "other"
        val state = StatusTrayState.fromWire(obj["state"]?.jsonPrimitive?.contentOrNull ?: "unknown")
        val detail = obj["detail"]?.jsonPrimitive?.contentOrNull
        guardDetail(detail)
        return StatusTrayDomainStatus(
            name = StatusTrayDomain.fromWire(raw),
            state = state,
            detail = detail,
            rawName = if (StatusTrayDomain.fromWire(raw) == StatusTrayDomain.OTHER && raw != "other") raw else null,
        )
    }

    private fun guardDetail(detail: String?) {
        if (detail != null && FORBIDDEN.containsMatchIn(detail)) {
            throw InvalidStatusException("forbidden content in status detail")
        }
        if (detail != null && detail.length > 200) {
            throw InvalidStatusException("detail longer than 200 characters")
        }
    }
}

class InvalidStatusException(message: String) : Exception(message)
