# :report

A bug reporter that needs no permission of its own. Plain JVM, no Android API. Owner direction on
Droidtop/tracker#655: the person previews the whole report and chooses what goes in; the report is
one model with pluggable senders; no authenticating key ships in an app.

**What goes in.** Only what an app can read without a permission: its own logs and crash notes, a
device summary (`Build`, memory, screen), the settings that differ from their defaults, and what the
person types. No storage access, no `READ_LOGS`, no accounts. Gathering is the app's job (it needs
the Android SDK): the app builds a `ReportDraft` of `Section`s, each tagged with its `Source`.

- `ReportDraft` / `Section`: what the app gathered and the person edits. A section's `included` is the
  person's choice and the only thing that decides whether its text leaves the device.
- `ReportComposer.compose(draft)`: the one function that builds the preview and the thing a sender
  sends, so they cannot differ. It redacts again at that moment, so text edited after gathering is
  covered, and cuts to fixed limits (a cut says `[cut]`).
- `Redactor`: storage and app paths become `<storage>` / `<app>` (folder names with spaces
  included), home folders `<home>`, and emails, IPs, bearer and GitHub tokens, JWTs and
  `password=`/`token:` values become markers. Lifted from Enginehost's `ProblemReport.scrub`.
- `ReportSender` and `SendOutcome` (`Sent`, `HandOff`, `Failed(retryable)`):
  - `BugzillaSender`: `POST /rest/bug` to an intake address. No field for a key or login; the
    report id is the bug alias, so a retry is not filed twice. https only.
  - `IssueFormSender` (GitHub, Gitea): hands the app a `/issues/new?title=` address and the body to
    put on the clipboard (a diagnostic is too long for an address). The person files it as themselves.
  - `EmailSender`: a `mailto:` hand-off, the body also on the clipboard.

An authenticating key shipped in an app is public the moment the app is. A tracker that needs a login
is reached through an intake endpoint that holds the key server-side, or through a hand-off the person
completes with their own account. Which tracker is hosted is a separate decision (#655).
