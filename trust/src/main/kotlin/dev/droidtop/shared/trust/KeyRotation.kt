package dev.droidtop.shared.trust

/** A key and the generation its certifier gave it: 0 for an origin's first key and for every key that is not official. */
data class KeyGeneration(val fingerprint: String, val serial: Int = 0)

/**
 * Whether an official key offered for an origin becomes that origin's key. Only the certifier's
 * serial decides: a new origin is added, the key already held changes nothing, a higher serial is
 * a rotation, and anything else (the same serial with a different key, or a lower one, which is
 * what replaying an old or leaked key looks like) is refused.
 */
object KeyRotation {
    enum class Decision { NEW_ORIGIN, UNCHANGED, ROTATE, REFUSE }

    fun decide(current: KeyGeneration?, offered: KeyGeneration): Decision = when {
        current == null -> Decision.NEW_ORIGIN
        current.fingerprint.equals(offered.fingerprint, ignoreCase = true) -> Decision.UNCHANGED
        offered.serial > current.serial -> Decision.ROTATE
        else -> Decision.REFUSE
    }
}

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
