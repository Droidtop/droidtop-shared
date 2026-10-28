package dev.droidtop.shared.version

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {
    @Test
    fun `loose versions compare numeric and text components`() {
        assertTrue(Versions.compareLoose("0.10", "0.9") > 0)
        assertTrue(Versions.compareLoose("1.2", "1.1.9") > 0)
        assertTrue(Versions.compareLoose("0.8.3b", "0.8.3") > 0)
        assertTrue(Versions.compareLoose("1.0", "") > 0)
        assertEquals(0, Versions.compareLoose("1.2", "1.2"))
        assertEquals(0, Versions.compareLoose("1_2", "1-2"))
    }

    @Test
    fun `an empty version is the oldest and two empty ones are equal`() {
        assertTrue(Versions.compareLoose("", "0") < 0)
        assertEquals(0, Versions.compareLoose("", ""))
    }

    @Test
    fun `image tags order by number, newest last`() {
        val tags = listOf("3.20", "3.9", "12", "3.100")
        assertEquals(listOf("3.9", "3.20", "3.100", "12"), tags.sortedWith(Versions::compareLoose))
    }

    @Test
    fun `a number is compared as a number even when it overflows an int`() {
        assertTrue(Versions.compareLoose("20260101123456", "20251231235959") > 0)
    }
}
