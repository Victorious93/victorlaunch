package com.victorious.launch.command

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import com.victorious.launch.VictorLaunchApp
import com.victorious.launch.core.command.CommandCodec
import com.victorious.launch.core.command.CommandTarget
import com.victorious.launch.core.command.SavedCommand
import com.victorious.launch.core.command.TermuxRun
import com.victorious.launch.dev.RootShell
import com.victorious.launch.prefs.AppSettings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume

/**
 * Executes command-palette commands. Only called from an explicit tap in [CommandPaletteDialog];
 * nothing in the bridge (BridgeService/BridgeDispatcher) references this class.
 */
class CommandRunner(private val context: Context, private val settings: AppSettings) {
    fun availability(target: CommandTarget): String? =
        when (target) {
            CommandTarget.SHIZUKU ->
                if (VictorLaunchApp.instance.privileged.isReady()) null else "Shizuku is off or not connected (Settings → Shizuku)."
            CommandTarget.ROOT ->
                if (settings.isRootShellAllowed()) null else "Root is not allowed. Turn on Dev panel → Allow root first."
            CommandTarget.TERMUX ->
                when {
                    !termuxInstalled() -> "Termux is not installed. " + TermuxRun.SETUP
                    ContextCompat.checkSelfPermission(context, TermuxRun.PERMISSION) != PackageManager.PERMISSION_GRANTED ->
                        "Missing Termux permission. " + TermuxRun.SETUP
                    else -> null
                }
        }

    private fun termuxInstalled(): Boolean =
        try {
            context.packageManager.getPackageInfo(TermuxRun.PACKAGE, 0)
            true
        } catch (_: Exception) {
            false
        }

    suspend fun run(command: String, target: CommandTarget): String {
        availability(target)?.let { return "[unavailable] $it" }
        val argv = listOf("sh", "-c", command)
        return when (target) {
            CommandTarget.SHIZUKU -> VictorLaunchApp.instance.privileged.exec(argv).let { (if (it.ok) "" else "[failed] ") + it.detail }
            CommandTarget.ROOT -> RootShell.run(settings, argv).let { (if (it.ok) "" else "[failed] ") + it.detail }
            CommandTarget.TERMUX -> runTermux(command)
        }
    }

    private suspend fun runTermux(command: String): String {
        val action = "${context.packageName}.TERMUX_RESULT.${UUID.randomUUID()}"
        val result =
            withTimeoutOrNull(60_000) {
                suspendCancellableCoroutine<String> { cont ->
                    val receiver =
                        object : BroadcastReceiver() {
                            override fun onReceive(c: Context, intent: Intent) {
                                runCatching { context.unregisterReceiver(this) }
                                val b: Bundle? = intent.getBundleExtra("result")
                                val text =
                                    if (b == null) {
                                        "[no result bundle from Termux]"
                                    } else {
                                        TermuxRun.formatResult(
                                            b.getString("stdout"),
                                            b.getString("stderr"),
                                            if (b.containsKey("exitCode")) b.getInt("exitCode") else null,
                                            if (b.containsKey("err")) b.getInt("err") else null,
                                            b.getString("errmsg"),
                                        )
                                    }
                                if (cont.isActive) cont.resume(text)
                            }
                        }
                    ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
                    cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }
                    val flags =
                        PendingIntent.FLAG_ONE_SHOT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    val pi = PendingIntent.getBroadcast(context, action.hashCode(), Intent(action).setPackage(context.packageName), flags)
                    val intent =
                        Intent(TermuxRun.ACTION)
                            .setClassName(TermuxRun.PACKAGE, TermuxRun.SERVICE)
                            .putExtra(TermuxRun.EXTRA_PATH, TermuxRun.BASH)
                            .putExtra(TermuxRun.EXTRA_ARGUMENTS, TermuxRun.arguments(command))
                            .putExtra(TermuxRun.EXTRA_WORKDIR, TermuxRun.HOME)
                            .putExtra(TermuxRun.EXTRA_BACKGROUND, true)
                            .putExtra(TermuxRun.EXTRA_PENDING_INTENT, pi)
                    try {
                        context.startService(intent)
                    } catch (e: Exception) {
                        runCatching { context.unregisterReceiver(receiver) }
                        if (cont.isActive) cont.resume("[failed] ${e.javaClass.simpleName}: ${e.message}. " + TermuxRun.SETUP)
                    }
                }
            }
        return result ?: "[timed out after 60s waiting for Termux; it may still be running]"
    }
}

/** Saved commands in SharedPreferences (not part of config.export). */
class CommandStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("command_palette", Context.MODE_PRIVATE)

    fun all(): List<SavedCommand> = CommandCodec.sorted(CommandCodec.decode(prefs.getString("saved", null)))

    fun save(list: List<SavedCommand>) {
        prefs.edit().putString("saved", CommandCodec.encode(list)).apply()
    }

    fun upsert(cmd: SavedCommand) = save(all().filter { it.id != cmd.id } + cmd)

    fun delete(id: String) = save(all().filter { it.id != id })
}
