package com.victorious.launch.mods

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.Bundle
import com.victorious.launch.prefs.AppSettings

/**
 * Sideloads a user-selected patched APK through [PackageInstaller].
 * The system confirmation sheet is required; this never silent-installs.
 */
object PatchedApkInstaller {
    fun startSession(activity: Activity, apkUri: Uri): String {
        val settings = AppSettings(activity)
        if (!ModsGate.canUse(settings.isModsMasterEnabled(), settings.isModEnabled(ModsTool.LSPATCH))) {
            return "Enable Mods and LSPatch first"
        }
        val installer = activity.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("base.apk", 0, -1).use { out ->
                val input =
                    activity.contentResolver.openInputStream(apkUri)
                        ?: return "Could not read the selected APK"
                input.use { it.copyTo(out) }
                session.fsync(out)
            }
            val confirm = Intent(activity, PatchedApkConfirmActivity::class.java)
            val flags =
                PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
            val pi = PendingIntent.getActivity(activity, sessionId, confirm, flags)
            session.commit(pi.intentSender)
        }
        return "System installer will ask you to confirm"
    }
}

class PatchedApkConfirmActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm =
                    if (Build.VERSION.SDK_INT >= 33) {
                        intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_INTENT)
                    }
                if (confirm != null) {
                    startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> Unit
        }
        finish()
    }
}
