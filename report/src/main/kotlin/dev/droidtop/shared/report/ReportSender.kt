package dev.droidtop.shared.report

/**
 * What became of a report handed to a [ReportSender].
 */
sealed interface SendOutcome {
    /** The tracker has it. [reference] and [url] say where, when it told us. */
    data class Sent(val reference: String?, val url: String?) : SendOutcome

    /**
     * The library cannot finish this one itself: the app opens [url] (a browser, a mail app) and puts
     * [clipboard] where the person can paste it, because an address is too short to carry a report.
     * The person completes the filing there, under their own account.
     */
    data class HandOff(val url: String, val clipboard: String?) : SendOutcome

    /** Nothing was filed. [retryable] says whether sending the same report again may work. */
    data class Failed(val reason: String, val retryable: Boolean) : SendOutcome
}

/**
 * A way to get a [ComposedReport] to a tracker. One report model, many senders: an app picks one
 * (or offers several) and the report is the same.
 *
 * **No sender holds a credential.** A sender is configured with an address only. A tracker that
 * needs a login is reached through an intake endpoint that holds the key on the server, or through a
 * hand-off the person completes with their own account. An authenticating key shipped in an app is
 * public the moment the app is.
 */
interface ReportSender {
    /** A short stable name for settings and logs, e.g. "bugzilla". */
    val id: String

    /** Blocking; off the main thread. Never throws for a failure to send: that is [SendOutcome.Failed]. */
    fun send(report: ComposedReport): SendOutcome
}
