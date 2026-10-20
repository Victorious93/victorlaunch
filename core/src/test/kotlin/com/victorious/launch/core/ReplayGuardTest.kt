package com.victorious.launch.core

import com.victorious.launch.core.protocol.ErrorCategory
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.ReplayCheck
import com.victorious.launch.core.protocol.ReplayGuard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGuardTest {
    @Test
    fun acceptsValidEnvelopeAndRejectsDuplicateNonce() {
        var now = 1_000_000L
        val guard = ReplayGuard(clock = { now })
        val nonce = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
        val first = guard.check("pkg.a", nonce, now, Protocol.DEFAULT_TTL_MS)
        assertTrue(first is ReplayCheck.Ok)
        val dup = guard.check("pkg.a", nonce, now, Protocol.DEFAULT_TTL_MS)
        assertEquals(ErrorCategory.REPLAY_DETECTED, (dup as ReplayCheck.Rejected).category)
        val otherSource = guard.check("pkg.b", nonce, now, Protocol.DEFAULT_TTL_MS)
        assertTrue(otherSource is ReplayCheck.Ok)
    }

    @Test
    fun futureSkewOver120sIsExpired() {
        val now = 5_000_000L
        val guard = ReplayGuard(clock = { now })
        val rejected =
            guard.check(
                "pkg.a",
                "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                now + 200_000L,
                300_000L,
            )
        assertEquals(ErrorCategory.EXPIRED, (rejected as ReplayCheck.Rejected).category)
    }

    @Test
    fun pastTimestampWithinMaxTtlIsAccepted() {
        val now = 5_000_000L
        val guard = ReplayGuard(clock = { now })
        val ok =
            guard.check(
                "pkg.a",
                "cccccccccccccccccccccccccccccccc",
                now - 200_000L,
                300_000L,
            )
        assertTrue(ok is ReplayCheck.Ok)
    }

    @Test
    fun ttlElapsedIsExpired() {
        val now = 5_000_000L
        val guard = ReplayGuard(clock = { now })
        val rejected =
            guard.check(
                "pkg.a",
                "dddddddddddddddddddddddddddddddd",
                now - 130_000L,
                120_000L,
            )
        assertEquals(ErrorCategory.EXPIRED, (rejected as ReplayCheck.Rejected).category)
    }
}
