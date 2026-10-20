package com.victorious.launch.core.protocol

sealed class ReplayCheck {
    data object Ok : ReplayCheck()

    data class Rejected(val category: String, val message: String) : ReplayCheck()
}

/**
 * Seen-nonce cache keyed by (sourceNodeRef, nonce). Retention is at least 10
 * minutes. Future-skew is 120s only — past validity is TTL-only (PR #234).
 */
class ReplayGuard(
    private val retainMs: Long = Protocol.NONCE_RETAIN_MS,
    private val maxEntries: Int = 4096,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val seen = LinkedHashMap<String, Long>(16, 0.75f, true)

    @Synchronized
    fun check(sourceNodeRef: String, nonce: String, timestampMs: Long, ttlMs: Long): ReplayCheck {
        if (sourceNodeRef.isBlank()) {
            return ReplayCheck.Rejected(ErrorCategory.INVALID_PARAMETERS, "source identity missing")
        }
        if (!Protocol.isValidNonce(nonce)) {
            return ReplayCheck.Rejected(ErrorCategory.INVALID_PARAMETERS, "nonce must be 128-bit")
        }
        val now = clock()
        val ttl = if (ttlMs <= 0) Protocol.DEFAULT_TTL_MS else Protocol.clampTtl(ttlMs)
        if (timestampMs > now + Protocol.MAX_FUTURE_SKEW_MS) {
            return ReplayCheck.Rejected(ErrorCategory.EXPIRED, "timestampMs is too far in the future")
        }
        if (now > timestampMs + ttl) {
            return ReplayCheck.Rejected(ErrorCategory.EXPIRED, "envelope ttl elapsed")
        }
        evict(now)
        val key = sourceNodeRef + "|" + Protocol.normalizeNonce(nonce)
        if (seen.containsKey(key)) {
            return ReplayCheck.Rejected(ErrorCategory.REPLAY_DETECTED, "duplicate (sourceNodeRef, nonce)")
        }
        seen[key] = now
        return ReplayCheck.Ok
    }

    @Synchronized
    private fun evict(now: Long) {
        val cutoff = now - retainMs
        val stale = seen.entries.filter { it.value < cutoff }.map { it.key }
        stale.forEach { seen.remove(it) }
        while (seen.size > maxEntries) {
            val oldest = seen.entries.firstOrNull()?.key ?: break
            seen.remove(oldest)
        }
    }
}
