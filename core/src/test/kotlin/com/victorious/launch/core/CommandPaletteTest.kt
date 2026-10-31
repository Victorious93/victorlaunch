package com.victorious.launch.core

import com.victorious.launch.core.command.CommandCodec
import com.victorious.launch.core.command.CommandTarget
import com.victorious.launch.core.command.DestructiveCommands
import com.victorious.launch.core.command.SavedCommand
import com.victorious.launch.core.command.TermuxRun
import com.victorious.launch.core.protocol.Ops
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandPaletteTest {
    @Test
    fun bridgeExposesNoExecOrShellOp() {
        val banned = listOf("exec", "shell", "command", "run", "su", "termux", "palette", "cmd", "sh")
        Ops.ALL.forEach { op ->
            val parts = op.name.lowercase().split('.', '_', '-')
            banned.forEach { b -> assertFalse("bridge op ${op.name} looks like an exec op", b in parts) }
        }
        listOf("shell.exec", "exec", "command.run", "termux.run", "palette.run", "shell.run").forEach {
            assertNull(Ops.descriptor(it))
        }
    }

    @Test
    fun destructiveDetection() {
        listOf(
            "rm -rf /sdcard/x", "ls; rm a", "dd if=/dev/zero of=/dev/block/sda", "reboot", "reboot recovery",
            "pm uninstall com.x", "pm clear com.x", "mkfs.ext4 /dev/x", "format", "svc power reboot", "echo x > /dev/block/sda",
        ).forEach { assertTrue(it, DestructiveCommands.needsConfirm(it)) }
        listOf("ls -la", "getprop ro.build.version.sdk", "pm list packages", "logcat -d", "echo format_ok", "dumpsys battery", "cat rmlist")
            .forEach { assertFalse(it, DestructiveCommands.needsConfirm(it)) }
        assertEquals(listOf("deletes files (rm)"), DestructiveCommands.reasons("rm x"))
    }

    @Test
    fun codecRoundTripsEscapes() {
        val list =
            listOf(
                SavedCommand("1", "tabs\tand\\slash", "echo 'a\tb'\nls", CommandTarget.SHIZUKU, true),
                SavedCommand("2", "termux", "pkg list-installed", CommandTarget.TERMUX),
            )
        assertEquals(list, CommandCodec.decode(CommandCodec.encode(list)))
        assertEquals(emptyList<SavedCommand>(), CommandCodec.decode(null))
        assertEquals(emptyList<SavedCommand>(), CommandCodec.decode("broken\tline"))
    }

    @Test
    fun favoritesFirstAndPaletteQuery() {
        val s = CommandCodec.sorted(listOf(SavedCommand("a", "b", "x", CommandTarget.ROOT), SavedCommand("c", "z", "y", CommandTarget.ROOT, true)))
        assertEquals("z", s.first().name)
        assertEquals("ls", CommandCodec.paletteQuery(">  ls"))
        assertNull(CommandCodec.paletteQuery("chrome"))
    }

    @Test
    fun termuxResult() {
        assertEquals("hi\n[exit 0]", TermuxRun.formatResult("hi\n", "", 0, -1, null))
        assertTrue(TermuxRun.formatResult(null, "bad", 1, 2, "denied").contains("[termux error 2] denied"))
        assertEquals("-c", TermuxRun.arguments("ls")[0])
    }
}
