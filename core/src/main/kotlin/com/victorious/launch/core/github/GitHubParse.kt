package com.victorious.launch.core.github

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object GitHubParse {
    private val json = Json { ignoreUnknownKeys = true }

    fun message(body: String): String {
        return try {
            val el = json.parseToJsonElement(body)
            val obj = el as? JsonObject ?: return body.take(200)
            obj["message"]?.jsonPrimitive?.contentOrNull ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
    }

    fun pulls(body: String): List<PullSummary> {
        val arr = array(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val number = o["number"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            PullSummary(
                number = number,
                title = str(o, "title").ifBlank { "#$number" },
                draft = o["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                user = (o["user"] as? JsonObject)?.let { str(it, "login") } ?: "",
                updatedAt = str(o, "updated_at"),
            )
        }
    }

    fun workflowRuns(body: String): List<WorkflowRunSummary> {
        val root = objOrArray(body)
        val arr =
            when (root) {
                is JsonObject -> (root["workflow_runs"] as? JsonArray) ?: return emptyList()
                is JsonArray -> root
                else -> return emptyList()
            }
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            WorkflowRunSummary(
                name = str(o, "name").ifBlank { "workflow" },
                status = str(o, "status"),
                conclusion = o["conclusion"]?.jsonPrimitive?.contentOrNull,
                branch = str(o, "head_branch"),
                title = str(o, "display_title").ifBlank { str(o, "head_sha").take(7) },
                updatedAt = str(o, "updated_at").ifBlank { str(o, "run_started_at") },
            )
        }
    }

    fun releases(body: String): List<ReleaseSummary> {
        val arr = array(body) ?: return emptyList()
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val tag = str(o, "tag_name")
            if (tag.isBlank()) return@mapNotNull null
            ReleaseSummary(
                tag = tag,
                name = o["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
                prerelease = o["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false,
                draft = o["draft"]?.jsonPrimitive?.booleanOrNull ?: false,
                publishedAt = o["published_at"]?.jsonPrimitive?.contentOrNull,
            )
        }
    }

    private fun str(o: JsonObject, key: String): String =
        o[key]?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun array(body: String): JsonArray? {
        return try {
            when (val el = json.parseToJsonElement(body)) {
                is JsonArray -> el
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun objOrArray(body: String): Any? {
        return try {
            json.parseToJsonElement(body)
        } catch (_: Exception) {
            null
        }
    }
}

object RelativeTime {
    fun ago(fromIso: String?, nowMs: Long): String {
        val then = parseIso(fromIso) ?: return ""
        val sec = ((nowMs - then).coerceAtLeast(0L)) / 1000L
        return when {
            sec < 45 -> "just now"
            sec < 60 * 60 -> "${sec / 60}m ago"
            sec < 48L * 60 * 60 -> "${sec / 3600}h ago"
            else -> "${sec / 86_400}d ago"
        }
    }

    fun resetsIn(resetEpochSec: Long, nowMs: Long): String {
        val sec = (resetEpochSec - nowMs / 1000L).coerceAtLeast(0L)
        return when {
            sec < 60 -> "resets in ${sec}s"
            sec < 3600 -> "resets in ${sec / 60}m"
            else -> "resets in ${sec / 3600}h"
        }
    }

    fun parseIso(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            raw.toLongOrNull()
        }
    }
}
