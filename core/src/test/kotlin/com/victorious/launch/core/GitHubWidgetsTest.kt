package com.victorious.launch.core

import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.ForbiddenBackupContentException
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.github.GitHubApi
import com.victorious.launch.core.github.GitHubCopy
import com.victorious.launch.core.github.GitHubError
import com.victorious.launch.core.github.GitHubParse
import com.victorious.launch.core.github.GitHubState
import com.victorious.launch.core.github.GitHubStateCodec
import com.victorious.launch.core.github.GitHubWidgetKind
import com.victorious.launch.core.github.PullSummary
import com.victorious.launch.core.github.RateLimit
import com.victorious.launch.core.github.RelativeTime
import com.victorious.launch.core.github.ReleaseSummary
import com.victorious.launch.core.github.RepoSlug
import com.victorious.launch.core.github.RepoSnapshot
import com.victorious.launch.core.github.WorkflowRunSummary
import com.victorious.launch.core.status.WidgetAccent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubWidgetsTest {
    private val repo = RepoSlug("Victorious93", "victorlaunch")
    private val now = 1_700_000_000_000L // 2023-11-14T22:13:20Z

    @Test
    fun parseRepoAcceptsSlugAndGithubUrls() {
        assertEquals(repo, GitHubApi.parseRepo("Victorious93/victorlaunch"))
        assertEquals(repo, GitHubApi.parseRepo("  https://github.com/Victorious93/victorlaunch.git  "))
        assertEquals(repo, GitHubApi.parseRepo("github.com/Victorious93/victorlaunch/tree/main"))
        assertNull(GitHubApi.parseRepo("nope"))
        assertNull(GitHubApi.parseRepo("https://evil.example/Victorious93/victorlaunch"))
        assertNull(GitHubApi.parseRepo("../etc/passwd"))
        assertNull(GitHubApi.parseRepo("owner/repo with space"))
    }

    @Test
    fun onlyApiGithubHttpsIsAllowed() {
        assertTrue(GitHubApi.isAllowedUrl(GitHubApi.pullsUrl(repo)))
        assertTrue(GitHubApi.isAllowedUrl(GitHubApi.runsUrl(repo)))
        assertTrue(GitHubApi.isAllowedUrl(GitHubApi.releasesUrl(repo)))
        assertFalse(GitHubApi.isAllowedUrl("http://api.github.com/repos/a/b"))
        assertFalse(GitHubApi.isAllowedUrl("https://github.com/Victorious93/victorlaunch"))
        assertFalse(GitHubApi.isAllowedUrl("https://api.github.com.evil.example/repos/a/b"))
        assertFalse(GitHubApi.isAllowedUrl("https://user:token@api.github.com/repos/a/b"))
        assertTrue(GitHubApi.pullsUrl(repo).startsWith("https://api.github.com/repos/"))
        assertFalse(GitHubApi.requestHeaders("secret").values.any { it.contains("secret") && !it.startsWith("Bearer ") })
        assertEquals("Bearer secret", GitHubApi.requestHeaders("secret")["Authorization"])
    }

    @Test
    fun parseRateLimitAndClassify() {
        val rate =
            GitHubApi.parseRateLimit(
                mapOf("x-ratelimit-limit" to "5000", "X-RateLimit-Remaining" to "0", "X-RATELIMIT-RESET" to "1700000060"),
            )
        assertEquals(RateLimit(5000, 0, 1_700_000_060L), rate)
        val limited =
            GitHubApi.classifyHttp(
                403,
                """{"message":"API rate limit exceeded for user"}""",
                rate,
                null,
                now,
            )
        assertTrue(limited is GitHubError.RateLimited)
        assertEquals(1_700_000_060L, (limited as GitHubError.RateLimited).resetEpochSec)
        assertTrue(limited.message.contains("resets in"))
        assertTrue(GitHubApi.classifyHttp(401, "bad creds", null, null, now) is GitHubError.Unauthorized)
        val notFound = GitHubApi.classifyHttp(404, "Not Found", null, null, now)
        assertTrue(notFound is GitHubError.Http && (notFound as GitHubError.Http).status == 404)
        val retry = GitHubApi.classifyHttp(403, "secondary rate limit", null, 30, now)
        assertTrue(retry is GitHubError.RateLimited)
        assertEquals(now / 1000 + 30, (retry as GitHubError.RateLimited).resetEpochSec)
    }

    @Test
    fun redactStripsPatsAndBearer() {
        val raw = "Authorization: Bearer github_pat_11AAAAAAA_BBBBBBBB and ghp_abcdefghijk and ghs_123456789"
        val redacted = GitHubApi.redact(raw)
        assertFalse(redacted.contains("github_pat_"))
        assertFalse(redacted.contains("ghp_abcdefghijk"))
        assertFalse(redacted.contains("ghs_123456789"))
        assertFalse(redacted.contains("Bearer github"))
        assertTrue(redacted.contains("***"))
    }

    @Test
    fun parsePullsRunsReleases() {
        val pulls =
            GitHubParse.pulls(
                """
                [
                  {"number":42,"title":"Fix widgets","draft":true,"user":{"login":"don"},"updated_at":"2023-11-14T21:13:20Z"},
                  {"number":7,"title":"Docs","draft":false,"user":{"login":"x"},"updated_at":"2023-11-14T20:13:20Z"}
                ]
                """.trimIndent(),
            )
        assertEquals(2, pulls.size)
        assertEquals(42, pulls[0].number)
        assertTrue(pulls[0].draft)
        assertEquals("don", pulls[0].user)

        val runs =
            GitHubParse.workflowRuns(
                """
                {"total_count":2,"workflow_runs":[
                  {"name":"assemble","status":"completed","conclusion":"failure","head_branch":"main",
                   "display_title":"Fix CI","updated_at":"2023-11-14T22:00:00Z"},
                  {"name":"test","status":"in_progress","conclusion":null,"head_branch":"feat",
                   "display_title":"WIP","updated_at":"2023-11-14T21:00:00Z"}
                ]}
                """.trimIndent(),
            )
        assertEquals(2, runs.size)
        assertTrue(runs[0].failed)
        assertTrue(runs[1].running)
        assertFalse(runs[0].ok)

        val rel =
            GitHubParse.releases(
                """
                [
                  {"tag_name":"debug-latest","name":"Debug latest","prerelease":true,"draft":false,"published_at":"2023-11-14T12:00:00Z"},
                  {"tag_name":"v1.0.0","name":"v1.0.0","prerelease":false,"draft":false,"published_at":"2023-11-01T12:00:00Z"}
                ]
                """.trimIndent(),
            )
        assertEquals("debug-latest", rel[0].tag)
        assertTrue(rel[0].prerelease)
        assertEquals("API rate limit exceeded", GitHubParse.message("""{"message":"API rate limit exceeded","documentation_url":"https://docs"}"""))
    }

    @Test
    fun widgetCopyNoTokenNoReposErrorsAndSuccess() {
        val none = GitHubCopy.of(GitHubWidgetKind.PULLS, GitHubState(hasToken = false), null, now)
        assertEquals(WidgetAccent.UNKNOWN, none.accent)
        assertTrue(none.line1.contains("token", ignoreCase = true))

        val noRepos = GitHubCopy.of(GitHubWidgetKind.ACTIONS, GitHubState(hasToken = true, repos = emptyList()), null, now)
        assertEquals(WidgetAccent.UNKNOWN, noRepos.accent)
        assertTrue(noRepos.line1.contains("repositor", ignoreCase = true))

        val pull =
            PullSummary(42, "Fix widgets", false, "don", "2023-11-14T21:13:20Z")
        val runOk = WorkflowRunSummary("assemble", "completed", "success", "main", "Ship it", "2023-11-14T22:03:20Z")
        val runFail = WorkflowRunSummary("test", "completed", "failure", "main", "Oops", "2023-11-14T22:10:20Z")
        val rel = ReleaseSummary("v1.2.0", "1.2.0", false, false, "2023-11-12T22:13:20Z")
        val snap =
            RepoSnapshot(repo, pulls = listOf(pull), runs = listOf(runFail, runOk), releases = listOf(rel), fetchedAtMs = now)
        val state = GitHubState(repos = listOf(repo), snapshots = mapOf(repo.full to snap), hasToken = true, fetchedAtMs = now)

        val prs = GitHubCopy.of(GitHubWidgetKind.PULLS, state, repo, now)
        assertEquals(WidgetAccent.OK, prs.accent)
        assertTrue(prs.title.contains("Victorious93/victorlaunch"))
        assertTrue(prs.line1.contains("1 open"))
        assertTrue(prs.line2.contains("#42"))
        assertTrue(prs.line2.contains("1h ago") || prs.line2.contains("60m ago"))

        val ci = GitHubCopy.of(GitHubWidgetKind.ACTIONS, state, repo, now)
        assertEquals(WidgetAccent.DOWN, ci.accent)
        assertTrue(ci.line1.contains("✗") || ci.line1.contains("assemble") || ci.line1.contains("test"))

        val releases = GitHubCopy.of(GitHubWidgetKind.RELEASES, state, repo, now)
        assertEquals(WidgetAccent.OK, releases.accent)
        assertTrue(releases.line1.contains("1.2.0") || releases.line1.contains("v1.2.0"))
        assertTrue(releases.line2.contains("2d ago"))

        val limited =
            GitHubCopy.of(
                GitHubWidgetKind.PULLS,
                GitHubState(
                    repos = listOf(repo),
                    hasToken = true,
                    globalError = GitHubError.RateLimited(now / 1000 + 600, "Rate limited · resets in 10m"),
                ),
                repo,
                now,
            )
        assertEquals(WidgetAccent.STALE, limited.accent)
        assertTrue(limited.line1.contains("Rate limited"))
        assertTrue(limited.line2.contains("resets in"))
    }

    @Test
    fun stateCodecRoundTripNeverWritesAToken() {
        val state =
            GitHubState(
                repos = listOf(repo),
                snapshots =
                    mapOf(
                        repo.full to
                            RepoSnapshot(
                                repo,
                                pulls = listOf(PullSummary(1, "t", false, "u", "2023-11-14T00:00:00Z")),
                                fetchedAtMs = 9,
                            ),
                    ),
                rate = RateLimit(5000, 42, 99),
                globalError = GitHubError.Network("timeout Bearer github_pat_11AAAAAAA_BBBBBBBB"),
                fetchedAtMs = 9,
                hasToken = true,
            )
        val encoded = GitHubStateCodec.encode(state)
        assertFalse(encoded.contains("github_pat_"))
        assertFalse(encoded.contains("BBBBBBBB"))
        assertFalse(encoded.contains("Bearer "))
        val back = GitHubStateCodec.decode(encoded)
        assertEquals(listOf(repo), back.repos)
        assertEquals(1, back.snapshots[repo.full]?.pulls?.size)
        assertEquals(42, back.rate?.remaining)
        assertTrue(back.hasToken)
        assertTrue(back.globalError is GitHubError.Network)
        assertEquals(listOf(repo), GitHubStateCodec.decodeRepos(GitHubStateCodec.encodeRepos(listOf(repo))))
    }

    @Test
    fun configExportNeverHoldsAGithubTokenAndScanRejectsOne() {
        val json = ConfigBackupCodec.exportJson(LauncherPreferences(dock = listOf("com.android.settings")))
        assertFalse(json.contains("token", ignoreCase = true))
        assertFalse(json.contains("github", ignoreCase = true))
        assertFalse(json.contains("secret", ignoreCase = true))
        var rejected = false
        try {
            ConfigBackupCodec.scan("""{"schemaVersion":1,"githubToken":"github_pat_xxx"}""")
        } catch (_: ForbiddenBackupContentException) {
            rejected = true
        }
        assertTrue(rejected)
    }

    @Test
    fun relativeTimeAndRateLine() {
        assertEquals("just now", RelativeTime.ago("2023-11-14T22:13:00Z", now))
        assertEquals("10m ago", RelativeTime.ago("2023-11-14T22:03:20Z", now))
        assertEquals("2h ago", RelativeTime.ago("2023-11-14T20:13:20Z", now))
        assertEquals("resets in 10m", RelativeTime.resetsIn(now / 1000 + 600, now))
        val line = GitHubCopy.rateLine(RateLimit(5000, 4875, now / 1000 + 3600), now)
        assertTrue(line!!.contains("4875/5000"))
        assertTrue(line.contains("resets in 1h"))
    }
}
