package com.victorious.launch.dev

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.dev.DevNamespace
import com.victorious.launch.core.dev.DevOptions
import com.victorious.launch.core.dev.RouteDecision
import com.victorious.launch.core.dev.WriteCapability
import com.victorious.launch.core.dev.WriteRoute
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.shizuku.PrivilegedResult

/** Reads and writes developer settings via WRITE_SECURE_SETTINGS, Shizuku, or opted-in root. */
class DevSettings(private val context: Context, private val settings: AppSettings) {
    private val resolver get() = context.contentResolver

    fun hasWriteSecureSettings(): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED

    fun hasReadLogs(): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_LOGS) == PackageManager.PERMISSION_GRANTED

    fun capability(): WriteCapability =
        WriteCapability(
            hasWriteSecureSettings = hasWriteSecureSettings(),
            shizukuReady = VictorLaunchApp.instance.privileged.isReady(),
            rootAllowed = settings.isRootShellAllowed(),
        )

    fun read(ns: DevNamespace, key: String): String? =
        try {
            when (ns) {
                DevNamespace.GLOBAL -> Settings.Global.getString(resolver, key)
                DevNamespace.SECURE -> Settings.Secure.getString(resolver, key)
                DevNamespace.SYSTEM -> Settings.System.getString(resolver, key)
                DevNamespace.PROP -> readProp(key)
            }
        } catch (_: Exception) {
            null
        }

    fun readProp(key: String): String? =
        try {
            val p = ProcessBuilder(DevOptions.readArgv(DevNamespace.PROP, key)).redirectErrorStream(true).start()
            val out = p.inputStream.bufferedReader().readText().trim()
            p.waitFor()
            out.ifBlank { null }
        } catch (_: Exception) {
            null
        }

    /** Whether the write can happen, and if not, a human reason. */
    fun decision(needsShell: Boolean): RouteDecision = DevOptions.route(capability(), needsShell)

    fun write(ns: DevNamespace, key: String, value: String, needsShell: Boolean): PrivilegedResult {
        val decision = decision(needsShell)
        if (decision is RouteDecision.Unavailable) return PrivilegedResult(false, decision.reason)
        val route = (decision as RouteDecision.Use).route
        val argv =
            try {
                DevOptions.writeArgv(ns, key, value)
            } catch (e: IllegalArgumentException) {
                return PrivilegedResult(false, "Refused: ${e.message}")
            }
        return when (route) {
            WriteRoute.DIRECT_WSS ->
                try {
                    val ok =
                        when (ns) {
                            DevNamespace.GLOBAL -> Settings.Global.putString(resolver, key, value)
                            DevNamespace.SECURE -> Settings.Secure.putString(resolver, key, value)
                            else -> false
                        }
                    if (ok) PrivilegedResult(true, "ok") else shellFallback(argv)
                } catch (_: Exception) {
                    shellFallback(argv)
                }
            WriteRoute.SHIZUKU -> VictorLaunchApp.instance.privileged.exec(argv)
            WriteRoute.ROOT -> RootShell.run(settings, argv)
        }
    }

    /** Runs a shell argv through Shizuku, else opted-in root. */
    fun shell(argv: List<String>): PrivilegedResult {
        val priv = VictorLaunchApp.instance.privileged
        return when {
            priv.isReady() -> priv.exec(argv)
            settings.isRootShellAllowed() -> RootShell.run(settings, argv)
            else -> PrivilegedResult(false, "Needs Shizuku or root")
        }
    }

    private fun shellFallback(argv: List<String>): PrivilegedResult {
        val cap = capability()
        return when {
            cap.shizukuReady -> VictorLaunchApp.instance.privileged.exec(argv)
            cap.rootAllowed -> RootShell.run(settings, argv)
            else -> PrivilegedResult(false, "The system refused the write; Shizuku or root needed")
        }
    }

    fun grantToSelf(permission: String): PrivilegedResult = shell(DevOptions.grantArgv(context.packageName, permission))
}
