package com.victorious.launch.core

import com.victorious.launch.core.inspect.AppInspect
import org.junit.Assert.assertEquals
import org.junit.Test

class AppInspectTest {
    @Test
    fun sdkLabels() {
        assertEquals("34 (Android 14)", AppInspect.sdkLabel(34))
        assertEquals("99", AppInspect.sdkLabel(99))
        assertEquals("unknown", AppInspect.sdkLabel(null))
    }

    @Test
    fun installSources() {
        assertEquals("Google Play (com.android.vending)", AppInspect.installSource("com.android.vending", false))
        assertEquals("Preinstalled (system)", AppInspect.installSource(null, true))
        assertEquals("Unknown / sideloaded", AppInspect.installSource("", false))
        assertEquals("x.y", AppInspect.installSource("x.y", false))
    }

    @Test
    fun apkNames() {
        assertEquals("a.b-1.2.apk", AppInspect.apkFileName("a.b", "1.2", null))
        assertEquals("a.b-1_2_beta-config.arm64_v8a.apk", AppInspect.apkFileName("a.b", "1 2/beta", "config.arm64_v8a"))
        assertEquals("config.xxhdpi", AppInspect.splitNameFromPath("/data/app/x/split_config.xxhdpi.apk"))
    }

    @Test
    fun permissionsGrantedFirst() {
        val rows = AppInspect.permissionRows(listOf("android.permission.CAMERA", "android.permission.INTERNET", "x.Y"), intArrayOf(1, 3, 1), 2)
        assertEquals(listOf("INTERNET", "CAMERA", "x.Y"), rows.map { it.name })
        assertEquals(listOf(true, false, false), rows.map { it.granted })
    }

    @Test
    fun bytes() {
        assertEquals("512 B", AppInspect.humanBytes(512))
        assertEquals("1.5 MB", AppInspect.humanBytes(1572864))
    }
}
