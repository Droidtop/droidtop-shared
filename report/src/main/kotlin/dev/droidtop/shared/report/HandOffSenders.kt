package dev.droidtop.shared.report

import java.net.URLEncoder

private fun encode(text: String): String = URLEncoder.encode(text, Charsets.UTF_8).replace("+", "%20")

/**
 * Hand-off to a GitHub or Gitea repository's "new issue" page (`/issues/new`). The address carries
 * the title only; the body travels on the clipboard, because a diagnostic report is longer than an
 * address can be and a long address is cut or refused. The person pastes it and files it under their
 * own account, so the app holds no key.
 */
class IssueFormSender(
    override val id: String,
    /** The repository's page, e.g. `https://github.com/owner/issues` or `https://gitea.example/owner/issues`. */
    private val repositoryUrl: String,
    /** Added to the address as `labels=` where the host supports it (GitHub). */
    private val label: String? = null,
) : ReportSender {
    init {
        require(repositoryUrl.startsWith("https://")) { "an issue form is opened over https only" }
    }

    override fun send(report: ComposedReport): SendOutcome {
        val base = repositoryUrl.trimEnd('/') + "/issues/new"
        val query = buildList {
            add("title=" + encode(report.title))
            if (label != null) add("labels=" + encode(label))
        }.joinToString("&")
        return SendOutcome.HandOff("$base?$query", report.body)
    }
}

/**
 * Hand-off to the person's mail app. A `mailto:` address carries the subject; the body is on the
 * clipboard for the same reason as [IssueFormSender], and also in the address when it is short enough
 * ([MAX_BODY_IN_ADDRESS]) so the mail app opens with it already written.
 */
class EmailSender(private val to: String) : ReportSender {
    override val id = "email"

    init {
        require(Regex("[^@\\s,;]+@[^@\\s,;]+").matches(to)) { "not an email address" }
    }

    override fun send(report: ComposedReport): SendOutcome {
        val subject = "[${report.app.name}] ${report.title}"
        val body = report.body.takeIf { it.length <= MAX_BODY_IN_ADDRESS }
        val url = "mailto:$to?subject=" + encode(subject) + (body?.let { "&body=" + encode(it) } ?: "")
        return SendOutcome.HandOff(url, report.body)
    }

    companion object {
        const val MAX_BODY_IN_ADDRESS = 1500
    }
}
