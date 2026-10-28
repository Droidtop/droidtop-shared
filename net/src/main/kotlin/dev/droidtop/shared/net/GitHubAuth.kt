package dev.droidtop.shared.net

import java.net.HttpURLConnection
import java.net.URI

/**
 * Which requests may carry the user's GitHub token, and how a request with one is opened.
 *
 * A token is attached ONLY to https requests for [tokenHosts]. A redirect is followed by hand,
 * so the header is decided again for every hop: a private release asset answers with a redirect
 * to a pre-signed objects.githubusercontent.com URL, and that host rejects a request that
 * carries both a signature and an Authorization header. The host match is exact: a subdomain
 * of a token host is not one. With no token the request is the unauthenticated one it always was.
 *
 * Only [DEFAULT] exists outside this module; the other constructor is for tests that talk to a
 * loopback server, where https cannot be had.
 */
class GitHubAuth internal constructor(
    val tokenHosts: Set<String>,
    private val httpsOnly: Boolean,
) {
    /** The `Authorization` header value for [url], or null when [token] is blank or [url] is not a request for a token host. */
    fun authorizationFor(url: String, token: String?): String? {
        val clean = token?.trim().orEmpty()
        if (clean.isEmpty()) return null
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (httpsOnly && !"https".equals(uri.scheme, ignoreCase = true)) return null
        val host = uri.host?.lowercase() ?: return null
        return if (host in tokenHosts) "Bearer $clean" else null
    }

    /** Whether [url] is a private-release-asset API URL, which needs `Accept: application/octet-stream` to return the file instead of its JSON description. */
    fun isApiAssetUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.host.equals("api.github.com", ignoreCase = true) && uri.path.orEmpty().contains("/releases/assets/")
    }

    /**
     * Opens [url] and returns a connection whose response code is a final one: redirects are
     * followed by hand, to https addresses only, at most [MAX_REDIRECTS] hops. The caller reads
     * the body and calls `disconnect()`. [headers] go on the first request only (a conditional
     * `If-None-Match`, an `Accept`), or on every hop with [everyHop] (a `Range`, which must reach
     * the server a release asset redirects to); the token is decided again on every hop.
     */
    fun open(
        url: String,
        token: String?,
        connectTimeoutMs: Int,
        readTimeoutMs: Int,
        headers: Map<String, String> = emptyMap(),
        everyHop: Boolean = false,
    ): HttpURLConnection {
        var current = url
        repeat(MAX_REDIRECTS + 1) { hop ->
            val connection = URI(current).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            if (hop == 0 || everyHop) {
                headers.filterKeys { !it.equals("Authorization", ignoreCase = true) }
                    .forEach { (name, value) -> connection.setRequestProperty(name, value) }
            }
            authorizationFor(current, token)?.let { header ->
                connection.setRequestProperty("Authorization", header)
                if (isApiAssetUrl(current)) connection.setRequestProperty("Accept", "application/octet-stream")
            }
            val code = connection.responseCode
            if (code !in REDIRECTS) return connection
            val location = connection.getHeaderField("Location")
            connection.disconnect()
            val next = location?.let { runCatching { URI(current).resolve(it).toURL().toString() }.getOrNull() }
                ?: error("HTTP $code from $current without a usable Location")
            require(!httpsOnly || next.startsWith("https://", ignoreCase = true)) { "a redirect to a non-https address was refused" }
            current = next
        }
        error("too many redirects from $url")
    }

    /**
     * The address and headers a download of [url] must use. A request for a token host is
     * resolved here, by hand, to its final address (a private release asset redirects to a
     * pre-signed URL that must not get the token); the headers are then decided for that final
     * address alone. With no token the address is returned as it is. Makes a network round trip:
     * call off the main thread.
     */
    fun downloadRequestFor(url: String, token: String?): Pair<String, Map<String, String>> {
        if (authorizationFor(url, token) == null) return url to emptyMap()
        val connection = open(url, token, 15_000, 30_000)
        val finalUrl = connection.url.toString()
        connection.disconnect()
        val headers = buildMap {
            authorizationFor(finalUrl, token)?.let { header ->
                put("Authorization", header)
                if (isApiAssetUrl(finalUrl)) put("Accept", "application/octet-stream")
            }
        }
        return finalUrl to headers
    }

    companion object {
        const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)

        /** The hosts a token may be sent to. Every other host, including the signed-URL redirect target, gets none. */
        val TOKEN_HOSTS: Set<String> = setOf("api.github.com", "github.com", "raw.githubusercontent.com")

        val DEFAULT = GitHubAuth(TOKEN_HOSTS, httpsOnly = true)

        /** The masked form shown beside a stored token: the last four characters only. */
        fun masked(token: String): String = if (token.length <= 4) "****" else "****" + token.takeLast(4)
    }
}
