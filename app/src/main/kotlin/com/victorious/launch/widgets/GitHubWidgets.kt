package com.victorious.launch.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.victorious.launch.R
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.github.GitHubCopy
import com.victorious.launch.core.github.GitHubState
import com.victorious.launch.core.github.GitHubWidgetCopy
import com.victorious.launch.core.github.GitHubWidgetKind
import com.victorious.launch.core.status.WidgetAccent
import com.victorious.launch.core.theme.FleetThemePalettes
import com.victorious.launch.github.GitHubPrefs
import com.victorious.launch.github.GitHubRefresh
import com.victorious.launch.github.GitHubTokenStore
import com.victorious.launch.theme.FleetThemeResolver

object GitHubWidgets {
    fun updateAll(context: Context, state: GitHubState? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val resolved = state ?: currentState(context)
        GitHubWidgetKind.entries.forEach { kind ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass(kind)))
            ids.forEach { bind(context, mgr, it, kind, resolved) }
        }
    }

    fun currentState(context: Context): GitHubState {
        val prefs = GitHubPrefs(context)
        val hasToken =
            try {
                GitHubTokenStore(context).hasToken()
            } catch (_: Exception) {
                false
            }
        return prefs.state().copy(hasToken = hasToken, repos = prefs.repos())
    }

    fun bind(context: Context, mgr: AppWidgetManager, widgetId: Int, kind: GitHubWidgetKind, state: GitHubState) {
        val repo = GitHubPrefs(context).widgetRepo(widgetId)
        val copy = GitHubCopy.of(kind, state, repo, System.currentTimeMillis())
        mgr.updateAppWidget(widgetId, views(context, copy, widgetId))
    }

    fun views(context: Context, copy: GitHubWidgetCopy, widgetId: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_github)
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
                200 + copy.kind.ordinal * 1000 + widgetId,
                GitHubRefresh.openSettingsIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_root, open)
        val refresh =
            PendingIntent.getBroadcast(
                context,
                300 + copy.kind.ordinal * 1000 + widgetId,
                Intent(context, GitHubRefreshReceiver::class.java).setAction(GitHubRefresh.ACTION_REFRESH),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        views.setOnClickPendingIntent(R.id.widget_refresh, refresh)
        return views
    }

    fun bindOwned(context: Context, widgetId: Int, state: GitHubState) {
        val mgr = AppWidgetManager.getInstance(context)
        GitHubWidgetKind.entries.forEach { kind ->
            val ids = mgr.getAppWidgetIds(ComponentName(context, providerClass(kind)))
            if (widgetId in ids) bind(context, mgr, widgetId, kind, state)
        }
    }

        fun providerClass(kind: GitHubWidgetKind): Class<out AppWidgetProvider> =
        when (kind) {
            GitHubWidgetKind.PULLS -> GitHubPullsWidget::class.java
            GitHubWidgetKind.ACTIONS -> GitHubActionsWidget::class.java
            GitHubWidgetKind.RELEASES -> GitHubReleasesWidget::class.java
        }
}

abstract class GitHubStatusWidget(private val kind: GitHubWidgetKind) : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = GitHubWidgets.currentState(context)
        appWidgetIds.forEach { GitHubWidgets.bind(context, appWidgetManager, it, kind, state) }
        GitHubRefresh.schedule(context)
        GitHubRefresh.refreshNow(context)
    }

    override fun onEnabled(context: Context) {
        GitHubRefresh.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = GitHubPrefs(context)
        appWidgetIds.forEach { prefs.clearWidget(it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == GitHubRefresh.ACTION_REFRESH) {
            GitHubRefresh.refreshNow(context)
        }
    }
}

class GitHubPullsWidget : GitHubStatusWidget(GitHubWidgetKind.PULLS)

class GitHubActionsWidget : GitHubStatusWidget(GitHubWidgetKind.ACTIONS)

class GitHubReleasesWidget : GitHubStatusWidget(GitHubWidgetKind.RELEASES)

class GitHubRefreshReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != GitHubRefresh.ACTION_REFRESH) return
        GitHubRefresh.refreshNow(context)
    }
}
