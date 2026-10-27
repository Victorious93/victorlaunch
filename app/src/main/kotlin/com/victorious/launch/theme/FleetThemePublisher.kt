package com.victorious.launch.theme

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.victorious.launch.core.theme.FleetThemeAccess
import com.victorious.launch.core.theme.FleetThemeContract
import com.victorious.launch.core.theme.FleetThemeSnapshot
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.widgets.FleetWidgets
import com.victorious.launch.widgets.TrayCache

object FleetThemePublisher {
    val CONTENT_URI: Uri = Uri.parse(FleetThemeContract.contentUri())

    fun publish(context: Context, settings: AppSettings) {
        val snap = FleetThemeResolver.current(context, settings)
        context.contentResolver.notifyChange(CONTENT_URI, null)
        broadcast(context, settings, snap)
        FleetWidgets.updateAll(context, TrayCache.load())
    }

    fun broadcast(context: Context, settings: AppSettings, snap: FleetThemeSnapshot) {
        val extras =
            Intent(FleetThemeContract.ACTION_CHANGED)
                .putExtra(FleetThemeContract.EXTRA_SCHEMA_VERSION, snap.schemaVersion)
                .putExtra(FleetThemeContract.EXTRA_MODE, snap.mode.name)
                .putExtra(FleetThemeContract.EXTRA_ICON_STYLE, snap.iconStyle.name)
                .putExtra(FleetThemeContract.EXTRA_UPDATED_AT, snap.updatedAtMs)
        FleetThemeAccess.broadcastPackages(settings.themeExtraPins()).forEach { pkg ->
            if (!isInstalled(context, pkg)) return@forEach
            context.sendBroadcast(Intent(extras).setPackage(pkg))
        }
    }

    private fun isInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
}
