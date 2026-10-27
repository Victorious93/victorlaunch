package com.victorious.launch.core.backup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

enum class BackupConflictPolicy {
    SKIP,
    OVERWRITE,
    MERGE_BY_ID,
    ;

    val wire: String
        get() =
            when (this) {
                SKIP -> "skip"
                OVERWRITE -> "overwrite"
                MERGE_BY_ID -> "merge-by-id"
            }

    companion object {
        fun fromWire(raw: String?): BackupConflictPolicy =
            when (raw) {
                "overwrite" -> OVERWRITE
                "merge-by-id" -> MERGE_BY_ID
                else -> SKIP
            }
    }
}

data class LauncherPreferences(
    val schemaVersion: Int = SCHEMA_VERSION,
    val dock: List<String> = emptyList(),
    val workspace: List<WorkspaceItem> = emptyList(),
    val hiddenPackages: List<String> = emptyList(),
    val lockedPackages: List<String> = emptyList(),
    val lockedFolders: List<String> = emptyList(),
    val islandTabLocked: Boolean = false,
    val gridColumns: Int = 4,
    val externalControlEnabled: Boolean = false,
    val pairedControllerPackage: String? = null,
) {
    companion object {
        const val SCHEMA_VERSION = 1
    }
}

data class WorkspaceItem(
    val packageName: String,
    val x: Int,
    val y: Int,
    val kind: String = KIND_APP,
    val page: Int = 0,
    val folderId: String? = null,
    val title: String? = null,
    val children: List<String> = emptyList(),
    val widgetId: Int? = null,
    val spanX: Int = 1,
    val spanY: Int = 1,
) {
    companion object {
        const val KIND_APP = "app"
        const val KIND_FOLDER = "folder"
        const val KIND_WIDGET = "widget"
    }

    fun itemId(): String =
        when (kind) {
            KIND_FOLDER -> folderId ?: "folder-$x-$y-$page"
            KIND_WIDGET -> "widget-${widgetId ?: "$page-$x-$y"}"
            else -> "app-$packageName-$page-$x-$y"
        }
}

data class ImportReport(
    val dryRun: Boolean,
    val wouldWrite: Boolean,
    val applied: Boolean,
    val notes: List<String>,
    val preferences: LauncherPreferences?,
)

class ForbiddenBackupContentException(message: String) : Exception(message)

object ConfigBackupCodec {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    private val FORBIDDEN =
        Regex(
            """(?i)(password|api[_-]?key|api[_-]?token|token|secret|code[_-]?word|whitelist|signing[_-]?key|private[_-]?key|keystore|twilio|authToken|numberPool)""",
        )

    private val REMOTE_COMMAND_TRUE =
        Regex(""""externalControlEnabled"\s*:\s*true""")

    private val CONTROLLER_FULL_TRUE =
        Regex(""""controllerFullControl(?:Enabled)?"\s*:\s*true""")

    private val MODS_TRUE =
        Regex("""(?i)"[^"]*mods[^"]*"\s*:\s*true""")

    private val NETWORK_TRUE =
        Regex("""(?i)"[^"]*network[^"]*"\s*:\s*true""")

    fun exportJson(prefs: LauncherPreferences): String {
        val forced = prefs.copy(externalControlEnabled = false)
        val text = json.encodeToString(JsonObject.serializer(), encode(forced))
        scan(text)
        return text
    }

    fun decode(text: String): LauncherPreferences {
        scan(text)
        val obj = json.parseToJsonElement(text).jsonObject
        return decode(obj)
    }

    fun encode(prefs: LauncherPreferences): JsonObject =
        buildJsonObject {
            put("schemaVersion", prefs.schemaVersion)
            put(
                "dock",
                JsonArray(prefs.dock.map { JsonPrimitive(it) }),
            )
            put(
                "workspace",
                JsonArray(
                    prefs.workspace.map {
                        buildJsonObject {
                            put("packageName", it.packageName)
                            put("x", it.x)
                            put("y", it.y)
                            put("kind", it.kind)
                            put("page", it.page)
                            if (it.folderId != null) put("folderId", it.folderId)
                            if (it.title != null) put("title", it.title)
                            if (it.children.isNotEmpty()) {
                                put("children", JsonArray(it.children.map { child -> JsonPrimitive(child) }))
                            }
                            if (it.widgetId != null) put("widgetId", it.widgetId)
                            put("spanX", it.spanX)
                            put("spanY", it.spanY)
                        }
                    },
                ),
            )
            put("hiddenPackages", JsonArray(prefs.hiddenPackages.map { JsonPrimitive(it) }))
            put("lockedPackages", JsonArray(prefs.lockedPackages.map { JsonPrimitive(it) }))
            put("lockedFolders", JsonArray(prefs.lockedFolders.map { JsonPrimitive(it) }))
            put("islandTabLocked", prefs.islandTabLocked)
            put("gridColumns", prefs.gridColumns)
            // Never persist a live remote.command / external-control ON flag.
            put("externalControlEnabled", false)
            if (prefs.pairedControllerPackage != null) {
                put("pairedControllerPackage", prefs.pairedControllerPackage)
            }
        }

    fun decode(obj: JsonObject): LauncherPreferences {
        scan(obj.toString())
        val schema = obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1
        if (schema != LauncherPreferences.SCHEMA_VERSION) {
            throw ForbiddenBackupContentException("unknown schemaVersion $schema")
        }
        return LauncherPreferences(
            schemaVersion = schema,
            dock = stringList(obj["dock"]),
            workspace = workspaceList(obj["workspace"]),
            hiddenPackages = stringList(obj["hiddenPackages"]),
            lockedPackages = stringList(obj["lockedPackages"]),
            lockedFolders = stringList(obj["lockedFolders"]),
            islandTabLocked = obj["islandTabLocked"]?.jsonPrimitive?.contentOrNull.toBoolean(),
            gridColumns = obj["gridColumns"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 4,
            externalControlEnabled = false,
            pairedControllerPackage = obj["pairedControllerPackage"]?.jsonPrimitive?.contentOrNull,
        )
    }

    fun import(
        live: LauncherPreferences,
        incomingJson: String,
        dryRun: Boolean,
        policy: BackupConflictPolicy,
        confirmed: Boolean,
    ): ImportReport {
        // decode() always forces the flag off; inspect the raw payload so we
        // still record that a true value was ignored.
        val incomingRequestedRemote = REMOTE_COMMAND_TRUE.containsMatchIn(incomingJson)
        val incomingRequestedControllerFull = CONTROLLER_FULL_TRUE.containsMatchIn(incomingJson)
        val incomingRequestedMods = MODS_TRUE.containsMatchIn(incomingJson)
        val incomingRequestedNetwork = NETWORK_TRUE.containsMatchIn(incomingJson)
        val incoming = decode(incomingJson)
        val merged =
            when (policy) {
                BackupConflictPolicy.SKIP ->
                    live.copy(
                        dock = live.dock.ifEmpty { incoming.dock },
                        workspace = live.workspace.ifEmpty { incoming.workspace },
                        hiddenPackages = unionSkip(live.hiddenPackages, incoming.hiddenPackages),
                        lockedPackages = unionSkip(live.lockedPackages, incoming.lockedPackages),
                        lockedFolders = unionSkip(live.lockedFolders, incoming.lockedFolders),
                    )
                BackupConflictPolicy.OVERWRITE ->
                    incoming.copy(externalControlEnabled = false)
                BackupConflictPolicy.MERGE_BY_ID ->
                    live.copy(
                        dock = mergeById(live.dock, incoming.dock),
                        workspace = mergeWorkspace(live.workspace, incoming.workspace),
                        hiddenPackages = mergeById(live.hiddenPackages, incoming.hiddenPackages),
                        lockedPackages = mergeById(live.lockedPackages, incoming.lockedPackages),
                        lockedFolders = mergeById(live.lockedFolders, incoming.lockedFolders),
                        islandTabLocked = live.islandTabLocked,
                        gridColumns = live.gridColumns,
                        pairedControllerPackage = live.pairedControllerPackage ?: incoming.pairedControllerPackage,
                    )
            }.copy(externalControlEnabled = false)

        val notes = mutableListOf<String>()
        if (incomingRequestedRemote) {
            notes.add("incoming externalControlEnabled was ignored; remote.command stays off")
        }
        if (incomingRequestedControllerFull) {
            notes.add("incoming controller full-control flag was ignored; kill switch is not set by backup")
        }
        if (incomingRequestedMods) {
            notes.add("incoming Mods flags were ignored; Mods stays off until enabled on the device")
        }
        if (incomingRequestedNetwork) {
            notes.add("incoming Network Monitor flags were ignored; the monitor stays off until enabled on the device")
        }
        notes.add("conflictPolicy=${policy.wire}")
        if (dryRun) {
            return ImportReport(
                dryRun = true,
                wouldWrite = true,
                applied = false,
                notes = notes + "dryRun: no write",
                preferences = merged,
            )
        }
        if (!confirmed) {
            return ImportReport(
                dryRun = false,
                wouldWrite = true,
                applied = false,
                notes = notes + "confirmation_required",
                preferences = merged,
            )
        }
        return ImportReport(
            dryRun = false,
            wouldWrite = true,
            applied = true,
            notes = notes,
            preferences = merged,
        )
    }

    fun scan(text: String) {
        if (FORBIDDEN.containsMatchIn(text)) {
            throw ForbiddenBackupContentException("backup payload contains a forbidden name")
        }
    }

    private fun stringList(el: JsonElement?): List<String> {
        if (el !is JsonArray) return emptyList()
        return el.mapNotNull { it.jsonPrimitive.contentOrNull }
    }

    private fun workspaceList(el: JsonElement?): List<WorkspaceItem> {
        if (el !is JsonArray) return emptyList()
        return el.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: WorkspaceItem.KIND_APP
            val pkg = obj["packageName"]?.jsonPrimitive?.contentOrNull ?: ""
            if (kind == WorkspaceItem.KIND_APP && pkg.isBlank()) return@mapNotNull null
            WorkspaceItem(
                packageName = pkg,
                x = obj["x"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                y = obj["y"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                kind = kind,
                page = obj["page"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                folderId = obj["folderId"]?.jsonPrimitive?.contentOrNull,
                title = obj["title"]?.jsonPrimitive?.contentOrNull,
                children = stringList(obj["children"]),
                widgetId = obj["widgetId"]?.jsonPrimitive?.contentOrNull?.toIntOrNull(),
                spanX = obj["spanX"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
                spanY = obj["spanY"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 1,
            )
        }
    }

    private fun unionSkip(live: List<String>, incoming: List<String>): List<String> =
        (live + incoming.filterNot { it in live }).distinct()

    private fun mergeById(live: List<String>, incoming: List<String>): List<String> =
        (live + incoming.filterNot { it in live }).distinct()

    private fun mergeWorkspace(live: List<WorkspaceItem>, incoming: List<WorkspaceItem>): List<WorkspaceItem> {
        val liveIds = live.map { it.itemId() }.toSet()
        return live + incoming.filterNot { it.itemId() in liveIds }
    }
}
