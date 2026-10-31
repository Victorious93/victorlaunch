package com.victorious.launch.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.os.Build
import android.widget.Toast

/** One app shortcut (the "Compose", "New tab"… entries apps publish), as shown in the long-press menu. */
data class AppShortcut(val label: String, val info: ShortcutInfo)

enum class UninstallKind { UNINSTALL, UNINSTALL_UPDATES, NONE }

/**
 * Long-press actions for an app cell. Work-profile (Island) apps go through [LauncherApps] with the
 * app's own UserHandle, so App info / shortcuts target the right profile.
 */
object AppActions {
    private fun la(ctx: Context) = ctx.getSystemService(LauncherApps::class.java)

    fun uninstallKind(ctx: Context, app: LaunchableApp): UninstallKind = try {
        val ai = la(ctx).getApplicationInfo(app.packageName, 0, app.user)
        when {
            ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0 -> UninstallKind.UNINSTALL_UPDATES
            ai.flags and ApplicationInfo.FLAG_SYSTEM != 0 -> UninstallKind.NONE
            app.packageName == ctx.packageName -> UninstallKind.NONE
            else -> UninstallKind.UNINSTALL
        }
    } catch (e: Exception) { UninstallKind.UNINSTALL }

    /** Opens Android's uninstall confirmation. Android always asks the user; nothing is removed silently. */
    fun uninstall(ctx: Context, app: LaunchableApp) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { toast(ctx, "Couldn't open uninstaller") }
    }

    fun appInfo(ctx: Context, app: LaunchableApp) {
        val cn = ComponentName.unflattenFromString(app.component)
        try {
            if (cn != null) la(ctx).startAppDetailsActivity(cn, app.user, null, null)
            else ctx.startActivity(
                Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) { toast(ctx, "Couldn't open app info") }
    }

    fun openInPlayStore(ctx: Context, app: LaunchableApp) {
        val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try { ctx.startActivity(market) } catch (e: Exception) {
            try { ctx.startActivity(web) } catch (_: Exception) { toast(ctx, "No store or browser installed") }
        }
    }

    /** Split screen via FLAG_ACTIVITY_LAUNCH_ADJACENT; Android decides whether it can actually split. */
    fun openSplit(ctx: Context, app: LaunchableApp) {
        val cn = ComponentName.unflattenFromString(app.component) ?: return
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        try { ctx.startActivity(i) } catch (e: Exception) { toast(ctx, "Split screen not available") }
    }

    /**
     * Static + dynamic shortcuts. Only works while VictorLaunch is the default home app
     * (Android grants shortcut access to the default launcher only); returns empty otherwise.
     */
    fun shortcuts(ctx: Context, app: LaunchableApp): List<AppShortcut> {
        if (Build.VERSION.SDK_INT < 25) return emptyList()
        val launcher = la(ctx)
        return try {
            if (!launcher.hasShortcutHostPermission()) return emptyList()
            val q = LauncherApps.ShortcutQuery()
                .setPackage(app.packageName)
                .setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED
                )
            (launcher.getShortcuts(q, app.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedBy { it.rank }
                .distinctBy { it.id }
                .take(5)
                .map { AppShortcut((it.shortLabel ?: it.longLabel ?: it.id).toString(), it) }
        } catch (e: Exception) { emptyList() }
    }

    fun startShortcut(ctx: Context, s: AppShortcut) {
        if (Build.VERSION.SDK_INT < 25) return
        try { la(ctx).startShortcut(s.info, null, null) } catch (e: Exception) { toast(ctx, "Couldn't open shortcut") }
    }

    private fun toast(ctx: Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
}
