package com.victorious.launch.theme

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.theme.FleetThemeAccess
import com.victorious.launch.core.theme.FleetThemeCodec
import com.victorious.launch.core.theme.FleetThemeContract
import com.victorious.launch.security.SigningCerts

/**
 * Read-only exported theme. Callers must pass package+cert verification.
 * The declared signature permission is not the gate — fleet apps do not
 * share a signing key.
 */
class FleetThemeProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        if (MATCHER.match(uri) != CURRENT) throw IllegalArgumentException("unknown uri $uri")
        enforceRead()
        val context = context ?: throw IllegalStateException("no context")
        val settings = VictorLaunchApp.instance.settings
        val snap = FleetThemeResolver.current(context, settings)
        val json = FleetThemeCodec.encode(snap)
        val columns = projection ?: FleetThemeContract.COLUMNS
        val cursor = MatrixCursor(columns)
        val values =
            columns.map { col ->
                when (col) {
                    FleetThemeContract.COL_SCHEMA_VERSION -> snap.schemaVersion
                    FleetThemeContract.COL_MODE -> snap.mode.name
                    FleetThemeContract.COL_ICON_STYLE -> snap.iconStyle.name
                    FleetThemeContract.COL_ICON_PACK -> snap.iconPackPackage ?: ""
                    FleetThemeContract.COL_JSON -> json
                    FleetThemeContract.COL_UPDATED_AT -> snap.updatedAtMs
                    FleetThemeContract.COL_BACKGROUND -> snap.palette.background
                    FleetThemeContract.COL_SURFACE -> snap.palette.surface
                    FleetThemeContract.COL_PRIMARY -> snap.palette.primary
                    FleetThemeContract.COL_SECONDARY -> snap.palette.secondary
                    FleetThemeContract.COL_ON_BACKGROUND -> snap.palette.onBackground
                    FleetThemeContract.COL_MUTED -> snap.palette.muted
                    FleetThemeContract.COL_DANGER -> snap.palette.danger
                    FleetThemeContract.COL_STALE -> snap.palette.stale
                    FleetThemeContract.COL_TILE -> snap.palette.tile
                    else -> null
                }
            }
        cursor.addRow(values.toTypedArray())
        return cursor
    }

    override fun getType(uri: Uri): String? =
        if (MATCHER.match(uri) == CURRENT) {
            "vnd.android.cursor.item/vnd.${FleetThemeContract.AUTHORITY}.current"
        } else {
            null
        }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw SecurityException("fleet theme is read-only")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw SecurityException("fleet theme is read-only")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?,
    ): Int {
        throw SecurityException("fleet theme is read-only")
    }

    private fun enforceRead() {
        val ctx = context ?: throw SecurityException("no context")
        val pkg = callingPackage ?: throw SecurityException("missing calling package")
        val certs = SigningCerts.forPackage(ctx.packageManager, pkg)
        val extra = VictorLaunchApp.instance.settings.themeExtraPins()
        if (!FleetThemeAccess.canRead(pkg, certs, extra, ctx.packageName)) {
            throw SecurityException("untrusted fleet-theme caller $pkg")
        }
    }

    companion object {
        private const val CURRENT = 1
        private val MATCHER =
            UriMatcher(UriMatcher.NO_MATCH).apply {
                addURI(FleetThemeContract.AUTHORITY, FleetThemeContract.PATH_CURRENT, CURRENT)
            }
    }
}
