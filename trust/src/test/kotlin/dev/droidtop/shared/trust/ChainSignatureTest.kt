package dev.droidtop.shared.trust

import dev.droidtop.shared.testpki.TestPki
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x509.KeyUsage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The certificate signature format (docs/SPEC.md 12a "Signatures: certificate chains to Droidtop Core",
 * Droidtop/tracker#550). Every key and certificate here is generated in the test; Core is a stand-in.
 */
class ChainSignatureTest {
    private val ucmRootKey = TestPki.rsa()
    private val ucmRoot = TestPki.cert("UCM root (test)", ucmRootKey.public).ca(-1).selfSigned(ucmRootKey.private).build()
    private val coreKey = TestPki.rsa()

    /** Core as the owner's CA issues it: under the UCM root, which droidtop must never need. */
    private val core = TestPki.cert("Droidtop Core (test)", coreKey.public).ca(0).issuedBy(ucmRoot, ucmRootKey.private).build()
    private val anchor = ChainSignature.Anchor(core.publicKey, pinned = true)
    private val leafKey = TestPki.ec()
    private val leaf = TestPki.pluginLeaf(leafKey, "droidtop", core, coreKey.private)
    private val manifest = """{"id":"droidtop.example","origin":"droidtop"}""".toByteArray()
    private val now = System.currentTimeMillis() / 1000
    private val pluginTyp = ChainSignature.Purpose.PLUGIN.typ

    private fun verify(jws: String, content: ByteArray? = manifest, on: ChainSignature.Anchor = anchor) =
        ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, on, content)

    private fun refusedFor(result: ChainSignature.Result): String = (result as ChainSignature.Result.Refused).reason

    @Test
    fun `a leaf issued by Core verifies, and stops at Core`() {
        val result = verify(TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest))
        assertTrue(result is ChainSignature.Result.Verified)
        assertEquals(anchor.sha256, (result as ChainSignature.Result.Verified).anchor.sha256)
        assertTrue(ChainSignature.names(result.leaf, ChainSignature.ORIGIN_URN, "droidtop"))
    }

    @Test
    fun `an RSA leaf signs with RS256`() {
        val rsa = TestPki.rsa()
        val rsaLeaf = TestPki.pluginLeaf(rsa, "droidtop", core, coreKey.private)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(rsaLeaf), rsa.private, manifest)) is ChainSignature.Result.Verified)
    }

    @Test
    fun `changed content or another signer is refused`() {
        val jws = TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest)
        assertTrue(verify(jws, content = manifest + ' '.code.toByte()) is ChainSignature.Result.Refused)
        val other = TestPki.ec()
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(leaf), other.private, manifest)) is ChainSignature.Result.Refused)
    }

    @Test
    fun `a chain that includes the signing root or reaches past it is refused`() {
        assertTrue(refusedFor(verify(TestPki.detached(pluginTyp, listOf(leaf, core), leafKey.private, manifest))).contains("signing root"))
        // Signed by a key above the root (Core, the owner's CA), not by the root: untrusted, even with that key in the chain.
        val underRoot = TestPki.pluginLeaf(leafKey, "droidtop", ucmRoot, ucmRootKey.private)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(underRoot), leafKey.private, manifest)) is ChainSignature.Result.Refused)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(underRoot, ucmRoot), leafKey.private, manifest)) is ChainSignature.Result.Refused)
    }

    @Test
    fun `a leaf issued under the Core name by another key is refused`() {
        val impostorKey = TestPki.rsa()
        val impostor = TestPki.cert("x", leafKey.public).eku(TestPki.PLUGIN).uri("urn:droidtop:origin:droidtop")
            .issuedByKey("Droidtop Core (test)", impostorKey.private).build()
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(impostor), leafKey.private, manifest)) is ChainSignature.Result.Refused)
    }

    @Test
    fun `the leaf must be a signing certificate for this purpose`() {
        fun leafWith(build: (TestPki.Cert) -> TestPki.Cert): X509Certificate =
            build(TestPki.cert("l", leafKey.public).uri("urn:droidtop:origin:droidtop")).issuedBy(core, coreKey.private).build()
        val cases = mapOf(
            "no EKU" to leafWith { it },
            "catalog EKU" to leafWith { it.eku(TestPki.CATALOG) },
            "CA leaf" to leafWith { it.eku(TestPki.PLUGIN).ca(0) },
            "no digitalSignature" to leafWith { it.eku(TestPki.PLUGIN).keyUsage(KeyUsage.nonRepudiation) },
            "keyCertSign" to leafWith { it.eku(TestPki.PLUGIN).keyUsage(KeyUsage.digitalSignature or KeyUsage.keyCertSign) },
            "no key usage" to leafWith { it.eku(TestPki.PLUGIN).noKeyUsage() },
        )
        cases.forEach { (name, cert) ->
            assertTrue(name, verify(TestPki.detached(pluginTyp, listOf(cert), leafKey.private, manifest)) is ChainSignature.Result.Refused)
        }
    }

    @Test
    fun `a certificate with a critical extension nobody understands is refused`() {
        val critical = TestPki.cert("critical", leafKey.public).eku(TestPki.PLUGIN).uri("urn:droidtop:origin:droidtop")
            .criticalExtension("1.2.3.4.5").issuedBy(core, coreKey.private).build()
        val result = verify(TestPki.detached(pluginTyp, listOf(critical), leafKey.private, manifest))
        assertTrue(refusedFor(result), refusedFor(result).contains("critical extensions"))
        // An intermediate is held to it as well.
        val orgKey = TestPki.rsa()
        val org = TestPki.cert("org", orgKey.public).ca(1).criticalExtension("1.2.3.4.5").selfSigned(orgKey.private).build()
        val repoKey = TestPki.ec()
        val repo = TestPki.pluginLeaf(repoKey, "acme", org, orgKey.private)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(repo, org), repoKey.private, manifest), on = ChainSignature.Anchor(orgKey.public)) is ChainSignature.Result.Refused)
    }

    @Test
    fun `a chain is verified without a signature, and a revoked anchor key refuses it`() {
        assertEquals(null, ChainSignature.verifyChain(listOf(leaf), ChainSignature.Purpose.PLUGIN, anchor))
        assertTrue(ChainSignature.verifyChain(listOf(leaf), ChainSignature.Purpose.CATALOG, anchor) != null)
        assertTrue(ChainSignature.verifyChain(emptyList(), ChainSignature.Purpose.PLUGIN, anchor) != null)
        assertTrue(ChainSignature.verifyChain(listOf(leaf, leaf, leaf, leaf), ChainSignature.Purpose.PLUGIN, anchor)!!.contains("1 to 3"))
        val byLeaf = ChainSignature.verifyChain(listOf(leaf), ChainSignature.Purpose.PLUGIN, anchor, revoked = { it == leaf })
        assertTrue(byLeaf!!, byLeaf.contains("revoked"))
        assertTrue(ChainSignature.verifyChain(listOf(leaf), ChainSignature.Purpose.PLUGIN, anchor, anchorRevoked = true)!!.contains("revoked"))
        val jws = TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest)
        assertTrue(ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, anchor, manifest, anchorRevoked = true) is ChainSignature.Result.Refused)
    }

    @Test
    fun `the key a signature claims is readable without trusting it`() {
        val jws = TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest)
        assertEquals(Sha256.hex(leaf.publicKey.encoded), ChainSignature.claimedLeafKeySha256(jws))
        for (bad in listOf("", "a.b.c", "x", TestPki.header(pluginTyp, emptyList(), leafKey.private) + "..AAAA")) {
            assertNull(bad, ChainSignature.claimedLeafKeySha256(bad))
        }
    }

    @Test
    fun `ES256 signatures verify for every value shape`() {
        // Many signatures have r and s with a high first bit or with leading zero bytes, which the DER conversion must get right.
        repeat(64) { index ->
            val content = "$index".toByteArray()
            assertTrue(verify(TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, content), content = content) is ChainSignature.Result.Verified)
        }
    }

    @Test
    fun `the typ binds the purpose`() {
        val jws = TestPki.detached(ChainSignature.Purpose.CATALOG.typ, listOf(leaf), leafKey.private, manifest)
        assertTrue(verify(jws) is ChainSignature.Result.Refused)
    }

    @Test
    fun `expiry never invalidates a signature, only revocation does`() {
        // Owner, 2026-10-15: "Can we ignore its expiration date? We don't want older plugins to be invalidated".
        val expired = TestPki.cert("old", leafKey.public).eku(TestPki.PLUGIN).uri("urn:droidtop:origin:droidtop")
            .valid(Date((now - 86_400 * 30) * 1000), Date((now - 86_400) * 1000)).issuedBy(core, coreKey.private).build()
        val notYet = TestPki.cert("future", leafKey.public).eku(TestPki.PLUGIN).uri("urn:droidtop:origin:droidtop")
            .valid(Date((now + 86_400) * 1000), Date((now + 86_400 * 30) * 1000)).issuedBy(core, coreKey.private).build()
        for (cert in listOf(expired, notYet)) {
            val jws = TestPki.detached(pluginTyp, listOf(cert), leafKey.private, manifest)
            assertTrue(verify(jws) is ChainSignature.Result.Verified)
            val revokedCerts = setOf(Sha256.hex(cert.encoded))
            val revoked = { c: X509Certificate -> Sha256.hex(c.encoded) in revokedCerts }
            assertTrue(ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, anchor, manifest, revoked) is ChainSignature.Result.Refused)
        }
    }

    @Test
    fun `revocation by certificate or key refuses the chain`() {
        val jws = TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest)
        val byCert = { c: X509Certificate -> Sha256.hex(c.encoded) == Sha256.hex(leaf.encoded) }
        val byKey = { c: X509Certificate -> Sha256.hex(c.publicKey.encoded) == Sha256.hex(leaf.publicKey.encoded) }
        val neither = { c: X509Certificate -> Sha256.hex(c.encoded) == "0".repeat(64) }
        assertTrue(ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, anchor, manifest, byCert) is ChainSignature.Result.Refused)
        assertTrue(ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, anchor, manifest, byKey) is ChainSignature.Result.Refused)
        assertTrue(ChainSignature.verify(jws, ChainSignature.Purpose.PLUGIN, anchor, manifest, neither) is ChainSignature.Result.Verified)
    }

    @Test
    fun `a trusted key anchors its own chain, self-signed or through an intermediate`() {
        val publisher = TestPki.ec()
        val selfSigned = TestPki.cert("acme", publisher.public).eku(TestPki.PLUGIN).uri("urn:droidtop:origin:acme").selfSigned(publisher.private).build()
        val trusted = ChainSignature.Anchor(publisher.public)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(selfSigned), publisher.private, manifest), on = trusted) is ChainSignature.Result.Verified)

        val orgKey = TestPki.rsa()
        val org = TestPki.cert("acme org", orgKey.public).ca(1).selfSigned(orgKey.private).build()
        val repoKey = TestPki.ec()
        val repo = TestPki.pluginLeaf(repoKey, "acme", org, orgKey.private)
        val orgAnchor = ChainSignature.Anchor(orgKey.public)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(repo), repoKey.private, manifest), on = orgAnchor) is ChainSignature.Result.Verified)
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(repo, org), repoKey.private, manifest), on = orgAnchor) is ChainSignature.Result.Verified)
        // The same chain under a key the person does not trust.
        assertTrue(verify(TestPki.detached(pluginTyp, listOf(repo), repoKey.private, manifest), on = trusted) is ChainSignature.Result.Refused)
    }

    @Test
    fun `an intermediate must be a CA within its path length`() {
        val orgKey = TestPki.rsa()
        val org = TestPki.cert("org", orgKey.public).ca(-1).selfSigned(orgKey.private).build()
        val subKey = TestPki.rsa()
        val notCa = TestPki.cert("sub", subKey.public).issuedBy(org, orgKey.private).build()
        val repoKey = TestPki.ec()
        val repo = TestPki.pluginLeaf(repoKey, "acme", notCa, subKey.private)
        val result = verify(TestPki.detached(pluginTyp, listOf(repo, notCa), repoKey.private, manifest), on = ChainSignature.Anchor(orgKey.public))
        assertTrue(result is ChainSignature.Result.Refused)
    }

    @Test
    fun `plugin names narrow what a leaf may sign`() {
        val narrow = TestPki.cert("n", leafKey.public).eku(TestPki.PLUGIN)
            .uri("urn:droidtop:origin:droidtop", "urn:droidtop:plugin:droidtop.example", "urn:droidtop:plugin:droidtop.tools.*")
            .issuedBy(core, coreKey.private).build()
        assertTrue(ChainSignature.coversPlugin(narrow, "droidtop.example"))
        assertTrue(ChainSignature.coversPlugin(narrow, "droidtop.tools.one"))
        assertFalse(ChainSignature.coversPlugin(narrow, "droidtop.tools."))
        assertFalse(ChainSignature.coversPlugin(narrow, "droidtop.other"))
        assertTrue(ChainSignature.coversPlugin(leaf, "droidtop.anything"))
    }

    @Test
    fun `malformed signatures are refused, never thrown`() {
        listOf("", "a.b", "a.b.c", "..", TestPki.header(pluginTyp, emptyList(), leafKey.private) + "..AAAA").forEach {
            assertTrue(it, verify(it) is ChainSignature.Result.Refused)
        }
    }

    @Test
    fun `keys are P-256 or RSA of at least 2048 bits`() {
        assertTrue(ChainSignature.parseSpki(java.util.Base64.getEncoder().encodeToString(leafKey.public.encoded)) != null)
        assertTrue(ChainSignature.parseSpki(java.util.Base64.getEncoder().encodeToString(coreKey.public.encoded)) != null)
        val small = java.security.KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        assertNull(ChainSignature.parseSpki(java.util.Base64.getEncoder().encodeToString(small.public.encoded)))
        assertNull(ChainSignature.parseSpki("not a key"))
    }

    /** A key on another 256-bit curve is refused: secp256k1 has a 256-bit field too, and the contract says P-256 (Droidtop/tracker#769). */
    @Test
    fun `an EC key on another 256-bit curve is not accepted`() {
        val p = java.math.BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
        val gx = java.math.BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16)
        val gy = java.math.BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16)
        val n = java.math.BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
        val curve = java.security.spec.EllipticCurve(java.security.spec.ECFieldFp(p), java.math.BigInteger.ZERO, java.math.BigInteger.valueOf(7))
        val spec = java.security.spec.ECParameterSpec(curve, java.security.spec.ECPoint(gx, gy), n, 1)
        val k1 = object : java.security.interfaces.ECPublicKey {
            override fun getParams() = spec
            override fun getW() = java.security.spec.ECPoint(gx, gy)
            override fun getAlgorithm() = "EC"
            override fun getFormat() = "X.509"
            override fun getEncoded() = ByteArray(0)
        }
        assertEquals(256, k1.params.curve.field.fieldSize)
        assertFalse("secp256k1 is not P-256", ChainSignature.acceptableKey(k1))
        assertTrue(ChainSignature.acceptableKey(leafKey.public))
    }

    /**
     * Android's provider is Conscrypt (BoringSSL), whose getExtendedKeyUsage() throws on droidtop's UUID arc;
     * found when :library-core's tests ran after a Robolectric test had installed it. Every check must hold under it.
     */
    @Test
    fun `verification holds under Conscrypt, the provider a device uses`() {
        val provider = org.conscrypt.Conscrypt.newProvider()
        java.security.Security.insertProviderAt(provider, 1)
        try {
            val parsed = ChainSignature.parseCertificate(java.util.Base64.getEncoder().encodeToString(leaf.encoded))!!
            assertTrue(parsed.javaClass.name, parsed.javaClass.name.startsWith("org.conscrypt"))
            assertTrue(verify(TestPki.detached(pluginTyp, listOf(leaf), leafKey.private, manifest)) is ChainSignature.Result.Verified)
            val catalogTyp = TestPki.detached(ChainSignature.Purpose.CATALOG.typ, listOf(leaf), leafKey.private, manifest)
            assertTrue(ChainSignature.verify(catalogTyp, ChainSignature.Purpose.CATALOG, anchor, manifest) is ChainSignature.Result.Refused)
            val rsa = TestPki.rsa()
            val rsaLeaf = TestPki.pluginLeaf(rsa, "droidtop", core, coreKey.private)
            assertTrue(verify(TestPki.detached(pluginTyp, listOf(rsaLeaf), rsa.private, manifest)) is ChainSignature.Result.Verified)
        } finally {
            java.security.Security.removeProvider(provider.name)
        }
    }
}
