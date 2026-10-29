package dev.droidtop.shared.trust

/** Origin ids written as GitHub repositories. */
object Origins {
    private val GITHUB = Regex("https://github\\.com/([^/]+)/([^/]+)", RegexOption.IGNORE_CASE)

    /** `https://github.com/Owner/Repo.git/` as `https://github.com/owner/repo`; anything else, trimmed, unchanged. */
    fun normalizeGithub(value: String): String {
        val candidate = value.trim().trimEnd('/').removeSuffix(".git")
        val match = GITHUB.matchEntire(candidate) ?: return candidate
        return "https://github.com/${match.groupValues[1].lowercase()}/${match.groupValues[2].lowercase()}"
    }
}
