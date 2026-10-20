package com.victorious.launch.shizuku

import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/**
 * Startup modes for the Shizuku server. In this PR the wireless pairing
 * *session* and the native starter binary are not vendored; we open the
 * matching system screens / stock Shizuku app and return the adb line
 * the user must run.
 */
object ShizukuStarters {
    const val STOCK_PACKAGE = ShizukuGate.STOCK_PACKAGE

    fun wirelessDebuggingIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent("android.settings.ADB_WIRELESS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        } else {
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun developerSettingsIntent(): Intent =
        Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun stockManagerIntent(context: Context): Intent? {
        return context.packageManager.getLaunchIntentForPackage(STOCK_PACKAGE)
    }

    fun adbCommand(): String =
        "adb shell sh /storage/emulated/0/Android/data/$STOCK_PACKAGE/start.sh"

    fun rootCommand(): String =
        "su -c sh /data/user/0/$STOCK_PACKAGE/start.sh"

    fun modeHelp(mode: ShizukuStartupMode): String =
        when (mode) {
            ShizukuStartupMode.WIRELESS_DEBUG ->
                "Android 11+: enable Developer options → Wireless debugging, pair with the code in the stock Shizuku app (or, after embed, VictorLaunch's pairing sheet). Then start the server on-device."
            ShizukuStartupMode.ADB_COMMAND ->
                "On a computer with USB debugging: ${adbCommand()}. Re-run after every reboot until the server is embedded."
            ShizukuStartupMode.ROOT ->
                "If the device is rooted: ${rootCommand()}. Sui (Magisk) is also accepted by the Shizuku API client."
        }
}
