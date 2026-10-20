package com.victorious.launch.core

import com.victorious.launch.shizuku.PrivilegedCommands
import com.victorious.launch.shizuku.ShizukuGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuGateTest {
    @Test
    fun privilegedOpsRequireExplicitEnableAndBinderAndPermission() {
        assertFalse(ShizukuGate.canUsePrivileged(false, true, true))
        assertFalse(ShizukuGate.canUsePrivileged(true, false, true))
        assertFalse(ShizukuGate.canUsePrivileged(true, true, false))
        assertTrue(ShizukuGate.canUsePrivileged(true, true, true))
    }

    @Test
    fun freezeAndForceStopCommands() {
        assertEquals(listOf("pm", "suspend", "com.android.settings"), PrivilegedCommands.freeze("com.android.settings", true))
        assertEquals(listOf("pm", "unsuspend", "com.android.settings"), PrivilegedCommands.freeze("com.android.settings", false))
        assertEquals(listOf("am", "force-stop", "com.android.settings"), PrivilegedCommands.forceStop("com.android.settings"))
        assertEquals(
            listOf("appops", "set", "com.android.settings", "GET_USAGE_STATS", "allow"),
            PrivilegedCommands.grantUsageAccess("com.android.settings"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShellMetacharacters() {
        PrivilegedCommands.forceStop("com.android.settings; reboot")
    }
}
