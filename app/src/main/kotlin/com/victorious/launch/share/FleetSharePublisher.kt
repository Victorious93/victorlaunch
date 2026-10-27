package com.victorious.launch.share

import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import com.victorious.launch.R
import com.victorious.launch.core.share.FleetShareCatalog
import com.victorious.launch.core.share.ShareTargetStatus
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.security.SigningCerts

object FleetSharePublisher {
    const val EXTRA_TARGET = "share_target_id"
    const val CATEGORY = "com.victorious.launch.share.FLEET"

    fun installedCerts(context: Context): Map<String, List<String>> {
        val pm = context.packageManager
        val out = LinkedHashMap<String, List<String>>()
        FleetShareCatalog.ALL.flatMap { it.packageNames }.distinct().forEach { pkg ->
            val certs = SigningCerts.forPackage(pm, pkg)
            if (certs.isNotEmpty()) out[pkg] = certs
        }
        return out
    }

    fun statuses(context: Context, settings: AppSettings): List<ShareTargetStatus> {
        return FleetShareCatalog.ALL.map {
            FleetShareCatalog.status(it, installedCerts(context), settings.sharePins())
        }
    }

    fun publish(context: Context, settings: AppSettings) {
        if (Build.VERSION.SDK_INT < 25) return
        val mgr = context.getSystemService(ShortcutManager::class.java) ?: return
        val ready = FleetShareCatalog.ready(installedCerts(context), settings.sharePins())
        val shortcuts =
            ready.map { row ->
                ShortcutInfo.Builder(context, row.spec.id)
                    .setShortLabel(row.spec.label)
                    .setLongLabel("Share to ${row.spec.label}")
                    .setIcon(Icon.createWithResource(context, R.mipmap.ic_launcher))
                    .setCategories(setOf(CATEGORY))
                    .setIntent(
                        Intent(context, ShareCatcherActivity::class.java).apply {
                            action = Intent.ACTION_SEND
                            putExtra(EXTRA_TARGET, row.spec.id)
                            setPackage(context.packageName)
                        },
                    )
                    .build()
            }
        mgr.dynamicShortcuts = shortcuts
    }
}
