package com.victorious.launch.shizuku

import android.content.Context
import android.telephony.TelephonyManager
import com.victorious.launch.mods.ModsCommands
import com.victorious.launch.mods.RootModuleInstallRequest
import com.victorious.launch.mods.RootModuleKind
import com.victorious.launch.network.NetworkCommands

/**
 * Runs in a Shizuku UserService process as ADB (uid 2000) or root (uid 0).
 * This is not a normal app process — no [android.content.ContentResolver].
 */
class PrivilegedUserService : IPrivilegedService.Stub {
    private val appContext: Context?

    @Suppress("unused")
    constructor() {
        appContext = null
    }

    @Suppress("unused")
    constructor(context: Context) {
        appContext = context.applicationContext ?: context
    }

    override fun freeze(packageName: String, frozen: Boolean, userId: Int): String =
        run(PrivilegedCommands.freeze(packageName, frozen, userId))

    override fun hide(packageName: String, hidden: Boolean, userId: Int): String =
        run(PrivilegedCommands.hide(packageName, hidden, userId))

    override fun forceStop(packageName: String, userId: Int): String =
        run(PrivilegedCommands.forceStop(packageName, userId))

    override fun grantUsageAccess(packageName: String): String =
        run(PrivilegedCommands.grantUsageAccess(packageName))

    override fun createManagedProfile(name: String): String =
        run(PrivilegedCommands.createManagedProfile(name))

    override fun quietMode(userId: Int, quiet: Boolean): String =
        run(PrivilegedCommands.quietMode(userId, quiet))

    override fun installExisting(packageName: String, userId: Int): String =
        run(PrivilegedCommands.installExisting(packageName, userId))

    override fun grantWriteSecureSettings(packageName: String): String =
        run(ModsCommands.grantWriteSecureSettings(packageName))

    override fun listAdbModules(): String = run(ModsCommands.listAdbModules())

    override fun packagePath(packageName: String): String = run(ModsCommands.packagePath(packageName))

    override fun installRootModule(kind: String, zipPath: String): String {
        val parsed = RootModuleKind.fromId(kind) ?: return "error: unknown root kind"
        val err = RootModuleInstallRequest(parsed, zipPath).validate()
        if (err != null) return "error: $err"
        return run(ModsCommands.installModule(parsed, zipPath))
    }

    override fun setAllowedNetworkTypes(bitmask: Long): String {
        if (bitmask == 0L) return "error: empty bitmask"
        val api = invokeAllowedNetworkTypes(bitmask)
        val mode = run(NetworkCommands.setPreferredNetworkMode(NetworkCommands.MODE_NR_LTE))
        return "telephony=$api; preferred_network_mode=$mode"
    }

    override fun setAllowed2g(allowed: Boolean): String {
        val a = run(NetworkCommands.setAllowed2g(allowed))
        val b = run(NetworkCommands.setEnable2g(allowed))
        return "allowed_2g=$a; enable_2g=$b"
    }

    override fun setPreferredNetworkMode(mode: Int): String =
        run(NetworkCommands.setPreferredNetworkMode(mode))

    override fun radioRecover(): String {
        val onCmd = run(NetworkCommands.airplaneCmd(true))
        val onSet = run(NetworkCommands.airplaneSetting(true))
        val onBc = run(NetworkCommands.airplaneBroadcast(true))
        try {
            Thread.sleep(1_500)
        } catch (_: InterruptedException) {
        }
        val offCmd = run(NetworkCommands.airplaneCmd(false))
        val offSet = run(NetworkCommands.airplaneSetting(false))
        val offBc = run(NetworkCommands.airplaneBroadcast(false))
        return "on($onCmd/$onSet/$onBc) off($offCmd/$offSet/$offBc)"
    }

    override fun destroy() {
        System.exit(0)
    }

    private fun invokeAllowedNetworkTypes(bitmask: Long): String {
        val ctx = appContext ?: return "no context"
        return try {
            val tm = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val method =
                TelephonyManager::class.java.getMethod(
                    "setAllowedNetworkTypesForReason",
                    Integer.TYPE,
                    java.lang.Long.TYPE,
                )
            method.invoke(tm, 0, bitmask)
            "ok bitmask=$bitmask"
        } catch (e: Exception) {
            "unsupported:${e.message}"
        }
    }

    private fun run(command: List<String>): String {
        return try {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            val out = process.inputStream.bufferedReader().use { it.readText() }.trim()
            val code = process.waitFor()
            if (code == 0) out.ifBlank { "ok" } else "error $code: $out"
        } catch (e: Exception) {
            "error: ${e.message}"
        }
    }
}
