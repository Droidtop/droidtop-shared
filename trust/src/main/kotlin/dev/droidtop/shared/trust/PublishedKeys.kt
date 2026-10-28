package dev.droidtop.shared.trust

import java.net.URI
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Finding and reading the key a plugin source publishes for itself ("trust on first use from a
 * source"): the person adds the SOURCE, the app fetches the key it publishes, shows the origin and
 * its fingerprint, and the person confirms. Nothing here is trusted by itself, and nothing here
 * fetches: the network leg is the app's, which hands the bytes to [select].
 */
object PublishedKeys {
    /** What a source published: the origin id it claims and the anchor key to trust for it (single-line SPKI base64). */
    data class Published(val origin: String, val keyBase64: String)

    sealed interface Selection {
        data class Fetched(val key: Published) : Selection
        data class Failed(val reason: String) : Selection
    }

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * The https URLs a source's key can live at, tried in order. A github.com repo address becomes
     * the raw.githubusercontent.com path of [fileName] on the named branch (or the default branch
     * via HEAD), so the person pastes the address a browser shows; an https address ending in
     * `.json` is the catalog index or key file itself; any other https address is a root with
     * [fileName] expected at its top. Empty when the input is not a URL, or would be fetched over
     * plaintext http: a key fetched in the clear makes the first-use step itself the attack, so it
     * is refused rather than upgraded. The one exception is github.com, whose derived raw URL is
     * always https.
     */
    fun keyUrlsFor(sourceUrl: String, fileName: String): List<String> {
        val trimmed = sourceUrl.trim().trimEnd('/')
        if (trimmed.isEmpty()) return emptyList()
        val isHttps = trimmed.startsWith("https://", ignoreCase = true)
        val isHttp = trimmed.startsWith("http://", ignoreCase = true)
        if (!isHttps && !isHttp) return emptyList()
        val body = trimmed.substringAfter("://")
        if (isHttps && body.endsWith(".json", ignoreCase = true)) return listOf(trimmed)

        val uri = runCatching { URI("https://$body") }.getOrNull() ?: return emptyList()
        val host = uri.host?.lowercase() ?: return emptyList()
        if (host == "github.com" || host == "www.github.com") {
            val segments = uri.path.orEmpty().trim('/').split('/')
            if (segments.size < 2) return emptyList()
            val owner = segments[0]
            val repo = segments[1].removeSuffix(".git")
            // A /tree/<branch> (or /blob/<branch>) address names the branch to read the file from.
            val branch = if (segments.size >= 4 && (segments[2] == "tree" || segments[2] == "blob")) segments[3] else "HEAD"
            return listOf("https://raw.githubusercontent.com/$owner/$repo/$branch/$fileName")
        }
        if (isHttp) return emptyList()
        return listOf("https://$body/$fileName")
    }

    /**
     * Reads a published key file or catalog index: a JSON object with `origin` and one of
     * `certificate` (PEM or base64 DER), `key` or `publicKey` (a bare SubjectPublicKeyInfo), which
     * is the entire documented contract, so anything else is "doesn't publish a key", not a
     * versioned format to guess at. The key returned is [anchorKey]'s; what cannot be read is
     * passed through for the caller to refuse.
     */
    fun parse(text: String): Published? {
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return null
        val origin = obj.string("origin")?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val given = listOf("certificate", "key", "publicKey").firstNotNullOfOrNull { obj.string(it)?.trim()?.takeIf(String::isNotBlank) } ?: return null
        return Published(origin, anchorKey(origin, given) ?: given)
    }

    /**
     * The anchor key for [origin] from what a source publishes or a person pastes: a certificate
     * (its own key; who issued it is never read, so an origin is anchored only at the key the
     * person chose) or a bare SubjectPublicKeyInfo, P-256 or RSA of 2048 bits or more. A
     * certificate that names origins must name [origin]. The result is single-line base64, so a
     * store holds one form of a key and comparisons can be exact. Null when nothing usable is there.
     */
    fun anchorKey(origin: String, text: String): String? {
        val certificate = ChainSignature.parseCertificate(text)
        val key = if (certificate != null) {
            val names = ChainSignature.uris(certificate).filter { it.startsWith(ChainSignature.ORIGIN_URN) }
            if (names.isNotEmpty() && ChainSignature.ORIGIN_URN + origin !in names) return null
            certificate.publicKey.takeIf(ChainSignature::acceptableKey)
        } else {
            ChainSignature.parseSpki(text)
        } ?: return null
        return Base64.getEncoder().encodeToString(key.encoded)
    }

    /**
     * Why an origin id can't be trusted, or null when it can. Lowercase letters, digits and
     * hyphens only (the id prefixes every plugin id, `<origin>.<name>`), at most [maxLength] characters,
     * and never one of [reserved]: the ids the app itself certifies, which a third party may not
     * claim.
     */
    fun originProblem(origin: String, reserved: Set<String>, maxLength: Int = 64): String? {
        val id = origin.trim()
        if (id.isEmpty()) return "origin id can't be blank"
        if (id in reserved) return "the origin id \"$id\" is official -- a third-party origin can't claim it"
        if (!id.matches(Regex("[a-z0-9-]+"))) {
            return "origin id must be lowercase letters, digits and hyphens (it prefixes every plugin id, \"<origin>.<name>\")"
        }
        if (id.length > maxLength) return "origin id must be at most $maxLength characters"
        return null
    }

    /**
     * Fetches and validates a source's key. [get] is the app's network leg (bytes of the URL, or
     * null), so the URL derivation and parsing run with no network. Before a key is returned it
     * must be a usable anchor key and its origin id one a third party may hold ([originProblem]):
     * a source publishing an official id is refused before it ever reaches a prompt.
     */
    fun select(
        sourceUrl: String,
        fileNames: List<String>,
        reserved: Set<String>,
        maxOriginLength: Int = 64,
        get: (String) -> ByteArray?,
    ): Selection {
        val candidates = fileNames.flatMap { keyUrlsFor(sourceUrl, it) }.distinct()
        if (candidates.isEmpty()) {
            return Selection.Failed(
                "That doesn't look like a plugin source address. Use a GitHub repo (https://github.com/<owner>/<repo>), " +
                    "a catalog index ending in .json, or a plain https address -- plaintext http is refused.",
            )
        }
        var lastProblem = "no candidate to fetch"
        for (url in candidates) {
            val bytes = runCatching { get(url) }.getOrNull()
            if (bytes == null) {
                lastProblem = "couldn't fetch $url"
                continue
            }
            val published = parse(bytes.toString(Charsets.UTF_8))
            if (published == null) {
                lastProblem = "$url has no \"origin\" and \"certificate\" fields"
                continue
            }
            if (ChainSignature.parseSpki(published.keyBase64) == null) {
                lastProblem = "the certificate $url publishes is not one that can be used for that origin"
                continue
            }
            originProblem(published.origin, reserved, maxOriginLength)?.let {
                return Selection.Failed("Origin \"${published.origin}\" can't be trusted: $it.")
            }
            return Selection.Fetched(published)
        }
        return Selection.Failed(
            "Couldn't fetch a usable key from $sourceUrl ($lastProblem). " +
                "A plugin source publishes ${fileNames.joinToString(" or ")} with \"origin\" and \"certificate\" at its root; " +
                "a catalog index carries the same fields.",
        )
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
}
