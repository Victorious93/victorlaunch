package com.victorious.launch.core.protocol

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Frozen EXTERNAL_NODE envelope v1.1 (flat fields).
 *
 * Wire names are camelCase. `requestId` is also accepted as the outer
 * BridgeRequest `request_id` when the JSON is wrapped.
 */
data class Envelope(
    val op: String,
    val requestId: String,
    val targetNodeRef: String,
    val actionRef: String = "",
    val params: JsonObject = JsonObject(emptyMap()),
    val nonce: String,
    val timestampMs: Long,
    val ttlMs: Long = Protocol.DEFAULT_TTL_MS,
    val destructive: Boolean = false,
    val trigger: JsonObject? = null,
    val dryRun: Boolean = false,
)

object Protocol {
    const val PROTOCOL_MAJOR = 1
    const val PROTOCOL_MINOR = 1
    const val ACTION_BRIDGE = "com.victorious.launch.action.BRIDGE"
    const val MSG_REQUEST = 1
    const val MSG_RESPONSE = 2
    const val KEY_REQUEST = "request"
    const val KEY_RESPONSE = "response"
    const val DEFAULT_TTL_MS = 120_000L
    const val MAX_TTL_MS = 300_000L
    const val MAX_FUTURE_SKEW_MS = 120_000L
    const val NONCE_RETAIN_MS = 10L * 60L * 1000L
    const val NODE_ID_PREFIX = "victorlaunch-"
    const val PACKAGE_NAME = "com.victorious.launch"
    const val CONTROLLER_PACKAGE = "ai.droidcommand.app"
    const val CONTROLLER_BRIDGE_ACTION = "ai.droidcommand.app.action.BRIDGE"
    const val CONNECTION_EXTERNAL_NODE = "EXTERNAL_NODE"

    private val NONCE_HEX = Regex("^[0-9a-fA-F]{32}$")
    private val NONCE_UUID =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val NODE_ID_REGEX = Regex("^" + Regex.escape(NODE_ID_PREFIX) + "[0-9a-f]{32}$")

    fun isValidNonce(nonce: String): Boolean = NONCE_HEX.matches(nonce) || NONCE_UUID.matches(nonce)

    fun normalizeNonce(nonce: String): String = nonce.replace("-", "").lowercase()

    fun isValidNodeId(id: String): Boolean = NODE_ID_REGEX.matches(id) || id == "controller"

    fun clampTtl(ttlMs: Long): Long = ttlMs.coerceIn(1L, MAX_TTL_MS)
}

data class BridgeError(val category: String, val message: String)

data class BridgeResponse(
    val ok: Boolean,
    val requestId: String? = null,
    val result: JsonObject? = null,
    val error: BridgeError? = null,
) {
    fun toJson(): JsonObject =
        buildJsonObject {
            put("protocol_major", Protocol.PROTOCOL_MAJOR)
            put("protocol_minor", Protocol.PROTOCOL_MINOR)
            if (requestId != null) put("request_id", requestId)
            put("ok", ok)
            if (result != null) put("result", result)
            if (error != null) {
                put(
                    "error",
                    buildJsonObject {
                        put("category", error.category)
                        put("message", error.message)
                    },
                )
            }
        }

    companion object {
        fun fail(requestId: String?, category: String, message: String): BridgeResponse =
            BridgeResponse(
                ok = false,
                requestId = requestId,
                error = BridgeError(category, message),
            )

        fun ok(requestId: String?, result: JsonObject): BridgeResponse =
            BridgeResponse(ok = true, requestId = requestId, result = result)
    }
}

object EnvelopeCodec {
    fun parse(root: JsonObject): Envelope {
        val wrapped = root["parameters"]?.jsonObject
        val body = wrapped ?: root
        val op =
            string(root, "op")
                ?: string(root, "operation")
                ?: string(body, "op")
                ?: string(body, "actionRef")
                ?: ""
        val requestId =
            string(root, "requestId")
                ?: string(root, "request_id")
                ?: string(body, "requestId")
                ?: ""
        val target =
            string(body, "targetNodeRef")
                ?: string(root, "targetNodeRef")
                ?: string(root, "target_node_id")
                ?: ""
        val actionRef = string(body, "actionRef").orEmpty()
        val params =
            body["params"]?.jsonObject
                ?: body["args"]?.jsonObject
                ?: JsonObject(emptyMap())
        val nonce = string(body, "nonce") ?: string(root, "nonce") ?: ""
        val timestampMs =
            long(body, "timestampMs")
                ?: long(root, "timestampMs")
                ?: 0L
        val ttlMs =
            long(body, "ttlMs")
                ?: long(root, "ttlMs")
                ?: Protocol.DEFAULT_TTL_MS
        val destructive =
            bool(body, "destructive")
                ?: bool(root, "destructive")
                ?: false
        val trigger = body["trigger"]?.jsonObject ?: root["trigger"]?.jsonObject
        val dryRun =
            bool(body, "dryRun")
                ?: bool(params, "dryRun")
                ?: string(params, "dryRun")?.equals("true", ignoreCase = true)
                ?: false
        return Envelope(
            op = op,
            requestId = requestId,
            targetNodeRef = target,
            actionRef = actionRef,
            params = params,
            nonce = nonce,
            timestampMs = timestampMs,
            ttlMs = ttlMs,
            destructive = destructive,
            trigger = trigger,
            dryRun = dryRun,
        )
    }

    private fun string(obj: JsonObject, key: String): String? =
        obj[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun long(obj: JsonObject, key: String): Long? = obj[key]?.jsonPrimitive?.longOrNull

    private fun bool(obj: JsonObject, key: String): Boolean? = obj[key]?.jsonPrimitive?.booleanOrNull
}
