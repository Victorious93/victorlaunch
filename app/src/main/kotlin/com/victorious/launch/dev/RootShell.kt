package com.victorious.launch.dev

import com.victorious.launch.core.dev.ShellQuote
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.shizuku.PrivilegedResult
import java.util.concurrent.TimeUnit

/**
 * Runs commands through `su`, but only after the user opted in (Dev panel → Allow root).
 * Nothing here is reachable from the bridge.
 */
object RootShell {
    fun run(settings: AppSettings, argv: List<String>, timeoutSec: Long = 30): PrivilegedResult {
        if (!settings.isRootShellAllowed()) return PrivilegedResult(false, "Root is not allowed (Dev panel → Allow root)")
        return try {
            val p = ProcessBuilder("su", "-c", ShellQuote.join(argv)).redirectErrorStream(true).start()
            val out = StringBuilder()
            val reader = Thread { out.append(p.inputStream.bufferedReader().readText()) }.also { it.start() }
            if (!p.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                p.destroy()
                return PrivilegedResult(false, "error: timed out")
            }
            reader.join(1000)
            val text = out.toString().trim()
            if (p.exitValue() == 0) PrivilegedResult(true, text.ifBlank { "ok" }) else PrivilegedResult(false, "error ${p.exitValue()}: $text")
        } catch (e: Exception) {
            PrivilegedResult(false, "error: ${e.message ?: "su unavailable"}")
        }
    }
}
