package com.victorious.launch.shizuku

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Client against whatever Shizuku binder is in the system — stock app
 * today, an embedded server later. Does nothing until [userEnabled]
 * is true.
 */
class ShizukuSession(
    private val userEnabled: () -> Boolean,
    private val onChanged: () -> Unit = {},
) {
    private val binderReceived =
        Shizuku.OnBinderReceivedListener { onChanged() }
    private val binderDead =
        Shizuku.OnBinderDeadListener { onChanged() }
    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { _, _ -> onChanged() }

    fun start() {
        Shizuku.addBinderReceivedListener(binderReceived)
        Shizuku.addBinderDeadListener(binderDead)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderReceived)
        Shizuku.removeBinderDeadListener(binderDead)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun snapshot(): ShizukuSnapshot {
        if (!userEnabled()) {
            return ShizukuSnapshot(
                userEnabled = false,
                binderAlive = false,
                permissionGranted = false,
                uid = null,
            )
        }
        val alive =
            try {
                Shizuku.pingBinder()
            } catch (_: Exception) {
                false
            }
        if (!alive) {
            return ShizukuSnapshot(
                userEnabled = true,
                binderAlive = false,
                permissionGranted = false,
                uid = null,
            )
        }
        val granted =
            try {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            } catch (_: Exception) {
                false
            }
        val uid =
            try {
                Shizuku.getUid()
            } catch (_: Exception) {
                null
            }
        return ShizukuSnapshot(
            userEnabled = true,
            binderAlive = true,
            permissionGranted = granted,
            uid = uid,
        )
    }

    fun requestPermission(requestCode: Int = 4191) {
        if (!userEnabled()) return
        if (!snapshot().binderAlive) return
        if (snapshot().permissionGranted) return
        try {
            Shizuku.requestPermission(requestCode)
        } catch (_: Exception) {
        }
    }
}
