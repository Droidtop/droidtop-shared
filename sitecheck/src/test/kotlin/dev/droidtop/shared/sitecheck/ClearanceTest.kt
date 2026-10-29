package dev.droidtop.shared.sitecheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClearanceTest {
    @Test
    fun `a web view's clearance covers its site and the site's subdomains, with the view's user agent`() {
        val ua = "Mozilla/5.0 (Linux; Android 14; wv) Chrome/130"
        val clearance = Clearance.from(ua, mapOf("f95zone.to" to "xf_user=42; cf_clearance=abc", "example.org" to "sid=1"))!!
        assertEquals(mapOf("f95zone.to" to "cf_clearance=abc"), clearance.cookies)
        assertEquals("cf_clearance=abc", clearance.forHost("attachments.f95zone.to"))
        assertNull(clearance.forHost("notf95zone.to"))
        assertEquals(ua, clearance.userAgent)
    }

    @Test
    fun `a view that cleared nothing earns nothing`() {
        assertNull(Clearance.from(null, mapOf("example.org" to "sid=1")))
        assertNull(Clearance.from("ua", emptyMap()))
    }

    @Test
    fun `the longest site that covers a host wins, and a newer pass replaces an older one`() {
        val broad = Clearance("A", mapOf("example.org" to "cf_clearance=1", "files.example.org" to "cf_clearance=2"))
        assertEquals("cf_clearance=2", broad.forHost("files.example.org"))
        assertEquals("cf_clearance=1", broad.forHost("www.example.org"))
        val newer = broad.plus(Clearance("B", mapOf("example.org" to "cf_clearance=3")))
        assertEquals("cf_clearance=3", newer.forHost("www.example.org"))
        assertEquals("cf_clearance=2", newer.forHost("files.example.org"))
        assertEquals("B", newer.userAgent)
        assertEquals("a newer pass without a user agent keeps the old one", "A", broad.plus(Clearance(null, emptyMap())).userAgent)
    }

    @Test
    fun `forgetting a site removes only its clearance`() {
        val c = Clearance("A", mapOf("a.org" to "cf_clearance=1", "b.org" to "cf_clearance=2")).without("a.org")
        assertNull(c.forHost("a.org"))
        assertEquals("cf_clearance=2", c.forHost("b.org"))
        assertTrue(c.cookies.keys == setOf("b.org"))
    }
}
