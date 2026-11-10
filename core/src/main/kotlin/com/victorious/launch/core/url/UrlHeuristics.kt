package com.victorious.launch.core.url

import java.net.IDN
import java.net.URI
import java.util.Locale

enum class FindingSeverity {
    INFO,
    CAUTION,
    DANGER,
}

enum class UrlVerdict {
    OK,
    CAUTION,
    DANGER,
}

data class UrlFinding(
    val id: String,
    val severity: FindingSeverity,
    val title: String,
    val detail: String,
)

data class HeuristicReport(
    val input: String,
    val url: String?,
    val host: String?,
    val scheme: String?,
    val findings: List<UrlFinding>,
    val verdict: UrlVerdict,
)

object UrlHeuristics {
    val SUSPICIOUS_TLDS: Set<String> =
        setOf(
            "zip", "mov", "click", "country", "review", "accountant", "loan", "men",
            "bid", "win", "date", "rest", "fit", "work", "cfd", "sbs", "cyou", "quest",
            "tk", "ml", "ga", "cf", "gq", "top", "xyz", "link", "cam", "download",
            "stream", "racing", "science", "party", "gdn", "icu",
        )

    val KNOWN_SHORTENERS: Set<String> =
        setOf(
            "bit.ly", "bitly.com", "j.mp", "t.co", "tinyurl.com", "tiny.cc", "is.gd",
            "v.gd", "cli.gs", "ow.ly", "buff.ly", "rebrand.ly", "cutt.ly", "shorturl.at",
            "goo.gl", "rb.gy", "s.id", "t.ly", "tiny.one", "short.io", "lnkd.in",
            "db.tt", "po.st", "bc.vc", "soo.gd", "q.gs", "trib.al", "fb.me", "amzn.to",
            "geni.us", "lnk.to", "bl.ink", "ow.ly",
        )

    fun extractUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        parse(trimmed)?.let { return normalize(it) }
        val match = URL_IN_TEXT.find(trimmed) ?: return null
        return parse(match.value)?.let { normalize(it) }
    }

    fun analyze(raw: String): HeuristicReport {
        val extracted = extractUrl(raw)
        if (extracted == null) {
            val findings =
                listOf(
                    UrlFinding("invalid", FindingSeverity.CAUTION, "Not a URL", "Paste an http or https link first."),
                )
            return HeuristicReport(raw, null, null, null, findings, UrlVerdict.CAUTION)
        }
        val uri = parse(extracted)
        if (uri == null) {
            return HeuristicReport(
                raw,
                extracted,
                null,
                null,
                listOf(UrlFinding("invalid", FindingSeverity.CAUTION, "Could not parse URL", extracted)),
                UrlVerdict.CAUTION,
            )
        }
        val findings = mutableListOf<UrlFinding>()
        val scheme = uri.scheme?.lowercase(Locale.US)
        val hostRaw = hostOf(uri)
        val host = hostRaw?.lowercase(Locale.US)

        when {
            scheme == "https" ->
                findings += UrlFinding("https", FindingSeverity.INFO, "Uses HTTPS", "The link is served over TLS.")
            scheme == "http" ->
                findings +=
                    UrlFinding(
                        "http",
                        FindingSeverity.CAUTION,
                        "Not HTTPS",
                        "This link uses plain HTTP. The page and any redirects can be altered in transit.",
                    )
            else ->
                findings +=
                    UrlFinding(
                        "scheme",
                        FindingSeverity.DANGER,
                        "Unusual scheme",
                        "Expected http or https, found ${scheme ?: "(none)"}.",
                    )
        }

        if (host.isNullOrBlank()) {
            findings += UrlFinding("host", FindingSeverity.DANGER, "Missing host", "The URL has no hostname.")
        } else {
            if (isRawIp(host)) {
                findings +=
                    UrlFinding(
                        "ip",
                        FindingSeverity.CAUTION,
                        "Raw IP host",
                        "The host is an IP address ($host), not a domain name.",
                    )
            }
            if (hasPunycodeOrIdn(host, hostRaw ?: host)) {
                findings +=
                    UrlFinding(
                        "idn",
                        FindingSeverity.DANGER,
                        "Punycode / IDN host",
                        "The hostname uses punycode or non-ASCII characters, which can hide a look-alike domain.",
                    )
            }
            if (hasMixedScripts(unicodeHost(uri))) {
                findings +=
                    UrlFinding(
                        "homograph",
                        FindingSeverity.DANGER,
                        "Possible typograph / mixed scripts",
                        "The hostname mixes more than one writing system in a single label.",
                    )
            }
            val tld = host.substringAfterLast('.', missingDelimiterValue = "")
            if (tld in SUSPICIOUS_TLDS) {
                findings +=
                    UrlFinding(
                        "tld",
                        FindingSeverity.CAUTION,
                        "Uncommon or frequently abused TLD",
                        ".$tld is often used on disposable or look-alike domains. Confirm the site before you sign in.",
                    )
            }
            val shortHost = canonicalShortener(host)
            if (shortHost != null) {
                findings +=
                    UrlFinding(
                        "shortener",
                        FindingSeverity.CAUTION,
                        "Known shortener",
                        "$shortHost hides the real destination. Expand the link before you trust it.",
                    )
            }
        }

        if (!uri.userInfo.isNullOrEmpty() || (uri.rawAuthority ?: uri.authority).orEmpty().contains('@')) {
            findings +=
                UrlFinding(
                    "userinfo",
                    FindingSeverity.DANGER,
                    "Credentials or @ in the host",
                    "The URL includes user info before the host, a common phishing obfuscation.",
                )
        }

        val length = extracted.length
        when {
            length >= 1024 ->
                findings +=
                    UrlFinding(
                        "length",
                        FindingSeverity.DANGER,
                        "Very long URL",
                        "This URL is $length characters, which is often used to hide the real destination.",
                    )
            length >= 180 ->
                findings +=
                    UrlFinding(
                        "length",
                        FindingSeverity.CAUTION,
                        "Long URL",
                        "This URL is $length characters. Check the host and path before you open it.",
                    )
        }

        val encoded = PERCENT.findAll(extracted).count()
        if (encoded >= 8 && encoded * 4 >= extracted.length) {
            findings +=
                UrlFinding(
                    "encoding",
                    FindingSeverity.CAUTION,
                    "Heavily encoded URL",
                    "A large share of this URL is percent-encoded, which can hide the destination.",
                )
        }

        val verdict =
            when {
                findings.any { it.severity == FindingSeverity.DANGER } -> UrlVerdict.DANGER
                findings.any { it.severity == FindingSeverity.CAUTION } -> UrlVerdict.CAUTION
                else -> UrlVerdict.OK
            }
        return HeuristicReport(raw, extracted, host, scheme, findings, verdict)
    }

    fun parse(raw: String): URI? {
        val s = raw.trim()
        if (s.isEmpty() || s.any { it.isWhitespace() }) return null
        return try {
            val uri = if ("://" in s) URI(s) else URI("https://$s")
            if (hostOf(uri) == null && uri.authority.isNullOrBlank()) null else uri
        } catch (_: Exception) {
            null
        }
    }

    fun normalize(uri: URI): String = uri.toString()

    /** java.net.URI leaves host null for non-ASCII authorities; fall back to authority. */
    fun hostOf(uri: URI): String? {
        uri.host?.let { return it }
        val auth = uri.rawAuthority ?: uri.authority ?: return null
        val rest = if ('@' in auth) auth.substringAfterLast('@') else auth
        if (rest.startsWith("[")) {
            val end = rest.indexOf(']')
            return if (end > 0) rest.substring(1, end) else rest
        }
        return rest.substringBefore(':').ifBlank { null }
    }

    fun unicodeHost(uri: URI): String {
        val host = hostOf(uri) ?: return ""
        return try {
            IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
        } catch (_: Exception) {
            host
        }
    }

    fun isRawIp(host: String): Boolean {
        val h = host.trim().lowercase(Locale.US).removePrefix("[").removeSuffix("]")
        if (h.contains(':')) return IPV6_CHARS.matches(h)
        val parts = h.split('.')
        if (parts.size != 4) return false
        return parts.all { it.toIntOrNull()?.let { n -> n in 0..255 } == true }
    }

    internal fun hasPunycodeOrIdn(hostLower: String, originalHost: String): Boolean {
        if (hostLower.split('.').any { it.startsWith("xn--") }) return true
        if (originalHost.any { it.code > 127 }) return true
        return try {
            val ascii = IDN.toASCII(originalHost, IDN.ALLOW_UNASSIGNED)
            ascii.startsWith("xn--") || ascii.contains(".xn--")
        } catch (_: Exception) {
            false
        }
    }

    internal fun hasMixedScripts(host: String): Boolean {
        val decoded =
            try {
                IDN.toUnicode(host, IDN.ALLOW_UNASSIGNED)
            } catch (_: Exception) {
                host
            }
        return decoded.split('.').any { label ->
            val scripts = label.mapNotNull { scriptOf(it) }.toSet()
            scripts.size > 1
        }
    }

    internal fun canonicalShortener(host: String): String? {
        if (host in KNOWN_SHORTENERS) return host
        return KNOWN_SHORTENERS.firstOrNull { host == it || host.endsWith(".$it") }
    }

    private fun scriptOf(c: Char): String? {
        if (!c.isLetter()) return null
        val block = Character.UnicodeBlock.of(c) ?: return null
        return when (block) {
            Character.UnicodeBlock.BASIC_LATIN,
            Character.UnicodeBlock.LATIN_1_SUPPLEMENT,
            Character.UnicodeBlock.LATIN_EXTENDED_A,
            Character.UnicodeBlock.LATIN_EXTENDED_B,
            Character.UnicodeBlock.LATIN_EXTENDED_ADDITIONAL,
            -> "latin"
            Character.UnicodeBlock.CYRILLIC,
            Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY,
            -> "cyrillic"
            Character.UnicodeBlock.GREEK,
            Character.UnicodeBlock.GREEK_EXTENDED,
            -> "greek"
            else -> block.toString()
        }
    }

    private val URL_IN_TEXT = Regex("""https?://[^\s<>\"']+""", RegexOption.IGNORE_CASE)
    private val PERCENT = Regex("%[0-9A-Fa-f]{2}")
    private val IPV6_CHARS = Regex("^[0-9a-f:]+$")
}
