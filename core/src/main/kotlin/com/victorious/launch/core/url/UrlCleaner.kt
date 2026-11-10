package com.victorious.launch.core.url

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class CleanResult(
    val original: String,
    val cleaned: String,
    val removed: List<String>,
    val changed: Boolean,
)

object UrlCleaner {
    private val EXACT =
        setOf(
            "fbclid", "fb_action_ids", "fb_action_types", "fb_source",
            "gclid", "gclsrc", "dclid", "gbraid", "wbraid", "gad_source", "gad_campaignid", "srsltid",
            "msclkid", "twclid", "li_fat_id", "igshid", "igsh", "ttclid",
            "mc_eid", "mc_cid", "mc_eh", "_ga", "_gl", "_hsenc", "_hsmi",
            "yclid", "ysclid", "ocid", "mkt_tok", "vero_id",
            "irclickid", "epik", "rdt_cid", "ndclid", "zanpid",
            "ref_src", "ref_url", "spm", "scm", "clickid", "click_id",
            "oly_anon_id", "oly_enc_id", "mc_tc",
            "__s", "__hsfp", "__hssc", "__hstc",
        )

    private val PREFIXES = listOf("utm_", "pk_", "mtm_", "ns_", "wt_")

    fun isTrackingParam(name: String): Boolean {
        val n = name.trim().lowercase()
        if (n.isEmpty()) return false
        if (n in EXACT) return true
        return PREFIXES.any { n.startsWith(it) }
    }

    fun clean(raw: String): CleanResult {
        val url = UrlHeuristics.extractUrl(raw) ?: return CleanResult(raw, raw, emptyList(), false)
        val uri =
            try {
                URI(url)
            } catch (_: Exception) {
                return CleanResult(raw, url, emptyList(), false)
            }
        val query = uri.rawQuery ?: return CleanResult(raw, url, emptyList(), false)
        if (query.isEmpty()) return CleanResult(raw, url, emptyList(), false)
        val removed = mutableListOf<String>()
        val kept = mutableListOf<String>()
        query.split('&').forEach { part ->
            if (part.isEmpty()) return@forEach
            val name = part.substringBefore('=').let { decode(it) }
            if (isTrackingParam(name)) {
                removed += name
            } else {
                kept += part
            }
        }
        if (removed.isEmpty()) return CleanResult(raw, url, emptyList(), false)
        val newQuery = kept.joinToString("&").ifEmpty { null }
        val cleaned =
            URI(
                uri.scheme,
                uri.authority,
                uri.path,
                newQuery,
                uri.fragment,
            ).toString()
        return CleanResult(raw, cleaned, removed.distinct(), cleaned != url)
    }

    private fun decode(s: String): String =
        try {
            URLDecoder.decode(s, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            s
        }

    fun encodeQuery(s: String): String = URLEncoder.encode(s, StandardCharsets.UTF_8.name())
}
