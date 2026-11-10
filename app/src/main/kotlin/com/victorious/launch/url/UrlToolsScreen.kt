package com.victorious.launch.url

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.victorious.launch.core.url.HeuristicReport
import com.victorious.launch.core.url.RedirectResult
import com.victorious.launch.core.url.UrlCleaner
import com.victorious.launch.core.url.UrlHeuristics
import com.victorious.launch.core.url.UrlVerdict
import com.victorious.launch.core.url.VirusTotalReport
import com.victorious.launch.theme.LocalFleetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun UrlToolsScreen(
    context: Context,
    initialUrl: String?,
    onClose: () -> Unit,
) {
    val colors = LocalFleetColors.current
    val scope = rememberCoroutineScope()
    val keyStore = remember { VirusTotalKeyStore(context) }
    var url by remember { mutableStateOf(initialUrl.orEmpty()) }
    var keyDraft by remember { mutableStateOf("") }
    var hasKey by remember { mutableStateOf(keyStore.hasKey()) }
    var busy by remember { mutableStateOf<String?>(null) }
    var heuristics by remember { mutableStateOf<HeuristicReport?>(null) }
    var vt by remember { mutableStateOf<VirusTotalReport?>(null) }
    var chain by remember { mutableStateOf<RedirectResult?>(null) }
    var note by remember { mutableStateOf<String?>(null) }
    var showQr by remember { mutableStateOf(false) }

    fun working(): String = UrlHeuristics.extractUrl(url) ?: url.trim()

    @Composable
    fun fieldColors() =
        TextFieldDefaults.colors(
            focusedTextColor = colors.onBackground,
            unfocusedTextColor = colors.onBackground,
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            cursorColor = colors.primary,
        )

    fun pasteFromClipboard(announceEmpty: Boolean = false) {
        val text = clipboardText(context)
        val extracted = text?.let { UrlHeuristics.extractUrl(it) }
        when {
            extracted != null -> {
                url = extracted
                heuristics = UrlHeuristics.analyze(extracted)
                note = "Pasted from clipboard"
            }
            announceEmpty -> note = "Clipboard has no http(s) link"
        }
    }

    LaunchedEffect(initialUrl) {
        if (url.isBlank()) pasteFromClipboard(announceEmpty = false)
        else if (url.isNotBlank()) heuristics = UrlHeuristics.analyze(url)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row {
            Text("URL Tools", color = colors.onBackground, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close", color = colors.primary) }
        }
        Text(
            "Nothing is sent anywhere until you tap a button. Offline checks always run on this device. " +
                "Expand follows redirects (HEAD, then GET; no cookies; the page is not rendered). " +
                "Shorten uses is.gd, which will see the link. A VirusTotal scan uses your own API key and sends the URL to virustotal.com. " +
                "Google Safe Browsing is not included (it would need a second API key).",
            color = colors.muted,
            fontSize = 13.sp,
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                heuristics = null
                vt = null
                chain = null
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Paste a link", color = colors.muted) },
            singleLine = false,
            maxLines = 4,
            colors = fieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pasteFromClipboard(announceEmpty = true) }) { Text("Paste") }
            OutlinedButton(onClick = {
                val cleaned = UrlCleaner.clean(url)
                if (!cleaned.changed) {
                    note = "No tracking parameters to strip"
                } else {
                    url = cleaned.cleaned
                    heuristics = UrlHeuristics.analyze(cleaned.cleaned)
                    note = "Removed: " + cleaned.removed.joinToString(", ")
                }
                vt = null
                chain = null
            }) { Text("Clean link") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = busy == null,
                onClick = {
                    heuristics = UrlHeuristics.analyze(url)
                    vt = null
                    note = "Offline check only — nothing was sent."
                },
            ) { Text("Safety check") }
            Button(
                enabled = busy == null,
                onClick = {
                    busy = "expand"
                    note = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { UrlToolsClient.unshorten(url) }
                        chain = result
                        val finalUrl = result.finalUrl
                        if (finalUrl != null) {
                            url = finalUrl
                            heuristics = UrlHeuristics.analyze(finalUrl)
                        }
                        note = result.reason ?: "Final URL: ${finalUrl ?: "?"}"
                        busy = null
                    }
                },
            ) { Text(if (busy == "expand") "Expanding…" else "Expand") }
            Button(
                enabled = busy == null,
                onClick = {
                    busy = "shorten"
                    note = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { UrlToolsClient.shorten(url) }
                        if (result.shortUrl != null) {
                            url = result.shortUrl
                            heuristics = UrlHeuristics.analyze(result.shortUrl)
                            note = "Shortened with is.gd. The shortener saw the original link."
                        } else {
                            note = result.error ?: "Shorten failed"
                        }
                        busy = null
                    }
                },
            ) { Text(if (busy == "shorten") "Shortening…" else "Shorten") }
        }
        Button(
            enabled = busy == null,
            onClick = {
                val key = keyStore.key()
                if (key.isNullOrBlank()) {
                    note = "Paste a VirusTotal API key below first."
                    return@Button
                }
                busy = "vt"
                note = null
                scope.launch {
                    val result = withContext(Dispatchers.IO) { UrlToolsClient.virusTotalScan(url, key) }
                    vt = result
                    note = result.error ?: "VirusTotal: ${result.stats?.verdict() ?: result.status ?: "done"}"
                    busy = null
                }
            },
        ) { Text(if (busy == "vt") "Scanning…" else "Scan with VirusTotal") }

        heuristics?.let { report ->
            Text("Offline verdict: ${report.verdict}", color = verdictColor(report.verdict, colors), fontWeight = FontWeight.SemiBold)
            report.findings.forEach { f ->
                Text("• ${f.title} — ${f.detail}", color = verdictColor(f.severity.name, colors), fontSize = 13.sp)
            }
        }
        vt?.let { report ->
            val stats = report.stats
            Text(
                if (stats != null) {
                    "VirusTotal: ${stats.verdict()}  ·  ${stats.malicious} malicious, ${stats.suspicious} suspicious, " +
                        "${stats.harmless} harmless, ${stats.undetected} undetected" +
                        if (stats.timeout > 0) ", ${stats.timeout} timeout" else ""
                } else {
                    "VirusTotal: ${report.error ?: report.status ?: "no result yet"}"
                },
                color = if (stats != null) verdictColor(stats.verdict(), colors) else colors.muted,
                fontWeight = FontWeight.SemiBold,
            )
        }
        chain?.let { result ->
            Text("Redirect chain", color = colors.onBackground, fontWeight = FontWeight.SemiBold)
            result.hops.forEach { hop ->
                val loc = hop.location?.let { " → $it" }.orEmpty()
                val extra = hop.note?.let { " (${it})" }.orEmpty()
                Text("${hop.index}. ${hop.method} ${hop.status ?: "-"}  ${hop.url}$loc$extra", color = colors.muted, fontSize = 12.sp)
            }
            Text("Final: ${result.finalUrl ?: "—"}", color = colors.onBackground, fontSize = 13.sp)
        }
        note?.let { Text(it, color = colors.primary, fontSize = 13.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { copyText(context, working()) }) { Text("Copy") }
            OutlinedButton(onClick = { shareText(context, working()) }) { Text("Share") }
            OutlinedButton(onClick = { openInBrowser(context, working()) }) { Text("Open in browser") }
            OutlinedButton(onClick = { showQr = !showQr }) { Text(if (showQr) "Hide QR" else "QR code") }
        }
        if (showQr) {
            val bmp = remember(url) { UrlQr.bitmap(working()) }
            if (bmp != null) {
                Image(bmp.asImageBitmap(), contentDescription = "QR code of the URL", modifier = Modifier.size(220.dp).padding(8.dp))
            } else {
                Text("Could not build a QR code for this text.", color = colors.danger, fontSize = 13.sp)
            }
        }

        Text("VirusTotal API key", color = colors.onBackground, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
        Text(
            if (!keyStore.available()) {
                "Encrypted storage is unavailable on this device, so a key cannot be saved."
            } else if (hasKey) {
                "A key is saved on this device (EncryptedSharedPreferences / Keystore). It is never logged or exported."
            } else {
                "Paste your own free VirusTotal API key. It is stored like the GitHub token and is never logged or included in a backup."
            },
            color = colors.muted,
            fontSize = 12.sp,
        )
        OutlinedTextField(
            value = keyDraft,
            onValueChange = { keyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("vt-…", color = colors.muted) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            colors = fieldColors(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = keyStore.available() && keyDraft.isNotBlank(),
                onClick = {
                    if (keyStore.save(keyDraft)) {
                        keyDraft = ""
                        hasKey = true
                        note = "VirusTotal key saved"
                    } else {
                        note = "Could not save the key"
                    }
                },
            ) { Text("Save key") }
            OutlinedButton(
                enabled = hasKey,
                onClick = {
                    keyStore.clear()
                    hasKey = false
                    keyDraft = ""
                    note = "VirusTotal key cleared"
                },
            ) { Text("Clear key") }
        }
    }
}

private fun verdictColor(verdict: UrlVerdict, colors: com.victorious.launch.theme.FleetUiColors) =
    when (verdict) {
        UrlVerdict.OK -> colors.primary
        UrlVerdict.CAUTION -> colors.stale
        UrlVerdict.DANGER -> colors.danger
    }

private fun verdictColor(severity: String, colors: com.victorious.launch.theme.FleetUiColors) =
    when (severity) {
        "DANGER" -> colors.danger
        "CAUTION" -> colors.stale
        else -> colors.muted
    }

private fun clipboardText(context: Context): String? {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = cm.primaryClip ?: return null
    if (clip.itemCount <= 0) return null
    return clip.getItemAt(0).coerceToText(context)?.toString()
}

private fun copyText(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
        return
    }
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("url", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    if (text.isBlank()) return
    val intent =
        Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
    context.startActivity(Intent.createChooser(intent, "Share link"))
}

private fun openInBrowser(context: Context, text: String) {
    val url = UrlHeuristics.extractUrl(text) ?: text.trim()
    if (url.isBlank()) {
        Toast.makeText(context, "No URL to open", Toast.LENGTH_SHORT).show()
        return
    }
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    if (uri == null || uri.scheme.isNullOrBlank()) {
        Toast.makeText(context, "No URL to open", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val installed =
        try {
            context.packageManager.getPackageInfo(UrlToolsClient.BROWSER_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    if (installed) intent.setPackage(UrlToolsClient.BROWSER_PACKAGE)
    try {
        context.startActivity(intent)
    } catch (_: Exception) {
        intent.setPackage(null)
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "No browser can open this URL", Toast.LENGTH_SHORT).show()
        }
    }
}
