package com.victorious.launch.github

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.app.Activity
import com.victorious.launch.core.github.GitHubCopy
import com.victorious.launch.core.github.GitHubError
import com.victorious.launch.core.github.RelativeTime
import com.victorious.launch.theme.LocalFleetColors
import kotlinx.coroutines.launch

@Composable
fun GitHubSettingsScreen(activity: Activity, onClose: () -> Unit) {
    val colors = LocalFleetColors.current
    val store = remember { GitHubTokenStore(activity) }
    val prefs = remember { GitHubPrefs(activity) }
    var hasToken by remember { mutableStateOf(store.hasToken()) }
    var tokenField by remember { mutableStateOf("") }
    var repoField by remember { mutableStateOf("") }
    var repos by remember { mutableStateOf(prefs.repos()) }
    var note by remember { mutableStateOf<String?>(null) }
    var state by remember { mutableStateOf(prefs.state().copy(hasToken = hasToken, repos = repos)) }
    val scope = rememberCoroutineScope()

    fun reload() {
        hasToken = store.hasToken()
        repos = prefs.repos()
        state = prefs.state().copy(hasToken = hasToken, repos = repos)
    }

    LaunchedEffect(Unit) { GitHubRefresh.schedule(activity) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("GitHub widgets", color = colors.primary, fontSize = 22.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        Text(
            "Home-screen widgets for pull requests, Actions runs and releases. " +
                "VictorLaunch talks only to https://api.github.com. A fine-grained token is stored with " +
                "EncryptedSharedPreferences (Android Keystore), is never logged, and is never included in config.export.",
            color = colors.muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text("Token", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        Text(
            if (!store.available()) {
                "Encrypted storage is unavailable on this device. The token will not be saved."
            } else if (hasToken) {
                "A token is stored. Paste a new one to replace it, or clear it."
            } else {
                "Create a fine-grained personal access token with read access to Pull requests, Actions and Contents (for releases) of the repos you add."
            },
            color = colors.muted,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = tokenField,
            onValueChange = { tokenField = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("Fine-grained token") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            enabled = store.available(),
        )
        Row(modifier = Modifier.padding(top = 8.dp)) {
            Button(
                onClick = {
                    val value = tokenField.trim()
                    if (value.isBlank()) {
                        note = "Paste a token first"
                        return@Button
                    }
                    if (!store.save(value)) {
                        note = "Could not save the token"
                        return@Button
                    }
                    tokenField = ""
                    hasToken = true
                    note = "Token saved"
                    GitHubRefresh.schedule(activity)
                    GitHubRefresh.refreshNow(activity)
                    reload()
                },
                enabled = store.available(),
            ) { Text("Save token") }
            OutlinedButton(
                onClick = {
                    store.clear()
                    tokenField = ""
                    hasToken = false
                    note = "Token cleared"
                    reload()
                    GitHubRefresh.refreshNow(activity)
                },
                enabled = hasToken,
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Clear") }
        }
        Text("Repositories", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        Text("owner/repo, or a github.com URL.", color = colors.muted, fontSize = 13.sp)
        OutlinedTextField(
            value = repoField,
            onValueChange = { repoField = it },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            label = { Text("owner/repo") },
            singleLine = true,
        )
        Button(
            onClick = {
                val added = prefs.addRepo(repoField)
                if (added == null) {
                    note = "Not a valid owner/repo"
                } else {
                    repoField = ""
                    note = "Added ${added.full}"
                    reload()
                    GitHubRefresh.refreshNow(activity)
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Add repository") }
        repos.forEach { repo ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Text(repo.full, color = colors.onBackground, fontSize = 14.sp, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = {
                        prefs.removeRepo(repo)
                        reload()
                        note = "Removed ${repo.full}"
                        GitHubRefresh.refreshNow(activity)
                    },
                ) { Text("Remove", color = colors.muted) }
            }
        }
        if (repos.isEmpty()) {
            Text("No repositories yet.", color = colors.muted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        Text("Status", color = colors.secondary, fontSize = 16.sp, modifier = Modifier.padding(top = 20.dp, bottom = 6.dp))
        val err = state.globalError
        if (err != null) {
            val color = if (err is GitHubError.RateLimited) colors.stale else colors.danger
            Text(err.message, color = color, fontSize = 13.sp)
        }
        GitHubCopy.rateLine(state.rate, System.currentTimeMillis())?.let {
            Text(it, color = colors.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (state.fetchedAtMs > 0L) {
            Text(
                "Last refresh ${RelativeTime.ago(java.time.Instant.ofEpochMilli(state.fetchedAtMs).toString(), System.currentTimeMillis())}",
                color = colors.muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Text(
            "Widgets refresh at least every 15 minutes via WorkManager, and when you tap Refresh on the widget or the button below.",
            color = colors.muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = {
                scope.launch {
                    note = "Refreshing…"
                    val next = GitHubRefresh.run(activity)
                    state = next
                    hasToken = next.hasToken
                    repos = next.repos
                    note =
                        when (val e = next.globalError) {
                            null -> "Refreshed"
                            else -> e.message
                        }
                }
            },
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Refresh now") }
        note?.let { Text(it, color = colors.secondary, modifier = Modifier.padding(top = 12.dp)) }
    }
}
