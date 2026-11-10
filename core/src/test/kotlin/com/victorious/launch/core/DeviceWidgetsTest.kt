package com.victorious.launch.core

import com.victorious.launch.core.device.BatteryCopy
import com.victorious.launch.core.device.CpuFreq
import com.victorious.launch.core.device.CpuSample
import com.victorious.launch.core.device.DeviceCopy
import com.victorious.launch.core.device.DeviceReadings
import com.victorious.launch.core.device.DeviceWidgetKind
import com.victorious.launch.core.device.MemInfo
import com.victorious.launch.core.device.ProcStat
import com.victorious.launch.core.device.RootDetect
import com.victorious.launch.core.device.RootEvidence
import com.victorious.launch.core.device.StorageCopy
import com.victorious.launch.core.device.ThermalStatus
import com.victorious.launch.core.device.UptimeCopy
import com.victorious.launch.core.status.WidgetAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceWidgetsTest {
    private val statIdle =
        "cpu  100 0 50 850 0 0 0 0 0 0\ncpu0 50 0 25 425 0 0 0 0 0 0\n"
    private val statBusy =
        "cpu  200 0 150 850 0 0 0 0 0 0\ncpu0 100 0 75 425 0 0 0 0 0 0\n"
    // first: total=1000 idle=850; second: total=1200 idle=850 → used 200 of 200 = 100%

    @Test
    fun procStatUsageAndIgnoresPerCpuLines() {
        val a = ProcStat.parse(statIdle)!!
        val b = ProcStat.parse(statBusy)!!
        assertEquals(CpuSample(1000, 850), a)
        assertEquals(100, b.usagePercent(a))
        val noChange = ProcStat.parse(statIdle)!!
        assertNull(noChange.usagePercent(a))
        assertNull(ProcStat.parse("intr 1 2 3\n"))
    }

    @Test
    fun cpuFreqFormatAndRange() {
        assertEquals(1800000L, CpuFreq.parseKhz("1800000\n"))
        assertEquals("1.8 GHz", CpuFreq.formatKhz(1_800_000))
        assertEquals("800 MHz", CpuFreq.formatKhz(800_000))
        assertEquals("1.8–2.4 GHz", CpuFreq.formatRange(listOf(1_800_000, 2_400_000, 2_000_000)))
        assertEquals("freq n/a", CpuFreq.formatRange(emptyList()))
    }

    @Test
    fun memStorageUptimeBatteryThermal() {
        val mem =
            MemInfo.parse(
                """
                MemTotal:        8388608 kB
                MemFree:         1000000 kB
                MemAvailable:    4194304 kB
                """.trimIndent(),
            )!!
        assertEquals(8L * 1024 * 1024, mem.totalKb)
        assertTrue(MemInfo.format(mem).contains("4 GB/8 GB RAM") || MemInfo.format(mem).startsWith("4"))
        assertEquals("32 GB free of 128 GB", StorageCopy.format(128L * 1024 * 1024 * 1024, 32L * 1024 * 1024 * 1024))
        assertEquals("2d 4h", UptimeCopy.format(((2 * 24 + 4) * 60 + 12) * 60_000L))
        assertEquals("45m", UptimeCopy.format(45 * 60_000L))
        assertEquals("81% 32.1°C charging", BatteryCopy.format(81, 321, true))
        assertEquals("bat n/a", BatteryCopy.format(null, null, false))
        assertEquals("thermal none", ThermalStatus.label(ThermalStatus.NONE))
        assertEquals("thermal severe", ThermalStatus.label(ThermalStatus.SEVERE))
        assertEquals(WidgetAccent.DOWN, ThermalStatus.accent(ThermalStatus.CRITICAL))
        assertEquals(WidgetAccent.STALE, ThermalStatus.accent(ThermalStatus.MODERATE))
    }

    @Test
    fun rootDetectionNeverRequiresSuAndOptInConfirms() {
        val cold = RootDetect.summarize(RootEvidence(existingPaths = emptyList(), installedPackages = emptySet(), optedIn = false))
        assertFalse(cold.likely)
        assertTrue(cold.line1.contains("No root"))
        assertTrue(cold.line2.contains("Allow root is off"))
        assertEquals(WidgetAccent.UNKNOWN, cold.accent)

        val hints =
            RootDetect.summarize(
                RootEvidence(
                    existingPaths = listOf("/system/bin/su", "/data/adb/magisk"),
                    installedPackages = setOf("com.topjohnwu.magisk"),
                    optedIn = false,
                ),
            )
        assertTrue(hints.likely)
        assertTrue(hints.line1.contains("Magisk"))
        assertTrue(hints.line1.contains("/system/bin/su"))
        assertTrue(hints.line2.contains("su not executed"))
        assertEquals(WidgetAccent.OK, hints.accent)

        val confirmed =
            RootDetect.summarize(
                RootEvidence(
                    existingPaths = listOf("/system/bin/su"),
                    installedPackages = emptySet(),
                    optedIn = true,
                    suOutput = "uid=0(root) gid=0(root)",
                    suOk = true,
                ),
            )
        assertTrue(RootDetect.uidZero("uid=0(root) gid=0(root)"))
        assertFalse(RootDetect.uidZero("uid=2000(shell)"))
        assertTrue(confirmed.line2.contains("uid=0"))
        assertEquals(WidgetAccent.OK, confirmed.accent)

        val failed =
            RootDetect.summarize(
                RootEvidence(
                    existingPaths = emptyList(),
                    installedPackages = emptySet(),
                    optedIn = true,
                    suOutput = "su: not found",
                    suOk = false,
                ),
            )
        assertTrue(failed.line2.contains("su failed"))
        assertEquals(WidgetAccent.UNKNOWN, failed.accent)
    }

    @Test
    fun widgetCopyInfoRootResources() {
        val readings =
            DeviceReadings(
                manufacturer = "Google",
                model = "Pixel 8 Pro",
                release = "15",
                sdk = 35,
                securityPatch = "2026-11-01",
                uptimeMs = (2 * 24 + 4) * 3600_000L,
                storageTotalBytes = 128L * 1024 * 1024 * 1024,
                storageFreeBytes = 32L * 1024 * 1024 * 1024,
                root =
                    RootEvidence(
                        existingPaths = listOf("/data/adb/ksu"),
                        installedPackages = setOf("me.weishu.kernelsu"),
                        optedIn = false,
                    ),
                procStatBefore = statIdle,
                procStatAfter = statBusy,
                cpuFreqKhz = listOf(1_800_000, 2_400_000),
                meminfo = "MemTotal: 8388608 kB\nMemAvailable: 4194304 kB\n",
                batteryPercent = 81,
                batteryTempTenthsC = 321,
                charging = false,
                thermalStatus = ThermalStatus.NONE,
            )
        val info = DeviceCopy.of(DeviceWidgetKind.INFO, readings)
        assertEquals("Device", info.title)
        assertTrue(info.line1.contains("Pixel 8 Pro"))
        assertTrue(info.line1.contains("Android 15"))
        assertTrue(info.line1.contains("API 35"))
        assertTrue(info.line2.contains("2026-11-01"))
        assertTrue(info.line2.contains("2d 4h"))
        assertTrue(info.line2.contains("32 GB free of 128 GB"))

        val root = DeviceCopy.of(DeviceWidgetKind.ROOT, readings)
        assertTrue(root.line1.contains("KernelSU"))
        assertTrue(root.line2.contains("Allow root is off"))

        val res = DeviceCopy.of(DeviceWidgetKind.RESOURCES, readings)
        assertTrue(res.line1.contains("CPU 100%"))
        assertTrue(res.line1.contains("1.8") && res.line1.contains("2.4"))
        assertTrue(res.line2.contains("RAM"))
        assertTrue(res.line2.contains("81%"))
        assertTrue(res.line2.contains("32.1°C"))
        assertTrue(res.line2.contains("thermal none"))
        assertEquals(WidgetAccent.OK, res.accent)

        val hot = DeviceCopy.resources(readings.copy(batteryPercent = 10, thermalStatus = ThermalStatus.SEVERE, charging = false))
        assertEquals(WidgetAccent.DOWN, hot.accent)

        assertEquals("Google Pixel 8 Pro", DeviceCopy.deviceName("Google", "Pixel 8 Pro"))
        assertEquals("Google Pixel", DeviceCopy.deviceName("Google", "Pixel"))
        // model already includes manufacturer
        assertEquals("Google Pixel 8", DeviceCopy.deviceName("Google", "Google Pixel 8"))
    }
}
