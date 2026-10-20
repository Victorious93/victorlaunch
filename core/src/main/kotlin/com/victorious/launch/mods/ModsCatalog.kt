package com.victorious.launch.mods

/**
 * Mods surface catalog. Full LSPosed / LSPatch / Geto / MMRL trees are
 * **not** vendored in this PR (native Zygisk, APK rewriter, WebUI).
 * Each tool has a root path and a non-root path; both stay off until
 * the user enables the master Mods switch **and** that tool.
 */
enum class ModsTool {
    LSPOSED,
    LSPATCH,
    GETO,
    MMRL,
    ;

    val id: String
        get() =
            when (this) {
                LSPOSED -> "lsposed"
                LSPATCH -> "lspatch"
                GETO -> "geto"
                MMRL -> "mmrl"
            }

    val title: String
        get() =
            when (this) {
                LSPOSED -> "LSPosed"
                LSPATCH -> "LSPatch"
                GETO -> "Geto"
                MMRL -> "MMRL"
            }

    val upstream: String
        get() =
            when (this) {
                LSPOSED -> "https://github.com/LSPosed/LSPosed"
                LSPATCH -> "https://github.com/LSPosed/LSPatch"
                GETO -> "https://github.com/JackEblan/Geto"
                MMRL -> "https://github.com/MMRLApp/MMRL"
            }

    val license: String
        get() =
            when (this) {
                LSPOSED, LSPATCH, MMRL -> "GPL-3.0"
                GETO -> "GPL-3.0-only"
            }

    val stockPackages: List<String>
        get() =
            when (this) {
                LSPOSED -> listOf("org.lsposed.manager", "org.lsposed.lspd")
                LSPATCH -> listOf("org.lsposed.lspatch")
                GETO -> listOf("com.android.geto")
                MMRL -> listOf("com.dergoogler.mmrl")
            }
}

data class ModsToolSpec(
    val tool: ModsTool,
    val rootPath: String,
    val nonRootPath: String,
    val embedStatus: String,
    val risks: String,
)

object ModsCatalog {
    val ALL: List<ModsToolSpec> =
        listOf(
            ModsToolSpec(
                tool = ModsTool.LSPOSED,
                rootPath =
                    "Detect Zygisk/Riru LSPosed (module dirs + stock manager package), open the manager, hand off module/scope UI. Shell listing of /data/adb/modules is available when Shizuku/root is ready.",
                nonRootPath =
                    "LSPosed needs Zygisk/Riru. Non-root uses the LSPatch row instead. If the manager APK is installed it can still be opened read-only.",
                embedStatus = "skeleton + stock org.lsposed.manager",
                risks =
                    "Xposed modules run inside other apps. A bad module can steal data or break the system. Reboot to undo. Never install a module you do not trust.",
            ),
            ModsToolSpec(
                tool = ModsTool.LSPATCH,
                rootPath =
                    "Same patcher as non-root, or install LSPosed as a Magisk/KernelSU module via MMRL. Patching does not require Zygisk.",
                nonRootPath =
                    "Select a target APK and modules, confirm, then hand off to stock LSPatch (org.lsposed.lspatch). Shizuku can resolve pm path. Installing the patched APK always uses the system installer so you confirm.",
                embedStatus = "skeleton; lspatch.jar / native rewriter not vendored",
                risks =
                    "Patched APKs resign the app. Play Integrity / banking apps may refuse them. Uninstall the patched package to undo. Confirm every install.",
            ),
            ModsToolSpec(
                tool = ModsTool.GETO,
                rootPath =
                    "Grant WRITE_SECURE_SETTINGS to com.android.geto via su, then open Geto to apply per-app settings (Developer Options, USB debugging, etc.).",
                nonRootPath =
                    "Grant WRITE_SECURE_SETTINGS through Shizuku (pm grant) when Shizuku is enabled, then open stock Geto. Without Shizuku, only a hand-off is offered.",
                embedStatus = "skeleton + stock com.android.geto",
                risks =
                    "WRITE_SECURE_SETTINGS can hide developer options and change global settings. A banking app may still detect other signals. Revoke the grant in App permissions / appops.",
            ),
            ModsToolSpec(
                tool = ModsTool.MMRL,
                rootPath =
                    "Open MMRL to browse/install Magisk, KernelSU, and APatch modules. Optional magisk --install-module command is built but not auto-run.",
                nonRootPath =
                    "Read-only: a static list of well-known repo names (no INTERNET, no download). Hand-off to stock MMRL (com.dergoogler.mmrl) which itself needs root to install.",
                embedStatus = "skeleton + stock com.dergoogler.mmrl; no repo fetch",
                risks =
                    "Magisk modules modify the system at boot. A bad zip can bootloop. Keep a recovery path. MMRL is GPLv3; we do not vendor its WebUI.",
            ),
        )

    fun spec(tool: ModsTool): ModsToolSpec = ALL.first { it.tool == tool }

    /** Static, offline MMRL repo names. Not fetched. */
    val MMRL_READONLY_REPOS: List<String> =
        listOf(
            "Magisk Modules Alt Repo",
            "Googlers Repository (repo.mmrl.dev)",
            "KernelSU module catalog",
            "APatch module catalog",
        )

    fun detectInstalled(
        tool: ModsTool,
        installedPackages: Set<String>,
    ): Boolean = tool.stockPackages.any { it in installedPackages }

    fun detectLsposedModules(moduleDirNames: List<String>): Boolean =
        moduleDirNames.any { name ->
            val n = name.lowercase()
            n.contains("lsposed") || n.contains("zygisk_lsposed") || n.contains("riru_lsposed")
        }
}

object ModsGate {
    fun canUse(masterEnabled: Boolean, toolEnabled: Boolean): Boolean = masterEnabled && toolEnabled
}

/**
 * Don wrote "Geto" (no URL). Identification for the Mods row.
 *
 * **Identified as:** [JackEblan/Geto](https://github.com/JackEblan/Geto),
 * F-Droid / GitHub app `com.android.geto`, license GPL-3.0-only.
 *
 * **Confidence: 90%.** It is the well-known Android "Geto" that applies
 * per-app `WRITE_SECURE_SETTINGS` (developer options, animations, and
 * similar). That sits next to LSPosed / LSPatch / MMRL on a Shizuku or
 * rooted phone. Don did not paste a repository URL, so this is not 100%.
 *
 * Rejected: unrelated "GETO" acronyms; other apps with similar names
 * that are not settings-override tools.
 */
object GetoIdentification {
    const val SPELLING = "Geto"
    const val UPSTREAM = "https://github.com/JackEblan/Geto"
    const val PACKAGE = "com.android.geto"
    const val LICENSE = "GPL-3.0-only"
    const val CONFIDENCE_PERCENT = 90
    const val RATIONALE =
        "Don named Geto next to LSPosed, LSPatch and MMRL. JackEblan/Geto " +
            "(com.android.geto, GPL-3.0-only) is the Android app that applies " +
            "per-app WRITE_SECURE_SETTINGS. No URL was given, so confidence is 90%."
}

enum class RootModuleKind {
    MAGISK,
    KERNELSU,
    APATCH,
    ;

    val id: String
        get() =
            when (this) {
                MAGISK -> "magisk"
                KERNELSU -> "kernelsu"
                APATCH -> "apatch"
            }

    val title: String
        get() =
            when (this) {
                MAGISK -> "Magisk"
                KERNELSU -> "KernelSU"
                APATCH -> "APatch"
            }

    companion object {
        fun fromId(raw: String): RootModuleKind? = entries.firstOrNull { it.id == raw }
    }
}

data class LspatchRequest(
    val targetPackage: String,
    val modulePackages: List<String>,
) {
    fun validate(): String? {
        if (!PACKAGE.matches(targetPackage)) return "invalid target package"
        if (modulePackages.isEmpty()) return "pick at least one module"
        if (modulePackages.any { !PACKAGE.matches(it) }) return "invalid module package"
        return null
    }

    companion object {
        private val PACKAGE = Regex("^[A-Za-z0-9._]+$")
    }
}

data class GetoGrantRequest(val packageName: String = GetoIdentification.PACKAGE) {
    fun validate(): String? {
        if (packageName !in ModsTool.GETO.stockPackages) return "Geto package is not the identified stock app"
        return null
    }
}

data class RootModuleInstallRequest(
    val kind: RootModuleKind,
    val zipPath: String,
) {
    fun validate(): String? {
        if (!SAFE_ZIP.matches(zipPath)) return "invalid zip path"
        return null
    }

    companion object {
        val SAFE_ZIP = Regex("^[/A-Za-z0-9._-]+\\.zip$")
    }
}
