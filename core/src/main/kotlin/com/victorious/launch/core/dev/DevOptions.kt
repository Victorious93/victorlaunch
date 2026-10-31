package com.victorious.launch.core.dev

/** Where a developer setting lives. PROP is a system property (shell-only). */
enum class DevNamespace(val cli: String) { GLOBAL("global"), SECURE("secure"), SYSTEM("system"), PROP("prop") }

data class DevToggle(
    val id: String,
    val title: String,
    val namespace: DevNamespace,
    val key: String,
    val onValue: String,
    val offValue: String,
    /** True when a normal app cannot write it even with WRITE_SECURE_SETTINGS (private system keys, props). */
    val needsShell: Boolean,
    val readOnly: Boolean = false,
) {
    fun isOn(current: String?): Boolean = current != null && current.trim() == onValue
}

/** How a settings write can be performed on this device right now. */
enum class WriteRoute { DIRECT_WSS, SHIZUKU, ROOT }

data class WriteCapability(
    val hasWriteSecureSettings: Boolean,
    val shizukuReady: Boolean,
    val rootAllowed: Boolean,
)

sealed class RouteDecision {
    data class Use(val route: WriteRoute) : RouteDecision()
    data class Unavailable(val reason: String) : RouteDecision()
}

object DevOptions {
    val WIRELESS_ADB = DevToggle("adb_wifi", "Wireless debugging", DevNamespace.GLOBAL, "adb_wifi_enabled", "1", "0", needsShell = false)
    val USB_DEBUGGING = DevToggle("adb", "USB debugging", DevNamespace.GLOBAL, "adb_enabled", "1", "0", needsShell = false, readOnly = true)
    val SHOW_TAPS = DevToggle("show_taps", "Show taps", DevNamespace.SYSTEM, "show_touches", "1", "0", needsShell = true)
    val POINTER_LOCATION = DevToggle("pointer", "Pointer location", DevNamespace.SYSTEM, "pointer_location", "1", "0", needsShell = true)
    val LAYOUT_BOUNDS = DevToggle("layout_bounds", "Show layout bounds", DevNamespace.PROP, "debug.layout", "true", "false", needsShell = true)
    /** 7 = AC | USB | wireless. */
    val STAY_AWAKE = DevToggle("stay_awake", "Stay awake while charging", DevNamespace.GLOBAL, "stay_on_while_plugged_in", "7", "0", needsShell = false)

    val QUICK_TOGGLES = listOf(SHOW_TAPS, POINTER_LOCATION, LAYOUT_BOUNDS, STAY_AWAKE)

    val ANIMATION_KEYS = listOf("window_animation_scale", "transition_animation_scale", "animator_duration_scale")
    val ANIMATION_SCALES = listOf("0", "0.5", "1", "1.5", "2", "5", "10")

    private val KEY_RE = Regex("^[a-z0-9_.]{1,64}$")
    private val VALUE_RE = Regex("^[A-Za-z0-9_.-]{1,32}$")

    fun isSafeKey(key: String): Boolean = KEY_RE.matches(key)

    fun isSafeValue(value: String): Boolean = VALUE_RE.matches(value)

    /**
     * Picks a write route. Direct WRITE_SECURE_SETTINGS only works for global/secure keys;
     * private system keys and props need a shell (Shizuku or opted-in root).
     */
    fun route(cap: WriteCapability, toggleNeedsShell: Boolean): RouteDecision =
        when {
            !toggleNeedsShell && cap.hasWriteSecureSettings -> RouteDecision.Use(WriteRoute.DIRECT_WSS)
            cap.shizukuReady -> RouteDecision.Use(WriteRoute.SHIZUKU)
            cap.rootAllowed -> RouteDecision.Use(WriteRoute.ROOT)
            toggleNeedsShell ->
                RouteDecision.Unavailable(
                    "Needs Shizuku or root: this setting can only be changed from a shell. " +
                        "Start Shizuku (Settings → Shizuku) or allow root in the Dev panel.",
                )
            else ->
                RouteDecision.Unavailable(
                    "Needs Shizuku, root, or WRITE_SECURE_SETTINGS. Grant it once with: " +
                        "adb shell pm grant com.victorious.launch android.permission.WRITE_SECURE_SETTINGS",
                )
        }

    /** Shell argv that writes [value] for [namespace]/[key]. Throws on unsafe input. */
    fun writeArgv(namespace: DevNamespace, key: String, value: String): List<String> {
        require(isSafeKey(key)) { "bad key" }
        require(isSafeValue(value)) { "bad value" }
        return when (namespace) {
            // Poke the activity manager so running apps redraw with the new prop (what Settings does).
            DevNamespace.PROP -> listOf("sh", "-c", "setprop $key $value; service call activity 1599295570 >/dev/null 2>&1; echo ok")
            else -> listOf("settings", "put", namespace.cli, key, value)
        }
    }

    fun readArgv(namespace: DevNamespace, key: String): List<String> {
        require(isSafeKey(key)) { "bad key" }
        return when (namespace) {
            DevNamespace.PROP -> listOf("getprop", key)
            else -> listOf("settings", "get", namespace.cli, key)
        }
    }

    /** `adb connect` target text, or null when the port is unknown. */
    fun wirelessEndpoint(ip: String?, portProp: String?): String? {
        val port = portProp?.trim()?.toIntOrNull()?.takeIf { it in 1..65535 } ?: return null
        val host = ip?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return "$host:$port"
    }

    fun grantArgv(packageName: String, permission: String): List<String> = listOf("pm", "grant", packageName, permission)
}

object ShellQuote {
    /** POSIX single-quote a string for `sh -c`. */
    fun quote(s: String): String = "'" + s.replace("'", "'\\''") + "'"

    fun join(argv: List<String>): String = argv.joinToString(" ") { quote(it) }
}
