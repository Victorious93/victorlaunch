package com.victorious.launch.core.theme

import com.victorious.launch.core.fleet.FleetPins
import com.victorious.launch.core.protocol.Protocol
import com.victorious.launch.core.protocol.TrustedPeers
import com.victorious.launch.core.share.FleetShareCatalog
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

enum class FleetThemeMode {
    LIGHT,
    DARK,
    NEON_FLEET,
    MATERIAL_YOU,
    ;

    companion object {
        fun parse(raw: String?): FleetThemeMode {
            val key = raw?.trim()?.uppercase()?.replace('-', '_') ?: return NEON_FLEET
            return entries.firstOrNull { it.name == key } ?: NEON_FLEET
        }
    }
}

enum class FleetIconStyle {
    SYSTEM,
    MONOCHROME,
    TINTED,
    ICON_PACK,
    ;

    companion object {
        fun parse(raw: String?): FleetIconStyle {
            val key = raw?.trim()?.uppercase()?.replace('-', '_') ?: return SYSTEM
            return entries.firstOrNull { it.name == key } ?: SYSTEM
        }
    }
}

data class FleetPalette(
    val background: String,
    val surface: String,
    val primary: String,
    val secondary: String,
    val onBackground: String,
    val muted: String,
    val danger: String,
    val stale: String,
    val tile: String,
)

data class FleetThemeSnapshot(
    val schemaVersion: Int = FleetThemeContract.SCHEMA_VERSION,
    val mode: FleetThemeMode,
    val iconStyle: FleetIconStyle,
    val iconPackPackage: String?,
    val palette: FleetPalette,
    val updatedAtMs: Long,
)

object FleetThemeContract {
    const val SCHEMA_VERSION = 1
    const val AUTHORITY = "com.victorious.launch.theme"
    const val PATH_CURRENT = "current"
    const val PERMISSION = "com.victorious.launch.permission.READ_FLEET_THEME"
    const val ACTION_CHANGED = "com.victorious.launch.action.FLEET_THEME_CHANGED"
    const val EXTRA_SCHEMA_VERSION = "schemaVersion"
    const val EXTRA_MODE = "mode"
    const val EXTRA_ICON_STYLE = "iconStyle"
    const val EXTRA_UPDATED_AT = "updatedAtMs"

    const val COL_SCHEMA_VERSION = "schemaVersion"
    const val COL_MODE = "mode"
    const val COL_ICON_STYLE = "iconStyle"
    const val COL_ICON_PACK = "iconPackPackage"
    const val COL_JSON = "json"
    const val COL_UPDATED_AT = "updatedAtMs"
    const val COL_BACKGROUND = "background"
    const val COL_SURFACE = "surface"
    const val COL_PRIMARY = "primary"
    const val COL_SECONDARY = "secondary"
    const val COL_ON_BACKGROUND = "onBackground"
    const val COL_MUTED = "muted"
    const val COL_DANGER = "danger"
    const val COL_STALE = "stale"
    const val COL_TILE = "tile"

    val COLUMNS: Array<String> =
        arrayOf(
            COL_SCHEMA_VERSION,
            COL_MODE,
            COL_ICON_STYLE,
            COL_ICON_PACK,
            COL_JSON,
            COL_UPDATED_AT,
            COL_BACKGROUND,
            COL_SURFACE,
            COL_PRIMARY,
            COL_SECONDARY,
            COL_ON_BACKGROUND,
            COL_MUTED,
            COL_DANGER,
            COL_STALE,
            COL_TILE,
        )

    fun contentUri(): String = "content://$AUTHORITY/$PATH_CURRENT"
}

object FleetThemePalettes {
    val NEON =
        FleetPalette(
            background = "#FF0B0C12",
            surface = "#FF161821",
            primary = "#FF00E5FF",
            secondary = "#FFE840FF",
            onBackground = "#FFF5F7FF",
            muted = "#FF9AA3B5",
            danger = "#FFFF6B8A",
            stale = "#FFFFC14D",
            tile = "#CC1A1D2B",
        )
    val DARK =
        FleetPalette(
            background = "#FF121212",
            surface = "#FF1E1E1E",
            primary = "#FF80DEEA",
            secondary = "#FFCE93D8",
            onBackground = "#FFEEEEEE",
            muted = "#FF9E9E9E",
            danger = "#FFEF9A9A",
            stale = "#FFFFCC80",
            tile = "#CC2A2A2A",
        )
    val LIGHT =
        FleetPalette(
            background = "#FFF5F7FF",
            surface = "#FFFFFFFF",
            primary = "#FF00838F",
            secondary = "#FF9C27B0",
            onBackground = "#FF0B0C12",
            muted = "#FF5C6475",
            danger = "#FFC6284A",
            stale = "#FFC68900",
            tile = "#E6E6E9F2",
        )

    fun paletteFor(mode: FleetThemeMode, materialYou: FleetPalette? = null): FleetPalette =
        when (mode) {
            FleetThemeMode.NEON_FLEET -> NEON
            FleetThemeMode.DARK -> DARK
            FleetThemeMode.LIGHT -> LIGHT
            FleetThemeMode.MATERIAL_YOU -> materialYou ?: NEON
        }

    fun parseArgb(hex: String): Int {
        val h = hex.trim().removePrefix("#")
        val value = h.toLongOrNull(16) ?: return 0xFF00E5FF.toInt()
        return when (h.length) {
            6 -> (0xFF000000L or value).toInt()
            8 -> value.toInt()
            else -> 0xFF00E5FF.toInt()
        }
    }
}

object FleetThemeCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(snapshot: FleetThemeSnapshot): String =
        buildJsonObject {
            put("schemaVersion", snapshot.schemaVersion)
            put("mode", snapshot.mode.name)
            put("iconStyle", snapshot.iconStyle.name)
            put("iconPackPackage", snapshot.iconPackPackage ?: "")
            put("updatedAtMs", snapshot.updatedAtMs)
            put(
                "palette",
                buildJsonObject {
                    put("background", snapshot.palette.background)
                    put("surface", snapshot.palette.surface)
                    put("primary", snapshot.palette.primary)
                    put("secondary", snapshot.palette.secondary)
                    put("onBackground", snapshot.palette.onBackground)
                    put("muted", snapshot.palette.muted)
                    put("danger", snapshot.palette.danger)
                    put("stale", snapshot.palette.stale)
                    put("tile", snapshot.palette.tile)
                },
            )
        }.toString()

    fun decode(text: String): FleetThemeSnapshot {
        val obj = json.parseToJsonElement(text).jsonObject
        val schema = obj["schemaVersion"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            ?: throw IllegalArgumentException("schemaVersion missing")
        if (schema != FleetThemeContract.SCHEMA_VERSION) {
            throw IllegalArgumentException("unsupported schemaVersion $schema")
        }
        val paletteObj = obj["palette"]?.jsonObject ?: JsonObject(emptyMap())
        return FleetThemeSnapshot(
            schemaVersion = schema,
            mode = FleetThemeMode.parse(obj["mode"]?.jsonPrimitive?.contentOrNull),
            iconStyle = FleetIconStyle.parse(obj["iconStyle"]?.jsonPrimitive?.contentOrNull),
            iconPackPackage = obj["iconPackPackage"]?.jsonPrimitive?.contentOrNull?.ifBlank { null },
            palette =
                FleetPalette(
                    background = paletteObj.hex("background", FleetThemePalettes.NEON.background),
                    surface = paletteObj.hex("surface", FleetThemePalettes.NEON.surface),
                    primary = paletteObj.hex("primary", FleetThemePalettes.NEON.primary),
                    secondary = paletteObj.hex("secondary", FleetThemePalettes.NEON.secondary),
                    onBackground = paletteObj.hex("onBackground", FleetThemePalettes.NEON.onBackground),
                    muted = paletteObj.hex("muted", FleetThemePalettes.NEON.muted),
                    danger = paletteObj.hex("danger", FleetThemePalettes.NEON.danger),
                    stale = paletteObj.hex("stale", FleetThemePalettes.NEON.stale),
                    tile = paletteObj.hex("tile", FleetThemePalettes.NEON.tile),
                ),
            updatedAtMs = obj["updatedAtMs"]?.jsonPrimitive?.longOrNull ?: 0L,
        )
    }

    private fun JsonObject.hex(key: String, fallback: String): String =
        this[key]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: fallback
}

object FleetThemeAccess {
    fun canRead(
        callerPackage: String,
        callerCerts: List<String>,
        extraPins: Map<String, List<String>>,
        selfPackage: String = Protocol.PACKAGE_NAME,
    ): Boolean {
        if (callerPackage == selfPackage) return true
        if (callerCerts.isEmpty()) return false
        if (FleetThemeConsumers.isPinned(callerPackage, callerCerts)) return true
        val pairingRows =
            extraPins.flatMap { (pkg, certs) -> certs.map { pkg to it } }
        if (TrustedPeers.authorize(callerPackage, callerCerts, pairingRows)) return true
        return callerCerts.any { cert ->
            FleetPins.isTrusted(callerPackage, cert, extraPins[callerPackage].orEmpty())
        }
    }

    fun broadcastPackages(extraPins: Map<String, List<String>>): Set<String> {
        val out = LinkedHashSet<String>()
        out.addAll(FleetPins.KNOWN.keys)
        out.addAll(FleetThemeConsumers.PINS.keys)
        FleetShareCatalog.ALL.forEach { out.addAll(it.packageNames) }
        out.addAll(extraPins.keys)
        out.remove(Protocol.PACKAGE_NAME)
        return out
    }
}
