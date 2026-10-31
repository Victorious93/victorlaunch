package com.victorious.launch.inspect

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.victorious.launch.core.inspect.AppInspect
import com.victorious.launch.security.SigningCerts
import com.victorious.launch.theme.LocalFleetColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class AppDetails(
    val packageName: String,
    val label: String,
    val versionName: String?,
    val versionCode: Long,
    val targetSdk: Int,
    val minSdk: Int?,
    val installSource: String,
    val certs: List<String>,
    val permissions: List<AppInspect.PermRow>,
    val components: String,
    val apkPaths: List<String>,
)

object AppInspector {
    @Suppress("DEPRECATION")
    fun load(context: Context, packageName: String, label: String): AppDetails? {
        val pm = context.packageManager
        val flags =
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS
        val info: PackageInfo =
            try {
                pm.getPackageInfo(packageName, flags)
            } catch (_: Exception) {
                try {
                    pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                } catch (_: Exception) {
                    return null
                }
            }
        val ai = info.applicationInfo
        val isSystem = ai != null && (ai.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        val installer =
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    pm.getInstallSourceInfo(packageName).installingPackageName
                } else {
                    pm.getInstallerPackageName(packageName)
                }
            } catch (_: Exception) {
                null
            }
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        val paths = buildList {
            ai?.sourceDir?.let { add(it) }
            ai?.splitSourceDirs?.let { addAll(it) }
        }
        return AppDetails(
            packageName = packageName,
            label = label,
            versionName = info.versionName,
            versionCode = versionCode,
            targetSdk = ai?.targetSdkVersion ?: 0,
            minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) ai?.minSdkVersion else null,
            installSource = AppInspect.installSource(installer, isSystem),
            certs = SigningCerts.forPackage(pm, packageName),
            permissions =
                AppInspect.permissionRows(
                    info.requestedPermissions?.toList().orEmpty(),
                    info.requestedPermissionsFlags,
                    PackageInfo.REQUESTED_PERMISSION_GRANTED,
                ),
            components =
                AppInspect.componentSummary(
                    info.activities?.size ?: 0,
                    info.services?.size ?: 0,
                    info.receivers?.size ?: 0,
                    info.providers?.size ?: 0,
                ),
            apkPaths = paths,
        )
    }

    /** Copies base + split APKs into a SAF tree. Returns number of files written. */
    fun extract(context: Context, d: AppDetails, tree: Uri): Int {
        val cr = context.contentResolver
        val parent =
            DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        d.apkPaths.forEachIndexed { i, path ->
            val split = if (i == 0) null else AppInspect.splitNameFromPath(path)
            val name = AppInspect.apkFileName(d.packageName, d.versionName, split)
            val out =
                DocumentsContract.createDocument(cr, parent, "application/vnd.android.package-archive", name)
                    ?: return@forEachIndexed
            cr.openOutputStream(out)?.use { os -> File(path).inputStream().use { it.copyTo(os) } } ?: return@forEachIndexed
            n++
        }
        return n
    }
}

@Composable
fun AppInspectorDialog(packageName: String, label: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val colors = LocalFleetColors.current
    val scope = rememberCoroutineScope()
    val details = remember(packageName) { AppInspector.load(context, packageName, label) }
    var status by remember { mutableStateOf<String?>(null) }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri == null || details == null) return@rememberLauncherForActivityResult
            status = "Extracting…"
            scope.launch {
                status =
                    try {
                        val n = withContext(Dispatchers.IO) { AppInspector.extract(context, details, uri) }
                        "Saved $n of ${details.apkPaths.size} APK file(s)"
                    } catch (e: Exception) {
                        "Extract failed: ${e.javaClass.simpleName}"
                    }
            }
        }
    fun copy(text: String) {
        val cm = context.getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("app inspector", text))
        Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(colors.tile, RoundedCornerShape(16.dp))
                .padding(16.dp),
        ) {
            Text(label, color = colors.onBackground, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            if (details == null) {
                Text("Package not found or not visible to VictorLaunch.", color = colors.danger)
                TextButton(onClick = onDismiss) { Text("Close") }
                return@Column
            }
            LazyColumn(Modifier.weight(1f)) {
                item {
                    Field("Package", details.packageName, colors.muted, colors.onBackground) { copy(details.packageName) }
                    Field("Version", "${details.versionName ?: "?"} (${details.versionCode})", colors.muted, colors.onBackground) {
                        copy("${details.versionName} (${details.versionCode})")
                    }
                    Field("Target SDK", AppInspect.sdkLabel(details.targetSdk), colors.muted, colors.onBackground)
                    Field("Min SDK", AppInspect.sdkLabel(details.minSdk), colors.muted, colors.onBackground)
                    Field("Installed by", details.installSource, colors.muted, colors.onBackground)
                    Field("Components", details.components, colors.muted, colors.onBackground)
                    val apkSize = details.apkPaths.sumOf { runCatching { File(it).length() }.getOrDefault(0L) }
                    Field(
                        "APK files",
                        "${details.apkPaths.size} (${AppInspect.humanBytes(apkSize)})",
                        colors.muted,
                        colors.onBackground,
                    )
                    if (details.certs.isEmpty()) {
                        Field("Signing cert SHA-256", "unavailable", colors.muted, colors.onBackground)
                    }
                    details.certs.forEach { c ->
                        Field("Signing cert SHA-256 (tap to copy)", c, colors.muted, colors.onBackground, mono = true) { copy(c) }
                    }
                    val granted = details.permissions.count { it.granted }
                    Text(
                        "Permissions: ${details.permissions.size} requested, $granted granted",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(details.permissions) { p ->
                    Text(
                        (if (p.granted) "✓ " else "· ") + p.name,
                        color = if (p.granted) colors.onBackground else colors.muted,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            status?.let { Text(it, color = colors.secondary, fontSize = 12.sp) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { picker.launch(null) }) { Text("Extract APK…") }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }) { Text("App info") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

@Composable
private fun Field(
    title: String,
    value: String,
    titleColor: androidx.compose.ui.graphics.Color,
    valueColor: androidx.compose.ui.graphics.Color,
    mono: Boolean = false,
    onCopy: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (onCopy != null) Modifier.clickable { onCopy() } else Modifier)
            .padding(vertical = 4.dp),
    ) {
        Text(title, color = titleColor, fontSize = 11.sp)
        Text(value, color = valueColor, fontSize = if (mono) 11.sp else 14.sp, fontFamily = if (mono) FontFamily.Monospace else null)
    }
}
