package com.victorious.launch.core

import com.victorious.launch.core.island.IslandPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandPolicyTest {
    @Test
    fun wipeAndLockdownRequireExplicitConfirm() {
        assertFalse(IslandPolicy.canWipe(confirmed = false))
        assertTrue(IslandPolicy.canWipe(confirmed = true))
        assertFalse(IslandPolicy.canLockdown(confirmed = false))
        assertTrue(IslandPolicy.canLockdown(confirmed = true))
    }

    @Test
    fun offlineRestrictionsAreThePublicDpmSet() {
        assertTrue(IslandPolicy.OFFLINE_RESTRICTIONS.contains(IslandPolicy.RESTRICTION_WIFI))
        assertTrue(IslandPolicy.OFFLINE_RESTRICTIONS.contains(IslandPolicy.RESTRICTION_SHARE_IN))
        assertTrue(IslandPolicy.describeOffline(nonRoot = true).contains("does not drop packets"))
    }
}
