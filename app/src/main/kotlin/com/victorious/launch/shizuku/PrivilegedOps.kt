package com.victorious.launch.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.os.IBinder
import com.victorious.launch.prefs.AppSettings
import rikka.shizuku.Shizuku

data class PrivilegedResult(val ok: Boolean, val detail: String)

/**
 * Binds a Shizuku UserService only after the user has enabled Shizuku
 * in Settings and granted the binder permission.
 */
class PrivilegedOps(
    private val context: Context,
    private val settings: AppSettings,
    private val session: ShizukuSession,
) {
    private var service: IPrivilegedService? = null

    private val args: Shizuku.UserServiceArgs =
        Shizuku.UserServiceArgs(ComponentName(context, PrivilegedUserService::class.java))
            .daemon(false)
            .processNameSuffix("priv")
            .debuggable(false)
            .version(1)

    private val connection =
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                service = if (binder != null) IPrivilegedService.Stub.asInterface(binder) else null
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service = null
            }
        }

    fun refreshBinding() {
        val snap = session.snapshot()
        if (!snap.ready) {
            unbind()
            return
        }
        if (service != null) return
        try {
            Shizuku.bindUserService(args, connection)
        } catch (_: Exception) {
        }
    }

    fun unbind() {
        try {
            Shizuku.unbindUserService(args, connection, true)
        } catch (_: Exception) {
        }
        service = null
    }

    fun freeze(packageName: String, frozen: Boolean): PrivilegedResult =
        call("freeze") { it.freeze(packageName, frozen) }

    fun hide(packageName: String, hidden: Boolean): PrivilegedResult =
        call("hide") { it.hide(packageName, hidden) }

    fun forceStop(packageName: String): PrivilegedResult =
        call("force-stop") { it.forceStop(packageName) }

    fun grantUsageAccess(packageName: String): PrivilegedResult =
        call("usage-access") { it.grantUsageAccess(packageName) }

    fun createManagedProfile(name: String): PrivilegedResult =
        call("create-profile") { it.createManagedProfile(name) }

    fun quietMode(userId: Int, quiet: Boolean): PrivilegedResult =
        call("quiet-mode") { it.quietMode(userId, quiet) }

    fun installExisting(packageName: String, userId: Int): PrivilegedResult =
        call("install-existing") { it.installExisting(packageName, userId) }

    private fun call(op: String, body: (IPrivilegedService) -> String): PrivilegedResult {
        if (!settings.isShizukuEnabled()) {
            return PrivilegedResult(false, "Shizuku is off (Settings → Shizuku)")
        }
        refreshBinding()
        val svc = service
        if (svc == null) {
            return PrivilegedResult(false, "Shizuku UserService not bound for $op")
        }
        return try {
            val detail = body(svc)
            PrivilegedResult(ok = !detail.startsWith("error"), detail = detail)
        } catch (e: Exception) {
            PrivilegedResult(false, e.message ?: op)
        }
    }
}
