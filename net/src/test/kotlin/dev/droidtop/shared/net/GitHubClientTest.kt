package dev.droidtop.shared.net

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

class GitHubClientTest {
    private val servers = mutableListOf<HttpServer>()

    @After fun stop() = servers.forEach { it.stop(0) }

    /** A loopback server; [handler] answers every request. The port is the server's. */
    private fun serve(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> exchange.use { handler(it) } }
        server.start()
        servers += server
        return server
    }

    private fun HttpExchange.reply(status: Int, body: String = "", vararg headers: Pair<String, String>) {
        headers.forEach { (name, value) -> responseHeaders.add(name, value) }
        val bytes = body.toByteArray()
        sendResponseHeaders(status, if (status == 304) -1 else bytes.size.toLong())
        if (status != 304) responseBody.use { it.write(bytes) }
    }

    private val HttpServer.base get() = "http://127.0.0.1:${address.port}"

    /** Tokens may go to 127.0.0.1 here; "localhost" is another host name for the same server. */
    private val loopback = GitHubAuth(setOf("127.0.0.1"), httpsOnly = false)

    @Test fun `the token goes to a token host and a redirect to another host loses it`() {
        val seen = mutableListOf<String?>()
        val signed = serve { exchange ->
            seen += "signed:" + exchange.requestHeaders.getFirst("Authorization")
            exchange.reply(200, "payload")
        }
        val api = serve { exchange ->
            seen += "api:" + exchange.requestHeaders.getFirst("Authorization")
            exchange.reply(302, "", "Location" to "http://localhost:${signed.address.port}/file")
        }
        val out = ByteArrayOutputStream()
        GitHubClient("test/1", { "secret" }, loopback).download("${api.base}/asset", out, 1024)
        assertEquals(listOf("api:Bearer secret", "signed:null"), seen)
        assertEquals("payload", out.toString())
    }

    @Test fun `the default policy sends a token to the three hosts and to no other`() {
        val auth = GitHubAuth.DEFAULT
        for (host in listOf("api.github.com", "github.com", "raw.githubusercontent.com")) {
            assertEquals("Bearer t", auth.authorizationFor("https://$host/x", " t "))
        }
        for (url in listOf(
            "https://objects.githubusercontent.com/x",
            "https://sub.api.github.com/x",
            "https://api.github.com.evil.com/x",
            "https://evilgithub.com/x",
            "http://api.github.com/x",
            "https://user@evil.com@api.github.com/x",
            "not a url",
        )) {
            assertNull(url, auth.authorizationFor(url, "t"))
        }
        assertNull(auth.authorizationFor("https://api.github.com/x", "  "))
        assertNull(auth.authorizationFor("https://api.github.com/x", null))
    }

    @Test fun `a redirect to plain http is refused`() {
        val api = serve { it.reply(302, "", "Location" to "http://example.invalid/x") }
        val secure = GitHubAuth(setOf("127.0.0.1"), httpsOnly = true)
        try {
            GitHubClient("test/1", { null }, secure).download("http://127.0.0.1:${api.address.port}/a", ByteArrayOutputStream(), 10)
            fail("a plain-http redirect was followed")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("non-https"))
        }
    }

    @Test fun `too many redirects stop`() {
        lateinit var self: HttpServer
        self = serve { it.reply(302, "", "Location" to "${self.base}/again") }
        try {
            GitHubClient("test/1", { null }, loopback).get("${self.base}/start")
            fail("a redirect loop ended")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("too many redirects"))
        }
    }

    @Test fun `a conditional request that holds is null and sends the etag`() {
        var condition: String? = null
        val api = serve { exchange ->
            condition = exchange.requestHeaders.getFirst("If-None-Match")
            exchange.reply(304)
        }
        assertNull(GitHubClient("test/1", { null }, loopback).get("${api.base}/x", ifNoneMatch = "\"abc\""))
        assertEquals("\"abc\"", condition)
    }

    @Test fun `a refusal carries the rate limit headers`() {
        val api = serve { it.reply(403, "{}", "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1757500000") }
        try {
            GitHubClient("test/1", { null }, loopback).get("${api.base}/x")
            fail("403 was accepted")
        } catch (e: GitHubHttpException) {
            assertEquals(403, e.status)
            assertEquals(0, e.rateLimitRemaining)
            assertEquals(1_757_500_000L, e.rateLimitResetEpochSeconds)
        }
    }

    @Test fun `a refusal without the headers says so with nulls`() {
        val api = serve { it.reply(500) }
        try {
            GitHubClient("test/1", { null }, loopback).get("${api.base}/x")
            fail("500 was accepted")
        } catch (e: GitHubHttpException) {
            assertEquals(500, e.status)
            assertNull(e.rateLimitRemaining)
            assertNull(e.rateLimitResetEpochSeconds)
        }
    }

    @Test fun `a body past the limit is refused`() {
        val api = serve { it.reply(200, "x".repeat(100)) }
        try {
            GitHubClient("test/1", { null }, loopback).get("${api.base}/x", maxBytes = 50)
            fail("an oversize body was read")
        } catch (e: GitHubClient.TooLarge) {
            assertEquals(50, e.limit)
        }
    }

    @Test fun `a download is refused past the limit and reports progress`() {
        val api = serve { it.reply(200, "y".repeat(100)) }
        val client = GitHubClient("test/1", { null }, loopback)
        val progress = mutableListOf<Pair<Long, Long>>()
        val out = ByteArrayOutputStream()
        assertEquals(100L, client.download("${api.base}/x", out, 100) { copied, total -> progress += copied to total })
        assertEquals(100L, progress.last().first)
        assertEquals(100L, progress.last().second)
        try {
            client.download("${api.base}/x", ByteArrayOutputStream(), 99)
            fail("an oversize download was accepted")
        } catch (e: GitHubClient.TooLarge) {
            assertEquals(99, e.limit)
        }
    }

    @Test fun `every request names the app and the api version`() {
        var agent: String? = null
        var version: String? = null
        val api = serve { exchange ->
            agent = exchange.requestHeaders.getFirst("User-Agent")
            version = exchange.requestHeaders.getFirst("X-GitHub-Api-Version")
            exchange.reply(200, "[]")
        }
        GitHubClient("enginehost/9", { null }, loopback).get("${api.base}/x")
        assertEquals("enginehost/9", agent)
        assertEquals("2022-11-28", version)
    }

    @Test fun `releases are read across pages and a later page carries no condition`() {
        val conditions = mutableListOf<String?>()
        lateinit var api: HttpServer
        api = serve { exchange ->
            conditions += exchange.requestHeaders.getFirst("If-None-Match")
            if (exchange.requestURI.query.contains("page=2")) {
                exchange.reply(200, """[{"tag_name":"v1","draft":true,"assets":[]}]""")
            } else {
                exchange.reply(
                    200,
                    """[{"tag_name":"v2","name":"Two","prerelease":true,"published_at":"2026-01-01T00:00:00Z",
                        "assets":[{"name":"a.tar.xz","browser_download_url":"https://github.com/o/r/releases/download/v2/a.tar.xz",
                        "url":"https://api.github.com/repos/o/r/releases/assets/7","size":12,"digest":"sha256:ab"}]}]""",
                    "ETag" to "\"e1\"",
                    "Link" to "<${api.base}/repos/o/r/releases?per_page=100&page=2>; rel=\"next\", <${api.base}/x>; rel=\"last\"",
                )
            }
        }
        val listing = GitHubClient("test/1", { null }, loopback, apiBase = api.base).listReleases("o/r", knownEtag = "\"old\"")
        listing as ReleaseListing.Fetched
        assertEquals("\"e1\"", listing.etag)
        assertEquals(listOf("\"old\"", null), conditions)
        assertEquals(listOf("v2", "v1"), listing.releases.map { it.tag })
        val first = listing.releases[0]
        assertTrue(first.prerelease)
        assertEquals("Two", first.name)
        assertEquals(ReleaseAsset("a.tar.xz", "https://github.com/o/r/releases/download/v2/a.tar.xz", "https://api.github.com/repos/o/r/releases/assets/7", 12, "sha256:ab"), first.assets.single())
        assertTrue(listing.releases[1].draft)
    }

    @Test fun `an unchanged listing is reported as unchanged`() {
        val api = serve { it.reply(304) }
        assertEquals(ReleaseListing.Unchanged, GitHubClient("test/1", { null }, loopback, apiBase = api.base).listReleases("o/r", "\"e\""))
    }

    @Test fun `a listing that never ends is stopped`() {
        lateinit var api: HttpServer
        api = serve { it.reply(200, "[]", "Link" to "<${api.base}/next>; rel=\"next\"") }
        try {
            GitHubClient("test/1", { null }, loopback, apiBase = api.base).listReleases("o/r")
            fail("endless pagination was followed")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("pages"))
        }
    }

    @Test fun `a malformed listing is an error and not an empty list`() {
        val api = serve { it.reply(200, """{"message":"Not Found"}""") }
        try {
            GitHubClient("test/1", { null }, loopback, apiBase = api.base).listReleases("o/r")
            fail("an object was read as a release list")
        } catch (e: IOException) {
            throw AssertionError("parse failure should not be an IOException", e)
        } catch (e: RuntimeException) {
            // IllegalArgumentException from kotlinx: the body is not an array.
        }
    }

    @Test fun `the link header names the next page only`() {
        assertEquals("https://x/2", GitHubReleases.nextLink("""<https://x/2>; rel="next", <https://x/9>; rel="last""""))
        assertNull(GitHubReleases.nextLink("""<https://x/1>; rel="prev""""))
        assertNull(GitHubReleases.nextLink(null))
    }

    @Test fun `every answer is reported with the allowance left and never with a header`() {
        val api = serve { it.reply(200, "[]", "X-RateLimit-Remaining" to "41") }
        val told = mutableListOf<Triple<String, Int, String?>>()
        val client = GitHubClient("test/1", { "secret" }, loopback, onResponse = { url, status, left -> told += Triple(url, status, left) })
        client.get("${api.base}/repos/o/r/releases?per_page=100")
        assertEquals(listOf(Triple("${api.base}/repos/o/r/releases", 200, "41")), told)
        assertTrue(told.none { it.toString().contains("secret") })
    }

    @Test fun `a refusal is reported too`() {
        val api = serve { it.reply(403, "{}", "X-RateLimit-Remaining" to "0") }
        val told = mutableListOf<Int>()
        val client = GitHubClient("test/1", { null }, loopback, onResponse = { _, status, _ -> told += status })
        try { client.get("${api.base}/x"); fail("403 accepted") } catch (e: GitHubHttpException) { }
        assertEquals(listOf(403), told)
    }

    @Test fun `a token is masked to its last four characters`() {
        assertEquals("****wxyz", GitHubAuth.masked("ghp_abcdwxyz"))
        assertEquals("****", GitHubAuth.masked("abc"))
    }
}
