package com.victorious.launch.command

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.victorious.launch.core.command.CommandTarget
import com.victorious.launch.core.command.DestructiveCommands
import com.victorious.launch.core.command.SavedCommand
import com.victorious.launch.core.command.TermuxRun
import com.victorious.launch.prefs.AppSettings
import com.victorious.launch.theme.LocalFleetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Command palette, opened from drawer search with a leading ">". Every run is an explicit tap here. */
@Composable
fun CommandPaletteDialog(initial: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalFleetColors.current
    val scope = rememberCoroutineScope()
    val settings = remember { AppSettings(context) }
    val runner = remember { CommandRunner(context, settings) }
    val store = remember { CommandStore(context) }
    var command by remember { mutableStateOf(initial) }
    var target by remember { mutableStateOf(CommandTarget.SHIZUKU) }
    var saved by remember { mutableStateOf(store.all()) }
    var output by remember { mutableStateOf("") }
    var running by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<Pair<String, CommandTarget>?>(null) }
    var saveName by remember { mutableStateOf<String?>(null) }

    fun execute(cmd: String, t: CommandTarget) {
        running = true
        output = "$ $cmd  (${t.label})\n…"
        scope.launch {
            val out = withContext(Dispatchers.IO) { runCatching { runner.run(cmd, t) }.getOrElse { "[error] ${it.message}" } }
            output = "$ $cmd  (${t.label})\n$out"
            running = false
        }
    }

    fun requestRun(cmd: String, t: CommandTarget) {
        if (cmd.isBlank() || running) return
        if (DestructiveCommands.needsConfirm(cmd)) confirm = cmd to t else execute(cmd, t)
    }

    confirm?.let { (cmd, t) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Run destructive command?") },
            text = {
                Text("This command " + DestructiveCommands.reasons(cmd).joinToString("; ") + ".\n\n$cmd\n\nvia ${t.label}")
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    execute(cmd, t)
                }) { Text("Run anyway", color = colors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
    saveName?.let { name ->
        AlertDialog(
            onDismissRequest = { saveName = null },
            title = { Text("Save command") },
            text = { OutlinedTextField(value = name, onValueChange = { saveName = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    store.upsert(SavedCommand(UUID.randomUUID().toString(), name.ifBlank { command.take(30) }, command, target))
                    saved = store.all()
                    saveName = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { saveName = null }) { Text("Cancel") } },
        )
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(12.dp)
                .background(colors.tile, RoundedCornerShape(16.dp))
                .padding(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Command palette", color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text("Close") }
            }
            OutlinedTextField(
                value = command,
                onValueChange = { command = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("shell command") },
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, color = colors.onBackground),
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CommandTarget.entries.forEach { t ->
                    FilterChip(selected = target == t, onClick = { target = t }, label = { Text(t.label) })
                }
            }
            runner.availability(target)?.let { Text(it, color = colors.stale, fontSize = 12.sp) }
            if (target == CommandTarget.TERMUX && runner.availability(target) == null) {
                Text("Runs in Termux in the background with bash -c.", color = colors.muted, fontSize = 11.sp)
            }
            val reasons = DestructiveCommands.reasons(command)
            if (reasons.isNotEmpty()) Text("⚠ " + reasons.joinToString("; ") + " — you'll be asked to confirm", color = colors.danger, fontSize = 12.sp)
            Row {
                TextButton(enabled = !running && command.isNotBlank(), onClick = { requestRun(command, target) }) {
                    Text(if (running) "Running…" else "Run")
                }
                TextButton(enabled = command.isNotBlank(), onClick = { saveName = "" }) { Text("Save") }
                TextButton(enabled = output.isNotEmpty(), onClick = {
                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("output", output))
                }) { Text("Copy output") }
            }
            if (output.isNotEmpty()) {
                SelectionContainer(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .background(colors.onBackground.copy(alpha = 0.06f), RoundedCornerShape(8.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                ) {
                    Text(output, color = colors.onBackground, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                }
            }
            Text("Saved", color = colors.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
            if (saved.isEmpty()) Text("No saved commands yet.", color = colors.muted, fontSize = 12.sp)
            LazyColumn(Modifier.weight(1f)) {
                items(saved, key = { it.id }) { s ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            store.upsert(s.copy(favorite = !s.favorite))
                            saved = store.all()
                        }) { Text(if (s.favorite) "★" else "☆", color = colors.secondary) }
                        Column(Modifier.weight(1f)) {
                            Text(s.name, color = colors.onBackground, fontSize = 13.sp)
                            Text("${s.target.label} · ${s.command}", color = colors.muted, fontSize = 11.sp, maxLines = 1, fontFamily = FontFamily.Monospace)
                        }
                        TextButton(onClick = {
                            command = s.command
                            target = s.target
                        }) { Text("Edit") }
                        TextButton(enabled = !running, onClick = {
                            command = s.command
                            target = s.target
                            requestRun(s.command, s.target)
                        }) { Text("Run") }
                        TextButton(onClick = {
                            store.delete(s.id)
                            saved = store.all()
                        }) { Text("✕", color = colors.danger) }
                    }
                }
            }
            if (target == CommandTarget.TERMUX) Text(TermuxRun.SETUP, color = colors.muted, fontSize = 10.sp)
        }
    }
}
