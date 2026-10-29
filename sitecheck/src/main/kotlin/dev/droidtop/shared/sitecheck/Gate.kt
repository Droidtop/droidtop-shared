package dev.droidtop.shared.sitecheck

/** What trying to pass a site's check came to. */
sealed interface CheckOutcome {
    /** The check was passed and earned [clearance]. */
    data class Passed(val clearance: Clearance) : CheckOutcome

    /** The check needs a person: it is interactive, or the time ran out before the browser passed it by itself. */
    data object NeedsPerson : CheckOutcome

    /** The person did not pass it (closed the page, declined) or nothing could try, for [reason]. */
    data class Failed(val reason: String) : CheckOutcome
}

/**
 * The browser's own attempt at a check, with no one asked: load [url] in a web view nobody sees and wait up to
 * [timeoutMs] for the page's own script (a JavaScript challenge, a Turnstile that wants no input) to pass it, which
 * [SiteCheck.pagePassed] recognises. It never clicks, types, uses a solving service or forges an answer; a check that
 * turns interactive is [CheckOutcome.NeedsPerson] at once. The app provides it (it needs a web view).
 */
fun interface SiteSolver {
    suspend fun solve(url: String, timeoutMs: Long): CheckOutcome
}

/**
 * The person passing a check: shown [url] in a web view, in place on the screen that needed it when [inPlace], or
 * behind a notice naming the site ("Open to continue") when the request ran in the background. The app provides it
 * (it needs its own screens); [CheckOutcome.Passed] when the view shows the site's page with a pass cookie.
 */
fun interface SiteAsker {
    suspend fun ask(url: String, inPlace: Boolean): CheckOutcome
}
