package dev.droidtop.shared.sitecheck

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A site that checks the browser first, and the clearance passing it earns (Droidtop/tracker#600, #751). */
class SiteCheckTest {
    private fun page(text: String) = text.toByteArray()

    @Test
    fun `a challenge is told from the page by what F95Checker looks for, and by Cloudflare's own header`() {
        val challenge = page("<!DOCTYPE html><html><head><title>Just a moment...</title></head><script>(function(){window._cf_chl_opt = {")
        assertTrue(SiteCheck.isChallenge(403, emptyMap(), challenge))
        assertTrue(SiteCheck.isChallenge(503, emptyMap(), page("<script src=\"/cdn-cgi/challenge-platform/h/b/orchestrate/chl_page/v1\">")))
        assertTrue(SiteCheck.isChallenge(403, mapOf("CF-Mitigated" to "challenge"), page("")))
        assertTrue(SiteCheck.isChallenge(403, emptyMap(), page("<title>DDoS-Guard</title>")))
    }

    @Test
    fun `a page that only mentions a check, or an ordinary refusal, is not one`() {
        assertFalse("a thread that quotes the words is a page", SiteCheck.isChallenge(200, emptyMap(), page("<title>Just a moment...</title>")))
        assertFalse(SiteCheck.isChallenge(403, emptyMap(), page("<title>Forbidden</title>")))
        assertFalse(SiteCheck.isChallenge(404, mapOf("server" to "cloudflare"), page("Not found")))
        assertFalse(SiteCheck.isChallenge(403, mapOf("cf-mitigated" to "something-else"), page("denied")))
    }

    @Test
    fun `only a check's clearance cookies are kept, never an account's`() {
        assertEquals("cf_clearance=abc; __cf_bm=x", SiteCheck.clearanceOf("xf_user=42; cf_clearance=abc; xf_session=s; __cf_bm=x"))
        assertNull(SiteCheck.clearanceOf("xf_user=42; xf_session=s"))
        assertTrue(SiteCheck.isClearanceCookie("__ddg1_"))
        assertFalse(SiteCheck.isClearanceCookie("xf_user"))
    }

    @Test
    fun `a request's own cookie keeps its value, and the newest clearance replaces an older one`() {
        assertEquals("xf_user=42; cf_clearance=new", SiteCheck.mergeCookies("xf_user=42", "cf_clearance=new"))
        assertEquals("a session kept from before never sends its old clearance", "xf_user=42; cf_clearance=new", SiteCheck.mergeCookies("xf_user=42; cf_clearance=old", "cf_clearance=new"))
        assertEquals("xf_user=42", SiteCheck.mergeCookies("xf_user=42", "xf_user=7"))
        assertEquals("cf_clearance=new", SiteCheck.mergeCookies(null, "cf_clearance=new"))
        assertNull(SiteCheck.mergeCookies(null, null))
    }

    @Test
    fun `a pass is the site's own page with a pass cookie, never the challenge or a bot cookie alone`() {
        assertFalse(SiteCheck.pagePassed("Just a moment...", "cf_clearance=abc"))
        assertFalse(SiteCheck.pagePassed("Just a moment…", "cf_clearance=abc"))
        assertFalse(SiteCheck.pagePassed("Attention Required! | Cloudflare", "cf_clearance=abc"))
        assertFalse("a page still loading", SiteCheck.pagePassed("", "cf_clearance=abc"))
        assertFalse("__cf_bm is set on the challenge itself", SiteCheck.pagePassed("F95zone", "__cf_bm=x; xf_csrf=y"))
        assertTrue(SiteCheck.pagePassed("F95zone", "xf_csrf=y; cf_clearance=abc"))
        assertTrue(SiteCheck.pagePassed("Latest Updates | F95zone", "__ddg2_=z"))
    }

    @Test
    fun `a site covers itself and its subdomains, not a host that only ends the same`() {
        assertTrue(SiteCheck.covers("f95zone.to", "f95zone.to"))
        assertTrue(SiteCheck.covers("f95zone.to", "attachments.f95zone.to"))
        assertFalse(SiteCheck.covers("f95zone.to", "notf95zone.to"))
        assertFalse(SiteCheck.covers("attachments.f95zone.to", "f95zone.to"))
    }
}
