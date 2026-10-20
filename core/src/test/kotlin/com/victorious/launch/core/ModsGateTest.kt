package com.victorious.launch.core

import com.victorious.launch.mods.GetoGrantRequest
import com.victorious.launch.mods.GetoIdentification
import com.victorious.launch.mods.LspatchRequest
import com.victorious.launch.mods.ModsCatalog
import com.victorious.launch.mods.ModsCommands
import com.victorious.launch.mods.ModsGate
import com.victorious.launch.mods.ModsTool
import com.victorious.launch.mods.RootModuleInstallRequest
import com.victorious.launch.mods.RootModuleKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModsGateTest {
    @Test
    fun everyToolStaysOffUntilMasterAndToolEnabled() {
        for (tool in ModsTool.entries) {
            assertFalse(ModsGate.canUse(masterEnabled = false, toolEnabled = true))
            assertFalse(ModsGate.canUse(masterEnabled = true, toolEnabled = false))
            assertTrue(ModsGate.canUse(masterEnabled = true, toolEnabled = true))
            assertTrue(tool.license.startsWith("GPL-3.0"))
            assertTrue(tool.stockPackages.isNotEmpty())
            val spec = ModsCatalog.spec(tool)
            assertTrue(spec.rootPath.isNotBlank())
            assertTrue(spec.nonRootPath.isNotBlank())
        }
    }

    @Test
    fun detectsLsposedFromModuleDirOrManagerPackage() {
        assertTrue(ModsCatalog.detectLsposedModules(listOf("zygisk_lsposed")))
        assertFalse(ModsCatalog.detectLsposedModules(listOf("busybox")))
        assertTrue(ModsCatalog.detectInstalled(ModsTool.LSPOSED, setOf("org.lsposed.manager")))
        assertTrue(ModsCatalog.detectInstalled(ModsTool.GETO, setOf("com.android.geto")))
        assertTrue(ModsCatalog.detectInstalled(ModsTool.MMRL, setOf("com.dergoogler.mmrl")))
        assertTrue(ModsCatalog.detectInstalled(ModsTool.LSPATCH, setOf("org.lsposed.lspatch")))
    }

    @Test
    fun lspatchRequestRequiresSafePackagesAndAModule() {
        assertNull(LspatchRequest("com.android.settings", listOf("org.lsposed.example")).validate())
        assertEquals("pick at least one module", LspatchRequest("com.android.settings", emptyList()).validate())
        assertEquals("invalid target package", LspatchRequest("com.android.settings; rm", listOf("a.b")).validate())
    }

    @Test
    fun getoGrantOnlyAllowsIdentifiedPackage() {
        assertNull(GetoGrantRequest().validate())
        assertEquals(
            "Geto package is not the identified stock app",
            GetoGrantRequest("com.evil.app").validate(),
        )
    }

    @Test
    fun privilegedCommandBuildersRejectShellMetacharacters() {
        assertEquals(
            listOf("pm", "grant", "com.android.geto", "android.permission.WRITE_SECURE_SETTINGS"),
            ModsCommands.grantWriteSecureSettings("com.android.geto"),
        )
        assertEquals(listOf("ls", "/data/adb/modules"), ModsCommands.listAdbModules())
        assertEquals(
            listOf("magisk", "--install-module", "/sdcard/Download/mod.zip"),
            ModsCommands.magiskInstallModule("/sdcard/Download/mod.zip"),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun magiskInstallRejectsUnsafePath() {
        ModsCommands.magiskInstallModule("/tmp/mod.zip; reboot")
    }

    @Test
    fun mmrlReadonlyCatalogIsStaticAndNonEmpty() {
        assertTrue(ModsCatalog.MMRL_READONLY_REPOS.size >= 3)
        assertFalse(ModsCatalog.MMRL_READONLY_REPOS.any { it.startsWith("http") })
    }

    @Test
    fun getoIsJackEblanGetoAtHighConfidence() {
        assertEquals("Geto", GetoIdentification.SPELLING)
        assertEquals("com.android.geto", GetoIdentification.PACKAGE)
        assertEquals("https://github.com/JackEblan/Geto", GetoIdentification.UPSTREAM)
        assertEquals("GPL-3.0-only", GetoIdentification.LICENSE)
        assertEquals(90, GetoIdentification.CONFIDENCE_PERCENT)
        assertTrue(GetoIdentification.RATIONALE.contains("WRITE_SECURE_SETTINGS"))
        assertEquals(GetoIdentification.PACKAGE, ModsTool.GETO.stockPackages.first())
    }

    @Test
    fun rootModuleInstallersAreKindSpecificAndRejectUnsafePaths() {
        val zip = "/sdcard/Download/mod.zip"
        assertEquals(listOf("magisk", "--install-module", zip), ModsCommands.installModule(RootModuleKind.MAGISK, zip))
        assertEquals(listOf("ksud", "module", "install", zip), ModsCommands.installModule(RootModuleKind.KERNELSU, zip))
        assertEquals(listOf("apd", "module", "install", zip), ModsCommands.installModule(RootModuleKind.APATCH, zip))
        assertNull(RootModuleInstallRequest(RootModuleKind.MAGISK, zip).validate())
        assertEquals("invalid zip path", RootModuleInstallRequest(RootModuleKind.MAGISK, "/tmp/x.zip; rm").validate())
        for (kind in RootModuleKind.entries) {
            assertTrue(kind.id.isNotBlank())
            assertEquals(kind, RootModuleKind.fromId(kind.id))
        }
        assertEquals(null, RootModuleKind.fromId("supersu"))
    }
}
