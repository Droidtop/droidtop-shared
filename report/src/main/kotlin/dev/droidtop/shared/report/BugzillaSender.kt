package dev.droidtop.shared.report

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

/**
 * Files a report with a Bugzilla through its REST API (`POST /rest/bug`).
 *
 * [restBugUrl] is an intake: a Bugzilla that accepts reports from anonymous callers for the product,
 * or a small service in front of one that holds the credential and checks size and rate. The sender
 * has no field for a key, an API key header or a login on purpose (see [ReportSender]).
 *
 * The report id (dashes removed, so it fits Bugzilla's 40-character alias) is sent as the bug's alias, so a retry of the same report is refused by Bugzilla as a
 * duplicate alias instead of filing twice; that answer is read as success when the bug can be found.
 */
class BugzillaSender(
    private val restBugUrl: String,
    private val userAgent: String,
    private val showBugUrl: String? = null,
    private val connectTimeoutMs: Int = 15_000,
    private val readTimeoutMs: Int = 30_000,
    private val httpsOnly: Boolean = true,
) : ReportSender {
    override val id = "bugzilla"

    init {
        val uri = URI(restBugUrl)
        require(!httpsOnly || "https".equals(uri.scheme, ignoreCase = true)) { "a report is sent over https only" }
    }

    override fun send(report: ComposedReport): SendOutcome {
        val payload = buildJsonObject {
            put("product", report.app.product)
            put("component", report.app.component)
            put("summary", report.title)
            put("version", report.app.version)
            put("description", report.body)
            put("alias", "r" + report.reportId.replace("-", ""))
            put("op_sys", "Android")
            put("platform", "All")
            put("type", when (report.kind) { Kind.SUGGESTION -> "enhancement"; else -> "defect" })
            put("severity", if (report.kind == Kind.CRASH) "critical" else "normal")
        }.toString().toByteArray(Charsets.UTF_8)
        val connection = try {
            (URI(restBugUrl).toURL().openConnection() as HttpURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                requestMethod = "POST"
                doOutput = true
                setFixedLengthStreamingMode(payload.size)
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", userAgent)
            }
        } catch (e: IOException) {
            return SendOutcome.Failed(e.message ?: "could not connect", retryable = true)
        }
        return try {
            connection.outputStream.use { it.write(payload) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readNBytes(MAX_RESPONSE + 1).let { bytes -> if (bytes.size > MAX_RESPONSE) null else bytes.toString(Charsets.UTF_8) } }
            interpret(status, text)
        } catch (e: IOException) {
            SendOutcome.Failed(e.message ?: "the connection failed", retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun interpret(status: Int, text: String?): SendOutcome {
        val json = text?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
        if (status in 200..299) {
            val bug = json?.get("id")?.jsonPrimitive?.intOrNull
                ?: return SendOutcome.Failed("the tracker answered success without a bug number", retryable = false)
            return SendOutcome.Sent("bug $bug", showBugUrl?.let { "$it?id=$bug" })
        }
        val message = json?.get("message")?.jsonPrimitive?.contentOrNull
        val code = json?.get("code")?.jsonPrimitive?.intOrNull
        return when {
            // Bugzilla error 129: the alias is taken, so this report was already filed.
            code == ALIAS_TAKEN -> SendOutcome.Sent(reference = null, url = null)
            status == 429 || status >= 500 -> SendOutcome.Failed(message ?: "the tracker answered HTTP $status", retryable = true)
            else -> SendOutcome.Failed(message ?: "the tracker answered HTTP $status", retryable = false)
        }
    }

    private companion object {
        const val MAX_RESPONSE = 256 * 1024
        const val ALIAS_TAKEN = 129
    }
}
