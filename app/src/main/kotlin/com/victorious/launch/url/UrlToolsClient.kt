package com.victorious.launch.url

import com.victorious.launch.core.url.RedirectChain
import com.victorious.launch.core.url.RedirectResult
import com.victorious.launch.core.url.StepOutcome
import com.victorious.launch.core.url.UrlHeuristics
import com.victorious.launch.core.url.UrlNetPolicy
import com.victorious.launch.core.url.VirusTotalParse
import com.victorious.launch.core.url.VirusTotalReport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Network calls for URL Tools. Fixed-host calls go only to virustotal.com / is.gd.
 * Unshorten follows the user-supplied URL's hosts, only when the user taps Expand.
 * No cookies, no page rendering, nothing is sent without an explicit tap.
 */
object UrlToolsClient {
    const val BROWSER_PACKAGE = "com.victorious.browser"
    private const val UA = "VictorLaunch-URLTools"

    data class ShortenResult(val shortUrl: String?, val error: String?)

    fun unshorten(raw: String): RedirectResult =
        RedirectChain.follow(raw) { url, method -> oneShot(url, method) }

    fun shorten(raw: String): ShortenResult {
        val target = UrlHeuristics.extractUrl(raw) ?: return ShortenResult(null, "Paste an http or https link first.")
        val endpoint = UrlNetPolicy.shortenerCreateUrl(target)
        if (!UrlNetPolicy.isShortenerApiUrl(endpoint)) {
            return ShortenResult(null, "Blocked: shortener host is not allow-listed.")
        }
        val res = allowed(endpoint, "GET", emptyMap(), null, null)
        return when {
            res.error != null -> ShortenResult(null, res.error)
            res.body.startsWith("Error:", ignoreCase = true) -> ShortenResult(null, res.body.take(200))
            res.status in 200..299 && res.body.startsWith("http") -> ShortenResult(res.body.trim().lines().first(), null)
            else -> ShortenResult(null, "Shortener returned HTTP ${res.status}")
        }
    }

    fun virusTotalScan(raw: String, apiKey: String): VirusTotalReport {
        val target = UrlHeuristics.extractUrl(raw) ?: return VirusTotalReport(null, null, null, null, "Paste an http or https link first.")
        val reportUrl = UrlNetPolicy.virusTotalUrlReport(target)
        if (!UrlNetPolicy.isVirusTotalUrl(reportUrl)) {
            return VirusTotalReport(null, null, null, null, "Blocked: VirusTotal host is not allow-listed.")
        }
        val headers = mapOf("x-apikey" to apiKey, "Accept" to "application/json")
        val existing = allowed(reportUrl, "GET", headers, null, apiKey)
        if (existing.status in 200..299) {
            val parsed = VirusTotalParse.report(existing.body)
            if (parsed.stats != null) return parsed
        }
        val submit = UrlNetPolicy.virusTotalSubmitUrl()
        val posted =
            allowed(
                submit,
                "POST",
                headers + ("Content-Type" to "application/x-www-form-urlencoded"),
                "url=" + java.net.URLEncoder.encode(target, "UTF-8"),
                apiKey,
            )
        if (posted.error != null && posted.status !in 200..299) {
            return VirusTotalReport(null, null, null, null, posted.error)
        }
        val id = VirusTotalParse.analysisId(posted.body)
        if (id.isNullOrBlank()) {
            return VirusTotalParse.report(posted.body).let {
                if (it.error != null || it.stats != null) it
                else VirusTotalReport(null, null, null, null, posted.error ?: "VirusTotal did not return an analysis id")
            }
        }
        val analysisUrl = UrlNetPolicy.virusTotalAnalysisUrl(id)
        repeat(5) { attempt ->
            if (attempt > 0) {
                try {
                    Thread.sleep(2_000)
                } catch (_: InterruptedException) {
                    return VirusTotalReport(id, "queued", null, null, "Scan interrupted")
                }
            }
            val res = allowed(analysisUrl, "GET", headers, null, apiKey)
            if (res.error != null && res.status == null) {
                return VirusTotalReport(id, null, null, null, res.error)
            }
            val parsed = VirusTotalParse.report(res.body)
            if (parsed.stats != null && (parsed.status == null || parsed.status == "completed")) return parsed
            if (parsed.error != null && attempt == 4) return parsed
        }
        return VirusTotalReport(id, "queued", null, null, "VirusTotal is still scanning. Try again in a few seconds.")
    }

    private fun oneShot(url: String, method: String): StepOutcome {
        if (!RedirectChain.isFollowable(url)) return StepOutcome(error = "Blocked scheme or host")
        var conn: HttpURLConnection? = null
        return try {
            val u = URL(url)
            conn = u.openConnection() as HttpURLConnection
            if (u.protocol.equals("https", ignoreCase = true) && conn !is HttpsURLConnection) {
                return StepOutcome(error = "Blocked: expected HTTPS connection")
            }
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.requestMethod = method
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", UA)
            // Do not send or store cookies.
            conn.setRequestProperty("Connection", "close")
            val status = conn.responseCode
            val location = conn.headerFields.orEmpty()
                .entries
                .firstOrNull { it.key.equals("Location", ignoreCase = true) }
                ?.value
                ?.firstOrNull()
            // Drain the body without interpreting HTML.
            runCatching { (if (status in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes() } }
            StepOutcome(status = status, location = location)
        } catch (e: IOException) {
            StepOutcome(error = (e.message ?: e.javaClass.simpleName).take(160))
        } catch (e: Exception) {
            StepOutcome(error = e.javaClass.simpleName.take(160))
        } finally {
            conn?.disconnect()
        }
    }

    private data class Raw(val status: Int, val body: String, val error: String?)

    private fun allowed(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        secret: String?,
    ): Raw {
        if (!UrlNetPolicy.isAllowedFixedHost(url)) {
            return Raw(0, "", "Blocked: only virustotal.com and is.gd are allowed for this action")
        }
        var conn: HttpsURLConnection? = null
        return try {
            conn = URL(url).openConnection() as? HttpsURLConnection
                ?: return Raw(0, "", "Blocked: HTTPS required")
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.requestMethod = method
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", UA)
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            if (body != null) {
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            val err = if (status in 200..299) null else UrlNetPolicy.redactSecret("HTTP $status ${text.take(160)}", secret)
            Raw(status, text, err)
        } catch (e: IOException) {
            Raw(0, "", UrlNetPolicy.redactSecret((e.message ?: e.javaClass.simpleName).take(160), secret))
        } catch (e: Exception) {
            Raw(0, "", UrlNetPolicy.redactSecret(e.javaClass.simpleName.take(160), secret))
        } finally {
            conn?.disconnect()
        }
    }
}
