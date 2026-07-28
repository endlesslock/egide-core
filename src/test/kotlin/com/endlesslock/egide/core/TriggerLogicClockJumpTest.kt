package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM) for the CLOCK-JUMP guard in [TriggerLogic].
 *
 * SAFETY invariant: an aberrant timestamp, whether from a glitched real-time clock later corrected
 * over the network or from a corrupt stored value, must NEVER cause the instant erasure of a
 * legitimate user's phone. The bounds (an epoch floor at 2024, a ceiling of about ten years) sit
 * outside any realistic configuration, so they cannot reject a legitimate timer.
 */
class TriggerLogicClockJumpTest {

    // Plausible reference instants, in epoch seconds.
    private val nowSain = 1_750_000_000L          // around June 2025
    private val floor = TriggerLogic.PLAUSIBLE_EPOCH_FLOOR_SECONDS // 2024-01-01

    // ---------------------------------------------------------------------------------------------
    // timestampsPlausibles
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `recent and close timestamps are plausible`() {
        assertTrue(TriggerLogic.timestampsPlausibles(nowSain, nowSain - 259_200L)) // 72 hours apart
    }

    @Test
    fun `a since below the floor is implausible`() {
        assertFalse(TriggerLogic.timestampsPlausibles(nowSain, 100_000L)) // around 1970
    }

    @Test
    fun `a now below the floor is implausible`() {
        assertFalse(TriggerLogic.timestampsPlausibles(100_000L, nowSain))
    }

    @Test
    fun `a gap larger than ten years is implausible`() {
        assertFalse(TriggerLogic.timestampsPlausibles(floor + TriggerLogic.MAX_PLAUSIBLE_ELAPSED_SECONDS + 1L, floor))
    }

    // ---------------------------------------------------------------------------------------------
    // isolationDecision under a clock jump
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `nominal behaviour preserved - erase beyond the threshold on a sound base`() {
        val d = TriggerLogic.isolationDecision(
            now = nowSain, conditionActive = true, hours = 72, currentSince = nowSain - 259_200L
        )
        assertEquals(TriggerLogic.IsolationDecision.Wipe, d)
    }

    @Test
    fun `a corrupt since, below the floor, re-anchors instead of erasing`() {
        // Without the fix: now - since is about 55 years, far beyond 72 hours, so the legitimate
        // owner's phone would be erased.
        val d = TriggerLogic.isolationDecision(
            now = nowSain, conditionActive = true, hours = 72, currentSince = 100_000L
        )
        assertTrue("must re-anchor the timer, not erase", d is TriggerLogic.IsolationDecision.StartTimer)
        assertEquals(nowSain, (d as TriggerLogic.IsolationDecision.StartTimer).since)
    }

    @Test
    fun `an aberrant current clock, below the floor, decides nothing`() {
        val d = TriggerLogic.isolationDecision(
            now = 100_000L, conditionActive = true, hours = 72, currentSince = nowSain
        )
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `an absurd gap, over ten years, re-anchors instead of erasing`() {
        val oldSince = floor
        val farNow = floor + TriggerLogic.MAX_PLAUSIBLE_ELAPSED_SECONDS + 10L
        val d = TriggerLogic.isolationDecision(
            now = farNow, conditionActive = true, hours = 72, currentSince = oldSince
        )
        assertTrue(d is TriggerLogic.IsolationDecision.StartTimer)
    }

    // ---------------------------------------------------------------------------------------------
    // lockDurationShouldWipe under a clock jump
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `lock - nominal erase on a sound base`() {
        assertTrue(
            TriggerLogic.lockDurationShouldWipe(
                now = nowSain, lockedSince = nowSain - 259_200L, timeLimitSeconds = 259_200
            )
        )
    }

    @Test
    fun `lock - a corrupt lockedSince does not erase`() {
        assertFalse(
            TriggerLogic.lockDurationShouldWipe(
                now = nowSain, lockedSince = 100_000L, timeLimitSeconds = 259_200
            )
        )
    }

    @Test
    fun `lock - an aberrant current clock does not erase`() {
        assertFalse(
            TriggerLogic.lockDurationShouldWipe(
                now = 100_000L, lockedSince = nowSain, timeLimitSeconds = 259_200
            )
        )
    }
}
