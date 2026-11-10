package com.victorious.launch.core

import com.victorious.launch.core.backup.ConfigBackupCodec
import com.victorious.launch.core.backup.LauncherPreferences
import com.victorious.launch.core.protocol.Ops
import com.victorious.launch.core.url.FindingSeverity
import com.victorious.launch.core.url.RedirectChain
import com.victorious.launch.core.url.StepOutcome
import com.victorious.launch.core.url.UrlCleaner
import com.victorious.launch.core.url.UrlHeuristics
import com.victorious.launch.core.url.UrlNetPolicy
import com.victorious.launch.core.url.UrlToolsActions
import com.victorious.launch.core.url.UrlVerdict
import com.victorious.launch.core.url.VirusTotalParse
import com.victorious.launch.core.url.VirusTotalStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlToolsTest {
    @Test
    fun heuristicsHttpsOk() {
        val r = UrlHeuristics.analyze("https://example.com/path")
        assertEquals(UrlVerdict.OK, r.verdict)
        assertEquals("example.com", r.host)
        assertEquals("https", r.scheme)
        assertTrue(r.findings.none { it.severity != FindingSeverity.INFO })
    }

    @Test
    fun heuristicsHttpAndIpAndShortenerAndLong() {
        val http = UrlHeuristics.analyze("http://93.184.216.34/")
        assertEquals(UrlVerdict.CAUTION, http.verdict)
        assertTrue(http.findings.any { it.id == "http" })
        assertTrue(http.findings.any { it.id == "ip" })

        val short = UrlHeuristics.analyze("https://bit.ly/abc")
        assertEquals(UrlVerdict.CAUTION, short.verdict)
        assertTrue(short.findings.any { it.id == "shortener" })

        val longUrl = "https://example.com/" + "a".repeat(200)
        val longReport = UrlHeuristics.analyze(longUrl)
        assertTrue(longReport.findings.any { it.id == "length" })
    }

    @Test
    fun heuristicsPunycodeAndUserinfoAndTld() {
        val idn = UrlHeuristics.analyze("https://xn--80ak6aa92e.com/")
        assertEquals(UrlVerdict.DANGER, idn.verdict)
        assertTrue(idn.findings.any { it.id == "idn" })

        val mixed = UrlHeuristics.analyze("https://exаmple.com/") // Cyrillic а
        assertEquals(UrlVerdict.DANGER, mixed.verdict)
        assertTrue(mixed.findings.any { it.id == "idn" || it.id == "homograph" })

        val phish = UrlHeuristics.analyze("https://user:pass@example.com/")
        assertEquals(UrlVerdict.DANGER, phish.verdict)
        assertTrue(phish.findings.any { it.id == "userinfo" })

        val tld = UrlHeuristics.analyze("https://something.xyz/")
        assertEquals(UrlVerdict.CAUTION, tld.verdict)
        assertTrue(tld.findings.any { it.id == "tld" })
    }

    @Test
    fun extractUrlFromText() {
        assertEquals(
            "https://example.com/a",
            UrlHeuristics.extractUrl("See this: https://example.com/a please"),
        )
        assertNull(UrlHeuristics.extractUrl("not a link"))
    }

    @Test
    fun cleanStripsTrackingKeepsOthers() {
        val raw = "https://example.com/p?q=hello&utm_source=x&fbclid=abc&gclid=1&id=42&utm_campaign=c"
        val r = UrlCleaner.clean(raw)
        assertTrue(r.changed)
        assertEquals("https://example.com/p?q=hello&id=42", r.cleaned)
        assertTrue(r.removed.containsAll(listOf("utm_source", "fbclid", "gclid", "utm_campaign")))
        assertFalse(UrlCleaner.isTrackingParam("q"))
        assertFalse(UrlCleaner.isTrackingParam("id"))
        assertTrue(UrlCleaner.isTrackingParam("utm_medium"))
    }

    @Test
    fun cleanNoQueryUnchanged() {
        val r = UrlCleaner.clean("https://example.com/path")
        assertFalse(r.changed)
        assertEquals("https://example.com/path", r.cleaned)
        assertTrue(r.removed.isEmpty())
    }

    @Test
    fun redirectFollowsHeadThenStopsAt200() {
        val map =
            mapOf(
                "https://is.gd/abc" to StepOutcome(status = 301, location = "https://example.com/final"),
                "https://example.com/final" to StepOutcome(status = 200),
            )
        val calls = mutableListOf<String>()
        val r =
            RedirectChain.follow("https://is.gd/abc") { url, method ->
                calls += "$method $url"
                map[url]!!
            }
        assertTrue(r.complete)
        assertEquals("https://example.com/final", r.finalUrl)
        assertEquals(2, r.hops.size)
        assertEquals(listOf("HEAD https://is.gd/abc", "HEAD https://example.com/final"), calls)
    }

    @Test
    fun redirectFallsBackToGetAndResolvesRelative() {
        val r =
            RedirectChain.follow("https://example.com/a") { url, method ->
                when {
                    url == "https://example.com/a" && method == "HEAD" -> StepOutcome(status = 405)
                    url == "https://example.com/a" && method == "GET" -> StepOutcome(status = 302, location = "/b")
                    url == "https://example.com/b" -> StepOutcome(status = 200)
                    else -> StepOutcome(error = "unexpected $method $url")
                }
            }
        assertTrue(r.complete)
        assertEquals("https://example.com/b", r.finalUrl)
        assertEquals("GET", r.hops.first().method)
        assertEquals("/b", r.hops.first().location)
    }

    @Test
    fun redirectStopsAtMaxHopsAndCyclesAndBlocksJavascript() {
        val max =
            RedirectChain.follow("https://example.com/0", maxHops = 3) { url, _ ->
                val n = url.substringAfterLast('/').toInt()
                StepOutcome(status = 301, location = "https://example.com/${n + 1}")
            }
        assertFalse(max.complete)
        assertEquals(3, max.hops.size)
        assertEquals("Stopped at 3 hops", max.reason)

        val cycle =
            RedirectChain.follow("https://example.com/a") { url, _ ->
                if (url.endsWith("/a")) StepOutcome(status = 302, location = "https://example.com/b")
                else StepOutcome(status = 302, location = "https://example.com/a")
            }
        assertFalse(cycle.complete)
        assertEquals("Redirect cycle", cycle.reason)

        val blocked =
            RedirectChain.follow("https://example.com/a") { _, _ ->
                StepOutcome(status = 302, location = "javascript:alert(1)")
            }
        assertFalse(blocked.complete)
        assertEquals("Blocked redirect target", blocked.reason)
    }

    @Test
    fun netPolicyAllowListAndRedact() {
        assertTrue(UrlNetPolicy.isAllowedFixedHost("https://www.virustotal.com/api/v3/urls"))
        assertTrue(UrlNetPolicy.isAllowedFixedHost("https://is.gd/create.php?format=simple&url=x"))
        assertFalse(UrlNetPolicy.isAllowedFixedHost("https://api.github.com/repos/x"))
        assertFalse(UrlNetPolicy.isAllowedFixedHost("http://is.gd/create.php"))
        val secret = "vt-secret-123456"
        val redacted = UrlNetPolicy.redactSecret("x-apikey: $secret exploded", secret)
        assertFalse(redacted.contains(secret))
        assertTrue(redacted.contains("***"))
    }

    @Test
    fun virusTotalParseCounts() {
        val body =
            """{"data":{"id":"an-1","type":"analysis","attributes":{"status":"completed","stats":{"malicious":2,"suspicious":1,"harmless":60,"undetected":10,"timeout":0}}}}"""
        val r = VirusTotalParse.report(body)
        assertEquals("an-1", r.analysisId)
        assertEquals("completed", r.status)
        val s = r.stats!!
        assertEquals(2, s.malicious)
        assertEquals(1, s.suspicious)
        assertEquals(UrlVerdict.DANGER, s.verdict())
        assertEquals("an-1", VirusTotalParse.analysisId("""{"data":{"id":"an-1"}}"""))
        assertEquals(UrlVerdict.CAUTION, VirusTotalStats(0, 1, 10, 0).verdict())
        assertEquals(UrlVerdict.OK, VirusTotalStats(0, 0, 10, 2).verdict())
    }

    @Test
    fun searchAndBackupAndBridgeStayClean() {
        assertTrue(UrlToolsActions.matches("url"))
        assertTrue(UrlToolsActions.matches("unshorten"))
        assertTrue(UrlToolsActions.matches("utm"))
        assertFalse(UrlToolsActions.matches(""))
        val json = ConfigBackupCodec.exportJson(LauncherPreferences(dock = listOf("com.a")))
        assertFalse(json.contains("virustotal", ignoreCase = true))
        assertFalse(json.contains("api_key", ignoreCase = true))
        val banned = listOf("url.scan", "url.unshorten", "url.shorten", "virustotal")
        val names = Ops.ALL.map { it.name }
        banned.forEach { b -> assertFalse("bridge leaked $b", b in names) }
    }
}
