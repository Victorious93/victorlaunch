package com.victorious.launch.core.github

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Persist widget snapshots and the repo list. Never encodes a token. */
object GitHubStateCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encodeRepos(repos: List<RepoSlug>): String =
        JsonArray(repos.map { JsonPrimitive(it.full) }).toString()

    fun decodeRepos(raw: String?): List<RepoSlug> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.parseToJsonElement(raw).jsonArray.mapNotNull { el ->
                GitHubApi.parseRepo(el.jsonPrimitive.contentOrNull ?: return@mapNotNull null)
            }
        } catch (_: Exception) {
            raw.split(',', '\n', ';').mapNotNull { GitHubApi.parseRepo(it) }
        }
    }

    fun encode(state: GitHubState): String = encodeObject(state).toString()

    fun decode(raw: String?): GitHubState {
        if (raw.isNullOrBlank()) return GitHubState()
        return try {
            val o = json.parseToJsonElement(raw).jsonObject
            GitHubState(
                repos = (o["repos"] as? JsonArray)?.mapNotNull { GitHubApi.parseRepo(it.jsonPrimitive.contentOrNull ?: "") } ?: emptyList(),
                snapshots =
                    (o["snapshots"] as? JsonObject)?.entries?.associate { (k, v) ->
                        val slug = GitHubApi.parseRepo(k) ?: RepoSlug(k.substringBefore('/'), k.substringAfter('/', k))
                        k to decodeSnap(slug, v as? JsonObject)
                    } ?: emptyMap(),
                rate = (o["rate"] as? JsonObject)?.let { decodeRate(it) },
                globalError = decodeError(o["error"] as? JsonObject),
                fetchedAtMs = o["fetchedAtMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                hasToken = o["hasToken"]?.jsonPrimitive?.booleanOrNull ?: false,
            )
        } catch (_: Exception) {
            GitHubState()
        }
    }

    private fun encodeObject(state: GitHubState): JsonObject =
        buildJsonObject {
            put("repos", JsonArray(state.repos.map { JsonPrimitive(it.full) }))
            put(
                "snapshots",
                buildJsonObject {
                    state.snapshots.forEach { (k, snap) -> put(k, encodeSnap(snap)) }
                },
            )
            state.rate?.let { rate ->
                put(
                    "rate",
                    buildJsonObject {
                        put("limit", rate.limit)
                        put("remaining", rate.remaining)
                        put("resetEpochSec", rate.resetEpochSec)
                    },
                )
            }
            encodeError(state.globalError)?.let { put("error", it) }
            put("fetchedAtMs", state.fetchedAtMs)
            put("hasToken", state.hasToken)
        }

    private fun encodeSnap(s: RepoSnapshot): JsonObject =
        buildJsonObject {
            put("repo", s.repo.full)
            put(
                "pulls",
                buildJsonArray {
                    s.pulls.forEach {
                        add(
                            buildJsonObject {
                                put("number", it.number)
                                put("title", it.title)
                                put("draft", it.draft)
                                put("user", it.user)
                                put("updatedAt", it.updatedAt)
                            },
                        )
                    }
                },
            )
            put(
                "runs",
                buildJsonArray {
                    s.runs.forEach {
                        add(
                            buildJsonObject {
                                put("name", it.name)
                                put("status", it.status)
                                if (it.conclusion != null) put("conclusion", it.conclusion)
                                put("branch", it.branch)
                                put("title", it.title)
                                put("updatedAt", it.updatedAt)
                            },
                        )
                    }
                },
            )
            put(
                "releases",
                buildJsonArray {
                    s.releases.forEach {
                        add(
                            buildJsonObject {
                                put("tag", it.tag)
                                if (it.name != null) put("name", it.name)
                                put("prerelease", it.prerelease)
                                put("draft", it.draft)
                                if (it.publishedAt != null) put("publishedAt", it.publishedAt)
                            },
                        )
                    }
                },
            )
            encodeError(s.error)?.let { put("error", it) }
            put("fetchedAtMs", s.fetchedAtMs)
        }

    private fun decodeSnap(fallback: RepoSlug, o: JsonObject?): RepoSnapshot {
        if (o == null) return RepoSnapshot(fallback)
        val repo = GitHubApi.parseRepo(o["repo"]?.jsonPrimitive?.contentOrNull ?: "") ?: fallback
        return RepoSnapshot(
            repo = repo,
            pulls =
                (o["pulls"] as? JsonArray)?.mapNotNull { el ->
                    val p = el as? JsonObject ?: return@mapNotNull null
                    PullSummary(
                        number = p["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null,
                        title = p["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        draft = p["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                        user = p["user"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = p["updatedAt"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList(),
            runs =
                (o["runs"] as? JsonArray)?.mapNotNull { el ->
                    val p = el as? JsonObject ?: return@mapNotNull null
                    WorkflowRunSummary(
                        name = p["name"]?.jsonPrimitive?.contentOrNull ?: "workflow",
                        status = p["status"]?.jsonPrimitive?.contentOrNull ?: "",
                        conclusion = p["conclusion"]?.jsonPrimitive?.contentOrNull,
                        branch = p["branch"]?.jsonPrimitive?.contentOrNull ?: "",
                        title = p["title"]?.jsonPrimitive?.contentOrNull ?: "",
                        updatedAt = p["updatedAt"]?.jsonPrimitive?.contentOrNull ?: "",
                    )
                } ?: emptyList(),
            releases =
                (o["releases"] as? JsonArray)?.mapNotNull { el ->
                    val p = el as? JsonObject ?: return@mapNotNull null
                    ReleaseSummary(
                        tag = p["tag"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null,
                        name = p["name"]?.jsonPrimitive?.contentOrNull,
                        prerelease = p["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false,
                        draft = p["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                        publishedAt = p["publishedAt"]?.jsonPrimitive?.contentOrNull,
                    )
                } ?: emptyList(),
            error = decodeError(o["error"] as? JsonObject),
            fetchedAtMs = o["fetchedAtMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun decodeRate(o: JsonObject): RateLimit? {
        val limit = o["limit"]?.jsonPrimitive?.intOrNull ?: return null
        val remaining = o["remaining"]?.jsonPrimitive?.intOrNull ?: return null
        val reset = o["resetEpochSec"]?.jsonPrimitive?.longOrNull ?: return null
        return RateLimit(limit, remaining, reset)
    }

    private fun encodeError(err: GitHubError?): JsonObject? {
        if (err == null) return null
        return buildJsonObject {
            put(
                "kind",
                when (err) {
                    is GitHubError.NoToken -> "no_token"
                    is GitHubError.NoRepos -> "no_repos"
                    is GitHubError.Unauthorized -> "unauthorized"
                    is GitHubError.RateLimited -> "rate_limited"
                    is GitHubError.Http -> "http"
                    is GitHubError.Network -> "network"
                    is GitHubError.Blocked -> "blocked"
                },
            )
            put("message", GitHubApi.redact(err.message))
            if (err is GitHubError.RateLimited) put("resetEpochSec", err.resetEpochSec)
            if (err is GitHubError.Http) put("status", err.status)
        }
    }

    private fun decodeError(o: JsonObject?): GitHubError? {
        if (o == null) return null
        val msg = o["message"]?.jsonPrimitive?.contentOrNull ?: ""
        return when (o["kind"]?.jsonPrimitive?.contentOrNull) {
            "no_token" -> GitHubError.NoToken(msg.ifBlank { GitHubError.NoToken().message })
            "no_repos" -> GitHubError.NoRepos(msg.ifBlank { GitHubError.NoRepos().message })
            "unauthorized" -> GitHubError.Unauthorized(msg.ifBlank { GitHubError.Unauthorized().message })
            "rate_limited" -> GitHubError.RateLimited(o["resetEpochSec"]?.jsonPrimitive?.longOrNull ?: 0L, msg)
            "http" -> GitHubError.Http(o["status"]?.jsonPrimitive?.intOrNull ?: 0, msg)
            "network" -> GitHubError.Network(msg)
            "blocked" -> GitHubError.Blocked(msg.ifBlank { GitHubError.Blocked().message })
            else -> null
        }
    }
}
