package com.victorious.launch.core.url

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class VirusTotalStats(
    val malicious: Int,
    val suspicious: Int,
    val harmless: Int,
    val undetected: Int,
    val timeout: Int = 0,
) {
    val engines: Int get() = malicious + suspicious + harmless + undetected + timeout

    fun verdict(): UrlVerdict =
        when {
            malicious > 0 -> UrlVerdict.DANGER
            suspicious > 0 -> UrlVerdict.CAUTION
            else -> UrlVerdict.OK
        }
}

data class VirusTotalReport(
    val analysisId: String?,
    val status: String?,
    val stats: VirusTotalStats?,
    val permalink: String?,
    val error: String?,
)

object VirusTotalParse {
    private val json = Json { ignoreUnknownKeys = true }

    fun analysisId(body: String): String? =
        runCatching {
            json.parseToJsonElement(body)
                .jsonObject["data"]
                ?.jsonObject
                ?.get("id")
                ?.jsonPrimitive
                ?.contentOrNull
                ?.ifBlank { null }
        }.getOrNull()

    fun report(body: String): VirusTotalReport {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            root["error"]?.jsonObject?.let { err ->
                val msg = err["message"]?.jsonPrimitive?.contentOrNull ?: "VirusTotal error"
                return VirusTotalReport(null, null, null, null, msg)
            }
            val data = root["data"]?.jsonObject
                ?: return VirusTotalReport(null, null, null, null, "Missing data")
            val id = data["id"]?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            val attrs = data["attributes"]?.jsonObject
            val status = attrs?.get("status")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            val statsObj =
                (attrs?.get("stats") as? JsonObject)
                    ?: (attrs?.get("last_analysis_stats") as? JsonObject)
            val stats =
                statsObj?.let {
                    VirusTotalStats(
                        malicious = int(it, "malicious"),
                        suspicious = int(it, "suspicious"),
                        harmless = int(it, "harmless"),
                        undetected = int(it, "undetected"),
                        timeout = int(it, "timeout"),
                    )
                }
            val link =
                data["links"]?.jsonObject?.get("self")?.jsonPrimitive?.contentOrNull?.ifBlank { null }
            VirusTotalReport(id, status, stats, link, null)
        } catch (_: Exception) {
            VirusTotalReport(null, null, null, null, "Could not parse VirusTotal response")
        }
    }

    private fun int(obj: JsonObject, key: String): Int =
        obj[key]?.jsonPrimitive?.intOrNull ?: 0
}
