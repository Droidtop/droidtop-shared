package dev.droidtop.shared.sitecheck

/**
 * A site that answers with a browser check (a Cloudflare challenge, DDoS-Guard) instead of the page: how to tell one
 * from the page, which cookies are the check's clearance, and when a web view has passed it. Pure, with no Android in
 * it, so droidtop and Enginehost tell a check the same way (Droidtop/tracker#751; lifted from droidtop's `SiteChecks`,
 * Droidtop/tracker#600).
 *
 * The owner: "f95 has gotten more aggressive with cloudflare filtering. F95checker has gotten better at allowing users
 * to solve the captcha. we need to do the same". Nothing here solves a check: it only recognises one. Solving it is
 * the browser's own (a [SiteSolver]) or the person's (a [SiteAsker]); nothing scripts a click, uses a solving service
 * or forges an answer.
 */
object SiteCheck {
    /**
     * What a challenge page carries: F95Checker's two (modules/api.py `f95_cloudflare_captcha_messages`), Cloudflare's
     * challenge script, and DDoS-Guard's page.
     */
    private val MARKERS = listOf(
        "window._cf_chl_opt",
        "<title>Just a moment...</title>",
        "/cdn-cgi/challenge-platform/",
        "<title>DDoS-Guard</title>",
    )

    /** How much of an answer is looked at: a challenge says so near its top. */
    private const val LOOK_BYTES = 64 * 1024

    /** Whether an answer is a browser check rather than what was asked for. */
    fun isChallenge(status: Int, headers: Map<String, String>, body: ByteArray): Boolean {
        // Cloudflare names it outright on a challenge it serves.
        if (headers.entries.any { it.key.equals("cf-mitigated", ignoreCase = true) && it.value.trim().equals("challenge", ignoreCase = true) }) return true
        if (status != 403 && status != 429 && status != 503) return false
        val head = String(body, 0, minOf(body.size, LOOK_BYTES), Charsets.UTF_8)
        return MARKERS.any { head.contains(it, ignoreCase = true) }
    }

    /**
     * Whether a cookie is a check's clearance, which may go with any request to that site: Cloudflare's
     * (`cf_clearance`, `__cf_bm`, `cf_chl_*`) and DDoS-Guard's (`__ddg*`). Never an account's cookie.
     */
    fun isClearanceCookie(name: String): Boolean {
        val n = name.trim()
        return n == "cf_clearance" || n.startsWith("__cf") || n.startsWith("cf_chl") || n.startsWith("__ddg")
    }

    /** One `name=value` of a `Cookie` header, as written (trimmed). */
    private class Cookie(val name: String, val text: String)

    /** The cookies of a `Cookie` header ("a=1; b=2"); a piece with no `=` is not one. */
    private fun cookies(header: String?): List<Cookie> =
        header.orEmpty().split(';').map { it.trim() }.filter { it.contains('=') }.map { Cookie(it.substringBefore('=').trim(), it) }

    /** Only the clearance cookies of a `Cookie` header ("a=1; b=2"), as a header; null when it has none. */
    fun clearanceOf(cookieHeader: String): String? =
        cookies(cookieHeader).filter { isClearanceCookie(it.name) }.joinToString("; ") { it.text }.takeIf { it.isNotEmpty() }

    /**
     * [header] with [extra]'s cookies added; either may be null. A check's clearance in [extra] replaces one of the same
     * name in [header]: [extra] is the newest the person earned, and a signed-in session kept from before carries an
     * older `cf_clearance` that made the site ask again right after a pass (Droidtop/tracker#600). Any other cookie
     * [header] already names keeps its own value.
     */
    fun mergeCookies(header: String?, extra: String?): String? {
        val added = cookies(extra)
        val replaced = added.map { it.name }.filter(::isClearanceCookie).toSet()
        val kept = cookies(header).filter { it.name !in replaced }
        val named = kept.map { it.name }.toSet()
        return (kept + added.filter { it.name !in named }).joinToString("; ") { it.text }.takeIf { it.isNotEmpty() }
    }

    /** The titles a check's page has while it checks: Cloudflare's and DDoS-Guard's. */
    private val CHECK_TITLES = listOf("just a moment", "attention required", "checking your browser", "ddos-guard", "please wait")

    /** Whether a page titled [title] is still a check rather than the site's page; an untitled page counts as one. */
    fun isCheckTitle(title: String?): Boolean {
        val t = title?.trim()?.lowercase().orEmpty()
        return t.isEmpty() || CHECK_TITLES.any { t.startsWith(it) || t.contains("| cloudflare") }
    }

    /**
     * Whether a web view has passed a site's check, from what it shows: the site's own page (its title is not a
     * check's), and a pass cookie for it ([cookieHeader]: Cloudflare's `cf_clearance`, DDoS-Guard's `__ddg2_`).
     * The owner, 2026-10-24: "Can we have it autoclose when we detect the cloudflare pass?"
     */
    fun pagePassed(title: String?, cookieHeader: String?): Boolean {
        if (isCheckTitle(title)) return false
        val names = cookies(cookieHeader).map { it.name }
        return "cf_clearance" in names || "__ddg2_" in names
    }

    /** Whether [site] (a host, covering its subdomains) is [host] or a parent of it. */
    fun covers(site: String, host: String): Boolean = host == site || host.endsWith(".$site")
}
