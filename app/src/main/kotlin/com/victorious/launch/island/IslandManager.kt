package com.victorious.launch.island

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.UserHandle
import android.os.UserManager
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.shizuku.PrivilegedResult

class IslandManager(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun workProfile(): UserHandle? {
        val mine = android.os.Process.myUserHandle()
        return userManager.userProfiles.firstOrNull { it != mine }
    }

    fun hasWorkProfile(): Boolean = workProfile() != null

    fun isProfileOwner(): Boolean = dpm.isProfileOwnerApp(context.packageName)

    fun provisionIntent(): Intent =
        Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                IslandAdminReceiver.component(context),
            )
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }

    fun pauseWorkProfile(): Boolean {
        val user = workProfile() ?: return false
        val viaShizuku = tryShizukuQuiet(user, true)
        if (viaShizuku) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            userManager.requestQuietModeEnabled(true, user)
        } else {
            false
        }
    }

    fun resumeWorkProfile(): Boolean {
        val user = workProfile() ?: return false
        val viaShizuku = tryShizukuQuiet(user, false)
        if (viaShizuku) return true
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            userManager.requestQuietModeEnabled(false, user)
        } else {
            false
        }
    }

    private fun tryShizukuQuiet(user: UserHandle, quiet: Boolean): Boolean {
        val ops =
            try {
                VictorLaunchApp.instance.privileged
            } catch (_: Exception) {
                return false
            }
        return ops.quietMode(user.identifier, quiet).ok
    }

    fun isQuietMode(): Boolean {
        val user = workProfile() ?: return false
        return userManager.isQuietModeEnabled(user)
    }

    fun sendCommand(cmd: String, packageName: String) {
        if (tryShizuku(cmd, packageName).ok) return
        val intent =
            Intent(IslandCommandReceiver.ACTION).apply {
                setPackage(context.packageName)
                putExtra(IslandCommandReceiver.EXTRA_CMD, cmd)
                putExtra(IslandCommandReceiver.EXTRA_PACKAGE, packageName)
                addCategory(Intent.CATEGORY_DEFAULT)
            }
        if (isProfileOwner()) {
            IslandCommandReceiver().onReceive(context, intent)
            return
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            context.sendBroadcast(intent)
        }
    }

    fun tryShizuku(cmd: String, packageName: String): PrivilegedResult {
        val ops =
            try {
                VictorLaunchApp.instance.privileged
            } catch (_: Exception) {
                return PrivilegedResult(false, "Shizuku not initialised")
            }
        val user = workProfile()?.identifier ?: 0
        return when (cmd) {
            IslandCommandReceiver.CMD_CLONE -> ops.installExisting(packageName, user)
            IslandCommandReceiver.CMD_FREEZE -> ops.freeze(packageName, true)
            IslandCommandReceiver.CMD_UNFREEZE -> ops.freeze(packageName, false)
            IslandCommandReceiver.CMD_HIDE -> ops.hide(packageName, true)
            IslandCommandReceiver.CMD_UNHIDE -> ops.hide(packageName, false)
            else -> PrivilegedResult(false, "unknown cmd")
        }
    }

    fun removeWorkProfile() {
        if (isProfileOwner()) {
            dpm.wipeData(0)
        }
    }
}
