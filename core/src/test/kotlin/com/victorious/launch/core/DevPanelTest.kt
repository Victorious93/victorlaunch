package com.victorious.launch.core

import com.victorious.launch.core.dev.DevNamespace
import com.victorious.launch.core.dev.DevOptions
import com.victorious.launch.core.dev.LogFilter
import com.victorious.launch.core.dev.LogLevel
import com.victorious.launch.core.dev.LogRing
import com.victorious.launch.core.dev.Logcat
import com.victorious.launch.core.dev.RouteDecision
import com.victorious.launch.core.dev.ShellQuote
import com.victorious.launch.core.dev.WriteCapability
import com.victorious.launch.core.dev.WriteRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DevPanelTest {
    @Test
    fun routePrefersDirectThenShizukuThenRoot() {
        assertEquals(RouteDecision.Use(WriteRoute.DIRECT_WSS), DevOptions.route(WriteCapability(true, true, true), false))
        assertEquals(RouteDecision.Use(WriteRoute.SHIZUKU), DevOptions.route(WriteCapability(true, true, true), true))
        assertEquals(RouteDecision.Use(WriteRoute.ROOT), DevOptions.route(WriteCapability(true, false, true), true))
        assertEquals(RouteDecision.Use(WriteRoute.SHIZUKU), DevOptions.route(WriteCapability(false, true, false), false))
    }

    @Test
    fun unavailableExplainsWhy() {
        val shell = DevOptions.route(WriteCapability(true, false, false), true) as RouteDecision.Unavailable
        assertTrue(shell.reason.contains("Shizuku or root"))
        val any = DevOptions.route(WriteCapability(false, false, false), false) as RouteDecision.Unavailable
        assertTrue(any.reason.contains("WRITE_SECURE_SETTINGS"))
    }

    @Test
    fun writeArgvIsSafe() {
        assertEquals(listOf("settings", "put", "global", "adb_wifi_enabled", "1"),
            DevOptions.writeArgv(DevNamespace.GLOBAL, "adb_wifi_enabled", "1"))
        assertTrue(DevOptions.writeArgv(DevNamespace.PROP, "debug.layout", "true")[2].startsWith("setprop debug.layout true;"))
        for (bad in listOf("1; reboot", "\$(id)", "a b", "")) {
            assertTrue(runCatching { DevOptions.writeArgv(DevNamespace.GLOBAL, "k", bad) }.isFailure)
        }
        assertTrue(runCatching { DevOptions.writeArgv(DevNamespace.GLOBAL, "k;rm", "1") }.isFailure)
    }

    @Test
    fun togglesAndEndpoint() {
        assertTrue(DevOptions.STAY_AWAKE.isOn("7"))
        assertFalse(DevOptions.STAY_AWAKE.isOn("3"))
        assertFalse(DevOptions.SHOW_TAPS.isOn(null))
        assertTrue(DevOptions.USB_DEBUGGING.readOnly)
        assertEquals("192.168.1.5:37001", DevOptions.wirelessEndpoint("192.168.1.5", "37001\n"))
        assertNull(DevOptions.wirelessEndpoint("192.168.1.5", ""))
        assertNull(DevOptions.wirelessEndpoint(null, "5555"))
    }

    @Test
    fun parsesThreadtime() {
        val l = Logcat.parse("10-31 04:33:12.345  1234  5678 W ActivityManager: Slow op: x")
        assertEquals(1234, l.pid)
        assertEquals(LogLevel.WARN, l.level)
        assertEquals("ActivityManager", l.tag)
        assertEquals("Slow op: x", l.message)
        val spaced = Logcat.parse("10-31 04:33:12.345  1  2 E My Tag  : boom")
        assertEquals("My Tag", spaced.tag)
        val junk = Logcat.parse("--------- beginning of main")
        assertNull(junk.level)
    }

    @Test
    fun filters() {
        val a = Logcat.parse("10-31 04:33:12.345  10  10 D Foo: hello")
        val b = Logcat.parse("10-31 04:33:12.345  20  20 E Bar: world")
        assertTrue(LogFilter().matches(a))
        assertFalse(LogFilter(minLevel = LogLevel.INFO).matches(a))
        assertTrue(LogFilter(minLevel = LogLevel.INFO).matches(b))
        assertTrue(LogFilter(tag = "foo").matches(a))
        assertFalse(LogFilter(tag = "foo").matches(b))
        assertTrue(LogFilter(pids = setOf(20)).matches(b))
        assertFalse(LogFilter(pids = emptySet()).matches(b))
        assertTrue(LogFilter(text = "WORLD").matches(b))
        assertEquals(setOf(12, 34), Logcat.parsePidof("12 34\n"))
        assertEquals(emptySet<Int>(), Logcat.parsePidof(""))
    }

    @Test
    fun ringDropsOldest() {
        val r = LogRing(2)
        listOf("a", "b", "c").forEach { r.add(Logcat.parse(it)) }
        assertEquals(listOf("b", "c"), r.snapshot().map { it.raw })
        assertEquals("b\nc", Logcat.export(r.snapshot()))
    }

    @Test
    fun shellQuote() {
        assertEquals("'it'\\''s'", ShellQuote.quote("it's"))
        assertEquals("'ls' '-l'", ShellQuote.join(listOf("ls", "-l")))
    }
}
