package com.victorious.launch.core.fleet

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class FleetAsset(
    val abi: String,
    val url: String,
    val sizeBytes: Long,
    val sha256: String,
)

data class FleetApp(
    val id: String,
    val name: String,
    val repo: String,
    val applicationId: String,
    val versionName: String,
    val versionCode: Int,
    val debugBuild: Boolean,
    val releaseTag: String,
    val certSha256: String?,
    val assets: List<FleetAsset>,
)

data class FleetManifest(
    val schemaVersion: Int,
    val generatedAt: String,
    val apps: List<FleetApp>,
)

enum class FleetRowState {
    NOT_INSTALLED,
    UP_TO_DATE,
    UPDATE_AVAILABLE,
    DOWNGRADE,
    UNTRUSTED,
}

data class FleetPlanRow(
    val app: FleetApp,
    val installedVersionCode: Int?,
    val installedVersionName: String?,
    val trusted: Boolean,
    val state: FleetRowState,
)

object FleetManifestCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): FleetManifest {
        val obj = json.parseToJsonElement(text).jsonObject
        val schema = obj["schemaVersion"]?.jsonPrimitive?.intOrNull
            ?: throw IllegalArgumentException("schemaVersion missing")
        if (schema != 1) throw IllegalArgumentException("unsupported schemaVersion $schema")
        val generatedAt = obj["generatedAt"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val apps =
            (obj["apps"] as? JsonArray)?.mapNotNull { el ->
                val o = el as? JsonObject ?: return@mapNotNull null
                val applicationId = o["applicationId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val cert = o["certSha256"]?.jsonPrimitive?.contentOrNull
                FleetApp(
                    id = o["id"]?.jsonPrimitive?.contentOrNull ?: applicationId,
                    name = o["name"]?.jsonPrimitive?.contentOrNull ?: applicationId,
                    repo = o["repo"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    applicationId = applicationId,
                    versionName = o["versionName"]?.jsonPrimitive?.contentOrNull ?: "",
                    versionCode = o["versionCode"]?.jsonPrimitive?.intOrNull ?: 0,
                    debugBuild = o["debugBuild"]?.jsonPrimitive?.contentOrNull == "true",
                    releaseTag = o["releaseTag"]?.jsonPrimitive?.contentOrNull ?: "",
                    certSha256 = if (cert.isNullOrBlank() || cert == "null") null else cert,
                    assets =
                        (o["assets"] as? JsonArray)?.mapNotNull { a ->
                            val ao = a as? JsonObject ?: return@mapNotNull null
                            FleetAsset(
                                abi = ao["abi"]?.jsonPrimitive?.contentOrNull ?: "universal",
                                url = ao["url"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                                sizeBytes = ao["sizeBytes"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: 0L,
                                sha256 = ao["sha256"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                            )
                        }.orEmpty(),
                )
            }.orEmpty()
        return FleetManifest(schema, generatedAt, apps)
    }
}

object FleetPins {
    const val CONTROLLER_DEBUG =
        "88:00:51:A6:D9:5D:38:DD:8D:81:E1:93:1E:17:05:48:03:87:D5:93:C3:09:43:53:99:68:E6:EF:11:A0:C8:4D"
    const val BOUND_COMPANION_RELEASE =
        "9E:A7:B3:14:94:5B:0A:62:97:15:6F:F7:06:E7:0C:0A:B1:B7:24:DE:06:D5:09:9C:A5:12:8A:B4:43:F7:2D:8D"
    const val VICTORLAUNCH_DEBUG =
        "B9:9A:AF:E2:C1:41:8B:5D:64:AA:0D:4B:AC:61:FC:F7:A7:BA:81:44:35:E0:EF:89:E8:AA:55:BE:56:4E:4A:69"

    val KNOWN: Map<String, Set<String>> =
        mapOf(
            "ai.droidcommand.app" to setOf(CONTROLLER_DEBUG),
            "com.victorious.launch" to setOf(VICTORLAUNCH_DEBUG),
            "ai.hackerai.companion" to setOf(BOUND_COMPANION_RELEASE),
            "ai.pentestswarm.companion" to setOf(BOUND_COMPANION_RELEASE),
            "ai.edlcommander.companion" to setOf(BOUND_COMPANION_RELEASE),
        )

    fun normalize(cert: String): String =
        cert.replace(":", "").replace(" ", "").lowercase()

    fun isTrusted(applicationId: String, certSha256: String?, extraPins: List<String> = emptyList()): Boolean {
        if (certSha256.isNullOrBlank()) return false
        val incoming = normalize(certSha256)
        val known = KNOWN[applicationId].orEmpty().map { normalize(it) }.toSet()
        val extras = extraPins.map { normalize(it) }.toSet()
        return incoming in known || incoming in extras
    }
}

object FleetPlanner {
    fun plan(
        manifest: FleetManifest,
        installed: Map<String, Pair<Int, String>>,
        extraPins: List<String> = emptyList(),
    ): List<FleetPlanRow> {
        return manifest.apps.map { app ->
            val trusted = FleetPins.isTrusted(app.applicationId, app.certSha256, extraPins)
            val live = installed[app.applicationId]
            val state =
                when {
                    !trusted -> FleetRowState.UNTRUSTED
                    live == null -> FleetRowState.NOT_INSTALLED
                    live.first < app.versionCode -> FleetRowState.UPDATE_AVAILABLE
                    live.first > app.versionCode -> FleetRowState.DOWNGRADE
                    else -> FleetRowState.UP_TO_DATE
                }
            FleetPlanRow(
                app = app,
                installedVersionCode = live?.first,
                installedVersionName = live?.second,
                trusted = trusted,
                state = state,
            )
        }
    }

    fun apkMatchesRow(row: FleetPlanRow, apkCerts: List<String>, apkSha256: String?): Boolean {
        if (!row.trusted) return false
        val pinned = FleetPins.normalize(row.app.certSha256 ?: return false)
        if (apkCerts.none { FleetPins.normalize(it) == pinned }) return false
        val expected = row.app.assets.firstOrNull()?.sha256
        if (!expected.isNullOrBlank() && !apkSha256.isNullOrBlank()) {
            return expected.equals(apkSha256, ignoreCase = true)
        }
        return true
    }
}
