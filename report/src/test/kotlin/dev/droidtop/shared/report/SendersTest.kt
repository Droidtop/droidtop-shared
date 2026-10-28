package dev.droidtop.shared.report

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URLDecoder

class SendersTest {
    private val servers = mutableListOf<HttpServer>()

    @After fun stop() = servers.forEach { it.stop(0) }

    private fun serve(handler: (HttpExchange) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.createContext("/") { exchange -> exchange.use { handler(it) } }
        server.start()
        servers += server
        return server
    }

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }

    private val report = ReportComposer.compose(
        ReportDraft(
            AppIdentity("Enginehost", "0.5.0", product = "Enginehost", component = "Runtime"),
            Kind.CRASH, "Crash on start", "It crashed",
            listOf(Section("log", "App log", Source.APP_LOG, "boom")),
            reportId = "11111111-2222-3333-4444-555555555555",
        ),
    )

    private fun bugzilla(server: HttpServer) = BugzillaSender("http://127.0.0.1:${server.address.port}/rest/bug", "enginehost/0.5", showBugUrl = "https://bugs.example/show_bug.cgi", httpsOnly = false)

    @Test fun `bugzilla gets the report as a bug and no credential`() {
        var body = ""
        var headers = emptyMap<String, List<String>>()
        var path = ""
        val server = serve { e ->
            body = e.requestBody.readBytes().toString(Charsets.UTF_8)
            headers = e.requestHeaders.toMap()
            path = e.requestURI.toString()
            e.reply(200, """{"id": 42}""")
        }
        val outcome = bugzilla(server).send(report)
        assertEquals(SendOutcome.Sent("bug 42", "https://bugs.example/show_bug.cgi?id=42"), outcome)
        assertEquals("/rest/bug", path)
        val json = Json.parseToJsonElement(body).jsonObject
        assertEquals("Enginehost", json["product"]!!.jsonPrimitive.content)
        assertEquals("Runtime", json["component"]!!.jsonPrimitive.content)
        assertEquals("Crash on start", json["summary"]!!.jsonPrimitive.content)
        assertEquals("r11111111222233334444555555555555", json["alias"]!!.jsonPrimitive.content)
        assertEquals("critical", json["severity"]!!.jsonPrimitive.content)
        assertTrue(json["description"]!!.jsonPrimitive.content.contains("## App log\nboom"))
        // Never an authenticating key.
        val names = headers.keys.map { it.lowercase() }
        assertFalse(names.any { it == "authorization" || it.contains("api-key") || it.contains("bugzilla") })
        assertFalse(body.contains("api_key", ignoreCase = true))
        assertEquals("enginehost/0.5", headers.entries.first { it.key.equals("User-Agent", true) }.value.single())
    }

    @Test fun `a repeat of the same report is read as already filed`() {
        val server = serve { it.reply(400, """{"error":true,"code":129,"message":"The alias is already taken"}""") }
        assertEquals(SendOutcome.Sent(null, null), bugzilla(server).send(report))
    }

    @Test fun `a refusal is not retried and a busy tracker is`() {
        val refuse = serve { it.reply(403, """{"error":true,"code":410,"message":"You must log in"}""") }
        assertEquals(SendOutcome.Failed("You must log in", retryable = false), bugzilla(refuse).send(report))
        val busy = serve { it.reply(503, "") }
        assertEquals(SendOutcome.Failed("the tracker answered HTTP 503", retryable = true), bugzilla(busy).send(report))
        val limited = serve { it.reply(429, "") }
        assertTrue((bugzilla(limited).send(report) as SendOutcome.Failed).retryable)
    }

    @Test fun `success without a bug number is a failure`() {
        val server = serve { it.reply(200, "{}") }
        assertTrue(bugzilla(server).send(report) is SendOutcome.Failed)
    }

    @Test fun `a redirect is not followed with the report`() {
        val server = serve { e ->
            e.responseHeaders.add("Location", "http://127.0.0.1:1/elsewhere")
            e.sendResponseHeaders(302, -1)
        }
        assertTrue(bugzilla(server).send(report) is SendOutcome.Failed)
    }

    @Test fun `a server that is not there is a retryable failure`() {
        val dead = BugzillaSender("http://127.0.0.1:1/rest/bug", "t", httpsOnly = false, connectTimeoutMs = 500)
        val outcome = dead.send(report) as SendOutcome.Failed
        assertTrue(outcome.retryable)
    }

    @Test fun `bugzilla refuses a plain http address by default`() {
        try {
            BugzillaSender("http://bugs.example/rest/bug", "t")
            fail("plain http accepted")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("https"))
        }
    }

    @Test fun `an issue form carries only the title and the body goes to the clipboard`() {
        val outcome = IssueFormSender("github", "https://github.com/Droidtop/issues/", label = "enginehost").send(report) as SendOutcome.HandOff
        assertEquals("https://github.com/Droidtop/issues/issues/new?title=Crash%20on%20start&labels=enginehost", outcome.url)
        assertEquals(report.body, outcome.clipboard)
        assertFalse(outcome.url.contains("boom"))
    }

    @Test fun `an issue form needs https`() {
        try {
            IssueFormSender("gitea", "http://gitea.example/o/r")
            fail("plain http accepted")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test fun `email puts a short body in the address and always on the clipboard`() {
        val outcome = EmailSender("bugs@example.org").send(report) as SendOutcome.HandOff
        assertTrue(outcome.url.startsWith("mailto:bugs@example.org?subject="))
        val decoded = URLDecoder.decode(outcome.url.replace("%20", " "), Charsets.UTF_8)
        assertTrue(decoded.contains("[Enginehost] Crash on start"))
        assertTrue(decoded.contains("boom"))
        assertEquals(report.body, outcome.clipboard)

        val long = ReportComposer.compose(
            ReportDraft(report.app, Kind.BUG, "t", "d", listOf(Section("l", "Log", Source.APP_LOG, "z".repeat(5000)))),
        )
        val big = EmailSender("bugs@example.org").send(long) as SendOutcome.HandOff
        assertFalse(big.url.contains("body="))
        assertEquals(long.body, big.clipboard)
    }

    @Test fun `email refuses what is not an address`() {
        try {
            EmailSender("not an address")
            fail("accepted")
        } catch (e: IllegalArgumentException) {
        }
    }
}
