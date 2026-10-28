package dev.droidtop.shared.trust

import dev.droidtop.shared.trust.KeyRotation.Decision
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyRotationTest {
    private val held = KeyGeneration("AA11", serial = 1)

    @Test
    fun `a new origin is added`() {
        assertEquals(Decision.NEW_ORIGIN, KeyRotation.decide(null, KeyGeneration("AA11")))
    }

    @Test
    fun `the key already held changes nothing, whatever its case`() {
        assertEquals(Decision.UNCHANGED, KeyRotation.decide(held, KeyGeneration("aa11", serial = 1)))
        assertEquals(Decision.UNCHANGED, KeyRotation.decide(held, KeyGeneration("AA11", serial = 0)))
    }

    @Test
    fun `only a higher serial rotates`() {
        assertEquals(Decision.ROTATE, KeyRotation.decide(held, KeyGeneration("BB22", serial = 2)))
        assertEquals(Decision.REFUSE, KeyRotation.decide(held, KeyGeneration("BB22", serial = 1)))
        assertEquals(Decision.REFUSE, KeyRotation.decide(held, KeyGeneration("BB22", serial = 0)))
    }

    @Test
    fun `origins written as github repositories are normalised`() {
        assertEquals("https://github.com/acme/repo", Origins.normalizeGithub(" https://GitHub.com/Acme/Repo.git/ "))
        assertEquals("https://github.com/acme/repo", Origins.normalizeGithub("https://github.com/acme/repo"))
        assertEquals("droidtop", Origins.normalizeGithub(" droidtop "))
        assertEquals("https://example.com/Acme/Repo", Origins.normalizeGithub("https://example.com/Acme/Repo"))
    }
}
