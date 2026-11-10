package com.victorious.launch.core.github

import com.victorious.launch.core.status.WidgetAccent

data class GitHubWidgetCopy(
    val kind: GitHubWidgetKind,
    val title: String,
    val line1: String,
    val line2: String,
    val accent: WidgetAccent,
)

object GitHubCopy {
    fun of(
        kind: GitHubWidgetKind,
        state: GitHubState,
        repoFilter: RepoSlug?,
        nowMs: Long,
    ): GitHubWidgetCopy {
        val titleKind =
            when (kind) {
                GitHubWidgetKind.PULLS -> "PRs"
                GitHubWidgetKind.ACTIONS -> "CI"
                GitHubWidgetKind.RELEASES -> "Releases"
            }
        if (!state.hasToken) {
            return GitHubWidgetCopy(kind, titleKind, "No token", GitHubError.NoToken().message, WidgetAccent.UNKNOWN)
        }
        if (state.repos.isEmpty()) {
            return GitHubWidgetCopy(kind, titleKind, "No repositories", GitHubError.NoRepos().message, WidgetAccent.UNKNOWN)
        }
        val repos = if (repoFilter != null) listOf(repoFilter) else state.repos
        val snaps = repos.map { state.snapshots[it.full] ?: RepoSnapshot(it) }
        val global = state.globalError
        if (global is GitHubError.Unauthorized) {
            return GitHubWidgetCopy(kind, titleKind, "Bad token", global.message, WidgetAccent.DOWN)
        }
        if (global is GitHubError.RateLimited && snaps.all { it.fetchedAtMs == 0L && it.error != null || it.fetchedAtMs == 0L && it.pulls.isEmpty() && it.runs.isEmpty() && it.releases.isEmpty() }) {
            return GitHubWidgetCopy(kind, titleFor(titleKind, repos), "Rate limited", global.message, WidgetAccent.STALE)
        }
        val title = titleFor(titleKind, repos)
        return when (kind) {
            GitHubWidgetKind.PULLS -> pulls(title, kind, snaps, global, nowMs)
            GitHubWidgetKind.ACTIONS -> actions(title, kind, snaps, global, nowMs)
            GitHubWidgetKind.RELEASES -> releases(title, kind, snaps, global, nowMs)
        }
    }

    fun rateLine(rate: RateLimit?, nowMs: Long): String? {
        if (rate == null) return null
        val reset = RelativeTime.resetsIn(rate.resetEpochSec, nowMs)
        return "API ${rate.remaining}/${rate.limit} remaining · $reset"
    }

    private fun titleFor(kind: String, repos: List<RepoSlug>): String =
        if (repos.size == 1) "$kind · ${repos[0].full}" else kind

    private fun pulls(
        title: String,
        kind: GitHubWidgetKind,
        snaps: List<RepoSnapshot>,
        global: GitHubError?,
        nowMs: Long,
    ): GitHubWidgetCopy {
        val err = firstHard(snaps, global)
        if (err != null && snaps.all { it.pulls.isEmpty() }) return errorCopy(kind, title, err)
        val counts = snaps.map { snap -> "${short(snap.repo)} ${snap.pulls.size} open" }
        val line1 = counts.joinToString(" · ").ifBlank { "0 open" }
        val latest = snaps.flatMap { s -> s.pulls.map { s.repo to it } }
            .maxByOrNull { RelativeTime.parseIso(it.second.updatedAt) ?: 0L }
        val line2 =
            if (latest == null) {
                staleOrUpdated(snaps, global, nowMs, "No open pull requests")
            } else {
                val draft = if (latest.second.draft) " (draft)" else ""
                val who = if (latest.second.user.isBlank()) "" else " · ${latest.second.user}"
                val repo = if (snaps.size > 1) "${short(latest.first)} " else ""
                "$repo#${latest.second.number} ${latest.second.title}$draft$who · ${RelativeTime.ago(latest.second.updatedAt, nowMs)}"
            }
        return GitHubWidgetCopy(kind, title, line1, line2, accent(snaps, global, failed = false))
    }

    private fun actions(
        title: String,
        kind: GitHubWidgetKind,
        snaps: List<RepoSnapshot>,
        global: GitHubError?,
        nowMs: Long,
    ): GitHubWidgetCopy {
        val err = firstHard(snaps, global)
        if (err != null && snaps.all { it.runs.isEmpty() }) return errorCopy(kind, title, err)
        val latestPerRepo = snaps.mapNotNull { snap -> snap.runs.firstOrNull()?.let { snap to it } }
        if (latestPerRepo.isEmpty()) {
            return GitHubWidgetCopy(kind, title, "No workflow runs", staleOrUpdated(snaps, global, nowMs, ""), accent(snaps, global, false))
        }
        val line1 =
            latestPerRepo.joinToString(" · ") { (snap, run) ->
                val mark =
                    when {
                        run.failed -> "✗"
                        run.ok -> "✓"
                        run.running -> "…"
                        else -> run.conclusion ?: run.status
                    }
                "${short(snap.repo)} ${run.name} $mark"
            }
        val latest = latestPerRepo.maxByOrNull { RelativeTime.parseIso(it.second.updatedAt) ?: 0L }!!
        val run = latest.second
        val branch = if (run.branch.isBlank()) "" else " · ${run.branch}"
        val line2 = "${run.title}$branch · ${RelativeTime.ago(run.updatedAt, nowMs)}"
        val failed = latestPerRepo.any { it.second.failed }
        return GitHubWidgetCopy(kind, title, line1, line2, accent(snaps, global, failed))
    }

    private fun releases(
        title: String,
        kind: GitHubWidgetKind,
        snaps: List<RepoSnapshot>,
        global: GitHubError?,
        nowMs: Long,
    ): GitHubWidgetCopy {
        val err = firstHard(snaps, global)
        if (err != null && snaps.all { it.releases.isEmpty() }) return errorCopy(kind, title, err)
        val published = snaps.map { snap -> snap.releases.firstOrNull { !it.draft }?.let { snap.repo to it } }
        val any = published.filterNotNull()
        if (any.isEmpty()) {
            return GitHubWidgetCopy(kind, title, "No releases", staleOrUpdated(snaps, global, nowMs, ""), accent(snaps, global, false))
        }
        val line1 =
            any.joinToString(" · ") { (repo, rel) ->
                val pre = if (rel.prerelease) " (pre)" else ""
                val label = rel.name?.takeIf { it.isNotBlank() && it != rel.tag } ?: rel.tag
                "${short(repo)} $label$pre"
            }
        val newest = any.maxByOrNull { RelativeTime.parseIso(it.second.publishedAt) ?: 0L }!!
        val line2 = "published ${RelativeTime.ago(newest.second.publishedAt, nowMs)}"
        return GitHubWidgetCopy(kind, title, line1, line2, accent(snaps, global, false))
    }

    private fun short(repo: RepoSlug): String = repo.name

    private fun firstHard(snaps: List<RepoSnapshot>, global: GitHubError?): GitHubError? =
        global as? GitHubError.Unauthorized
            ?: global as? GitHubError.RateLimited
            ?: snaps.mapNotNull { it.error }.firstOrNull()
            ?: global

    private fun errorCopy(kind: GitHubWidgetKind, title: String, err: GitHubError): GitHubWidgetCopy {
        val line1 =
            when (err) {
                is GitHubError.RateLimited -> "Rate limited"
                is GitHubError.Unauthorized -> "Bad token"
                is GitHubError.Http -> "Error ${err.status}"
                is GitHubError.Network -> "Network error"
                is GitHubError.NoToken -> "No token"
                is GitHubError.NoRepos -> "No repositories"
                is GitHubError.Blocked -> "Blocked host"
            }
        val accent =
            when (err) {
                is GitHubError.RateLimited -> WidgetAccent.STALE
                is GitHubError.NoToken, is GitHubError.NoRepos -> WidgetAccent.UNKNOWN
                else -> WidgetAccent.DOWN
            }
        return GitHubWidgetCopy(kind, title, line1, err.message, accent)
    }

    private fun staleOrUpdated(snaps: List<RepoSnapshot>, global: GitHubError?, nowMs: Long, empty: String): String {
        if (global is GitHubError.RateLimited) return global.message
        val fetched = snaps.map { it.fetchedAtMs }.filter { it > 0 }.maxOrNull()
        return if (fetched != null && fetched > 0) {
            "updated ${RelativeTime.ago(java.time.Instant.ofEpochMilli(fetched).toString(), nowMs)}"
        } else {
            empty
        }
    }

    private fun accent(snaps: List<RepoSnapshot>, global: GitHubError?, failed: Boolean): WidgetAccent {
        if (failed) return WidgetAccent.DOWN
        if (global is GitHubError.RateLimited || snaps.any { it.error is GitHubError.RateLimited }) return WidgetAccent.STALE
        if (global is GitHubError.Unauthorized || snaps.any { it.error is GitHubError.Unauthorized }) return WidgetAccent.DOWN
        if (snaps.any { it.error != null } && snaps.all { it.pulls.isEmpty() && it.runs.isEmpty() && it.releases.isEmpty() && it.error != null }) {
            return WidgetAccent.DOWN
        }
        if (snaps.any { it.error != null }) return WidgetAccent.STALE
        return WidgetAccent.OK
    }
}
