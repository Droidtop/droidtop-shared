package dev.droidtop.shared.report

/**
 * Takes out of a piece of text what was never meant to leave the device, keeping the shape of
 * the line so a reader can still follow it: a storage or app path becomes the name of the place
 * ("<storage>", "<app>"), a home directory "<home>", and an email, an IP address, a bearer
 * token, a GitHub token, a JWT and the value after `password=` or `token:` become a marker.
 *
 * It runs again over everything at the moment a report is composed, so text the person edited
 * after it was gathered is covered too. A redactor cannot know every secret; the preview the
 * person reads before sending is the other half of the guarantee.
 *
 * Lifted from Enginehost's `ProblemReport.scrub`, without the game-specific part (the game's
 * folder is one of the names to [hide]).
 */
object Redactor {
    // A folder name can hold spaces ("Secret Game"), so every segment that another "/" follows is
    // taken whole; the last one ends at whitespace, so the words after a path are kept.
    private val storage = Regex("""/(?:storage|sdcard|mnt)(?:/[^/\n]+(?=/))*(?:/\S*)?""")
    private val appData = Regex("""/data/(?:user/\d+|data|media)(?:/[^/\n]+(?=/))*(?:/\S*)?""")
    private val email = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val ipv4 = Regex("\\b\\d{1,3}(\\.\\d{1,3}){3}\\b")
    private val home = Regex("/(?:home|Users)/[^/\\s]+")
    private val windowsHome = Regex("""[A-Za-z]:\\Users\\[^\\\s]+""")
    private val bearer = Regex("Bearer\\s+\\S+", RegexOption.IGNORE_CASE)
    private val githubToken = Regex("gh[pousr]_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}")
    private val jwt = Regex("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*")
    private val secretPair = Regex(
        """\b(token|password|passwd|secret|api[_-]?key|authorization)(\s*[=:]\s*)\S+""",
        RegexOption.IGNORE_CASE,
    )

    /**
     * [text] with the above taken out. [hide] lists names to blank out wherever they appear (a game's
     * folder, a person's name); each becomes "<hidden>". [addresses] is off for text that carries
     * version numbers, where 4.2.1.0 is not an IP address.
     */
    fun redact(text: String, hide: List<String> = emptyList(), addresses: Boolean = true): String {
        var result = text
        for (name in hide.map(String::trim).filter(String::isNotEmpty).sortedByDescending(String::length)) {
            result = result.replace(name, "<hidden>")
        }
        result = result.replace(storage, "<storage>")
        result = result.replace(appData, "<app>")
        result = result.replace(email, "<email>")
        if (addresses) result = result.replace(ipv4, "<ip>")
        result = result.replace(home, "<home>")
        result = result.replace(windowsHome, "<home>")
        result = result.replace(bearer, "Bearer <redacted>")
        result = result.replace(githubToken, "<redacted>")
        result = result.replace(jwt, "<redacted>")
        result = result.replace(secretPair, "$1$2<redacted>")
        return result.trim()
    }
}
