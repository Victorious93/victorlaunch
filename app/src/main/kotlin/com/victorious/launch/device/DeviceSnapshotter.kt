package com.victorious.launch.device

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.os.SystemClock
import com.victorious.launch.core.device.DeviceReadings
import com.victorious.launch.core.device.MemInfo
import com.victorious.launch.core.device.RootDetect
import com.victorious.launch.core.device.RootEvidence
import com.victorious.launch.dev.RootShell
import com.victorious.launch.prefs.AppSettings
import java.io.File

/** Gather on-device readings. `su` is never started unless Allow root is on. */
object DeviceSnapshotter {
    /** [confirmSu] only from an explicit in-app tap; periodic widget refreshes pass false. */
    fun read(context: Context, settings: AppSettings, confirmSu: Boolean = false): DeviceReadings {
        val optedIn = settings.isRootShellAllowed()
        val su =
            if (optedIn && confirmSu) {
                RootShell.run(settings, listOf("id"), timeoutSec = 3)
            } else {
                null
            }
        val before = readText("/proc/stat")
        try {
            Thread.sleep(250)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        val after = readText("/proc/stat")
        val bat = battery(context)
        return DeviceReadings(
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            release = Build.VERSION.RELEASE.orEmpty(),
            sdk = Build.VERSION.SDK_INT,
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty(),
            uptimeMs = SystemClock.elapsedRealtime(),
            storageTotalBytes = storage().first,
            storageFreeBytes = storage().second,
            root =
                RootEvidence(
                    existingPaths = RootDetect.ALL_PATHS.filter { exists(it) },
                    installedPackages = installed(context, RootDetect.ALL_PACKAGES),
                    optedIn = optedIn,
                    suOutput = su?.detail,
                    suOk = su?.ok,
                ),
            procStatBefore = before,
            procStatAfter = after,
            cpuFreqKhz = cpuFreqs(),
            meminfo = meminfo(context),
            batteryPercent = bat.first,
            batteryTempTenthsC = bat.second,
            charging = bat.third,
            thermalStatus = thermal(context),
        )
    }

    private fun exists(path: String): Boolean =
        try {
            File(path).exists()
        } catch (_: Exception) {
            false
        }

    private fun readText(path: String): String =
        try {
            File(path).readText()
        } catch (_: Exception) {
            ""
        }

    private fun cpuFreqs(): List<Long> {
        val dir = File("/sys/devices/system/cpu")
        val cores = dir.listFiles { _, name -> name.matches(Regex("cpu\\d+")) } ?: return emptyList()
        return cores.sortedBy { it.name } .mapNotNull { core ->
            val raw = readText("${core.absolutePath}/cpufreq/scaling_cur_freq")
            raw.trim().toLongOrNull()?.takeIf { it > 0L }
        }
    }

    private fun storage(): Pair<Long, Long> {
        return try {
            val st = StatFs(Environment.getDataDirectory().absolutePath)
            st.totalBytes to st.availableBytes
        } catch (_: Exception) {
            0L to 0L
        }
    }

    private fun installed(context: Context, packages: Set<String>): Set<String> {
        val pm = context.packageManager
        return packages.filter { pkg ->
            try {
                pm.getPackageInfo(pkg, 0)
                true
            } catch (_: Exception) {
                false
            }
        }.toSet()
    }

    private fun battery(context: Context): Triple<Int?, Int?, Boolean> {
        return try {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) return Triple(null, null, false)
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 0)
            val percent = if (level >= 0 && scale > 0) ((level * 100L) / scale).toInt() else null
            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE).let { if (it == Int.MIN_VALUE) null else it }
            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
            Triple(percent, temp, plugged)
        } catch (_: Exception) {
            Triple(null, null, false)
        }
    }

    private fun thermal(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            context.getSystemService(PowerManager::class.java)?.currentThermalStatus
        } catch (_: Exception) {
            null
        }
    }

    private fun meminfo(context: Context): String {
        val raw = readText("/proc/meminfo")
        if (MemInfo.parse(raw) != null) return raw
        val ram = activityRam(context) ?: return raw
        return "MemTotal: ${ram.first / 1024} kB\nMemAvailable: ${ram.second / 1024} kB\n"
    }

    fun activityRam(context: Context): Pair<Long, Long>? {
        return try {
            val info = ActivityManager.MemoryInfo()
            context.getSystemService(ActivityManager::class.java)?.getMemoryInfo(info) ?: return null
            info.totalMem to info.availMem
        } catch (_: Exception) {
            null
        }
    }
}
