package com.victorious.launch.launcher

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.drawable.Drawable
import android.os.UserHandle
import android.os.UserManager

data class LaunchableApp(
    val packageName: String,
    val label: String,
    val component: String,
    val user: UserHandle,
    val userSerial: Long,
    val isWorkProfile: Boolean,
    val category: com.victorious.launch.core.launcher.AppCategory = com.victorious.launch.core.launcher.AppCategory.OTHER,
)

class AppCatalog(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    fun allLaunchable(): List<LaunchableApp> {
        val mine = android.os.Process.myUserHandle()
        val mySerial = userManager.getSerialNumberForUser(mine)
        return userManager.userProfiles.flatMap { user ->
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                toLaunchable(info, serial, serial != mySerial)
            }
        }.sortedBy { it.label.lowercase() }
    }

    fun personal(): List<LaunchableApp> = allLaunchable().filterNot { it.isWorkProfile }

    fun island(): List<LaunchableApp> = allLaunchable().filter { it.isWorkProfile }

    fun loadIcon(app: LaunchableApp): Drawable? {
        val user = app.user
        val list = launcherApps.getActivityList(app.packageName, user)
        val info = list.firstOrNull { it.componentName.flattenToShortString() == app.component } ?: list.firstOrNull()
        return info?.getBadgedIcon(0)
    }

    fun launch(app: LaunchableApp) {
        val cn =
            android.content.ComponentName.unflattenFromString(app.component)
                ?: return
        launcherApps.startMainActivity(cn, app.user, null, null)
    }

    fun launchPackage(packageName: String): Boolean {
        val hit = personal().firstOrNull { it.packageName == packageName } ?: return false
        launch(hit)
        return true
    }

    private fun toLaunchable(info: LauncherActivityInfo, serial: Long, work: Boolean): LaunchableApp =
        LaunchableApp(
            packageName = info.componentName.packageName,
            label = info.label.toString(),
            component = info.componentName.flattenToShortString(),
            user = info.user,
            userSerial = serial,
            isWorkProfile = work,
            category =
                com.victorious.launch.core.launcher.DrawerModel.categorize(
                    packageName = info.componentName.packageName,
                    androidCategory = info.applicationInfo.category,
                    isSystem = (info.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0,
                ),
        )

    companion object {
        fun homeIntent(): Intent =
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    }
}
