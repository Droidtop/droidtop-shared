package dev.droidtop.shared.launchcontract

/**
 * The names droidtop and Enginehost agree on when one starts the other (Enginehost's docs/contract.md;
 * droidtop's docs/SPEC.md 7d, "what droidtop requires of Enginehost"). Strings only: no behaviour, no UI,
 * no Android class.
 *
 * **Every value is what released builds already send and read.** A rename here is a break between two
 * apps that update on their own schedules, so the tests pin each string, and a change needs both apps to
 * accept both spellings for a release first.
 */
object EnginehostIntents {
    /** Enginehost's application id. */
    const val PACKAGE = "dev.enginehost"

    // ---- What a caller fires at Enginehost ----

    /** Start a game: extras [EXTRA_PATH], optionally [EXTRA_CONFIG], [EXTRA_AUTOINSTALL_PLUGIN], [EXTRA_ACCENT]. Open to any app, by design; each app's launch is the person's decision. */
    const val ACTION_LAUNCH = "dev.enginehost.LAUNCH"

    /** Open the config creator for a game: [EXTRA_PATH], optionally [EXTRA_CONFIG] as the starting point. */
    const val ACTION_CONFIGURE = "dev.enginehost.CONFIGURE"

    /** Open Enginehost's settings page, as a second front door. */
    const val ACTION_CONFIGURE_SETTINGS = "dev.enginehost.CONFIGURE_SETTINGS"

    /** Open where Enginehost keeps saves (the person's chosen folder for system locations). */
    const val ACTION_CONFIGURE_SAVES = "dev.enginehost.CONFIGURE_SAVES"

    /** Scan a folder for games ([EXTRA_PATH], optional). */
    const val ACTION_SCAN = "dev.enginehost.SCAN"

    /** End the running game. Answered for droidtop alone, by its signature, because it identifies rather than launches; the result carries [EXTRA_ENDED]. */
    const val ACTION_END_GAME = "dev.enginehost.END_GAME"

    // ---- What Enginehost sends back ----

    /** Broadcast to the launching app's package only, once per run, when its game ends. Extras [EXTRA_PATH], [EXTRA_SAVE_FOLDER], [EXTRA_REASON], optionally [EXTRA_SAVE_ROOT_REFUSED]. */
    const val ACTION_GAME_ENDED = "dev.enginehost.GAME_ENDED"

    // ---- Extras ----

    /** Absolute path of the game's folder (on LAUNCH, CONFIGURE, SCAN, and on GAME_ENDED). */
    const val EXTRA_PATH = "path"

    /** A raw `enginehost.json`-shaped JSON string (see [EnginehostConfig]). */
    const val EXTRA_CONFIG = "config"

    /** Boolean on LAUNCH: install the matching core without asking when none is installed. */
    const val EXTRA_AUTOINSTALL_PLUGIN = "autoinstallPlugin"

    /** On LAUNCH: the caller's accent, `#RRGGBB`, for the screens of the session it starts. */
    const val EXTRA_ACCENT = "accent"

    /** On END_GAME's result: whether a game was running and has ended. */
    const val EXTRA_ENDED = "ended"

    /** On a refused call's result: why (see [ERROR_UNTRUSTED_CALLER]). */
    const val EXTRA_ERROR = "error"

    /** On GAME_ENDED: the folder the game's saves are in. */
    const val EXTRA_SAVE_FOLDER = "saveFolder"

    /** On GAME_ENDED: how the game ended ([REASON_EXITED], [REASON_CRASHED]). */
    const val EXTRA_REASON = "reason"

    /** On GAME_ENDED, for a launch flagged `savesSynced` whose `saveRoot` was not taken: why. */
    const val EXTRA_SAVE_ROOT_REFUSED = "saveRootRefused"

    const val ERROR_UNTRUSTED_CALLER = "untrusted_caller"

    const val REASON_EXITED = "exited"
    const val REASON_CRASHED = "crashed"

    // ---- The read-only capabilities provider ----

    /** `content://dev.enginehost.capabilities/installed`: what is installed, read-only. */
    const val CAPABILITIES_AUTHORITY = "dev.enginehost.capabilities"
    const val CAPABILITIES_INSTALLED_PATH = "installed"
}
