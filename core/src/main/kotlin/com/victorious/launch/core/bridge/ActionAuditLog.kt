package com.victorious.launch.core.bridge

/**
 * Ring buffer of remote action attempts (success and denial).
 * Authorization still happens before dispatch; this is the audit trail.
 */
data class ActionAuditEvent(
    val atMs: Long,
    val sourceNodeRef: String,
    val op: String,
    val requestId: String,
    val nonce: String,
    val ok: Boolean,
    val detail: String,
)

class ActionAuditLog(private val capacity: Int = 64) {
    private val events = ArrayDeque<ActionAuditEvent>()

    @Synchronized
    fun record(event: ActionAuditEvent) {
        events.addLast(event)
        while (events.size > capacity) events.removeFirst()
    }

    @Synchronized
    fun snapshot(): List<ActionAuditEvent> = events.toList()

    @Synchronized
    fun clear() {
        events.clear()
    }
}
