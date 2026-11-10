package com.victorious.launch.core.device

import com.victorious.launch.core.status.WidgetAccent
import java.util.Locale

enum class DeviceWidgetKind {
    INFO,
    ROOT,
    RESOURCES,
}

data class DeviceWidgetCopy(
    val kind: DeviceWidgetKind,
    val title: String,
    val line1: String,
    val line2: String,
    val accent: WidgetAccent,
)

data class CpuSample(val total: Long, val idle: Long) {
    fun usagePercent(previous: CpuSample): Int? {
        val dTotal = total - previous.total
        val dIdle = idle - previous.idle
        if (dTotal <= 0L) return null
        val used = (dTotal - dIdle).coerceAtLeast(0L)
        return ((used * 100L) / dTotal).toInt().coerceIn(0, 100)
    }
}

data class MemoryFigures(val totalKb: Long, val availableKb: Long) {
    val usedKb: Long get() = (totalKb - availableKb).coerceAtLeast(0L)
}

data class RootEvidence(
    val existingPaths: List<String>,
    val installedPackages: Set<String>,
    val optedIn: Boolean,
    val suOutput: String? = null,
    val suOk: Boolean? = null,
)

data class DeviceReadings(
    val manufacturer: String,
    val model: String,
    val release: String,
    val sdk: Int,
    val securityPatch: String,
    val uptimeMs: Long,
    val storageTotalBytes: Long,
    val storageFreeBytes: Long,
    val root: RootEvidence,
    val procStatBefore: String,
    val procStatAfter: String,
    val cpuFreqKhz: List<Long>,
    val meminfo: String,
    val batteryPercent: Int?,
    val batteryTempTenthsC: Int?,
    val charging: Boolean,
    val thermalStatus: Int?,
)

object ProcStat {
    fun parse(text: String): CpuSample? {
        val line = text.lineSequence().firstOrNull { it.startsWith("cpu ") } ?: return null
        val parts = line.trim().split(Regex("\\s+")).drop(1)
        if (parts.size < 4) return null
        val nums = parts.mapNotNull { it.toLongOrNull() }
        if (nums.size < 4) return null
        val idle = nums[3] + nums.getOrElse(4) { 0L } // idle + iowait
        val total = nums.take(8).sum()
        return CpuSample(total, idle)
    }
}

object CpuFreq {
    fun parseKhz(raw: String): Long? = raw.trim().lineSequence().firstOrNull()?.toLongOrNull()?.takeIf { it > 0L }

    fun formatKhz(khz: Long): String {
        return if (khz >= 1000_000L) {
            val ghz = khz / 1000.0 / 1000.0
            "${trimFrac(ghz)} GHz"
        } else {
            val mhz = khz / 1000.0
            "${trimFrac(mhz)} MHz"
        }
    }

    fun formatRange(values: List<Long>): String {
        val ok = values.filter { it > 0L }
        if (ok.isEmpty()) return "freq n/a"
        val min = ok.min()
        val max = ok.max()
        if (min == max) return formatKhz(min)
        val bothGhz = min >= 1_000_000L && max >= 1_000_000L
        val bothMhz = min < 1_000_000L && max < 1_000_000L
        return if (bothGhz) {
            val lo = String.format(Locale.US, "%.2f", min / 1_000_000.0).trimEnd('0').trimEnd('.')
            val hi = String.format(Locale.US, "%.2f", max / 1_000_000.0).trimEnd('0').trimEnd('.')
            "$lo–$hi GHz"
        } else if (bothMhz) {
            val lo = String.format(Locale.US, "%.2f", min / 1000.0).trimEnd('0').trimEnd('.')
            val hi = String.format(Locale.US, "%.2f", max / 1000.0).trimEnd('0').trimEnd('.')
            "$lo–$hi MHz"
        } else {
            "${formatKhz(min)}–${formatKhz(max)}"
        }
    }

    private fun trimFrac(n: Double): String {
        val s = String.format(Locale.US, "%.2f", n)
        return s.trimEnd('0').trimEnd('.')
    }
}

object MemInfo {
    fun parse(procMeminfo: String): MemoryFigures? {
        var total: Long? = null
        var avail: Long? = null
        procMeminfo.lineSequence().forEach { line ->
            val parts = line.trim().split(Regex("\\s+"))
            if (parts.size < 2) return@forEach
            val kb = parts[1].toLongOrNull() ?: return@forEach
            when {
                parts[0].startsWith("MemTotal") -> total = kb
                parts[0].startsWith("MemAvailable") -> avail = kb
            }
        }
        val t = total ?: return null
        val a = avail ?: return null
        return MemoryFigures(t, a)
    }

    fun format(fig: MemoryFigures): String = "${formatKb(fig.usedKb)}/${formatKb(fig.totalKb)} RAM"
}

object StorageCopy {
    fun format(totalBytes: Long, freeBytes: Long): String {
        if (totalBytes <= 0L) return "storage n/a"
        return "${formatBytes(freeBytes)} free of ${formatBytes(totalBytes)}"
    }
}

object UptimeCopy {
    fun format(elapsedMs: Long): String {
        val totalMin = (elapsedMs.coerceAtLeast(0L) / 60_000L)
        val days = totalMin / (60 * 24)
        val hours = (totalMin % (60 * 24)) / 60
        val minutes = totalMin % 60
        return when {
            days > 0L -> "${days}d ${hours}h"
            hours > 0L -> "${hours}h ${minutes}m"
            else -> "${minutes}m"
        }
    }
}

object BatteryCopy {
    fun format(percent: Int?, tempTenthsC: Int?, charging: Boolean): String {
        val level = if (percent == null) "bat n/a" else "${percent.coerceIn(0, 100)}%"
        val temp = if (tempTenthsC == null) null else "${trimTemp(tempTenthsC / 10.0)}°C"
        val charge = if (charging) "charging" else null
        return listOfNotNull(level, temp, charge).joinToString(" ")
    }

    private fun trimTemp(n: Double): String {
        val s = String.format(Locale.US, "%.1f", n)
        return s.trimEnd('0').trimEnd('.')
    }
}

object ThermalStatus {
    const val NONE = 0
    const val LIGHT = 1
    const val MODERATE = 2
    const val SEVERE = 3
    const val CRITICAL = 4
    const val EMERGENCY = 5
    const val SHUTDOWN = 6

    fun label(status: Int?): String =
        when (status) {
            null -> "thermal n/a"
            NONE -> "thermal none"
            LIGHT -> "thermal light"
            MODERATE -> "thermal moderate"
            SEVERE -> "thermal severe"
            CRITICAL -> "thermal critical"
            EMERGENCY -> "thermal emergency"
            SHUTDOWN -> "thermal shutdown"
            else -> "thermal $status"
        }

    fun accent(status: Int?): WidgetAccent =
        when {
            status == null || status <= LIGHT -> WidgetAccent.OK
            status == MODERATE -> WidgetAccent.STALE
            else -> WidgetAccent.DOWN
        }
}

object RootDetect {
    val SU_PATHS =
        listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/system/sbin/su",
            "/vendor/bin/su",
            "/su/bin/su",
        )

    val MAGISK_PATHS = listOf("/data/adb/magisk", "/sbin/.magisk")
    val KSU_PATHS = listOf("/data/adb/ksu")
    val APATCH_PATHS = listOf("/data/adb/ap", "/data/adb/apatch")

    val MAGISK_PACKAGES = setOf("com.topjohnwu.magisk")
    val KSU_PACKAGES = setOf("me.weishu.kernelsu", "com.rifsxd.ksunext")
    val APATCH_PACKAGES = setOf("me.bmax.apatch")
    val ALL_PACKAGES = MAGISK_PACKAGES + KSU_PACKAGES + APATCH_PACKAGES

    val ALL_PATHS: List<String> = SU_PATHS + MAGISK_PATHS + KSU_PATHS + APATCH_PATHS

    fun uidZero(output: String?): Boolean {
        if (output.isNullOrBlank()) return false
        return Regex("""uid=0\b""").containsMatchIn(output)
    }

    data class Summary(
        val line1: String,
        val line2: String,
        val likely: Boolean,
        val accent: WidgetAccent,
    )

    fun summarize(ev: RootEvidence): Summary {
        val paths = ev.existingPaths.toSet()
        val suPaths = SU_PATHS.filter { it in paths }
        val magiskPath = MAGISK_PATHS.any { it in paths }
        val ksuPath = KSU_PATHS.any { it in paths }
        val apPath = APATCH_PATHS.any { it in paths }
        val magiskPkg = ev.installedPackages.any { it in MAGISK_PACKAGES }
        val ksuPkg = ev.installedPackages.any { it in KSU_PACKAGES }
        val apPkg = ev.installedPackages.any { it in APATCH_PACKAGES }

        val bits = mutableListOf<String>()
        if (magiskPkg || magiskPath) bits += "Magisk" + if (magiskPkg) " app" else " files"
        if (ksuPkg || ksuPath) bits += "KernelSU" + if (ksuPkg) " app" else " files"
        if (apPkg || apPath) bits += "APatch" + if (apPkg) " app" else " files"
        if (suPaths.isNotEmpty()) bits += "su at ${suPaths.first()}"
        val likely = bits.isNotEmpty()
        val line1 = if (bits.isEmpty()) "No root indicators" else bits.joinToString(" · ")

        val line2 =
            when {
                !ev.optedIn -> "su not executed (Dev panel → Allow root is off)"
                ev.suOk == true && uidZero(ev.suOutput) -> "su confirmed (uid=0)"
                ev.suOk == true -> "su ran · ${ev.suOutput?.take(80) ?: "ok"}"
                ev.suOk == false -> "su failed · ${ev.suOutput?.take(80) ?: "unavailable"}"
                else -> "Allow root is on · tap Refresh in Device widgets to confirm su"
            }
        val accent =
            when {
                ev.optedIn && ev.suOk == true && uidZero(ev.suOutput) -> WidgetAccent.OK
                ev.optedIn && ev.suOk == false -> if (likely) WidgetAccent.STALE else WidgetAccent.UNKNOWN
                likely -> WidgetAccent.OK
                else -> WidgetAccent.UNKNOWN
            }
        return Summary(line1, line2, likely, accent)
    }
}

object DeviceCopy {
    fun of(kind: DeviceWidgetKind, r: DeviceReadings): DeviceWidgetCopy =
        when (kind) {
            DeviceWidgetKind.INFO -> info(r)
            DeviceWidgetKind.ROOT -> root(r)
            DeviceWidgetKind.RESOURCES -> resources(r)
        }

    fun info(r: DeviceReadings): DeviceWidgetCopy {
        val name = deviceName(r.manufacturer, r.model)
        val android = "Android ${r.release} (API ${r.sdk})"
        val patch = if (r.securityPatch.isBlank()) "patch n/a" else "patch ${r.securityPatch}"
        val up = "up ${UptimeCopy.format(r.uptimeMs)}"
        val storage = StorageCopy.format(r.storageTotalBytes, r.storageFreeBytes)
        return DeviceWidgetCopy(
            kind = DeviceWidgetKind.INFO,
            title = "Device",
            line1 = "$name · $android",
            line2 = "$patch · $up · $storage",
            accent = WidgetAccent.OK,
        )
    }

    fun root(r: DeviceReadings): DeviceWidgetCopy {
        val s = RootDetect.summarize(r.root)
        return DeviceWidgetCopy(DeviceWidgetKind.ROOT, "Root", s.line1, s.line2, s.accent)
    }

    fun resources(r: DeviceReadings): DeviceWidgetCopy {
        val before = ProcStat.parse(r.procStatBefore)
        val after = ProcStat.parse(r.procStatAfter)
        val usage = if (before != null && after != null) after.usagePercent(before) else null
        val cpu = if (usage == null) "CPU n/a" else "CPU $usage%"
        val freq = CpuFreq.formatRange(r.cpuFreqKhz)
        val ram = MemInfo.parse(r.meminfo)?.let { MemInfo.format(it) } ?: "RAM n/a"
        val bat = BatteryCopy.format(r.batteryPercent, r.batteryTempTenthsC, r.charging)
        val thermal = ThermalStatus.label(r.thermalStatus)
        val accent =
            when {
                (r.batteryPercent != null && r.batteryPercent <= 15 && !r.charging) ||
                    ThermalStatus.accent(r.thermalStatus) == WidgetAccent.DOWN -> WidgetAccent.DOWN
                ThermalStatus.accent(r.thermalStatus) == WidgetAccent.STALE ||
                    (r.batteryPercent != null && r.batteryPercent <= 20 && !r.charging) -> WidgetAccent.STALE
                else -> WidgetAccent.OK
            }
        return DeviceWidgetCopy(
            kind = DeviceWidgetKind.RESOURCES,
            title = "Resources",
            line1 = "$cpu · $freq",
            line2 = "$ram · $bat · $thermal",
            accent = accent,
        )
    }

    fun deviceName(manufacturer: String, model: String): String {
        val mfr = manufacturer.trim()
        val mdl = model.trim()
        if (mdl.isBlank()) return mfr.ifBlank { "Unknown device" }
        if (mfr.isBlank() || mdl.startsWith(mfr, ignoreCase = true)) return mdl
        return "$mfr $mdl"
    }
}

internal fun formatKb(kb: Long): String = formatBytes(kb * 1024L)

internal fun formatBytes(bytes: Long): String {
    val b = bytes.coerceAtLeast(0L).toDouble()
    return when {
        b >= 1024.0 * 1024 * 1024 -> "${trimNum(b / (1024.0 * 1024 * 1024))} GB"
        b >= 1024.0 * 1024 -> "${trimNum(b / (1024.0 * 1024))} MB"
        else -> "${trimNum(b / 1024.0)} KB"
    }
}

private fun trimNum(n: Double): String {
    val s = String.format(Locale.US, "%.1f", n)
    return s.trimEnd('0').trimEnd('.')
}
