package com.victorious.launch.core.inspect

/** Pure formatting for the long-press app inspector. */
object AppInspect {
    private val codenames =
        mapOf(
            21 to "5.0", 22 to "5.1", 23 to "6", 24 to "7.0", 25 to "7.1", 26 to "8.0", 27 to "8.1",
            28 to "9", 29 to "10", 30 to "11", 31 to "12", 32 to "12L", 33 to "13", 34 to "14", 35 to "15", 36 to "16",
        )

    fun sdkLabel(sdk: Int?): String =
        when {
            sdk == null || sdk <= 0 -> "unknown"
            codenames[sdk] != null -> "$sdk (Android ${codenames[sdk]})"
            else -> "$sdk"
        }

    private val stores =
        mapOf(
            "com.android.vending" to "Google Play",
            "org.fdroid.fdroid" to "F-Droid",
            "com.aurora.store" to "Aurora Store",
            "com.amazon.venezia" to "Amazon Appstore",
            "com.sec.android.app.samsungapps" to "Galaxy Store",
            "com.google.android.packageinstaller" to "Package installer",
            "com.android.packageinstaller" to "Package installer",
            "com.android.shell" to "adb",
        )

    fun installSource(installer: String?, isSystem: Boolean): String =
        when {
            installer.isNullOrBlank() -> if (isSystem) "Preinstalled (system)" else "Unknown / sideloaded"
            else -> stores[installer]?.let { "$it ($installer)" } ?: installer
        }

    /** File name for an extracted APK: base -> pkg-ver.apk, split -> pkg-ver-split.apk. */
    fun apkFileName(packageName: String, versionName: String?, splitName: String?): String {
        val ver = (versionName ?: "unknown").replace(Regex("[^A-Za-z0-9._-]"), "_")
        val pkg = packageName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val split = splitName?.replace(Regex("[^A-Za-z0-9._-]"), "_")
        return if (split.isNullOrEmpty()) "$pkg-$ver.apk" else "$pkg-$ver-$split.apk"
    }

    /** Split name from a split APK path like /data/app/.../split_config.arm64_v8a.apk. */
    fun splitNameFromPath(path: String): String =
        path.substringAfterLast('/').removeSuffix(".apk").removePrefix("split_")

    data class PermRow(val name: String, val granted: Boolean)

    /** Granted first, then alphabetical; android.permission. prefix shortened. */
    fun permissionRows(requested: List<String>, flags: IntArray?, grantedFlag: Int): List<PermRow> =
        requested.mapIndexed { i, p ->
            PermRow(shortPerm(p), flags != null && i < flags.size && (flags[i] and grantedFlag) != 0)
        }.sortedWith(compareBy<PermRow>({ !it.granted }, { it.name }))

    fun shortPerm(p: String): String = p.removePrefix("android.permission.")

    fun componentSummary(activities: Int, services: Int, receivers: Int, providers: Int): String =
        "$activities activities · $services services · $receivers receivers · $providers providers"

    fun humanBytes(b: Long): String =
        when {
            b < 1024 -> "$b B"
            b < 1024L * 1024 -> "%.1f KB".format(b / 1024.0)
            b < 1024L * 1024 * 1024 -> "%.1f MB".format(b / 1048576.0)
            else -> "%.2f GB".format(b / 1073741824.0)
        }
}
