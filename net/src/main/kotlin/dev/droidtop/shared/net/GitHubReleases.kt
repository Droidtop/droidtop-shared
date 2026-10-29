package dev.droidtop.shared.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** One file attached to a release. [downloadUrl] is the public `browser_download_url`; [apiUrl] is the one a private release needs. */
data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val apiUrl: String?,
    val size: Long,
    /** GitHub's `sha256:<hex>` digest of the file, when it reports one. */
    val digest: String?,
)

data class Release(
    val tag: String,
    val name: String?,
    val body: String?,
    val draft: Boolean,
    val prerelease: Boolean,
    val publishedAt: String?,
    val assets: List<ReleaseAsset>,
)

/** What one look at a repository's releases produced. */
sealed interface ReleaseListing {
    data class Fetched(val releases: List<Release>, val etag: String?) : ReleaseListing

    /** GitHub answered 304: what the caller holds is still current, and it cost no allowance. */
    object Unchanged : ReleaseListing
}

/** Reading GitHub's `releases` JSON and its `Link` header. */
object GitHubReleases {

    /** The releases in [body] (an array as `GET /repos/{owner}/{repo}/releases` returns), in the order given. Throws when it is not one. */
    fun parse(body: String): List<Release> {
        val array: JsonArray = Json.parseToJsonElement(body).jsonArray
        return array.map { element ->
            val release = element.jsonObject
            Release(
                tag = release.string("tag_name") ?: throw IllegalArgumentException("a release without a tag_name"),
                name = release.string("name")?.takeIf(String::isNotBlank),
                body = release.string("body")?.takeIf(String::isNotBlank),
                draft = release.bool("draft"),
                prerelease = release.bool("prerelease"),
                publishedAt = release.string("published_at"),
                assets = (release["assets"] as? JsonArray).orEmpty().map { asset(it.jsonObject) },
            )
        }
    }

    private fun asset(asset: JsonObject) = ReleaseAsset(
        name = asset.string("name") ?: throw IllegalArgumentException("a release asset without a name"),
        downloadUrl = asset.string("browser_download_url") ?: throw IllegalArgumentException("a release asset without a browser_download_url"),
        apiUrl = asset.string("url"),
        size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0L,
        digest = asset.string("digest")?.takeIf(String::isNotBlank),
    )

    /** The `rel="next"` address in a `Link` header, or null when there is no further page. */
    fun nextLink(link: String?): String? {
        if (link == null) return null
        for (part in link.split(',')) {
            val pieces = part.split(';').map(String::trim)
            if (pieces.drop(1).any { it.replace(" ", "").equals("rel=\"next\"", ignoreCase = true) }) {
                val target = pieces[0]
                if (target.startsWith("<") && target.endsWith(">")) return target.substring(1, target.length - 1)
            }
        }
        return null
    }

    private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull

    private fun JsonObject.bool(name: String): Boolean = (this[name] as? JsonPrimitive)?.booleanOrNull ?: false
}
