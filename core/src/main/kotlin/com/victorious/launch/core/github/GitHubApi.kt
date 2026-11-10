package com.victorious.launch.core.github

import java.net.URI

enum class GitHubWidgetKind {
    PULLS,
    ACTIONS,
    RELEASES,
}

data class RepoSlug(val owner: String, val name: String) {
    val full: String get() = "$owner/$name"

    override fun toString(): String = full
}

data class PullSummary(
    val number: Int,
    val title: String,
    val draft: Boolean,
    val user: String,
    val updatedAt: String,
)

data class WorkflowRunSummary(
    val name: String,
    val status: String,
    val conclusion: String?,
    val branch: String,
    val title: String,
    val updatedAt: String,
) {
    val ok: Boolean get() = status == "completed" && conclusion == "success"
    val failed: Boolean get() = status == "completed" && conclusion != null && conclusion != "success" && conclusion != "cancelled" && conclusion != "skipped"
    val running: Boolean get() = status == "in_progress" || status == "queued" || status == "pending" || status == "waiting"
}

data class ReleaseSummary(
    val tag: String,
    val name: String?,
    val prerelease: Boolean,
    val draft: Boolean,
    val publishedAt: String?,
)

data class RateLimit(
    val limit: Int,
    val remaining: Int,
    val resetEpochSec: Long,
) {
    fun exhausted(): Boolean = remaining <= 0

    fun resetsInMs(nowMs: Long): Long = (resetEpochSec * 1000L - nowMs).coerceAtLeast(0L)
}

sealed class GitHubError {
    abstract val message: String

    data class NoToken(override val message: String = "Add a fine-grained token in Settings → GitHub widgets") : GitHubError()

    data class NoRepos(override val message: String = "Add a repository in Settings → GitHub widgets") : GitHubError()

    data class Unauthorized(override val message: String = "Token rejected. Update it in Settings → GitHub widgets") : GitHubError()

    data class RateLimited(
        val resetEpochSec: Long,
        override val message: String,
    ) : GitHubError()

    data class Http(val status: Int, override val message: String) : GitHubError()

    data class Network(override val message: String) : GitHubError()

    data class Blocked(override val message: String = "Blocked: only https://api.github.com is allowed") : GitHubError()
}

data class RepoSnapshot(
    val repo: RepoSlug,
    val pulls: List<PullSummary> = emptyList(),
    val runs: List<WorkflowRunSummary> = emptyList(),
    val releases: List<ReleaseSummary> = emptyList(),
    val error: GitHubError? = null,
    val fetchedAtMs: Long = 0L,
)

data class GitHubState(
    val repos: List<RepoSlug> = emptyList(),
    val snapshots: Map<String, RepoSnapshot> = emptyMap(),
    val rate: RateLimit? = null,
    val globalError: GitHubError? = null,
    val fetchedAtMs: Long = 0L,
    val hasToken: Boolean = false,
)

/** Request builders, allow-list, rate-limit headers, token redaction. No I/O. */
object GitHubApi {
    const val HOST = "api.github.com"
    const val ORIGIN = "https://api.github.com"
    const val USER_AGENT = "VictorLaunch"
    const val API_VERSION = "2022-11-28"
    const val ACCEPT = "application/vnd.github+json"
    const val PER_PAGE = 8

    private val SLUG = Regex("^[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?/[A-Za-z0-9](?:[A-Za-z0-9._-]*[A-Za-z0-9])?$")
    private val TOKEN = Regex("(?:github_pat_|gh[pours]_)[A-Za-z0-9_]{8,}|Bearer\\s+[A-Za-z0-9._\\-]+")

    fun parseRepo(raw: String): RepoSlug? {
        var s = raw.trim()
        if (s.endsWith(".git")) s = s.dropLast(4)
        s = s.trimEnd('/')
        val path =
            when {
                s.startsWith("https://github.com/") -> s.removePrefix("https://github.com/")
                s.startsWith("http://github.com/") -> s.removePrefix("http://github.com/")
                s.startsWith("github.com/") -> s.removePrefix("github.com/")
                else -> s
            }.trim().trimStart('/')
        val slug = path.substringBefore('/').let { owner ->
            val rest = path.substringAfter('/', missingDelimiterValue = "")
            val repo = rest.substringBefore('/')
            if (owner.isBlank() || repo.isBlank()) null else "$owner/$repo"
        } ?: return null
        if (!SLUG.matches(slug)) return null
        val owner = slug.substringBefore('/')
        val name = slug.substringAfter('/')
        return RepoSlug(owner, name)
    }

    fun isAllowedUrl(url: String): Boolean {
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.host.equals(HOST, ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.path.isNullOrEmpty() || uri.path.startsWith("/"))
        } catch (_: Exception) {
            false
        }
    }

    fun pullsUrl(repo: RepoSlug): String =
        "$ORIGIN/repos/${repo.owner}/${repo.name}/pulls?state=open&per_page=$PER_PAGE&sort=updated"

    fun runsUrl(repo: RepoSlug): String =
        "$ORIGIN/repos/${repo.owner}/${repo.name}/actions/runs?per_page=$PER_PAGE"

    fun releasesUrl(repo: RepoSlug): String =
        "$ORIGIN/repos/${repo.owner}/${repo.name}/releases?per_page=$PER_PAGE"

    fun requestHeaders(token: String): Map<String, String> =
        mapOf(
            "Authorization" to "Bearer $token",
            "Accept" to ACCEPT,
            "X-GitHub-Api-Version" to API_VERSION,
            "User-Agent" to USER_AGENT,
        )

    fun parseRateLimit(headers: Map<String, String>): RateLimit? {
        fun h(name: String): String? =
            headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
        val limit = h("X-RateLimit-Limit")?.toIntOrNull() ?: return null
        val remaining = h("X-RateLimit-Remaining")?.toIntOrNull() ?: return null
        val reset = h("X-RateLimit-Reset")?.toLongOrNull() ?: return null
        return RateLimit(limit, remaining, reset)
    }

    fun retryAfterSeconds(headers: Map<String, String>): Long? {
        val raw = headers.entries.firstOrNull { it.key.equals("Retry-After", ignoreCase = true) }?.value ?: return null
        return raw.toLongOrNull()
    }

    fun classifyHttp(status: Int, bodyMessage: String, rate: RateLimit?, retryAfterSec: Long?, nowMs: Long): GitHubError {
        val msg = redact(bodyMessage).ifBlank { "HTTP $status" }
        val rateLimited =
            status == 403 &&
                (
                    rate?.exhausted() == true ||
                        msg.contains("rate limit", ignoreCase = true) ||
                        msg.contains("secondary rate", ignoreCase = true) ||
                        retryAfterSec != null
                )
        return when {
            status == 401 -> GitHubError.Unauthorized()
            rateLimited -> {
                val reset =
                    when {
                        rate != null && rate.exhausted() -> rate.resetEpochSec
                        retryAfterSec != null -> nowMs / 1000 + retryAfterSec
                        else -> rate?.resetEpochSec ?: (nowMs / 1000 + 60)
                    }
                GitHubError.RateLimited(reset, "Rate limited · ${RelativeTime.resetsIn(reset, nowMs)}")
            }
            status == 404 -> GitHubError.Http(404, "Repo not found or token lacks access")
            else -> GitHubError.Http(status, msg)
        }
    }

    fun redact(text: String): String = TOKEN.replace(text, "***")
}
