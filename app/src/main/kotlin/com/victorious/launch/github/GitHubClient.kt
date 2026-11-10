package com.victorious.launch.github

import com.victorious.launch.core.github.GitHubApi
import com.victorious.launch.core.github.GitHubError
import com.victorious.launch.core.github.RateLimit
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/** HTTPS GET to api.github.com only. No redirects, no logging of headers or token. */
class GitHubClient(private val token: String) {
    data class Response(val status: Int, val body: String, val rate: RateLimit?, val retryAfter: Long?)

    fun get(url: String): Result {
        if (!GitHubApi.isAllowedUrl(url)) return Result.Err(GitHubError.Blocked(), null)
        var conn: HttpsURLConnection? = null
        return try {
            conn = URL(url).openConnection() as? HttpsURLConnection
                ?: return Result.Err(GitHubError.Blocked(), null)
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.requestMethod = "GET"
            conn.useCaches = false
            GitHubApi.requestHeaders(token).forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val status = conn.responseCode
            val headers = conn.headerFields.orEmpty()
                .filterKeys { it != null }
                .mapKeys { it.key!! }
                .mapValues { it.value.firstOrNull().orEmpty() }
            val rate = GitHubApi.parseRateLimit(headers)
            val retry = GitHubApi.retryAfterSeconds(headers)
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status in 200..299) {
                Result.Ok(Response(status, body, rate, retry))
            } else {
                val err =
                    GitHubApi.classifyHttp(
                        status,
                        com.victorious.launch.core.github.GitHubParse.message(body),
                        rate,
                        retry,
                        System.currentTimeMillis(),
                    )
                Result.Err(err, rate)
            }
        } catch (e: IOException) {
            Result.Err(GitHubError.Network(GitHubApi.redact(e.javaClass.simpleName + ": " + (e.message ?: "I/O error")).take(160)), null)
        } catch (e: Exception) {
            Result.Err(GitHubError.Network(GitHubApi.redact(e.javaClass.simpleName).take(160)), null)
        } finally {
            (conn as HttpURLConnection?)?.disconnect()
        }
    }

    sealed class Result {
        data class Ok(val response: Response) : Result()

        data class Err(val error: GitHubError, val rate: RateLimit?) : Result()
    }
}
