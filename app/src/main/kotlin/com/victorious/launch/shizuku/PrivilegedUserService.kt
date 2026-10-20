package com.victorious.launch.shizuku

import android.content.Context
import com.victorious.launch.mods.ModsCommands
import com.victorious.launch.mods.RootModuleInstallRequest
import com.victorious.launch.mods.RootModuleKind

/**
 * Runs in a Shizuku UserService process as ADB (uid 2000) or root (uid 0).
 * This is not a normal app process — no [android.content.ContentResolver].
 */
class PrivilegedUserService : IPrivilegedService.Stub {
    @Suppress("unused")
    constructor()

    @Suppress("unused")
    constructor(context: Context)

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

    override fun destroy() {
        System.exit(0)
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
