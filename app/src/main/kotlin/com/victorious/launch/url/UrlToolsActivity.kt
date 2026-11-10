package com.victorious.launch.url

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.url.UrlHeuristics
import com.victorious.launch.theme.FleetThemeResolver
import com.victorious.launch.theme.VictorLaunchTheme

class UrlToolsActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings =
            try {
                VictorLaunchApp.instance.settings
            } catch (_: Exception) {
                null
            }
        val initial = urlFrom(intent)
        setContent {
            val snapshot = androidx.compose.runtime.remember { settings?.let { FleetThemeResolver.current(this, it) } }
            VictorLaunchTheme(snapshot) {
                UrlToolsScreen(context = this, initialUrl = initial, onClose = { finish() })
            }
        }
    }

    companion object {
        const val EXTRA_URL = "url"

        fun intent(context: Context, url: String? = null): Intent =
            Intent(context, UrlToolsActivity::class.java).apply {
                if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!url.isNullOrBlank()) putExtra(EXTRA_URL, url)
            }

        fun urlFrom(intent: Intent?): String? {
            if (intent == null) return null
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { UrlHeuristics.extractUrl(it) ?: it.trim().takeIf { s -> s.contains("://") } }?.let { return it }
            intent.getStringExtra(EXTRA_URL)?.let { UrlHeuristics.extractUrl(it) ?: it.trim().ifBlank { null } }?.let { return it }
            intent.dataString?.let { UrlHeuristics.extractUrl(it) ?: it }?.let { return it }
            return null
        }
    }
}
