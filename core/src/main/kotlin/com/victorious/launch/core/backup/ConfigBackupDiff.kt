package com.victorious.launch.core.backup

data class BackupDiffLine(
    val field: String,
    val summary: String,
    val changed: Boolean,
)

/** Human-readable preview of what an import would change. Launcher prefs only. */
object ConfigBackupDiff {
    fun of(live: LauncherPreferences, wouldApply: LauncherPreferences): List<BackupDiffLine> {
        return listOf(
            listChange("Dock", live.dock, wouldApply.dock),
            itemsChange("Workspace", live.workspace.map { it.itemId() }, wouldApply.workspace.map { it.itemId() }),
            listChange("Hidden apps", live.hiddenPackages, wouldApply.hiddenPackages),
            listChange("Locked apps", live.lockedPackages, wouldApply.lockedPackages),
            listChange("Locked folders", live.lockedFolders, wouldApply.lockedFolders),
            scalar("Island tab lock", yn(live.islandTabLocked), yn(wouldApply.islandTabLocked)),
            scalar("Grid columns", live.gridColumns.toString(), wouldApply.gridColumns.toString()),
            scalar(
                "External control",
                "off (never enabled by backup)",
                "off (never enabled by backup)",
            ).copy(changed = false, summary = "stays off (backup cannot enable remote.command)"),
        )
    }

    fun render(lines: List<BackupDiffLine>, notes: List<String> = emptyList()): String {
        val body = lines.joinToString("\n") { line ->
            val mark = if (line.changed) "•" else "○"
            "$mark ${line.field}: ${line.summary}"
        }
        val extra = notes.filter { it.isNotBlank() && it != "dryRun: no write" && !it.startsWith("conflictPolicy=") }
        return if (extra.isEmpty()) body else body + "\n\n" + extra.joinToString("\n")
    }

    private fun yn(v: Boolean) = if (v) "on" else "off"

    private fun scalar(field: String, before: String, after: String): BackupDiffLine =
        if (before == after) BackupDiffLine(field, "unchanged ($before)", false)
        else BackupDiffLine(field, "$before → $after", true)

    private fun listChange(field: String, before: List<String>, after: List<String>): BackupDiffLine {
        if (before == after) return BackupDiffLine(field, "unchanged (${before.size})", false)
        val added = after.filterNot { it in before }
        val removed = before.filterNot { it in after }
        val bits = mutableListOf("${before.size} → ${after.size}")
        if (added.isNotEmpty()) bits += "added ${added.joinToString(", ")}"
        if (removed.isNotEmpty()) bits += "removed ${removed.joinToString(", ")}"
        return BackupDiffLine(field, bits.joinToString("; "), true)
    }

    private fun itemsChange(field: String, before: List<String>, after: List<String>): BackupDiffLine =
        listChange(field, before, after)
}
