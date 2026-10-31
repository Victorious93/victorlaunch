package com.victorious.launch.core.bridge

import com.victorious.launch.core.backup.BackupConflictPolicy
import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.ForbiddenBackupContentException
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.protocol.BridgeResponse
import com.victorious.launch.core.protocol.Envelope
import com.victorious.launch.core.protocol.EnvelopeCodec
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.OpKind
import com.victorious.launch.core.protocol.Ops
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.RemoteActionPolicy
import com.victorious.launch.core.protocol.ReplayCheck
import com.victorious.launch.core.protocol.ReplayGuard
import com.victorious.launch.core.status.NodeStatusSnapshot
import com.victorious.launch.core.status.NodeStatusSnapshotCodec
import com.victorious.launch.core.status.StatusTrayDomain
import com.victorious.launch.core.status.StatusTrayDomainStatus
import com.victorious.launch.core.status.StatusTrayState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

interface LauncherHost {
    fun nodeId(): String

    fun nowMs(): Long

    fun ownHealth(): NodeStatusSnapshot

    fun preferences(): LauncherPreferences

    fun applyPreferences(prefs: LauncherPreferences)

    fun installedPackages(): List<InstalledApp>

    fun layout(): JsonObject

    /** Apply an already-validated patch; return the new layout (same shape as [layout]). */
    fun setLayout(patch: LayoutPatch): JsonObject

    fun fleetPairs(): List<PairedNode>

    fun runningPackages(): RunningResult

    fun launchApp(packageName: String): Boolean

    fun openPanel(panel: String): Boolean

    fun networkStatus(): JsonObject
}

data class InstalledApp(val packageName: String, val label: String, val userSerial: Long)

data class PairedNode(val packageName: String, val certSha256: String)

sealed class RunningResult {
    data class Ok(val packages: List<String>) : RunningResult()

    data class Unavailable(val reason: String) : RunningResult()
}

class BridgeDispatcher(
    private val host: LauncherHost,
    private val replayGuard: ReplayGuard = ReplayGuard(clock = { host.nowMs() }),
    val audit: ActionAuditLog = ActionAuditLog(),
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun handle(
        raw: String?,
        sourceNodeRef: String,
        callerPaired: Boolean,
        externalControlEnabled: Boolean,
        confirmed: Boolean = false,
        callerIsPinnedController: Boolean = false,
        controllerFullControlEnabled: Boolean = true,
    ): BridgeResponse =
        when (
            val o =
                evaluate(
                    raw, sourceNodeRef, callerPaired, externalControlEnabled, confirmed,
                    callerIsPinnedController, controllerFullControlEnabled,
                )
        ) {
            is Outcome.Done -> o.response
            is Outcome.NeedsConfirm -> o.pending.denialResponse()
        }

    /** Result of [evaluate]: either a final response or an op parked for on-device confirmation. */
    sealed class Outcome {
        data class Done(val response: BridgeResponse) : Outcome()

        data class NeedsConfirm(val pending: PendingConfirm) : Outcome()
    }

    /**
     * An authorized, replay-checked request that only lacks the user's on-device OK.
     * The nonce is already consumed, so it can only be completed once via [complete].
     */
    class PendingConfirm internal constructor(
        val envelope: Envelope,
        val sourceNodeRef: String,
        internal val externalControlEnabled: Boolean,
        internal val callerIsPinnedController: Boolean,
        internal val controllerFullControlEnabled: Boolean,
    ) {
        /** Wall-clock ms after which the request may no longer be approved. */
        val expiresAtMs: Long
            get() = envelope.timestampMs + (if (envelope.ttlMs <= 0) Protocol.DEFAULT_TTL_MS else Protocol.clampTtl(envelope.ttlMs))

        internal var done = false

        fun denialResponse(): BridgeResponse =
            BridgeResponse.fail(
                envelope.requestId,
                ErrorCategory.CONFIRMATION_REQUIRED,
                "${envelope.op} needs on-device confirmation",
            )
    }

    /**
     * Finish a parked request. Approved and still inside its ttl -> run it.
     * Denied -> confirmation_required. Ttl elapsed -> expired. Second call -> replay_detected.
     */
    @Synchronized
    fun complete(pending: PendingConfirm, approved: Boolean): BridgeResponse {
        val env = pending.envelope
        if (pending.done) {
            return BridgeResponse.fail(env.requestId, ErrorCategory.REPLAY_DETECTED, "already completed")
        }
        pending.done = true
        if (host.nowMs() > pending.expiresAtMs) {
            return finish(env, pending.sourceNodeRef, BridgeResponse.fail(env.requestId, ErrorCategory.EXPIRED, "confirmation window elapsed"))
        }
        if (!approved) {
            return finish(env, pending.sourceNodeRef, BridgeResponse.fail(env.requestId, ErrorCategory.CONFIRMATION_REQUIRED, "declined on device"))
        }
        return run(
            env, pending.sourceNodeRef, pending.externalControlEnabled, false,
            pending.callerIsPinnedController, pending.controllerFullControlEnabled,
        )
    }

    fun evaluate(
        raw: String?,
        sourceNodeRef: String,
        callerPaired: Boolean,
        externalControlEnabled: Boolean,
        confirmed: Boolean = false,
        callerIsPinnedController: Boolean = false,
        controllerFullControlEnabled: Boolean = true,
    ): Outcome {
        val r = evaluateInner(
            raw, sourceNodeRef, callerPaired, externalControlEnabled, confirmed,
            callerIsPinnedController, controllerFullControlEnabled,
        )
        return if (r is BridgeResponse) Outcome.Done(r) else r as Outcome
    }

    private fun evaluateInner(
        raw: String?,
        sourceNodeRef: String,
        callerPaired: Boolean,
        externalControlEnabled: Boolean,
        confirmed: Boolean,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
    ): Any {
        if (raw.isNullOrBlank()) {
            return BridgeResponse.fail(null, ErrorCategory.INVALID_PARAMETERS, "empty request")
        }
        val root =
            try {
                json.parseToJsonElement(raw).jsonObject
            } catch (e: Exception) {
                return BridgeResponse.fail(null, ErrorCategory.INVALID_PARAMETERS, "not json")
            }
        val env =
            try {
                EnvelopeCodec.parse(root)
            } catch (e: Exception) {
                return BridgeResponse.fail(null, ErrorCategory.INVALID_PARAMETERS, "invalid envelope")
            }
        if (env.op.isBlank() || env.requestId.isBlank() || env.nonce.isBlank()) {
            return BridgeResponse.fail(
                env.requestId.ifBlank { null },
                ErrorCategory.INVALID_PARAMETERS,
                "op, requestId and nonce are required",
            )
        }
        if (env.targetNodeRef.isBlank()) {
            return BridgeResponse.fail(env.requestId, ErrorCategory.INVALID_PARAMETERS, "targetNodeRef is blank")
        }
        val self = host.nodeId()
        if (env.targetNodeRef != self &&
            env.targetNodeRef != "controller" &&
            env.targetNodeRef != Protocol.PACKAGE_NAME
        ) {
            return BridgeResponse.fail(env.requestId, ErrorCategory.NOT_FOUND, "unknown targetNodeRef")
        }
        if (!callerPaired) {
            return finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(env.requestId, ErrorCategory.NOT_PAIRED, "caller is not paired"),
            )
        }
        when (val replay = replayGuard.check(sourceNodeRef, env.nonce, env.timestampMs, env.ttlMs)) {
            ReplayCheck.Ok -> Unit
            is ReplayCheck.Rejected ->
                return finish(
                    env,
                    sourceNodeRef,
                    BridgeResponse.fail(env.requestId, replay.category, replay.message),
                )
        }
        val descriptor = Ops.descriptor(env.op)
        if (descriptor == null) {
            return BridgeResponse.fail(env.requestId, ErrorCategory.UNSUPPORTED_OP, "unsupported op ${env.op}")
        }
        val isLocalUi = sourceNodeRef == self || sourceNodeRef == Protocol.PACKAGE_NAME
        // Every peer except droidcommand-AI (pinned package+cert) and our own UI needs the
        // generic external-control switch, for reads as well. capabilities.get stays open so
        // a peer can discover that the switch is off.
        if (!isLocalUi && !callerIsPinnedController && !externalControlEnabled && env.op != Ops.CAPABILITIES_GET) {
            return finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(env.requestId, ErrorCategory.EXTERNAL_CONTROL_DISABLED, "external control is off"),
            )
        }
        if (
            !RemoteActionPolicy.allowsAction(
                env.op,
                callerIsPinnedController,
                controllerFullControlEnabled,
                externalControlEnabled,
                isLocalUi,
            )
        ) {
            return finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(
                    env.requestId,
                    ErrorCategory.EXTERNAL_CONTROL_DISABLED,
                    "external control is off",
                ),
            )
        }
        if (
            !isLocalUi &&
            !RemoteActionPolicy.remoteAllowed(
                env.op,
                callerIsPinnedController,
                controllerFullControlEnabled,
                externalControlEnabled,
            )
        ) {
            return finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(
                    env.requestId,
                    ErrorCategory.CAPABILITY_OFF,
                    "${env.op} is not remoteAllowed",
                ),
            )
        }
        if (
            RemoteActionPolicy.requiresConfirm(
                env.op,
                callerIsPinnedController,
                controllerFullControlEnabled,
            ) &&
            !env.dryRun &&
            !confirmed
        ) {
            if (env.op == Ops.LAYOUT_SET && !isLocalUi) {
                // Validate before bothering the user with a prompt.
                try {
                    LayoutPatch.parse(env.params)
                } catch (e: GateException) {
                    return finish(env, sourceNodeRef, BridgeResponse.fail(env.requestId, e.category, e.message ?: e.category))
                }
                audit.record(
                    ActionAuditEvent(host.nowMs(), sourceNodeRef, env.op, env.requestId, env.nonce, false, "awaiting_confirm"),
                )
                return Outcome.NeedsConfirm(
                    PendingConfirm(env, sourceNodeRef, externalControlEnabled, callerIsPinnedController, controllerFullControlEnabled),
                )
            }
            return finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(
                    env.requestId,
                    ErrorCategory.CONFIRMATION_REQUIRED,
                    "${env.op} needs on-device confirmation",
                ),
            )
        }
        return run(env, sourceNodeRef, externalControlEnabled, isLocalUi, callerIsPinnedController, controllerFullControlEnabled)
    }

    private fun run(
        env: Envelope,
        sourceNodeRef: String,
        externalControlEnabled: Boolean,
        isLocalUi: Boolean,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
    ): BridgeResponse {
        return try {
            finish(
                env,
                sourceNodeRef,
                dispatch(
                    env,
                    externalControlEnabled,
                    isLocalUi,
                    callerIsPinnedController,
                    controllerFullControlEnabled,
                ),
            )
        } catch (e: GateException) {
            finish(env, sourceNodeRef, BridgeResponse.fail(env.requestId, e.category, e.message ?: e.category))
        } catch (e: ForbiddenBackupContentException) {
            finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(env.requestId, ErrorCategory.INVALID_PARAMETERS, e.message ?: "forbidden"),
            )
        } catch (e: Exception) {
            finish(
                env,
                sourceNodeRef,
                BridgeResponse.fail(env.requestId, ErrorCategory.INTERNAL, e.message ?: "internal"),
            )
        }
    }

    private fun finish(env: Envelope, sourceNodeRef: String, resp: BridgeResponse): BridgeResponse {
        maybeAudit(env, sourceNodeRef, resp)
        return resp
    }

    private fun maybeAudit(env: Envelope, sourceNodeRef: String, resp: BridgeResponse) {
        val kind = Ops.descriptor(env.op)?.kind ?: return
        if (kind != OpKind.ACTION && kind != OpKind.DESTRUCTIVE) return
        audit.record(
            ActionAuditEvent(
                atMs = host.nowMs(),
                sourceNodeRef = sourceNodeRef,
                op = env.op,
                requestId = env.requestId,
                nonce = env.nonce,
                ok = resp.ok,
                detail = resp.error?.category ?: "ok",
            ),
        )
    }

    private fun dispatch(
        env: Envelope,
        externalControlEnabled: Boolean,
        isLocalUi: Boolean,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
    ): BridgeResponse {
        val result =
            when (env.op) {
                Ops.CAPABILITIES_GET ->
                    capabilities(
                        callerIsPinnedController,
                        controllerFullControlEnabled,
                        externalControlEnabled,
                    )
                Ops.NODE_STATUS_GET -> NodeStatusSnapshotCodec.encode(host.ownHealth())
                Ops.NETWORK_STATUS -> host.networkStatus()
                Ops.FLEET_LIST -> fleet()
                Ops.LAYOUT_GET -> host.layout()
                Ops.INSTALLED_LIST -> installed()
                Ops.RUNNING_LIST -> return runningResponse(env.requestId)
                Ops.CONFIG_EXPORT -> configExport()
                Ops.CONFIG_IMPORT -> configImport(env, isLocalUi)
                Ops.APP_LAUNCH ->
                    appLaunch(env, callerIsPinnedController, controllerFullControlEnabled, externalControlEnabled, isLocalUi)
                Ops.LAYOUT_SET ->
                    layoutSet(env, callerIsPinnedController, controllerFullControlEnabled, externalControlEnabled, isLocalUi)
                Ops.PANEL_OPEN ->
                    panelOpen(env, callerIsPinnedController, controllerFullControlEnabled, externalControlEnabled, isLocalUi)
                else -> return BridgeResponse.fail(env.requestId, ErrorCategory.UNSUPPORTED_OP, env.op)
            }
        return BridgeResponse.ok(env.requestId, result)
    }

    private fun capabilities(
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
    ): JsonObject =
        buildJsonObject {
            put("protocolMajor", Protocol.PROTOCOL_MAJOR)
            put("protocolMinor", Protocol.PROTOCOL_MINOR)
            put("externalControlEnabled", genericExternalControl)
            put("controllerFullControl", RemoteActionPolicy.controllerHasFullControl(callerIsPinnedController, controllerFullControlEnabled))
            putJsonArray("panels") { Panels.ALL.forEach { add(JsonPrimitive(it)) } }
            putJsonArray("ops") {
                Ops.ALL.forEach { op ->
                    add(
                        buildJsonObject {
                            put("name", op.name)
                            put("kind", op.kind.wire)
                            put(
                                "remoteAllowed",
                                RemoteActionPolicy.remoteAllowed(
                                    op.name,
                                    callerIsPinnedController,
                                    controllerFullControlEnabled,
                                    genericExternalControl,
                                ),
                            )
                            put(
                                "requiresConfirm",
                                RemoteActionPolicy.requiresConfirm(
                                    op.name,
                                    callerIsPinnedController,
                                    controllerFullControlEnabled,
                                ),
                            )
                        },
                    )
                }
            }
        }

    private fun fleet(): JsonObject =
        buildJsonObject {
            putJsonArray("nodes") {
                host.fleetPairs().forEach { n ->
                    add(
                        buildJsonObject {
                            put("packageName", n.packageName)
                            put("certSha256", n.certSha256)
                        },
                    )
                }
            }
        }

    private fun installed(): JsonObject =
        buildJsonObject {
            putJsonArray("apps") {
                host.installedPackages().forEach { app ->
                    add(
                        buildJsonObject {
                            put("packageName", app.packageName)
                            put("label", app.label)
                            put("userSerial", app.userSerial)
                        },
                    )
                }
            }
        }

    private fun runningResponse(requestId: String): BridgeResponse {
        return when (val r = host.runningPackages()) {
            is RunningResult.Ok ->
                BridgeResponse.ok(
                    requestId,
                    buildJsonObject {
                        putJsonArray("packages") { r.packages.forEach { add(JsonPrimitive(it)) } }
                    },
                )
            is RunningResult.Unavailable ->
                BridgeResponse.fail(requestId, r.reason, "usage access is not granted")
        }
    }

    private fun configExport(): JsonObject {
        val text = ConfigBackupCodec.exportJson(host.preferences())
        return buildJsonObject {
            put("packageName", Protocol.PACKAGE_NAME)
            put("schemaVersion", LauncherPreferences.SCHEMA_VERSION)
            put("clearPayload", text)
            put("wrapping", "none")
        }
    }

    private fun configImport(env: Envelope, isLocalUi: Boolean): JsonObject {
        val bundle = env.params["bundle"]?.jsonPrimitive?.contentOrNull
            ?: env.params["clearPayload"]?.jsonPrimitive?.contentOrNull
            ?: throw ForbiddenBackupContentException("bundle is required")
        val policy = BackupConflictPolicy.fromWire(env.params["conflictPolicy"]?.jsonPrimitive?.contentOrNull)
        val report =
            ConfigBackupCodec.import(
                live = host.preferences(),
                incomingJson = bundle,
                dryRun = env.dryRun,
                policy = policy,
                confirmed = isLocalUi || !env.dryRun,
            )
        if (!env.dryRun && report.applied) {
            val prefs = report.preferences ?: host.preferences()
            host.applyPreferences(prefs.copy(externalControlEnabled = false))
        }
        return buildJsonObject {
            put("dryRun", report.dryRun)
            put("applied", report.applied)
            putJsonArray("notes") { report.notes.forEach { add(JsonPrimitive(it)) } }
        }
    }

    private fun appLaunch(
        env: Envelope,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
        isLocalUi: Boolean,
    ): JsonObject {
        if (
            !RemoteActionPolicy.allowsAction(
                Ops.APP_LAUNCH,
                callerIsPinnedController,
                controllerFullControlEnabled,
                genericExternalControl,
                isLocalUi,
            )
        ) {
            throw GateException(ErrorCategory.EXTERNAL_CONTROL_DISABLED, "external control is off")
        }
        val pkg = (env.params["packageName"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?.takeIf { it.isNotBlank() }
            ?: throw GateException(ErrorCategory.INVALID_PARAMETERS, "packageName is required")
        val ok = host.launchApp(pkg)
        if (!ok) throw GateException(ErrorCategory.NOT_FOUND, "app not found")
        return buildJsonObject { put("launched", pkg) }
    }

    private fun layoutSet(
        env: Envelope,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
        isLocalUi: Boolean,
    ): JsonObject {
        if (
            !RemoteActionPolicy.allowsAction(
                Ops.LAYOUT_SET,
                callerIsPinnedController,
                controllerFullControlEnabled,
                genericExternalControl,
                isLocalUi,
            )
        ) {
            throw GateException(ErrorCategory.EXTERNAL_CONTROL_DISABLED, "external control is off")
        }
        return host.setLayout(LayoutPatch.parse(env.params))
    }

    private fun panelOpen(
        env: Envelope,
        callerIsPinnedController: Boolean,
        controllerFullControlEnabled: Boolean,
        genericExternalControl: Boolean,
        isLocalUi: Boolean,
    ): JsonObject {
        if (
            !RemoteActionPolicy.allowsAction(
                Ops.PANEL_OPEN,
                callerIsPinnedController,
                controllerFullControlEnabled,
                genericExternalControl,
                isLocalUi,
            )
        ) {
            throw GateException(ErrorCategory.EXTERNAL_CONTROL_DISABLED, "external control is off")
        }
        val panel = (env.params["panel"] as? JsonPrimitive)?.contentOrNull ?: "drawer"
        if (!Panels.isKnown(panel)) throw GateException(ErrorCategory.NOT_FOUND, "unknown panel")
        val ok = host.openPanel(panel)
        if (!ok) throw GateException(ErrorCategory.NOT_FOUND, "unknown panel")
        return buildJsonObject { put("panel", panel) }
    }
}

class GateException(val category: String, message: String) : Exception(message)

fun ownNodeHealth(
    nodeId: String,
    nowMs: Long,
    detail: String? = "launcher process alive",
    network: StatusTrayDomainStatus =
        StatusTrayDomainStatus(
            name = StatusTrayDomain.NETWORK,
            state = StatusTrayState.OFF,
            detail = "monitor off",
        ),
): NodeStatusSnapshot =
    NodeStatusSnapshot(
        nodeId = nodeId,
        packageName = Protocol.PACKAGE_NAME,
        connectionClass = Protocol.CONNECTION_EXTERNAL_NODE,
        updatedAtMs = nowMs,
        domains =
            listOf(
                StatusTrayDomainStatus(
                    name = StatusTrayDomain.NODE_HEALTH,
                    state = StatusTrayState.OK,
                    detail = detail,
                ),
                network,
            ),
    )
