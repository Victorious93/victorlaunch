package com.victorious.launch.dev

import com.victorious.launch.core.dev.LogLine
import com.victorious.launch.core.dev.LogRing
import com.victorious.launch.core.dev.Logcat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Streams `logcat` from VictorLaunch's own process. Without READ_LOGS the system only returns
 * our own lines; with it (granted via Shizuku/adb, then an app restart) it sees every app.
 */
class LogcatSession {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val ring = LogRing(5000)
    private var process: Process? = null
    private var job: Job? = null

    private val _lines = MutableStateFlow<List<LogLine>>(emptyList())
    val lines: StateFlow<List<LogLine>> = _lines
    private val _status = MutableStateFlow("Stopped")
    val status: StateFlow<String> = _status

    @Volatile
    var paused: Boolean = false

    fun start() {
        if (job?.isActive == true) return
        job =
            scope.launch {
                try {
                    val p = ProcessBuilder(Logcat.argv()).redirectErrorStream(true).start()
                    process = p
                    _status.value = "Streaming"
                    var dirty = false
                    val publisher =
                        launch {
                            while (isActive) {
                                delay(250)
                                if (dirty && !paused) {
                                    _lines.value = synchronized(ring) { ring.snapshot() }
                                    dirty = false
                                }
                            }
                        }
                    p.inputStream.bufferedReader().useLines { seq ->
                        for (raw in seq) {
                            if (!isActive) break
                            synchronized(ring) { ring.add(Logcat.parse(raw)) }
                            dirty = true
                        }
                    }
                    publisher.cancel()
                    _status.value = "logcat exited (${runCatching { p.waitFor() }.getOrDefault(-1)})"
                } catch (e: Exception) {
                    _status.value = "logcat unavailable: ${e.message}"
                }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
        process?.destroy()
        process = null
        _status.value = "Stopped"
    }

    fun clear() {
        synchronized(ring) { ring.clear() }
        _lines.value = emptyList()
    }

    fun snapshot(): List<LogLine> = synchronized(ring) { ring.snapshot() }
}
