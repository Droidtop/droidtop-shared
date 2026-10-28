package dev.droidtop.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportComposerTest {
    private val app = AppIdentity("Enginehost", "0.5.0", build = "1234")

    private fun draft(vararg sections: Section, details: String = "It crashed", title: String = "Crash on start", hide: List<String> = emptyList()) =
        ReportDraft(app, Kind.CRASH, title, details, sections.toList(), hide, reportId = "11111111-2222-3333-4444-555555555555")

    @Test fun `only included sections leave the device`() {
        val report = ReportComposer.compose(
            draft(
                Section("log", "App log", Source.APP_LOG, "line one", included = true),
                Section("dev", "Device", Source.DEVICE, "Pixel 8", included = false),
            ),
        )
        assertTrue(report.body.contains("## App log\nline one"))
        assertFalse(report.body.contains("Pixel 8"))
        assertFalse(report.body.contains("Device"))
        assertEquals(listOf("App log"), report.includedSections)
    }

    @Test fun `an included section with no text is left out`() {
        val report = ReportComposer.compose(draft(Section("log", "App log", Source.APP_LOG, "  ")))
        assertEquals(emptyList<String>(), report.includedSections)
    }

    @Test fun `text typed after the gathering is redacted at composition`() {
        val report = ReportComposer.compose(
            draft(Section("log", "App log", Source.APP_LOG, "user bob@example.com at /storage/emulated/0/x"), details = "my token=abc123 failed", hide = listOf("Zelda")),
        )
        assertFalse(report.body.contains("bob@"))
        assertFalse(report.body.contains("abc123"))
        assertFalse(report.body.contains("/storage/emulated"))
        assertTrue(report.body.startsWith("my token=<redacted> failed"))
    }

    @Test fun `the title is one redacted line of bounded length`() {
        val report = ReportComposer.compose(draft(title = "Crash\n  for bob@example.com " + "x".repeat(500)))
        assertFalse(report.title.contains('\n'))
        assertFalse(report.title.contains("bob@"))
        assertEquals(ReportComposer.MAX_TITLE, report.title.length)
    }

    @Test fun `the app version and the report id are in the body`() {
        val body = ReportComposer.compose(draft()).body
        assertTrue(body.contains("Enginehost 0.5.0 (build 1234)"))
        assertTrue(body.contains("Report id: 11111111-2222-3333-4444-555555555555"))
    }

    @Test fun `device text keeps its version numbers`() {
        val body = ReportComposer.compose(draft(Section("dev", "Device", Source.DEVICE, "Driver 512.10.4.1"))).body
        assertTrue(body.contains("512.10.4.1"))
    }

    @Test fun `a long section is cut from the front and says so`() {
        val body = ReportComposer.compose(draft(Section("log", "App log", Source.APP_LOG, "a".repeat(ReportComposer.MAX_SECTION + 50) + "END"))).body
        assertTrue(body.contains("[cut]"))
        assertTrue(body.contains("END"))
    }

    @Test fun `the preview and the send are the same composition`() {
        val d = draft(Section("log", "App log", Source.APP_LOG, "x"))
        assertEquals(ReportComposer.compose(d), ReportComposer.compose(d))
    }
}
