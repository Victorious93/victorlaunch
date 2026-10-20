package com.victorious.launch.mods

/**
 * Shell builders for Mods (uid 0 or 2000). Strings only — unit-tested.
 * These do not run until the Mods gate is on and the UserService is bound.
 */
object ModsCommands {
    fun grantWriteSecureSettings(packageName: String): List<String> {
        requirePackage(packageName)
        return listOf("pm", "grant", packageName, "android.permission.WRITE_SECURE_SETTINGS")
    }

    fun packagePath(packageName: String): List<String> {
        requirePackage(packageName)
        return listOf("pm", "path", packageName)
    }

    fun listAdbModules(): List<String> = listOf("ls", "/data/adb/modules")

    fun magiskInstallModule(zipPath: String): List<String> {
        requireSafeZip(zipPath)
        return listOf("magisk", "--install-module", zipPath)
    }

    fun kernelsuInstallModule(zipPath: String): List<String> {
        requireSafeZip(zipPath)
        return listOf("ksud", "module", "install", zipPath)
    }

    fun apatchInstallModule(zipPath: String): List<String> {
        requireSafeZip(zipPath)
        return listOf("apd", "module", "install", zipPath)
    }

    fun installModule(kind: RootModuleKind, zipPath: String): List<String> =
        when (kind) {
            RootModuleKind.MAGISK -> magiskInstallModule(zipPath)
            RootModuleKind.KERNELSU -> kernelsuInstallModule(zipPath)
            RootModuleKind.APATCH -> apatchInstallModule(zipPath)
        }

    private fun requireSafeZip(zipPath: String) {
        require(RootModuleInstallRequest.SAFE_ZIP.matches(zipPath)) { "invalid zip path" }
    }

    private fun requirePackage(packageName: String) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) { "invalid packageName" }
    }

}
