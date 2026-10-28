package dev.droidtop.shared.net

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * The one way either app talks to GitHub: a JSON request with the conditional and
 * rate-limit handling, a paginated release listing, and a bounded download. The token
 * is whatever [token] answers at the time of each request (null for none) and goes only
 * where [GitHubAuth] allows. [apiBase] is api.github.com; a test server stands in for it. Each app gives its own [userAgent] and keeps its own token
 * storage; nothing here stores a token. Blocking: call off the main thread.
 */
class GitHubClient(
    private val userAgent: String,
    private val token: () -> String?,
    private val auth: GitHubAuth = GitHubAuth.DEFAULT,
    private val apiBase: String = "https://api.github.com",
    private val apiTimeouts: Timeouts = Timeouts(15_000, 30_000),
    private val downloadTimeouts: Timeouts = Timeouts(20_000, 120_000),
) {
    data class Timeouts(val connectMs: Int, val readMs: Int)

    class Response(val body: ByteArray, val link: String?, val etag: String?) {
        fun text(): String = body.toString(Charsets.UTF_8)
    }

    /** A response longer than the caller reads; nothing of it is returned. */
    class TooLarge(val limit: Long) : IOException("HTTP response exceeded $limit bytes")

    /**
     * [url] answered with its body (at most [maxBytes], else [TooLarge]), or null for a 304
     * when [ifNoneMatch] held. Any other status throws [GitHubHttpException] with the
     * rate-limit headers.
     */
    fun get(url: String, ifNoneMatch: String? = null, maxBytes: Long = DEFAULT_MAX_BYTES): Response? {
        val headers = buildMap {
            put("Accept", "application/vnd.github+json")
            put("X-GitHub-Api-Version", API_VERSION)
            put("User-Agent", userAgent)
            if (ifNoneMatch != null) put("If-None-Match", ifNoneMatch)
        }
        val connection = auth.open(url, token(), apiTimeouts.connectMs, apiTimeouts.readMs, headers)
        try {
            val status = connection.responseCode
            if (status == 304) return null
            if (status !in 200..299) throw failure(connection.getHeaderField("X-RateLimit-Remaining"), connection.getHeaderField("X-RateLimit-Reset"), status)
            if (connection.contentLengthLong > maxBytes) throw TooLarge(maxBytes)
            val body = connection.inputStream.use { readAtMost(it, maxBytes) }
            return Response(body, connection.getHeaderField("Link"), connection.getHeaderField("ETag"))
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Every release of [ownerRepo] (`owner/name`), drafts included, following `Link` pages (at
     * most [MAX_PAGES]). [knownEtag] is the ETag stored with the listing the caller holds: GitHub
     * answers 304 when it is unchanged, and a 304 does not count against the unauthenticated
     * allowance of 60 requests an hour. Only the first page carries the condition, since its
     * ETag is the one stored.
     */
    fun listReleases(ownerRepo: String, knownEtag: String? = null, perPage: Int = 100): ReleaseListing {
        var next: String? = "$apiBase/repos/$ownerRepo/releases?per_page=$perPage"
        val releases = mutableListOf<Release>()
        var etag: String? = null
        var pages = 0
        while (next != null) {
            check(pages < MAX_PAGES) { "more than $MAX_PAGES pages of releases for $ownerRepo" }
            val response = get(next, if (pages == 0) knownEtag else null) ?: return ReleaseListing.Unchanged
            if (pages == 0) etag = response.etag
            pages++
            releases += GitHubReleases.parse(response.text())
            next = GitHubReleases.nextLink(response.link)
        }
        return ReleaseListing.Fetched(releases, etag)
    }

    /**
     * Writes the file at [url] to [sink] and returns its length. Refuses a non-2xx answer
     * ([GitHubHttpException]) and any file longer than [maxBytes], by header or by count
     * ([TooLarge]). [onProgress] gets the bytes written so far and the length the server
     * announced (-1 when it did not). Identity encoding and no cache, so a proxy that
     * mishandles compression or serves a stale copy cannot hand over wrong bytes that
     * only the caller's digest check would catch.
     */
    fun download(url: String, sink: OutputStream, maxBytes: Long, onProgress: (copied: Long, total: Long) -> Unit = { _, _ -> }): Long {
        val headers = mapOf(
            "User-Agent" to userAgent,
            "Cache-Control" to "no-cache",
            "Accept-Encoding" to "identity",
        )
        val connection = auth.open(url, token(), downloadTimeouts.connectMs, downloadTimeouts.readMs, headers)
        try {
            val status = connection.responseCode
            if (status !in 200..299) throw failure(connection.getHeaderField("X-RateLimit-Remaining"), connection.getHeaderField("X-RateLimit-Reset"), status)
            val total = connection.contentLengthLong
            if (total > maxBytes) throw TooLarge(maxBytes)
            var copied = 0L
            val buffer = ByteArray(64 * 1024)
            connection.inputStream.use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    copied += read
                    if (copied > maxBytes) throw TooLarge(maxBytes)
                    sink.write(buffer, 0, read)
                    onProgress(copied, total)
                }
            }
            if (total >= 0 && copied != total) throw IOException("download ended after $copied of $total bytes")
            return copied
        } finally {
            connection.disconnect()
        }
    }

    private fun failure(remaining: String?, reset: String?, status: Int) =
        GitHubHttpException(status, remaining?.toIntOrNull(), reset?.toLongOrNull(), "GitHub returned HTTP $status")

    private fun readAtMost(input: InputStream, limit: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            if (out.size() + read.toLong() > limit) throw TooLarge(limit)
            out.write(buffer, 0, read)
        }
        return out.toByteArray()
    }

    companion object {
        const val API_VERSION = "2022-11-28"
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
        const val MAX_PAGES = 10
    }
}
