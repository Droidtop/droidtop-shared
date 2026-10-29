package dev.droidtop.shared.sitecheck

/**
 * What passing checks earned: the user agent of the web view that passed them, which the clearance is bound to
 * (F95Checker does the same: its requests carry its web view's user agent and cookies), and, per site, only the
 * clearance cookies. A site covers its subdomains. Storage is the app's: this holds nothing but the values.
 */
data class Clearance(val userAgent: String?, val cookies: Map<String, String>) {
    /** The clearance cookies for [host]'s site (the longest site that covers it), or null. */
    fun forHost(host: String): String? = cookies.entries
        .filter { (site, _) -> SiteCheck.covers(site, host) }
        .maxByOrNull { it.key.length }?.value

    /** This one with [other]'s sites and user agent added over it: a newer pass wins. */
    fun plus(other: Clearance): Clearance = Clearance(other.userAgent ?: userAgent, cookies + other.cookies)

    /** This one without [site]'s clearance. */
    fun without(site: String): Clearance = Clearance(userAgent, cookies - site)

    companion object {
        /**
         * The clearance a web view left: its [userAgent] and, from [cookiesBySite] (a `Cookie` header per site), only
         * the clearance cookies. Null when it left none.
         */
        fun from(userAgent: String?, cookiesBySite: Map<String, String>): Clearance? {
            val cookies = cookiesBySite.mapNotNull { (site, header) -> SiteCheck.clearanceOf(header)?.let { site to it } }.toMap()
            return Clearance(userAgent, cookies).takeIf { cookies.isNotEmpty() }
        }
    }
}
