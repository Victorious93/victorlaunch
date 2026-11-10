package com.victorious.launch.core.url

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Locale

/** Fixed-host allow-list for VirusTotal and the is.gd shortener. Unshorten is not allow-listed here. */
object UrlNetPolicy {
    const val VT_ORIGIN = "https://www.virustotal.com"
    const val SHORTENER_ORIGIN = "https://is.gd"

    val VIRUSTOTAL_HOSTS: Set<String> = setOf("www.virustotal.com", "virustotal.com")
    val SHORTENER_HOSTS: Set<String> = setOf("is.gd", "www.is.gd")

    fun isAllowedFixedHost(url: String): Boolean {
        return try {
            val uri = URI(url)
            val host = uri.host?.lowercase(Locale.US) ?: return false
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                (host in VIRUSTOTAL_HOSTS || host in SHORTENER_HOSTS)
        } catch (_: Exception) {
            false
        }
    }

    fun isVirusTotalUrl(url: String): Boolean = hostOf(url) in VIRUSTOTAL_HOSTS && isAllowedFixedHost(url)

    fun isShortenerApiUrl(url: String): Boolean = hostOf(url) in SHORTENER_HOSTS && isAllowedFixedHost(url)

    fun virusTotalSubmitUrl(): String = "$VT_ORIGIN/api/v3/urls"

    fun virusTotalAnalysisUrl(id: String): String = "$VT_ORIGIN/api/v3/analyses/${id.trim()}"

    fun virusTotalUrlReport(url: String): String {
        val id =
            Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(url.toByteArray(StandardCharsets.UTF_8))
        return "$VT_ORIGIN/api/v3/urls/$id"
    }

    fun shortenerCreateUrl(target: String): String {
        val encoded = URLEncoder.encode(target, StandardCharsets.UTF_8.name())
        return "$SHORTENER_ORIGIN/create.php?format=simple&url=$encoded"
    }

    fun redactSecret(text: String, secret: String?): String {
        var out = text
        if (!secret.isNullOrBlank()) {
            out = out.replace(secret, "***")
        }
        out = HEADER_VALUE.replace(out, "x-apikey: ***")
        return out
    }

    private fun hostOf(url: String): String? =
        try {
            URI(url).host?.lowercase(Locale.US)
        } catch (_: Exception) {
            null
        }

    private val HEADER_VALUE = Regex("(?i)x-apikey\\s*[:=]\\s*\\S+")
}
