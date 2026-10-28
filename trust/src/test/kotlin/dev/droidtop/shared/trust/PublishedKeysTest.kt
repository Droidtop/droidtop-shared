package dev.droidtop.shared.trust

import dev.droidtop.shared.testpki.TestPki
import java.security.KeyPairGenerator
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The source-key fetch: URL derivation for the well-known key file, the published format, and that
 * nothing untrustworthy (an unusable key, an origin claiming an official id) ever survives to reach
 * a prompt. The network leg is injected, so these run the real derivation and parsing with none.
 */
class PublishedKeysTest {
    private val file = "droidtop-plugin-key.json"
    private val reserved = setOf("droidtop")

    private fun freshKeyPair() = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec("secp256r1"))
    }.generateKeyPair()

    private fun spkiBase64() = Base64.getEncoder().encodeToString(freshKeyPair().public.encoded)

    private fun keyJson(origin: String, key: String) = """{"origin":"$origin","key":"$key"}"""

    @Test
    fun `a github repo address becomes the raw well-known file address`() {
        val raw = "https://raw.githubusercontent.com/acme/acme-plugins/HEAD/$file"
        for (address in listOf(
            "https://github.com/acme/acme-plugins",
            "https://github.com/acme/acme-plugins/",
            "https://github.com/acme/acme-plugins.git",
            "http://github.com/acme/acme-plugins",
        )) {
            assertEquals(address, listOf(raw), PublishedKeys.keyUrlsFor(address, file))
        }
        assertEquals(
            listOf("https://raw.githubusercontent.com/acme/acme-plugins/nightly/$file"),
            PublishedKeys.keyUrlsFor("https://github.com/acme/acme-plugins/tree/nightly", file),
        )
    }

    @Test
    fun `an https json address is fetched directly and a plain https root gets the file appended`() {
        val index = "https://example.com/plugins/catalog-index.json"
        assertEquals(listOf(index), PublishedKeys.keyUrlsFor(index, file))
        assertEquals(listOf("https://example.com/plugins/$file"), PublishedKeys.keyUrlsFor("https://example.com/plugins", file))
        assertEquals(
            listOf("https://raw.githubusercontent.com/acme/acme-plugins/HEAD/$file"),
            PublishedKeys.keyUrlsFor("https://raw.githubusercontent.com/acme/acme-plugins/HEAD", file),
        )
    }

    @Test
    fun `plaintext http and things that are not addresses are refused`() {
        for (bad in listOf(
            "http://example.com/plugins",
            "http://example.com/plugins/index.json",
            "github.com/acme/acme-plugins",
            "",
            "not a url at all",
            "https://github.com/only-owner",
        )) {
            assertTrue(bad, PublishedKeys.keyUrlsFor(bad, file).isEmpty())
        }
    }

    @Test
    fun `parse reads the documented fields and refuses everything else`() {
        val key = spkiBase64()
        val published = PublishedKeys.parse(keyJson("acme", key))
        assertEquals("acme", published?.origin)
        assertEquals(key, published?.keyBase64)
        assertEquals(key, PublishedKeys.parse("""{"origin":"acme","publicKey":"$key"}""")?.keyBase64)

        assertNull(PublishedKeys.parse("""{"key":"$key"}"""))
        assertNull(PublishedKeys.parse("""{"origin":"acme"}"""))
        assertNull(PublishedKeys.parse("""{"origin":"acme","key":12}"""))
        assertNull(PublishedKeys.parse("this is not json"))
        assertNull(PublishedKeys.parse(""))
    }

    @Test
    fun `a certificate gives its own key, and must name the origin when it names any`() {
        val pair = freshKeyPair()
        val key = Base64.getEncoder().encodeToString(pair.public.encoded)
        val named = TestPki.cert("acme", pair.public).eku(TestPki.PLUGIN)
            .uri(ChainSignature.ORIGIN_URN + "acme").selfSigned(pair.private).build()
        assertEquals(key, PublishedKeys.anchorKey("acme", TestPki.pem(named)))
        assertNull(PublishedKeys.anchorKey("other", TestPki.pem(named)))
        val unnamed = TestPki.cert("plain", pair.public).selfSigned(pair.private).build()
        assertEquals(key, PublishedKeys.anchorKey("acme", Base64.getEncoder().encodeToString(unnamed.encoded)))
        assertEquals(key, PublishedKeys.anchorKey("acme", Base64.getMimeEncoder(64, "\n".toByteArray()).encodeToString(pair.public.encoded)))
        assertNull(PublishedKeys.anchorKey("acme", "not a key"))
    }

    @Test
    fun `origin ids that cannot be trusted are named`() {
        assertEquals(null, PublishedKeys.originProblem("acme-2", reserved))
        assertTrue(PublishedKeys.originProblem("droidtop", reserved)!!.contains("official"))
        assertTrue(PublishedKeys.originProblem("", reserved)!!.contains("blank"))
        assertTrue(PublishedKeys.originProblem("Acme", reserved)!!.contains("lowercase"))
        assertTrue(PublishedKeys.originProblem("a.b", reserved)!!.contains("lowercase"))
        assertEquals(null, PublishedKeys.originProblem("a".repeat(64), reserved))
        assertTrue(PublishedKeys.originProblem("a".repeat(65), reserved)!!.contains("at most 64"))
        assertTrue(PublishedKeys.originProblem("a".repeat(41), reserved, maxLength = 40)!!.contains("at most 40"))
    }

    @Test
    fun `select returns the source's validated key`() {
        val key = spkiBase64()
        val selection = PublishedKeys.select("https://github.com/acme/acme-plugins", listOf(file), reserved) { url ->
            assertEquals("https://raw.githubusercontent.com/acme/acme-plugins/HEAD/$file", url)
            keyJson("acme", key).toByteArray()
        }
        val published = (selection as PublishedKeys.Selection.Fetched).key
        assertEquals("acme", published.origin)
        assertEquals(key, published.keyBase64)
    }

    @Test
    fun `select tries each document name in turn`() {
        val key = spkiBase64()
        val asked = mutableListOf<String>()
        val selection = PublishedKeys.select("https://github.com/acme/acme-plugins", listOf(file, "enginehost-public-key.json"), reserved) { url ->
            asked += url.substringAfterLast('/')
            if (url.endsWith("enginehost-public-key.json")) keyJson("acme", key).toByteArray() else null
        }
        assertTrue(selection is PublishedKeys.Selection.Fetched)
        assertEquals(listOf(file, "enginehost-public-key.json"), asked)
    }

    @Test
    fun `select refuses an official origin id and a key that cannot be used`() {
        val official = PublishedKeys.select("https://github.com/acme/acme-plugins", listOf(file), reserved) {
            keyJson("droidtop", spkiBase64()).toByteArray()
        }
        assertTrue((official as PublishedKeys.Selection.Failed).reason.contains("official"))
        val unusable = PublishedKeys.select("https://example.com/plugins", listOf(file), reserved) {
            keyJson("acme", "not a real spki key").toByteArray()
        }
        assertTrue((unusable as PublishedKeys.Selection.Failed).reason.contains("can be used"))
    }

    @Test
    fun `select says why when nothing could be fetched`() {
        val nothing = PublishedKeys.select("https://example.com/plugins", listOf(file), reserved) { null }
        val reason = (nothing as PublishedKeys.Selection.Failed).reason
        assertTrue(reason, reason.contains("Couldn't fetch") && reason.contains(file))
        val notAnAddress = PublishedKeys.select("ftp://example.com", listOf(file), reserved) { error("no fetch") }
        assertTrue((notAnAddress as PublishedKeys.Selection.Failed).reason.contains("plaintext http is refused"))
    }
}
