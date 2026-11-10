package com.victorious.launch.github

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.victorious.launch.core.github.GitHubApi
import com.victorious.launch.core.github.GitHubError
import com.victorious.launch.core.github.GitHubParse
import com.victorious.launch.core.github.GitHubState
import com.victorious.launch.core.github.RateLimit
import com.victorious.launch.core.github.RepoSlug
import com.victorious.launch.core.github.RepoSnapshot
import com.victorious.launch.launcher.HomeActivity
import com.victorious.launch.launcher.LauncherIntents
import com.victorious.launch.widgets.GitHubWidgets
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object GitHubRefresh {
    const val PERIODIC = "github_widgets_periodic"
    const val ONCE = "github_widgets_once"
    const val ACTION_REFRESH = "com.victorious.launch.action.GITHUB_REFRESH"
    const val MIN_PERIOD_MINUTES = 15L

    fun schedule(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val periodic =
            PeriodicWorkRequestBuilder<GitHubRefreshWorker>(MIN_PERIOD_MINUTES, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
    }

    fun refreshNow(context: Context) {
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val once = OneTimeWorkRequestBuilder<GitHubRefreshWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.REPLACE, once)
    }

    fun openSettingsIntent(context: Context): Intent =
        Intent(context, HomeActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(LauncherIntents.EXTRA_OPEN, LauncherIntents.GITHUB)

    suspend fun run(context: Context): GitHubState =
        withContext(Dispatchers.IO) {
            val app = context.applicationContext
            val prefs = GitHubPrefs(app)
            val store = GitHubTokenStore(app)
            val repos = prefs.repos()
            val now = System.currentTimeMillis()
            if (!store.available()) {
                val state =
                    GitHubState(
                        repos = repos,
                        hasToken = false,
                        globalError = GitHubError.Network("Could not open the encrypted token store"),
                        fetchedAtMs = now,
                    )
                prefs.saveState(state)
                GitHubWidgets.updateAll(app, state)
                return@withContext state
            }
            val token = store.token()
            if (token.isNullOrBlank()) {
                val state = GitHubState(repos = repos, hasToken = false, globalError = GitHubError.NoToken(), fetchedAtMs = now)
                prefs.saveState(state)
                GitHubWidgets.updateAll(app, state)
                return@withContext state
            }
            if (repos.isEmpty()) {
                val state = GitHubState(repos = repos, hasToken = true, globalError = GitHubError.NoRepos(), fetchedAtMs = now)
                prefs.saveState(state)
                GitHubWidgets.updateAll(app, state)
                return@withContext state
            }
            val client = GitHubClient(token)
            val snapshots = LinkedHashMap<String, RepoSnapshot>()
            var rate: RateLimit? = null
            var global: GitHubError? = null
            repoLoop@ for (repo in repos) {
                val pulled = fetchRepo(client, repo, now)
                rate = pulled.second ?: rate
                snapshots[repo.full] = pulled.first
                val err = pulled.first.error
                if (err is GitHubError.Unauthorized || err is GitHubError.RateLimited) {
                    global = err
                    break@repoLoop
                }
                if (rate?.exhausted() == true) {
                    global = GitHubError.RateLimited(rate!!.resetEpochSec, "Rate limited · ${com.victorious.launch.core.github.RelativeTime.resetsIn(rate!!.resetEpochSec, now)}")
                    break@repoLoop
                }
            }
            val state =
                GitHubState(
                    repos = repos,
                    snapshots = snapshots,
                    rate = rate,
                    globalError = global,
                    fetchedAtMs = now,
                    hasToken = true,
                )
            prefs.saveState(state)
            GitHubWidgets.updateAll(app, state)
            state
        }

    private fun fetchRepo(client: GitHubClient, repo: RepoSlug, now: Long): Pair<RepoSnapshot, RateLimit?> {
        var rate: RateLimit? = null
        fun take(url: String): GitHubClient.Result {
            val result = client.get(url)
            rate =
                when (result) {
                    is GitHubClient.Result.Ok -> result.response.rate ?: rate
                    is GitHubClient.Result.Err -> result.rate ?: rate
                }
            return result
        }
        val pullsR = take(GitHubApi.pullsUrl(repo))
        if (pullsR is GitHubClient.Result.Err) return RepoSnapshot(repo, error = pullsR.error, fetchedAtMs = now) to rate
        val runsR = take(GitHubApi.runsUrl(repo))
        if (runsR is GitHubClient.Result.Err) return RepoSnapshot(repo, error = runsR.error, fetchedAtMs = now) to rate
        val relR = take(GitHubApi.releasesUrl(repo))
        if (relR is GitHubClient.Result.Err) return RepoSnapshot(repo, error = relR.error, fetchedAtMs = now) to rate
        return RepoSnapshot(
            repo = repo,
            pulls = GitHubParse.pulls((pullsR as GitHubClient.Result.Ok).response.body),
            runs = GitHubParse.workflowRuns((runsR as GitHubClient.Result.Ok).response.body),
            releases = GitHubParse.releases((relR as GitHubClient.Result.Ok).response.body),
            fetchedAtMs = now,
        ) to rate
    }
}

class GitHubRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val state = GitHubRefresh.run(applicationContext)
        return when (state.globalError) {
            is GitHubError.RateLimited, is GitHubError.Network -> Result.retry()
            else -> Result.success()
        }
    }
}
