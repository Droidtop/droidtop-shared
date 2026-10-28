package dev.droidtop.shared.report

import java.util.UUID

/** Which app is reporting. Each app, and each plugin, gives its own. */
data class AppIdentity(
    /** The name a reader sees, e.g. "Enginehost". */
    val name: String,
    val version: String,
    /** The build or commit, when the app has one. */
    val build: String? = null,
    /** Where it files: the tracker product, e.g. "Enginehost", or "Enginehost plugins/Ren'Py". */
    val product: String = name,
    val component: String = "General",
)

/** Where a section's text came from, so the person can see what each part is before choosing it. */
enum class Source {
    /** What the person typed. */
    TYPED,

    /** The app's own log, trimmed. */
    APP_LOG,

    /** A crash note the app itself wrote. */
    CRASH_NOTE,

    /** Facts any app can read without a permission: model, Android version, memory, screen. */
    DEVICE,

    /** The app's own settings that differ from their defaults. */
    SETTINGS,
}

/**
 * One part of a report. [included] is the person's choice and the only thing that decides whether
 * the text leaves the device: nothing is sent that is not shown, and nothing shown is sent unless
 * it is included. The app sets the starting choice for each section;
 * the person can switch any of them.
 */
data class Section(
    val id: String,
    val heading: String,
    val source: Source,
    val text: String,
    val included: Boolean = true,
)

/** A kind of report, which a tracker may file differently. */
enum class Kind { BUG, CRASH, SUGGESTION }

/** What the app has gathered and the person is editing. Nothing in it has been sent. */
data class ReportDraft(
    val app: AppIdentity,
    val kind: Kind,
    val title: String,
    /** What the person says happened, in their words. */
    val details: String,
    val sections: List<Section>,
    /** Names to blank out of everything (the person's own name, a game's folder). */
    val hide: List<String> = emptyList(),
    /** Stays the same for one report however many times a send is retried, so an intake can recognise a repeat. */
    val reportId: String = UUID.randomUUID().toString(),
)

/**
 * A report as it leaves the device: the exact text [ReportComposer.compose] produced, which is also
 * what the preview shows. A sender gets this and nothing else.
 */
data class ComposedReport(
    val app: AppIdentity,
    val kind: Kind,
    val reportId: String,
    val title: String,
    val body: String,
    /** The headings of the sections that are in [body], in order. */
    val includedSections: List<String>,
)

object ReportComposer {
    const val MAX_TITLE = 200
    const val MAX_SECTION = 20_000
    const val MAX_BODY = 60_000

    /**
     * Turns [draft] into the report that is previewed and sent: the title and the details, then each
     * included section under its heading, all of it passed through [Redactor] and cut to the limits
     * (a cut is marked). The same function builds the preview and the thing a sender sends, so the
     * two cannot differ.
     */
    fun compose(draft: ReportDraft): ComposedReport {
        val title = Redactor.redact(draft.title, draft.hide, addresses = false).replace(Regex("\\s+"), " ").take(MAX_TITLE)
        val included = draft.sections.filter { it.included && it.text.isNotBlank() }
        val body = buildString {
            append(Redactor.redact(draft.details, draft.hide, addresses = false))
            appendLine().appendLine()
            appendLine("${draft.app.name} ${draft.app.version}" + (draft.app.build?.let { " (build $it)" } ?: ""))
            appendLine("Report id: ${draft.reportId}")
            for (section in included) {
                appendLine().appendLine("## ${section.heading}")
                val redacted = Redactor.redact(section.text, draft.hide, addresses = section.source != Source.DEVICE && section.source != Source.SETTINGS)
                appendLine(if (redacted.length > MAX_SECTION) "[cut]\n" + redacted.takeLast(MAX_SECTION) else redacted)
            }
        }.trim()
        val capped = if (body.length > MAX_BODY) body.take(MAX_BODY) + "\n[cut]" else body
        return ComposedReport(draft.app, draft.kind, draft.reportId, title, capped, included.map(Section::heading))
    }
}
