package dev.droidtop.shared.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class RedactorTest {
    @Test fun `paths become the place they are in`() {
        assertEquals("opened <storage>", Redactor.redact("opened /storage/emulated/0/Games/Secret Game/save.dat"))
        assertEquals("failed <storage> twice", Redactor.redact("failed /sdcard/My Games/Zelda 2/save.dat twice"))
        assertEquals("<app>", Redactor.redact("/data/user/0/dev.enginehost/files/x"))
        assertEquals("in <home>/dev", Redactor.redact("in /home/alice/dev"))
        assertEquals("in <home>", Redactor.redact("in C:\\Users\\Bob"))
    }

    @Test fun `emails addresses and secrets go and the line keeps its shape`() {
        val out = Redactor.redact("mail a.b+c@example.org from 192.168.1.20 with Bearer abc.def password=hunter2 token: xyz")
        assertEquals("mail <email> from <ip> with Bearer <redacted> password=<redacted> token: <redacted>", out)
    }

    @Test fun `github tokens and jwts go`() {
        val out = Redactor.redact("ghp_abcdefghijklmnopqrstuvwx and github_pat_11ABCDEFG0abcdefghijklmnop and eyJhbGciOi.eyJzdWIiOiIx.sig")
        assertFalse(out.contains("ghp_"))
        assertFalse(out.contains("github_pat_"))
        assertFalse(out.contains("eyJ"))
        assertEquals("<redacted> and <redacted> and <redacted>", out)
    }

    @Test fun `addresses can be left alone where a version looks like one`() {
        assertEquals("engine 4.2.1.0", Redactor.redact("engine 4.2.1.0", addresses = false))
    }

    @Test fun `hidden names go longest first`() {
        assertEquals("<hidden> and <hidden>", Redactor.redact("Alice Smith and Alice", hide = listOf("Alice", "Alice Smith", " ", "")))
    }
}
