package com.victorious.launch.core

import com.victorious.launch.core.backup.BackupConflictPolicy
import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.ForbiddenBackupContentException
import com.victorious.launch.core.backup.LauncherPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBackupTest {
    @Test
    fun exportForcesExternalControlOff() {
        val json =
            ConfigBackupCodec.exportJson(
                LauncherPreferences(dock = listOf("com.android.settings"), externalControlEnabled = true),
            )
        assertTrue(json.contains("\"externalControlEnabled\":false"))
        assertFalse(json.contains("\"externalControlEnabled\":true"))
    }

    @Test
    fun importNeverEnablesRemoteCommandEvenOnOverwrite() {
        val live = LauncherPreferences(dock = listOf("a"), externalControlEnabled = false)
        val incoming = """{"schemaVersion":1,"dock":["b"],"workspace":[],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":5,"externalControlEnabled":true}"""
        val report =
            ConfigBackupCodec.import(
                live = live,
                incomingJson = incoming,
                dryRun = false,
                policy = BackupConflictPolicy.OVERWRITE,
                confirmed = true,
            )
        assertTrue(report.applied)
        assertFalse(report.preferences!!.externalControlEnabled)
        assertTrue(report.notes.any { it.contains("externalControlEnabled") })
    }

    @Test
    fun dryRunDoesNotApply() {
        val report =
            ConfigBackupCodec.import(
                live = LauncherPreferences(),
                incomingJson = """{"schemaVersion":1,"dock":["x"],"workspace":[],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":4,"externalControlEnabled":false}""",
                dryRun = true,
                policy = BackupConflictPolicy.SKIP,
                confirmed = true,
            )
        assertTrue(report.dryRun)
        assertFalse(report.applied)
    }

    @Test
    fun unconfirmedImportIsNotApplied() {
        val report =
            ConfigBackupCodec.import(
                live = LauncherPreferences(),
                incomingJson = """{"schemaVersion":1,"dock":["x"],"workspace":[],"hiddenPackages":[],"lockedPackages":[],"lockedFolders":[],"islandTabLocked":false,"gridColumns":4}""",
                dryRun = false,
                policy = BackupConflictPolicy.SKIP,
                confirmed = false,
            )
        assertFalse(report.applied)
        assertTrue(report.notes.contains("confirmation_required"))
    }

    @Test(expected = ForbiddenBackupContentException::class)
    fun refusesSecrets() {
        ConfigBackupCodec.decode("""{"schemaVersion":1,"password":"nope"}""")
    }
}
