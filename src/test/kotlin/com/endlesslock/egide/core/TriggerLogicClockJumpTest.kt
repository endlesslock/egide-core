package com.endlesslock.egide.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM) for the FORWARD-CLOCK-JUMP guard of [TriggerLogic].
 *
 * Safety invariant: an aberrant timestamp (a glitched RTC that NTP then corrects, or a corrupt
 * stored timestamp) must NEVER cause an instant erase of the legitimate owner's phone. The bounds
 * (epoch floor 2024, ceiling ~10 years) are outside any realistic configuration → they cannot
 * reject a legitimate timer.
 *
 * The timer decisions under a clock jump are covered by [DeadManTickTest]: the core no longer
 * decides on two readings of the wall clock, so the old cases disappeared with the functions they
 * tested. This file keeps only the plausibility guard, still used by the new core.
 */
class TriggerLogicClockJumpTest {

    // Plausible time markers (epoch s).
    private val nowSane = 1_750_000_000L                             // ~2025-06
    private val floor = TriggerLogic.PLAUSIBLE_EPOCH_FLOOR_SECONDS   // 2024-01-01

    @Test
    fun `recent, close timestamps are plausible`() {
        assertTrue(TriggerLogic.timestampsPlausibles(nowSane, nowSane - 259_200L)) // 72 h apart
    }

    @Test
    fun `a since earlier than the floor is implausible`() {
        assertFalse(TriggerLogic.timestampsPlausibles(nowSane, 100_000L)) // ~1970
    }

    @Test
    fun `a now earlier than the floor is implausible`() {
        assertFalse(TriggerLogic.timestampsPlausibles(100_000L, nowSane))
    }

    @Test
    fun `a gap larger than 10 years is implausible`() {
        assertFalse(
            TriggerLogic.timestampsPlausibles(
                floor + TriggerLogic.MAX_PLAUSIBLE_ELAPSED_SECONDS + 1L,
                floor
            )
        )
    }
}
