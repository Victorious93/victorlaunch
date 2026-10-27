package com.victorious.launch.core

import com.victorious.launch.core.fleet.FleetManifestCodec
import com.victorious.launch.core.fleet.FleetPins
import com.victorious.launch.core.fleet.FleetPlanner
import com.victorious.launch.core.fleet.FleetRowState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FleetManifestTest {
    private val sample =
        """
        {
          "schemaVersion": 1,
          "generatedAt": "2026-10-26T13:01:27Z",
          "apps": [
            {
              "id": "droidcommand-ai",
              "name": "DroidCommand AI",
              "repo": "Victorious93/droidcommand-AI",
              "applicationId": "ai.droidcommand.app",
              "versionName": "0.1.0",
              "versionCode": 2,
              "debugBuild": true,
              "releaseTag": "v0.1.0",
              "certSha256": "${FleetPins.CONTROLLER_DEBUG}",
              "assets": [{"abi":"universal","url":"https://example.invalid/app.apk","sizeBytes":10,"sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"}]
            },
            {
              "id": "mystery",
              "name": "Mystery",
              "repo": "Victorious93/mystery",
              "applicationId": "com.example.mystery",
              "versionName": "1.0",
              "versionCode": 1,
              "debugBuild": true,
              "releaseTag": "v1",
              "certSha256": "11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF:11:22:33:44:55:66:77:88:99:00:AA:BB:CC:DD:EE:FF",
              "assets": [{"abi":"universal","url":"https://example.invalid/x.apk","sizeBytes":1,"sha256":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"}]
            }
          ]
        }
        """.trimIndent()

    @Test
    fun parseSchemaV1AndPlanInstallStates() {
        val manifest = FleetManifestCodec.parse(sample)
        assertEquals(1, manifest.schemaVersion)
        val rows =
            FleetPlanner.plan(
                manifest,
                installed = mapOf("ai.droidcommand.app" to (1 to "0.0.1")),
            )
        assertEquals(FleetRowState.UPDATE_AVAILABLE, rows[0].state)
        assertTrue(rows[0].trusted)
        assertEquals(FleetRowState.UNTRUSTED, rows[1].state)
        assertFalse(rows[1].trusted)
    }

    @Test
    fun nullCertIsNeverTrusted() {
        assertFalse(FleetPins.isTrusted("ai.droidcommand.app", null))
        assertFalse(FleetPins.isTrusted("ai.droidcommand.app", ""))
    }

    @Test
    fun apkMustMatchPinnedCertAndOptionalAssetDigest() {
        val row =
            FleetPlanner.plan(
                FleetManifestCodec.parse(sample),
                installed = emptyMap(),
            ).first()
        assertTrue(
            FleetPlanner.apkMatchesRow(
                row,
                apkCerts = listOf(FleetPins.CONTROLLER_DEBUG.lowercase()),
                apkSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
        )
        assertFalse(
            FleetPlanner.apkMatchesRow(
                row,
                apkCerts = listOf(FleetPins.VICTORLAUNCH_DEBUG),
                apkSha256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnknownSchema() {
        FleetManifestCodec.parse("""{"schemaVersion":99,"generatedAt":"x","apps":[]}""")
    }
}
