package com.endlesslock.egide.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [EraserResponseLogic].
 *
 * SAFETY invariant: an irreversible erase is decided ONLY on an HTTP success (2xx) carrying a
 * non-empty body, matching the server contract "answers 200, so erase". Any non-2xx response,
 * including a 404 or 500 error page with a non-empty body, is safe and triggers NOTHING.
 */
class EraserResponseLogicTest {

    // ---------------------------------------------------------------------------------------------
    // Nominal behaviour: 200 with a body means erase. Unchanged.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `200 with a non-empty body triggers the erase`() {
        assertTrue(EraserResponseLogic.shouldTriggerWipe(200, "WIPE"))
    }

    @Test
    fun `the whole 2xx range with a body triggers the erase`() {
        for (code in 200..299) {
            assertTrue("code $code should trigger", EraserResponseLogic.shouldTriggerWipe(code, "x"))
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The fix: an error page with a non-empty body must no longer erase.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `404 with a non-empty error page does NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(404, "<html>Not Found</html>"))
    }

    @Test
    fun `500 with a non-empty body does NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(500, "Internal Server Error"))
    }

    @Test
    fun `503 with a non-empty banner does NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(503, "Service Unavailable"))
    }

    @Test
    fun `3xx redirects with a body do NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(301, "Moved"))
        assertFalse(EraserResponseLogic.shouldTriggerWipe(302, "Found"))
    }

    // ---------------------------------------------------------------------------------------------
    // Empty or null body: never an erase, whatever the status code.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `200 with a null body does NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(200, null))
    }

    @Test
    fun `200 with an empty body does NOT trigger the erase`() {
        assertFalse(EraserResponseLogic.shouldTriggerWipe(200, ""))
    }
}
