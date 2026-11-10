package com.victorious.launch.github

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.github.RepoSlug
import com.victorious.launch.theme.FleetThemeResolver
import com.victorious.launch.theme.LocalFleetColors
import com.victorious.launch.theme.VictorLaunchTheme
import com.victorious.launch.widgets.GitHubWidgets

class GitHubWidgetConfigureActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId =
            intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val settings =
            try {
                VictorLaunchApp.instance.settings
            } catch (_: Exception) {
                null
            }
        setContent {
            val snapshot = remember { settings?.let { FleetThemeResolver.current(this, it) } }
            VictorLaunchTheme(snapshot) {
                val colors = LocalFleetColors.current
                val prefs = remember { GitHubPrefs(this) }
                val repos = remember { prefs.repos() }
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text("GitHub widget", color = colors.primary, fontSize = 22.sp)
                    Text(
                        "Pick a repository for this widget, or show all configured repos. Add tokens and repos in Settings → GitHub widgets.",
                        color = colors.muted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
                    )
                    Button(
                        onClick = { finishWith(widgetId, null) },
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) { Text("All repositories") }
                    repos.forEach { repo ->
                        OutlinedButton(
                            onClick = { finishWith(widgetId, repo) },
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) { Text(repo.full) }
                    }
                    if (repos.isEmpty()) {
                        Text("No repositories saved yet.", color = colors.muted, fontSize = 13.sp)
                    }
                }
            }
        }
    }

    private fun finishWith(widgetId: Int, repo: RepoSlug?) {
        GitHubPrefs(this).setWidgetRepo(widgetId, repo)
        val state = GitHubWidgets.currentState(this)
        GitHubWidgets.bindOwned(this, widgetId, state)
        GitHubRefresh.schedule(this)
        GitHubRefresh.refreshNow(this)
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        finish()
    }
}
