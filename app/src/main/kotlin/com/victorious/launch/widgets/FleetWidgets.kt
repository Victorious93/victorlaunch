package com.victorious.launch.widgets

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.widget.RemoteViews
import com.victorious.launch.R
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.status.FleetWidgetCopy
import com.victorious.launch.core.status.FleetWidgetCopyFactory
import com.victorious.launch.core.status.FleetWidgetKind
import com.victorious.launch.core.status.PublishedTray
import com.victorious.launch.core.status.StatusFreshness
import com.victorious.launch.core.status.StatusTrayAggregator
import com.victorious.launch.core.status.WidgetAccent
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.launcher.HomeActivity
import com.victorious.launch.theme.FleetThemeResolver
import com.victorious.launch.status.ControllerClient
import com.victorious.launch.status.PullResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FleetWidgets {
    const val ACTION_REFRESH = "com.victorious.launch.action.WIDGET_REFRESH"

    fun updateAll(context: Context, tray: PublishedTray?) {
        val mgr = AppWidgetManager.getInstance(context)
        FleetWidgetKind.entries.forEach { kind ->
            val cn = ComponentName(context, providerClass(kind))
            val ids = mgr.getAppWidgetIds(cn)
            ids.forEach { id -> bind(context, mgr, id, kind, tray) }
        }
        schedule(context)
    }

    fun bind(
        context: Context,
        mgr: AppWidgetManager,
        widgetId: Int,
        kind: FleetWidgetKind,
        tray: PublishedTray?,
    ) {
        val copy = FleetWidgetCopyFactory.from(tray, kind, System.currentTimeMillis())
        mgr.updateAppWidget(widgetId, views(context, copy))
    }

    fun views(context: Context, copy: FleetWidgetCopy): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_fleet_status)
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
        val click =
            PendingIntent.getActivity(
                context,
                copy.kind.ordinal,
                Intent(context, HomeActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_root, click)
        return views
    }

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = refreshIntent(context)
        am.setRepeating(
            AlarmManager.ELAPSED_REALTIME,
            SystemClock.elapsedRealtime() + StatusFreshness.PULL_INTERVAL_MS,
            StatusFreshness.PULL_INTERVAL_MS,
            pi,
        )
    }

    fun refreshIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(ACTION_REFRESH).setPackage(context.packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun providerClass(kind: FleetWidgetKind): Class<out AppWidgetProvider> =
        when (kind) {
            FleetWidgetKind.MESH_VPN -> MeshVpnWidget::class.java
            FleetWidgetKind.BROWSER -> BrowserWidget::class.java
            FleetWidgetKind.MESSAGING -> MessagingWidget::class.java
            FleetWidgetKind.HEALTH -> HealthWidget::class.java
        }
}

abstract class FleetStatusWidget(private val kind: FleetWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val tray = lastTray()
        appWidgetIds.forEach { FleetWidgets.bind(context, appWidgetManager, it, kind, tray) }
        pullAndPublish(context)
        FleetWidgets.schedule(context)
    }

    override fun onEnabled(context: Context) {
        FleetWidgets.schedule(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == FleetWidgets.ACTION_REFRESH) {
            pullAndPublish(context)
        }
    }

    private fun lastTray(): PublishedTray? = TrayCache.load()

    private fun pullAndPublish(context: Context) {
        val app =
            try {
                VictorLaunchApp.instance
            } catch (_: Exception) {
                return
            }
        scope.launch {
            when (val pull = ControllerClient(context, app.settings).pullSnapshots()) {
                is PullResult.Ok -> {
                    val tray = StatusTrayAggregator().publish(pull.snapshots)
                    TrayCache.save(tray)
                    FleetWidgets.updateAll(context, tray)
                }
                else -> FleetWidgets.updateAll(context, TrayCache.load())
            }
        }
    }

    companion object {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}

class MeshVpnWidget : FleetStatusWidget(FleetWidgetKind.MESH_VPN)
class BrowserWidget : FleetStatusWidget(FleetWidgetKind.BROWSER)
class MessagingWidget : FleetStatusWidget(FleetWidgetKind.MESSAGING)
class HealthWidget : FleetStatusWidget(FleetWidgetKind.HEALTH)

class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != FleetWidgets.ACTION_REFRESH) return
        MeshVpnWidget().onReceive(context, intent)
    }
}

object TrayCache {
    @Volatile
    private var last: PublishedTray? = null

    fun save(tray: PublishedTray) {
        last = tray
    }

    fun load(): PublishedTray? = last
}
