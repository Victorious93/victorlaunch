package com.victorious.launch.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast

object GestureActions {
    /** Uses the hidden StatusBarManager API (EXPAND_STATUS_BAR is a normal permission). */
    fun expandNotifications(context: Context): Boolean =
        try {
            @Suppress("WrongConstant")
            val sbm = context.getSystemService("statusbar") ?: return false
            sbm.javaClass.getMethod("expandNotificationsPanel").invoke(sbm)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Could not expand notifications on this device", Toast.LENGTH_SHORT).show()
            false
        }

    fun lockScreen(context: Context) {
        if (LockScreenAccessibilityService.lock()) return
        Toast.makeText(
            context,
            "Turn on \"VictorLaunch gesture lock\" in Accessibility to lock with a gesture",
            Toast.LENGTH_LONG,
        ).show()
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchPackage(context: Context, catalog: AppCatalog, packageName: String) {
        if (catalog.launchPackage(packageName)) return
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent != null) {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } else {
            Toast.makeText(context, "$packageName is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
