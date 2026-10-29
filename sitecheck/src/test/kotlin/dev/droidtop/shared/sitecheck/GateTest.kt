package dev.droidtop.shared.sitecheck

import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The two interfaces an app plugs its web view and its screens into; here with fakes, run without a coroutine library. */
class GateTest {
    /** Runs a suspend function that never really suspends. */
    private fun <T> run(block: suspend () -> T): T {
        var outcome: Result<T>? = null
        block.startCoroutine(object : Continuation<T> {
            override val context = EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome = result }
        })
        return outcome!!.getOrThrow()
    }

    @Test
    fun `a solver and an asker answer with a clearance, a need for a person, or a reason`() {
        val clearance = Clearance("ua", mapOf("f95zone.to" to "cf_clearance=abc"))
        val solver = SiteSolver { url, timeout -> if (timeout >= 15_000L && url.contains("f95zone.to")) CheckOutcome.Passed(clearance) else CheckOutcome.NeedsPerson }
        assertEquals(CheckOutcome.Passed(clearance), run { solver.solve("https://f95zone.to/", 15_000L) })
        assertEquals(CheckOutcome.NeedsPerson, run { solver.solve("https://f95zone.to/", 100L) })
        val asker = SiteAsker { _, inPlace -> if (inPlace) CheckOutcome.Passed(clearance) else CheckOutcome.Failed("a notice was raised") }
        assertTrue(run { asker.ask("https://f95zone.to/", true) } is CheckOutcome.Passed)
        assertEquals(CheckOutcome.Failed("a notice was raised"), run { asker.ask("https://f95zone.to/", false) })
    }
}
