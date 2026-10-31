package com.victorious.launch.core.command

/** Where a palette command runs. Every run needs an explicit on-device tap; the bridge has no exec op. */
enum class CommandTarget(val label: String) {
    SHIZUKU("Shizuku shell"),
    ROOT("Root (su)"),
    TERMUX("Termux"),
}

data class SavedCommand(
    val id: String,
    val name: String,
    val command: String,
    val target: CommandTarget,
    val favorite: Boolean = false,
)

/** Line-based storage for SharedPreferences (one command per line, tab-separated, escaped). */
object CommandCodec {
    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

    private fun unesc(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '\\' && i + 1 < s.length) {
                when (s[i + 1]) {
                    't' -> sb.append('\t')
                    'n' -> sb.append('\n')
                    else -> sb.append(s[i + 1])
                }
                i += 2
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    fun encode(list: List<SavedCommand>): String =
        list.joinToString("\n") { listOf(it.id, it.name, it.command, it.target.name, if (it.favorite) "1" else "0").joinToString("\t") { f -> esc(f) } }

    fun decode(text: String?): List<SavedCommand> =
        text.orEmpty().lines().filter { it.isNotBlank() }.mapNotNull { line ->
            val f = splitTabs(line).map { unesc(it) }
            if (f.size < 5) return@mapNotNull null
            val target = CommandTarget.entries.firstOrNull { it.name == f[3] } ?: return@mapNotNull null
            SavedCommand(f[0], f[1], f[2], target, f[4] == "1")
        }

    private fun splitTabs(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\\' && i + 1 < line.length) {
                cur.append(c).append(line[i + 1])
                i += 2
                continue
            }
            if (c == '\t') {
                out += cur.toString()
                cur.clear()
            } else {
                cur.append(c)
            }
            i++
        }
        out += cur.toString()
        return out
    }

    /** Favorites first, then by name. */
    fun sorted(list: List<SavedCommand>): List<SavedCommand> = list.sortedWith(compareBy({ !it.favorite }, { it.name.lowercase() }))

    /** Palette query: text after a leading '>' in drawer search, or null if not a palette query. */
    fun paletteQuery(search: String): String? = if (search.startsWith(">")) search.drop(1).trimStart() else null
}

/** Flags commands that look destructive so the UI shows a confirm dialog first. */
object DestructiveCommands {
    private val rules: List<Pair<Regex, String>> =
        listOf(
            Regex("""(^|[\s;&|(`])rm(\s|$)""") to "deletes files (rm)",
            Regex("""(^|[\s;&|(`])dd(\s|$)""") to "raw disk write (dd)",
            Regex("""(^|[\s;&|(`])(reboot|shutdown|poweroff|halt)(\s|$)""") to "reboots or powers off the device",
            Regex("""(^|[\s;&|(`])svc\s+power\s+(reboot|shutdown)""") to "reboots or powers off the device",
            Regex("""(^|[\s;&|(`])pm\s+(uninstall|clear|disable(-user)?|hide|suspend)(\s|$)""") to "uninstalls, clears or disables an app (pm)",
            Regex("""(^|[\s;&|(`])cmd\s+package\s+(uninstall|clear)(\s|$)""") to "uninstalls or clears an app",
            Regex("""(^|[\s;&|(`])(mkfs(\.\w+)?|format|wipe|fastboot|flash_image|blkdiscard)(\s|$)""") to "formats or wipes storage",
            Regex("""(^|[\s;&|(`])(chmod|chown)\s+(-R\s+)?\S*\s*/(system|data|vendor)?(\s|$)""") to "changes system file permissions",
            Regex("""(^|[\s;&|(`])mount\s+.*-o\s*\S*(rw|remount)""") to "remounts a partition",
            Regex(""">\s*/dev/(block|sd|mmc)""") to "writes to a block device",
            Regex("""(^|[\s;&|(`])(setenforce|magisk\s+--remove-modules)(\s|$)""") to "changes system security state",
            Regex("""(^|[\s;&|(`])settings\s+(put|delete)\s""") to "changes system settings",
        )

    /** Reasons this command looks destructive; empty when it looks safe. */
    fun reasons(command: String): List<String> {
        val c = command.trim()
        return rules.filter { it.first.containsMatchIn(c) }.map { it.second }.distinct()
    }

    fun needsConfirm(command: String): Boolean = reasons(command).isNotEmpty()
}

/** Termux RUN_COMMAND intent contract (https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent). */
object TermuxRun {
    const val PACKAGE = "com.termux"
    const val SERVICE = "com.termux.app.RunCommandService"
    const val ACTION = "com.termux.RUN_COMMAND"
    const val PERMISSION = "com.termux.permission.RUN_COMMAND"
    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    const val BASH = "/data/data/com.termux/files/usr/bin/bash"
    const val HOME = "/data/data/com.termux/files/home"

    fun arguments(command: String): Array<String> = arrayOf("-c", command)

    const val SETUP =
        "Termux setup: 1) install Termux (F-Droid/GitHub build). 2) In Termux run: " +
            "mkdir -p ~/.termux && echo 'allow-external-apps=true' >> ~/.termux/termux.properties && termux-reload-settings. " +
            "3) Android Settings → Apps → VictorLaunch → Permissions → Additional permissions → Run commands in Termux environment → Allow."

    /** Formats Termux's result bundle fields into the output view text. */
    fun formatResult(stdout: String?, stderr: String?, exitCode: Int?, err: Int?, errmsg: String?): String =
        buildString {
            if (!stdout.isNullOrEmpty()) append(stdout.trimEnd()).append('\n')
            if (!stderr.isNullOrEmpty()) append("[stderr]\n").append(stderr.trimEnd()).append('\n')
            if (err != null && err != -1 && err != 0) append("[termux error $err] ").append(errmsg.orEmpty()).append('\n')
            append("[exit ${exitCode ?: "?"}]")
        }
}
