package com.victorious.launch.core.dev

enum class LogLevel(val letter: Char) {
    VERBOSE('V'), DEBUG('D'), INFO('I'), WARN('W'), ERROR('E'), FATAL('F'), ASSERT('A');

    companion object {
        fun of(c: Char): LogLevel? = entries.firstOrNull { it.letter == c }
    }
}

data class LogLine(
    val raw: String,
    val time: String?,
    val pid: Int?,
    val tid: Int?,
    val level: LogLevel?,
    val tag: String?,
    val message: String,
)

data class LogFilter(
    val tag: String = "",
    val minLevel: LogLevel = LogLevel.VERBOSE,
    /** Null = all processes. Empty = package not running → nothing matches. */
    val pids: Set<Int>? = null,
    val text: String = "",
) {
    fun matches(line: LogLine): Boolean {
        val lvl = line.level
        if (lvl != null && lvl.ordinal < minLevel.ordinal) return false
        if (lvl == null && minLevel != LogLevel.VERBOSE) return false
        if (tag.isNotBlank() && line.tag?.contains(tag.trim(), ignoreCase = true) != true) return false
        if (pids != null && (line.pid == null || line.pid !in pids)) return false
        if (text.isNotBlank() && !line.raw.contains(text, ignoreCase = true)) return false
        return true
    }
}

object Logcat {
    // 10-31 04:33:12.345  1234  5678 I ActivityManager: Start proc ...
    private val THREADTIME =
        Regex("""^(\d\d-\d\d\s+\d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEFA])\s+(.*?)\s*: (.*)$""")

    fun argv(tail: Int = 500): List<String> = listOf("logcat", "-v", "threadtime", "-T", tail.toString())

    fun parse(raw: String): LogLine {
        val m = THREADTIME.find(raw) ?: return LogLine(raw, null, null, null, null, null, raw)
        val g = m.groupValues
        return LogLine(raw, g[1], g[2].toIntOrNull(), g[3].toIntOrNull(), LogLevel.of(g[4][0]), g[5], g[6])
    }

    fun parsePidof(out: String?): Set<Int> =
        out.orEmpty().split(Regex("\\s+")).mapNotNull { it.toIntOrNull()?.takeIf { p -> p > 0 } }.toSet()

    fun export(lines: List<LogLine>): String = lines.joinToString("\n") { it.raw }
}

/** Bounded buffer that drops the oldest lines. Not thread-safe; guard externally. */
class LogRing(private val capacity: Int = 5000) {
    private val buf = ArrayDeque<LogLine>()

    init {
        require(capacity > 0)
    }

    fun add(line: LogLine) {
        if (buf.size == capacity) buf.removeFirst()
        buf.addLast(line)
    }

    fun clear() = buf.clear()

    val size: Int get() = buf.size

    fun snapshot(filter: LogFilter? = null): List<LogLine> = if (filter == null) buf.toList() else buf.filter(filter::matches)
}
