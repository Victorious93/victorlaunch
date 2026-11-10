package com.victorious.launch.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.victorious.launch.R
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.device.DeviceCopy
import com.victorious.launch.core.device.DeviceReadings
import com.victorious.launch.core.device.DeviceWidgetCopy
import com.victorious.launch.core.device.DeviceWidgetKind
import com.victorious.launch.core.status.WidgetAccent
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.device.DeviceSnapshotter
import com.victorious.launch.launcher.HomeActivity
import com.victorious.launch.launcher.LauncherIntents
import com.victorious.launch.theme.FleetThemeResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DeviceWidgets {
    const val ACTION_REFRESH = "com.victorious.launch.action.DEVICE_REFRESH"
    const val INTERVAL_MS = 60_000L

    fun updateAll(context: Context, readings: DeviceReadings? = null) {
        val resolved = readings ?: runCatching { snapshot(context) }.getOrNull()
        val mgr = AppWidgetManager.getInstance(context)
        DeviceWidgetKind.entries.forEach { kind ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass(kind)))
            ids.forEach { bind(context, mgr, it, kind, resolved) }
        }
        schedule(context)
    }

    fun snapshot(context: Context): DeviceReadings {
        val settings =
            try {
                VictorLaunchApp.instance.settings
            } catch (_: Exception) {
                throw IllegalStateException("app not ready")
            }
        return DeviceSnapshotter.read(context, settings)
    }

    fun bind(context: Context, mgr: AppWidgetManager, widgetId: Int, kind: DeviceWidgetKind, readings: DeviceReadings?) {
        val copy =
            if (readings != null) {
                DeviceCopy.of(kind, readings)
            } else {
                DeviceWidgetCopy(kind, title(kind), "Waiting…", "Open Settings → Device widgets", WidgetAccent.UNKNOWN)
            }
        mgr.updateAppWidget(widgetId, views(context, copy, widgetId))
    }

    fun views(context: Context, copy: DeviceWidgetCopy, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_device)
        views.setTextViewText(R.id.widget_title, copy.title)
        views.setTextViewText(R.id.widget_line1, copy.line1)
        views.setTextViewText(R.id.widget_line2, copy.line2)
        val palette =
            try {
                FleetThemeResolver.current(context, VictorLaunchApp.instance.settings).palette
            } catch (_: Exception) {
                FleetThemePalettes.NEON
            }
        val color =
            when (copy.accent) {
                WidgetAccent.OK -> FleetThemePalettes.parseArgb(palette.primary)
                WidgetAccent.STALE -> FleetThemePalettes.parseArgb(palette.stale)
                WidgetAccent.DOWN -> FleetThemePalettes.parseArgb(palette.danger)
                WidgetAccent.UNKNOWN -> FleetThemePalettes.parseArgb(palette.muted)
            }
        views.setInt(R.id.widget_root, "setBackgroundColor", FleetThemePalettes.parseArgb(palette.tile))
        views.setTextColor(R.id.widget_title, FleetThemePalettes.parseArgb(palette.secondary))
        views.setTextColor(R.id.widget_line1, color)
        views.setTextColor(R.id.widget_line2, FleetThemePalettes.parseArgb(palette.muted))
        views.setTextColor(R.id.widget_refresh, FleetThemePalettes.parseArgb(palette.secondary))
        val open =
            PendingIntent.getActivity(
                context,
                400 + copy.kind.ordinal * 1000 + widgetId,
                Intent(context, HomeActivity::class.java)
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    .putExtra(LauncherIntents.EXTRA_OPEN, LauncherIntents.DEVICE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        val refresh =
            PendingIntent.getBroadcast(
                context,
                500 + copy.kind.ordinal * 1000 + widgetId,
                Intent(context, DeviceRefreshReceiver::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        return views
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = refreshIntent(context)
        am.setRepeating(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + INTERVAL_MS, INTERVAL_MS, pi)
    }

    fun refreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            42,
            Intent(context, DeviceRefreshReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun refreshAsync(context: Context) {
        val app = context.applicationContext
        scope.launch {
            runCatching { updateAll(app, snapshot(app)) }.onFailure { updateAll(app, null) }
        }
    }

    fun providerClass(kind: DeviceWidgetKind): Class<out AppWidgetProvider> =
        when (kind) {
            DeviceWidgetKind.INFO -> DeviceInfoWidget::class.java
            DeviceWidgetKind.ROOT -> DeviceRootWidget::class.java
            DeviceWidgetKind.RESOURCES -> DeviceResourcesWidget::class.java
        }

    private fun title(kind: DeviceWidgetKind): String =
        when (kind) {
            DeviceWidgetKind.INFO -> "Device"
            DeviceWidgetKind.ROOT -> "Root"
            DeviceWidgetKind.RESOURCES -> "Resources"
        }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

abstract class DeviceStatusWidget(private val kind: DeviceWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { DeviceWidgets.bind(context, appWidgetManager, it, kind, null) }
        DeviceWidgets.schedule(context)
        DeviceWidgets.refreshAsync(context)
    }

    override fun onEnabled(context: Context) {
        DeviceWidgets.schedule(context)
        DeviceWidgets.refreshAsync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == DeviceWidgets.ACTION_REFRESH) {
            DeviceWidgets.refreshAsync(context)
        }
    }
}

class DeviceInfoWidget : DeviceStatusWidget(DeviceWidgetKind.INFO)

class DeviceRootWidget : DeviceStatusWidget(DeviceWidgetKind.ROOT)

class DeviceResourcesWidget : DeviceStatusWidget(DeviceWidgetKind.RESOURCES)

class DeviceRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DeviceWidgets.ACTION_REFRESH) return
        DeviceWidgets.refreshAsync(context)
    }
}
