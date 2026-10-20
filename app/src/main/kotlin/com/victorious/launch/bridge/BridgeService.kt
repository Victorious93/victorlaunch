package com.victorious.launch.bridge

import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.victorious.launch.VictorLaunchApp
import android.util.Log
import com.victorious.launch.core.bridge.BridgeDispatcher
import com.victorious.launch.core.protocol.BridgeResponse
import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.launcher.AppCatalog
import com.victorious.launch.security.SigningCerts

class BridgeService : Service() {
    private lateinit var messenger: Messenger
    private lateinit var dispatcher: BridgeDispatcher

    override fun onCreate() {
        super.onCreate()
        val settings = VictorLaunchApp.instance.settings
        dispatcher =
            BridgeDispatcher(
                AndroidHost(applicationContext, settings, AppCatalog(applicationContext)),
                audit = VictorLaunchApp.instance.actionAudit,
            )
        messenger = Messenger(IncomingHandler(Looper.getMainLooper()))
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    private inner class IncomingHandler(looper: Looper) : Handler(looper) {
        override fun handleMessage(msg: Message) {
            if (msg.what != Protocol.MSG_REQUEST) return
            val replyTo = msg.replyTo ?: return
            val raw = msg.data?.getString(Protocol.KEY_REQUEST)
            val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) msg.sendingUid else -1
            val pkgs = if (uid >= 0) packageManager.getPackagesForUid(uid)?.toList().orEmpty() else emptyList()
            val source = pkgs.sorted().joinToString(",")
            val certs = pkgs.flatMap { SigningCerts.forPackage(packageManager, it) }
            val paired = pkgs.any { VictorLaunchApp.instance.settings.isCallerPaired(it, certs) }
            val pinned = pkgs.any { TrustedPeers.isPinnedController(it, certs) }
            val auditSize = dispatcher.audit.snapshot().size
            val resp =
                try {
                    dispatcher.handle(
                        raw = raw,
                        sourceNodeRef = source,
                        callerPaired = paired,
                        externalControlEnabled = VictorLaunchApp.instance.settings.isExternalControlEnabled(),
                        confirmed = false,
                        callerIsPinnedController = pinned,
                        controllerFullControlEnabled =
                            VictorLaunchApp.instance.settings.isControllerFullControlEnabled(),
                    )
                } catch (e: Exception) {
                    BridgeResponse.fail(null, ErrorCategory.INTERNAL, e.message ?: "internal")
                }
            val audited = dispatcher.audit.snapshot()
            if (audited.size > auditSize) {
                val event = audited.last()
                Log.i(
                    "VictorLaunchAudit",
                    "${event.op} source=${event.sourceNodeRef} ok=${event.ok} ${event.detail} req=${event.requestId}",
                )
            }
            val out = Message.obtain(null, Protocol.MSG_RESPONSE)
            out.data =
                Bundle().apply {
                    putString(Protocol.KEY_RESPONSE, resp.toJson().toString())
                }
            try {
                replyTo.send(out)
            } catch (_: Exception) {
            }
        }
    }
}
