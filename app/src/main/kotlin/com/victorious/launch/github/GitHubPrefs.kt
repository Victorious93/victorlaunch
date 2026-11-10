package com.victorious.launch.github

import android.content.Context
import com.victorious.launch.core.github.GitHubApi
import com.victorious.launch.core.github.GitHubState
import com.victorious.launch.core.github.GitHubStateCodec
import com.victorious.launch.core.github.RepoSlug

/** Public GitHub widget settings and cached snapshots. Never stores the token. */
class GitHubPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun repos(): List<RepoSlug> = GitHubStateCodec.decodeRepos(prefs.getString(KEY_REPOS, null))

    fun setRepos(repos: List<RepoSlug>) {
        prefs.edit().putString(KEY_REPOS, GitHubStateCodec.encodeRepos(repos.distinctBy { it.full })).apply()
    }

    fun addRepo(raw: String): RepoSlug? {
        val slug = GitHubApi.parseRepo(raw) ?: return null
        setRepos(repos() + slug)
        return slug
    }

    fun removeRepo(slug: RepoSlug) {
        setRepos(repos().filterNot { it.full == slug.full })
        val stale = prefs.all.keys.filter { it.startsWith(KEY_WIDGET) && prefs.getString(it, null) == slug.full }
        if (stale.isNotEmpty()) {
            val e = prefs.edit()
            stale.forEach { e.remove(it) }
            e.apply()
        }
    }

    fun state(): GitHubState = GitHubStateCodec.decode(prefs.getString(KEY_STATE, null)).copy(
        repos = repos(),
        hasToken = false, // filled in by the refresh layer
    )

    fun saveState(state: GitHubState) {
        // Drop any accidental token-like field by going through the codec.
        prefs.edit().putString(KEY_STATE, GitHubStateCodec.encode(state.copy(repos = repos()))).apply()
    }

    fun widgetRepo(widgetId: Int): RepoSlug? {
        val raw = prefs.getString(KEY_WIDGET + widgetId, null) ?: return null
        if (raw == ALL) return null
        return GitHubApi.parseRepo(raw)
    }

    fun setWidgetRepo(widgetId: Int, repo: RepoSlug?) {
        prefs.edit().putString(KEY_WIDGET + widgetId, repo?.full ?: ALL).apply()
    }

    fun clearWidget(widgetId: Int) {
        prefs.edit().remove(KEY_WIDGET + widgetId).apply()
    }

    companion object {
        const val FILE = "github_widgets"
        const val ALL = "*"
        private const val KEY_REPOS = "repos"
        private const val KEY_STATE = "state"
        private const val KEY_WIDGET = "widget_"
    }
}
