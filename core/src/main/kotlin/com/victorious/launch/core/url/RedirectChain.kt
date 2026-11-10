package com.victorious.launch.core.url

import java.net.URI
import java.util.Locale

data class RedirectHop(
    val index: Int,
    val method: String,
    val url: String,
    val status: Int?,
    val location: String?,
    val note: String? = null,
)

data class StepOutcome(
    val status: Int? = null,
    val location: String? = null,
    val error: String? = null,
)

data class RedirectResult(
    val start: String,
    val hops: List<RedirectHop>,
    val finalUrl: String?,
    val complete: Boolean,
    val reason: String?,
)

object RedirectChain {
    const val MAX_HOPS = 10

    fun isFollowable(url: String): Boolean {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase(Locale.US)
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }

    fun resolve(base: String, location: String): String? {
        val loc = location.trim()
        if (loc.isEmpty()) return null
        return try {
            URI(base).resolve(URI(loc)).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun shouldRetryGet(outcome: StepOutcome): Boolean {
        if (outcome.status == 405 || outcome.status == 501) return true
        if (outcome.status in 300..399 && outcome.location.isNullOrBlank()) return true
        return false
    }

    /**
     * Follow redirects using the supplied one-shot request (no cookies, caller must not
     * render the body). Tries HEAD first, then GET on 405/501 or a 3xx without Location.
     */
    fun follow(
        start: String,
        maxHops: Int = MAX_HOPS,
        request: (url: String, method: String) -> StepOutcome,
    ): RedirectResult {
        val extracted = UrlHeuristics.extractUrl(start) ?: start.trim()
        if (!isFollowable(extracted)) {
            return RedirectResult(extracted, emptyList(), null, false, "Unsupported or invalid URL")
        }
        val hops = mutableListOf<RedirectHop>()
        val seen = linkedSetOf<String>()
        var current = extracted
        var hopsUsed = 0
        while (hopsUsed < maxHops) {
            if (current in seen) {
                hops +=
                    RedirectHop(
                        hopsUsed + 1,
                        "-",
                        current,
                        null,
                        null,
                        "Cycle: this URL was already in the chain",
                    )
                return RedirectResult(extracted, hops, current, false, "Redirect cycle")
            }
            seen += current
            hopsUsed++
            var method = "HEAD"
            var outcome =
                try {
                    request(current, method)
                } catch (e: Exception) {
                    StepOutcome(error = e.message ?: e.javaClass.simpleName)
                }
            if (shouldRetryGet(outcome)) {
                method = "GET"
                outcome =
                    try {
                        request(current, method)
                    } catch (e: Exception) {
                        StepOutcome(error = e.message ?: e.javaClass.simpleName)
                    }
            }
            val note =
                when {
                    outcome.error != null -> outcome.error
                    else -> null
                }
            hops +=
                RedirectHop(
                    index = hopsUsed,
                    method = method,
                    url = current,
                    status = outcome.status,
                    location = outcome.location,
                    note = note,
                )
            if (outcome.error != null && outcome.status == null) {
                return RedirectResult(extracted, hops, current, false, outcome.error)
            }
            val status = outcome.status
            if (status != null && status in 300..399) {
                val loc = outcome.location
                if (loc.isNullOrBlank()) {
                    return RedirectResult(extracted, hops, current, false, "Redirect without Location")
                }
                val next = resolve(current, loc)
                    ?: return RedirectResult(extracted, hops, current, false, "Invalid Location")
                if (!isFollowable(next)) {
                    hops += RedirectHop(hopsUsed + 1, "-", next, null, null, "Blocked scheme or host")
                    return RedirectResult(extracted, hops, current, false, "Blocked redirect target")
                }
                current = next
                continue
            }
            return RedirectResult(extracted, hops, current, true, null)
        }
        return RedirectResult(extracted, hops, current, false, "Stopped at $maxHops hops")
    }
}
