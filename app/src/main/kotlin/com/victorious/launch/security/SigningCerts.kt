package com.victorious.launch.security

import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

object SigningCerts {
    fun sha256Colon(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString(":") { b -> "%02X".format(b) }
    }

    @Suppress("DEPRECATION")
    fun forPackage(pm: PackageManager, packageName: String): List<String> {
        return try {
            val sigs =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    val info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                    val si = info.signingInfo ?: return emptyList()
                    si.apkContentsSigners
                } else {
                    pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
                }
            sigs.orEmpty().map { sha256Colon(it.toByteArray()) }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
