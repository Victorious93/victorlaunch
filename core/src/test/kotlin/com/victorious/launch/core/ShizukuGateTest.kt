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
        assertEquals(
            listOf("pm", "suspend", "--user", "0", "com.android.settings"),
            PrivilegedCommands.freeze("com.android.settings", true),
        )
        assertEquals(
            listOf("pm", "unsuspend", "--user", "0", "com.android.settings"),
            PrivilegedCommands.freeze("com.android.settings", false),
        )
        assertEquals(
            listOf("am", "force-stop", "--user", "0", "com.android.settings"),
            PrivilegedCommands.forceStop("com.android.settings"),
        )
        assertEquals(
            listOf("appops", "set", "com.android.settings", "GET_USAGE_STATS", "allow"),
            PrivilegedCommands.grantUsageAccess("com.android.settings"),
        )
    }

    @Test
    fun hideCloneAndQuietModeTargetAUser() {
        assertEquals(
            listOf("pm", "disable-user", "--user", "10", "com.android.settings"),
            PrivilegedCommands.hide("com.android.settings", true, 10),
        )
        assertEquals(
            listOf("pm", "enable", "--user", "10", "com.android.settings"),
            PrivilegedCommands.hide("com.android.settings", false, 10),
        )
        assertEquals(
            listOf("pm", "install-existing", "--user", "10", "com.android.settings"),
            PrivilegedCommands.installExisting("com.android.settings", 10),
        )
        assertEquals(listOf("am", "stop-user", "-f", "10"), PrivilegedCommands.quietMode(10, true))
        assertEquals(listOf("am", "start-user", "10"), PrivilegedCommands.quietMode(10, false))
    }

    @Test
    fun createManagedProfileStripsUnsafeNameChars() {
        assertEquals(
            listOf("pm", "create-user", "--profileOf", "0", "--managed", "Island"),
            PrivilegedCommands.createManagedProfile("Island!!!"),
        )
        val cmd = PrivilegedCommands.createManagedProfile("Island; rm -rf /")
        assertFalse(cmd.last().contains(";"))
        assertFalse(cmd.last().contains("/"))
        assertFalse(cmd.last().contains(" "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsShellMetacharacters() {
        PrivilegedCommands.forceStop("com.android.settings; reboot")
    }
}
