package com.victorious.launch.bridge

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import com.victorious.launch.core.bridge.BridgeDispatcher
import com.victorious.launch.core.protocol.BridgeResponse
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-wide hand-off between the bridge (service thread) and the home UI.
 * The home screen collects [panel] and [layoutRev]; [ConfirmActivity] resolves [pending].
 */
object BridgeEvents {
    /** Panel requested by `panel.open`; HomeScreen opens it and clears this back to null. */
    val panel = MutableStateFlow<String?>(null)

    /** Bumped after `layout.set` so the home grid re-reads its prefs. */
    val layoutRev = MutableStateFlow(0)

    class Waiting(
        val id: Long,
        val pending: BridgeDispatcher.PendingConfirm,
        val dispatcher: BridgeDispatcher,
        val reply: (BridgeResponse) -> Unit,
    )

    private val ids = AtomicLong(0)
    private val pending = ConcurrentHashMap<Long, Waiting>()
    private val main = Handler(Looper.getMainLooper())

    fun park(
        context: Context,
        p: BridgeDispatcher.PendingConfirm,
        dispatcher: BridgeDispatcher,
        nowMs: Long,
        reply: (BridgeResponse) -> Unit,
    ) {
        val id = ids.incrementAndGet()
        pending[id] = Waiting(id, p, dispatcher, reply)
        // Auto-resolve as expired once the envelope ttl runs out.
        main.postDelayed({ resolve(id, approved = false) }, (p.expiresAtMs - nowMs).coerceAtLeast(0) + 50)
        showPrompt(context)
    }

    fun oldest(): Waiting? = pending.values.minByOrNull { it.id }

    fun hasPending(): Boolean = pending.isNotEmpty()

    fun resolve(id: Long, approved: Boolean) {
        val w = pending.remove(id) ?: return
        val resp = w.dispatcher.complete(w.pending, approved)
        if (resp.ok) layoutRev.value = layoutRev.value + 1
        w.reply(resp)
    }

    /**
     * Start the prompt. If Android blocks a background activity start, HomeActivity
     * re-shows it on its next resume via [hasPending].
     */
    fun showPrompt(context: Context) {
        try {
            context.startActivity(
                Intent(context, ConfirmActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (_: Exception) {
        }
    }
}
