package com.victorious.launch.status

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.core.status.NodeStatusSnapshotCodec
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.security.SigningCerts
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.coroutines.resume

/**
 * Pulls `node.status.get` from the **controller only**. Never binds other
 * fleet nodes for status.
 */
class ControllerClient(
    private val context: Context,
    private val settings: AppSettings,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun controllerInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(Protocol.CONTROLLER_PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun controllerCertMatches(): Boolean {
        val pairing = settings.pairedController() ?: return false
        if (pairing.packageName != Protocol.CONTROLLER_PACKAGE) return false
        val certs = SigningCerts.forPackage(context.packageManager, Protocol.CONTROLLER_PACKAGE)
        return certs.any { it.equals(pairing.certSha256, ignoreCase = true) }
    }

    suspend fun pullSnapshots(): PullResult {
        if (!controllerInstalled()) return PullResult.MissingController
        val pairing = settings.pairedController() ?: return PullResult.NotPaired
        if (pairing.packageName != Protocol.CONTROLLER_PACKAGE) return PullResult.NotPaired
        if (!controllerCertMatches()) return PullResult.Unauthorized
        return bindAndRequest()
    }

    private suspend fun bindAndRequest(): PullResult =
        suspendCancellableCoroutine { cont ->
            val reply =
                Messenger(
                    object : Handler(Looper.getMainLooper()) {
                        override fun handleMessage(msg: Message) {
                            if (!cont.isActive) return
                            val raw = msg.data?.getString(Protocol.KEY_RESPONSE)
                            cont.resume(decode(raw))
                        }
                    },
                )
            val conn =
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        try {
                            val outgoing = Messenger(service)
                            val msg = Message.obtain(null, Protocol.MSG_REQUEST)
                            msg.replyTo = reply
                            msg.data = Bundle().apply { putString(Protocol.KEY_REQUEST, requestJson()) }
                            outgoing.send(msg)
                        } catch (e: Exception) {
                            if (cont.isActive) cont.resume(PullResult.Unreachable(e.message ?: "send failed"))
                        }
                    }

                    override fun onServiceDisconnected(name: ComponentName?) = Unit
                }
            val intent = Intent(Protocol.CONTROLLER_BRIDGE_ACTION).setPackage(Protocol.CONTROLLER_PACKAGE)
            val bound =
                try {
                    context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(PullResult.Unreachable(e.message ?: "bind failed"))
                    false
                }
            if (!bound) {
                if (cont.isActive) cont.resume(PullResult.Unreachable("controller bridge not exported"))
                return@suspendCancellableCoroutine
            }
            cont.invokeOnCancellation {
                try {
                    context.unbindService(conn)
                } catch (_: Exception) {
                }
            }
        }

    private fun requestJson(): String {
        val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val nonce = nonceBytes.joinToString("") { "%02x".format(it) }
        val requestId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        return """
            {
              "protocol_major": ${Protocol.PROTOCOL_MAJOR},
              "request_id": "$requestId",
              "target_node_id": "controller",
              "operation": "node.status.get",
              "timeout_ms": 120000,
              "parameters": {
                "targetNodeRef": "controller",
                "actionRef": "node.status.get",
                "args": {},
                "nonce": "$nonce",
                "timestampMs": $now,
                "ttlMs": ${Protocol.DEFAULT_TTL_MS},
                "destructive": false
              }
            }
            """.trimIndent()
    }

    private fun decode(raw: String?): PullResult {
        if (raw.isNullOrBlank()) return PullResult.Unreachable("empty response")
        return try {
            val obj = json.parseToJsonElement(raw).jsonObject
            val ok = obj["ok"]?.toString()?.contains("true") == true
            if (!ok) {
                val err = obj["error"]?.jsonObject
                val cat = err?.get("category")?.toString()?.trim('"') ?: "internal"
                return PullResult.RemoteError(cat)
            }
            val result = obj["result"] as? JsonObject ?: JsonObject(emptyMap())
            PullResult.Ok(NodeStatusSnapshotCodec.decodeTray(result))
        } catch (e: Exception) {
            PullResult.Unreachable(e.message ?: "decode failed")
        }
    }
}

sealed class PullResult {
    data class Ok(val snapshots: List<NodeStatusSnapshot>) : PullResult()

    data object MissingController : PullResult()

    data object NotPaired : PullResult()

    data object Unauthorized : PullResult()

    data class Unreachable(val reason: String) : PullResult()

    data class RemoteError(val category: String) : PullResult()
}
