package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [TriggerLogic].
 *
 * Covers the erase-trigger decision logic, including the evasion edge cases (clock rollback, absent
 * or non-numeric values). These are exactly the behaviours that matter for an anti-theft product,
 * and they are now verifiable without a device and without an emulator.
 *
 * Intended completeness:
 *  - [TriggerLogic.lockTimeLimitSeconds]: null, empty, non-numeric, "0", negative, "1", "72", very
 *    large without integer overflow, and whitespace, which the parser does not trim. The fallback
 *    for absent or invalid values is `0`, meaning the trigger is DISABLED, aligned with the
 *    airplane and network timers. That is a deliberate fail-open choice.
 *  - [TriggerLogic.lockDurationShouldWipe]: no reference, disarmed, the exact `==` boundary, just
 *    below it, clock rollback, large numbers.
 *  - [TriggerLogic.isolationDecision]: the full matrix of hours (non-positive or positive) times
 *    condition active times timer armed or not; that `StartTimer` carries `now`; the erase boundary
 *    at hours times 3600; clock rollback.
 *  - [TriggerLogic.accumulate]: active or not, non-positive threshold, negative, zero and positive
 *    deltas, the `>=` boundary, accumulation over several ticks, a counter already past the threshold.
 *  - [TriggerLogic.accumulateIsolation]: hours to seconds conversion, non-positive hours.
 *  - [TriggerLogic.maxFailedAttempts] and [TriggerLogic.failedAttemptsShouldWipe]: fallback, boundaries.
 */
class TriggerLogicTest {

    // ───────────────────────── lockTimeLimitSeconds ─────────────────────────

    @Test
    fun `time limit - an hours value goes through the historical computation`() {
        // 72 becomes 72 * 60 * 60 = 259 200 seconds, that is 72 hours.
        assertEquals(259_200, TriggerLogic.lockTimeLimitSeconds("72"))
        assertEquals(3_600, TriggerLogic.lockTimeLimitSeconds("1"))
    }

    @Test
    fun `time limit - an absent or invalid value disables the trigger`() {
        // Fail-open fallback to 0, meaning disabled, aligned with the airplane and network timers.
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(null))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("abc"))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds(null))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds("abc"))
        assertEquals(0, TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS)
    }

    @Test
    fun `time limit - an empty string disables the trigger`() {
        // An empty string does not parse, so it maps to 0 and the trigger is disabled.
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(""))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds(""))
    }

    @Test
    fun `time limit - untrimmed whitespace disables the trigger`() {
        // Parsing does NOT trim, so " 72 ", " 1" and "1 " are invalid and map to 0, disabled.
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(" 72 "))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(" 1"))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("1 "))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("   "))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds("   "))
    }

    @Test
    fun `time limit - zero yields zero seconds, so the trigger is effectively disarmed`() {
        // "0" gives 0 * 60 * 60 = 0, and downstream a threshold of 0 disarms the trigger.
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("0"))
    }

    @Test
    fun `time limit - a negative value produces a negative threshold`() {
        // "-1" gives -3600, which is then read as disarmed by lockDurationShouldWipe.
        assertEquals(-3_600, TriggerLogic.lockTimeLimitSeconds("-1"))
        assertEquals(-259_200, TriggerLogic.lockTimeLimitSeconds("-72"))
    }

    @Test
    fun `time limit - a large value does not overflow`() {
        // 100000 hours: 100000 * 3600 = 360 000 000, below the integer maximum, so no overflow.
        assertEquals(360_000_000, TriggerLogic.lockTimeLimitSeconds("100000"))
        // A corrupt entry must never wrap around into a negative threshold.
        assertEquals(Int.MAX_VALUE, TriggerLogic.lockTimeLimitSeconds(Int.MAX_VALUE.toString()))
        assertEquals(Int.MAX_VALUE, TriggerLogic.lockTimeLimitSeconds("999999999999999999"))
    }

    @Test
    fun `time limit - one gives exactly one hour`() {
        assertEquals(3_600, TriggerLogic.lockTimeLimitSeconds("1"))
        assertEquals(TriggerLogic.SECONDS_PER_HOUR, TriggerLogic.lockTimeLimitSeconds("1").toLong())
    }

    // ───────────────────────── lockDurationShouldWipe ─────────────────────────

    @Test
    fun `lock - no erase when there is no reference lock`() {
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 1_000, lockedSince = 0, timeLimitSeconds = 60))
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 1_000, lockedSince = -5, timeLimitSeconds = 60))
    }

    @Test
    fun `lock - erase once the limit is reached or passed`() {
        // A REALISTIC epoch base, since the plausibility guard rejects anything before 2024.
        val b = 1_750_000_000L
        // Elapsed 60 s against a 60 s limit: reached, because the comparison is >=.
        assertTrue(TriggerLogic.lockDurationShouldWipe(now = b + 60, lockedSince = b, timeLimitSeconds = 60))
        // Elapsed 120 s, beyond 60 s.
        assertTrue(TriggerLogic.lockDurationShouldWipe(now = b + 120, lockedSince = b, timeLimitSeconds = 60))
    }

    @Test
    fun `lock - no erase before the limit`() {
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 1_059, lockedSince = 1_000, timeLimitSeconds = 60))
    }

    @Test
    fun `lock - a clock rollback does not trigger, which is the anti-evasion property`() {
        // now < lockedSince, so elapsed time is negative and nothing ever fires.
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 500, lockedSince = 1_000, timeLimitSeconds = 60))
    }

    @Test
    fun `lock - a zero or negative threshold disarms the trigger`() {
        // A non-positive threshold means disarmed, consistent with the airplane and network timers
        // where 0 hours means disabled. Without this guard, a threshold of 0, which a user might
        // enter believing it disables the feature, erased the device almost immediately, since
        // (now - lockedSince) >= 0 is always true once a lock is armed.
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 10_000, lockedSince = 1, timeLimitSeconds = 0))
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 10_000, lockedSince = 1, timeLimitSeconds = -3_600))
    }

    @Test
    fun `lock - the exact boundary, just below and just above`() {
        // Limit of 100 s. Elapsed 99 does not erase; 100 does; 101 does.
        val b = 1_750_000_000L
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = b + 99, lockedSince = b, timeLimitSeconds = 100))
        assertTrue(TriggerLogic.lockDurationShouldWipe(now = b + 100, lockedSince = b, timeLimitSeconds = 100))
        assertTrue(TriggerLogic.lockDurationShouldWipe(now = b + 101, lockedSince = b, timeLimitSeconds = 100))
    }

    @Test
    fun `lock - a zero elapsed time never triggers on a positive threshold`() {
        // now equals lockedSince, so elapsed is 0. With a positive threshold, 0 >= threshold is false.
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 1_000, lockedSince = 1_000, timeLimitSeconds = 1))
        // And a non-positive threshold stays disarmed even when elapsed is 0.
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = 1_000, lockedSince = 1_000, timeLimitSeconds = 0))
    }

    @Test
    fun `lock - large realistic epoch values reach the threshold`() {
        val lockedSince = 2_000_000_000L
        val limit = 259_200 // 72 hours
        assertTrue(
            TriggerLogic.lockDurationShouldWipe(
                now = lockedSince + limit,
                lockedSince = lockedSince,
                timeLimitSeconds = limit
            )
        )
        assertFalse(
            TriggerLogic.lockDurationShouldWipe(
                now = lockedSince + limit - 1,
                lockedSince = lockedSince,
                timeLimitSeconds = limit
            )
        )
    }

    @Test
    fun `lock - integration with lockTimeLimitSeconds at one hour`() {
        // The threshold derived from "1", that is 3600 s, fires exactly at one hour and not a second earlier.
        val limit = TriggerLogic.lockTimeLimitSeconds("1")
        assertEquals(3_600, limit)
        val b = 1_750_000_000L
        assertTrue(TriggerLogic.lockDurationShouldWipe(now = b + limit, lockedSince = b, timeLimitSeconds = limit))
        assertFalse(TriggerLogic.lockDurationShouldWipe(now = b + limit - 1, lockedSince = b, timeLimitSeconds = limit))
    }

    // ───────────────────────── isolationDecision ─────────────────────────

    @Test
    fun `isolation - trigger disabled with no timer gives NoOp`() {
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = true, hours = 0, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - trigger disabled but timer armed gives ClearTimer`() {
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = true, hours = 0, currentSince = 500)
        assertEquals(TriggerLogic.IsolationDecision.ClearTimer, d)
    }

    @Test
    fun `isolation - first detection arms the timer on now`() {
        val d = TriggerLogic.isolationDecision(now = 1_234, conditionActive = true, hours = 72, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.StartTimer(1_234), d)
    }

    @Test
    fun `isolation - the condition disappearing rearms the timer`() {
        val d = TriggerLogic.isolationDecision(now = 5_000, conditionActive = false, hours = 72, currentSince = 1_000)
        assertEquals(TriggerLogic.IsolationDecision.ClearTimer, d)
    }

    @Test
    fun `isolation - condition gone and timer already at zero gives NoOp`() {
        val d = TriggerLogic.isolationDecision(now = 5_000, conditionActive = false, hours = 72, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - reaching the limit triggers the erase`() {
        // One hour is 3600 s. Timer armed at since = b, now = b + 3600, so elapsed 3600 >= 3600.
        val b = 1_750_000_000L
        val d = TriggerLogic.isolationDecision(now = b + 3_600, conditionActive = true, hours = 1, currentSince = b)
        assertEquals(TriggerLogic.IsolationDecision.Wipe, d)
    }

    @Test
    fun `isolation - limit not reached gives NoOp`() {
        // 72 hours is 259 200 s while elapsed is 1000 s.
        val d = TriggerLogic.isolationDecision(now = 2_000, conditionActive = true, hours = 72, currentSince = 1_000)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - a clock rollback never reaches the limit, which is the anti-evasion property`() {
        // now < currentSince, so elapsed is negative and the timer simply stays armed.
        val d = TriggerLogic.isolationDecision(now = 500, conditionActive = true, hours = 1, currentSince = 1_000)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    // Full matrix: non-positive hours take the else branch whatever the condition.

    @Test
    fun `isolation - zero hours, condition inactive, no timer gives NoOp`() {
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = false, hours = 0, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - zero hours, condition inactive, timer armed gives ClearTimer`() {
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = false, hours = 0, currentSince = 777)
        assertEquals(TriggerLogic.IsolationDecision.ClearTimer, d)
    }

    @Test
    fun `isolation - negative hours, condition active, no timer gives NoOp`() {
        // Non-positive hours mean disabled: a timer is NEVER started, even with the condition active.
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = true, hours = -5, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - negative hours, condition active, timer armed gives ClearTimer`() {
        // The trigger is disabled while a timer was still lying around, so it is reset.
        val d = TriggerLogic.isolationDecision(now = 1_000, conditionActive = true, hours = -5, currentSince = 100)
        assertEquals(TriggerLogic.IsolationDecision.ClearTimer, d)
    }

    @Test
    fun `isolation - negative hours, condition inactive, timer armed gives ClearTimer`() {
        val d = TriggerLogic.isolationDecision(now = 9_000, conditionActive = false, hours = -1, currentSince = 42)
        assertEquals(TriggerLogic.IsolationDecision.ClearTimer, d)
    }

    // Positive hours: the four combinations of timer state and condition.

    @Test
    fun `isolation - active with no timer arms it, and StartTimer carries now`() {
        // StartTimer must carry EXACTLY the `now` that was passed in, not some other value.
        val d = TriggerLogic.isolationDecision(now = 99_999, conditionActive = true, hours = 72, currentSince = 0)
        assertEquals(TriggerLogic.IsolationDecision.StartTimer(99_999), d)
        // And a different `now` changes the value carried.
        assertNotEquals(
            TriggerLogic.IsolationDecision.StartTimer(99_999),
            TriggerLogic.isolationDecision(now = 100_000, conditionActive = true, hours = 72, currentSince = 0)
        )
    }

    @Test
    fun `isolation - active with a timer just below the threshold gives NoOp`() {
        // hours = 1, that is 3600 s; since 1000, now 4599, elapsed 3599 < 3600.
        val d = TriggerLogic.isolationDecision(now = 4_599, conditionActive = true, hours = 1, currentSince = 1_000)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - the exact boundary at hours times 3600 triggers the erase`() {
        // hours = 2, that is 7200 s; elapsed 7200 equals 7200, and the comparison is >=.
        val b = 1_750_000_000L
        val d = TriggerLogic.isolationDecision(now = b + 7_200, conditionActive = true, hours = 2, currentSince = b)
        assertEquals(TriggerLogic.IsolationDecision.Wipe, d)
        // One second earlier: NoOp.
        val d2 = TriggerLogic.isolationDecision(now = b + 7_199, conditionActive = true, hours = 2, currentSince = b)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d2)
    }

    @Test
    fun `isolation - the exact boundary at the documented 72 hour default`() {
        val since = 1_750_000_000L
        val threshold = 72L * TriggerLogic.SECONDS_PER_HOUR
        assertEquals(
            TriggerLogic.IsolationDecision.Wipe,
            TriggerLogic.isolationDecision(now = since + threshold, conditionActive = true, hours = 72, currentSince = since)
        )
        assertEquals(
            TriggerLogic.IsolationDecision.NoOp,
            TriggerLogic.isolationDecision(now = since + threshold - 1, conditionActive = true, hours = 72, currentSince = since)
        )
    }

    @Test
    fun `isolation - a clock rollback leaves the timer armed, NoOp rather than ClearTimer`() {
        // now < currentSince but the condition still holds and hours is positive: do not erase, and
        // do not rearm either, so the persisted `since` is kept.
        val d = TriggerLogic.isolationDecision(now = 10, conditionActive = true, hours = 72, currentSince = 1_000_000)
        assertEquals(TriggerLogic.IsolationDecision.NoOp, d)
    }

    @Test
    fun `isolation - large epoch timestamps trigger correctly`() {
        val since = 1_900_000_000L
        val now = since + 72L * TriggerLogic.SECONDS_PER_HOUR
        assertEquals(
            TriggerLogic.IsolationDecision.Wipe,
            TriggerLogic.isolationDecision(now = now, conditionActive = true, hours = 72, currentSince = since)
        )
    }

    // ───────────────────────── COUNTER mode (accumulate) ─────────────────────────

    @Test
    fun `counter - accumulates the delta while the condition holds`() {
        val r1 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 0, deltaSeconds = 30)
        assertEquals(30L, r1.newAccSeconds)
        assertFalse(r1.wipe)
        val r2 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 30, deltaSeconds = 30)
        assertEquals(60L, r2.newAccSeconds)
        assertFalse(r2.wipe)
    }

    @Test
    fun `counter - erases when the counter reaches the threshold`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        assertEquals(100L, r.newAccSeconds)
        assertTrue(r.wipe)
    }

    @Test
    fun `counter - an inactive condition resets the counter and does not erase`() {
        val r = TriggerLogic.accumulate(active = false, limitSeconds = 100, accSeconds = 90, deltaSeconds = 30)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter - disabled by a zero threshold resets to zero`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 0, accSeconds = 50, deltaSeconds = 30)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter - a zero or negative delta does not increment, as on the first tick or after a reboot`() {
        // A delta of 0, the first tick, leaves the counter alone: powered-off time is never added.
        val r0 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 40, deltaSeconds = 0)
        assertEquals(40L, r0.newAccSeconds)
        assertFalse(r0.wipe)
        // A negative delta, a clock anomaly, is ignored rather than causing a jump.
        val rNeg = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 40, deltaSeconds = -50)
        assertEquals(40L, rNeg.newAccSeconds)
    }

    @Test
    fun `isolation counter - hours to seconds conversion`() {
        // One hour is 3600 s. acc 3590 plus delta 10 reaches 3600, so it erases.
        val r = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_590, deltaSeconds = 10)
        assertEquals(3_600L, r.newAccSeconds)
        assertTrue(r.wipe)
        // Below the threshold.
        val r2 = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = 30)
        assertFalse(r2.wipe)
    }

    @Test
    fun `counter - a negative threshold disarms and resets to zero`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = -10, accSeconds = 999, deltaSeconds = 5)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter - inactivity wins over a counter already past the threshold`() {
        // Even when acc >= limit, inactivity resets to 0 without erasing, since the condition is gone.
        val r = TriggerLogic.accumulate(active = false, limitSeconds = 50, accSeconds = 500, deltaSeconds = 0)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter - the boundary just below, then exactly at the threshold`() {
        // acc 90 plus delta 9 is 99, below 100, so no erase.
        val below = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 9)
        assertEquals(99L, below.newAccSeconds)
        assertFalse(below.wipe)
        // acc 90 plus delta 10 is exactly 100, and the comparison is >=.
        val exact = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        assertEquals(100L, exact.newAccSeconds)
        assertTrue(exact.wipe)
        // acc 90 plus delta 11 is 101, above 100.
        val above = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 11)
        assertEquals(101L, above.newAccSeconds)
        assertTrue(above.wipe)
    }

    @Test
    fun `counter - a counter already past the threshold fires even on a zero delta`() {
        // If the persisted counter is already at or above the threshold, with the condition active
        // and the threshold positive, a tick with no delta fires.
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 150, deltaSeconds = 0)
        assertEquals(150L, r.newAccSeconds)
        assertTrue(r.wipe)
    }

    @Test
    fun `counter - accumulation over several ticks reaches the threshold progressively`() {
        // Simulates successive checks, each delta being the powered-on time that elapsed.
        var acc = 0L
        val limit = 100
        // Tick 1: plus 40, so 40, no erase.
        val t1 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 40)
        acc = t1.newAccSeconds
        assertEquals(40L, acc)
        assertFalse(t1.wipe)
        // Tick 2: plus 40, so 80, no erase.
        val t2 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 40)
        acc = t2.newAccSeconds
        assertEquals(80L, acc)
        assertFalse(t2.wipe)
        // Tick 3: plus 25, so 105, at or above 100, which erases.
        val t3 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 25)
        acc = t3.newAccSeconds
        assertEquals(105L, acc)
        assertTrue(t3.wipe)
    }

    @Test
    fun `counter - an interruption in the middle resets everything`() {
        // Accumulate 60, then the condition goes inactive, which resets to 0, then accumulate again from 0.
        val a = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 0, deltaSeconds = 60)
        assertEquals(60L, a.newAccSeconds)
        val b = TriggerLogic.accumulate(active = false, limitSeconds = 100, accSeconds = a.newAccSeconds, deltaSeconds = 30)
        assertEquals(0L, b.newAccSeconds) // reset
        val c = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = b.newAccSeconds, deltaSeconds = 30)
        assertEquals(30L, c.newAccSeconds) // starts again from zero
        assertFalse(c.wipe)
    }

    @Test
    fun `counter - AccResult compares by value`() {
        // Structural equality of the result, which callers that compare the object rely on.
        assertEquals(
            TriggerLogic.AccResult(100L, true),
            TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        )
        assertEquals(
            TriggerLogic.AccResult(0L, false),
            TriggerLogic.accumulate(active = false, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        )
    }

    @Test
    fun `counter - the addition saturates instead of overflowing into a negative`() {
        val result = TriggerLogic.accumulate(
            active = true,
            limitSeconds = Int.MAX_VALUE,
            accSeconds = Long.MAX_VALUE - 2,
            deltaSeconds = 10
        )
        assertEquals(Long.MAX_VALUE, result.newAccSeconds)
        assertTrue(result.wipe)
    }

    // ───────────────────────── accumulateIsolation (hours to seconds) ─────────────────────────

    @Test
    fun `isolation counter - zero hours disarms, returning zero and no erase`() {
        // Non-positive hours give a limit of 0, so accumulation is disarmed even when active.
        val r = TriggerLogic.accumulateIsolation(active = true, hours = 0, accSeconds = 5_000, deltaSeconds = 100)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter - negative hours disarm`() {
        val r = TriggerLogic.accumulateIsolation(active = true, hours = -3, accSeconds = 5_000, deltaSeconds = 100)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter - an inactive condition resets even with a valid threshold`() {
        val r = TriggerLogic.accumulateIsolation(active = false, hours = 72, accSeconds = 200_000, deltaSeconds = 50)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter - the exact boundary at one hour`() {
        // One hour is 3600 s. acc 3599 plus delta 1 is exactly 3600, which erases.
        val exact = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_599, deltaSeconds = 1)
        assertEquals(3_600L, exact.newAccSeconds)
        assertTrue(exact.wipe)
        // acc 3598 plus delta 1 is 3599, below 3600, so no erase.
        val below = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_598, deltaSeconds = 1)
        assertEquals(3_599L, below.newAccSeconds)
        assertFalse(below.wipe)
    }

    @Test
    fun `isolation counter - a zero or negative delta does not accumulate`() {
        val r0 = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = 0)
        assertEquals(1_000L, r0.newAccSeconds)
        assertFalse(r0.wipe)
        val rNeg = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = -999)
        assertEquals(1_000L, rNeg.newAccSeconds)
        assertFalse(rNeg.wipe)
    }

    // ───────────────────────── failed passcode attempts ─────────────────────────

    @Test
    fun `attempts - the default threshold applies when the value is absent or invalid`() {
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(null))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("xx"))
        assertEquals(5, TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS)
    }

    @Test
    fun `attempts - erases at and beyond the threshold, never below`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 4, maxAttemptsRaw = "5"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 5, maxAttemptsRaw = "5"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 9, maxAttemptsRaw = "5"))
    }

    @Test
    fun `attempts - falls back to five when the persisted threshold is invalid`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 4, maxAttemptsRaw = null))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 5, maxAttemptsRaw = null))
    }

    @Test
    fun `attempts - an empty string and whitespace fall back to the default`() {
        // Neither "" nor " 5 " parses, since parsing does not trim, so the default of 5 applies.
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(""))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(" 5 "))
    }

    @Test
    fun `attempts - positive values are taken as written, non-positive ones are made safe`() {
        assertEquals(1, TriggerLogic.maxFailedAttempts("1"))
        assertEquals(3, TriggerLogic.maxFailedAttempts("3"))
        assertEquals(10, TriggerLogic.maxFailedAttempts("10"))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("0"))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("-2"))
    }

    @Test
    fun `attempts - a threshold of one fires on the very first failure`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "1"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 1, maxAttemptsRaw = "1"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 2, maxAttemptsRaw = "1"))
    }

    @Test
    fun `attempts - a corrupt threshold of zero falls back to the safe threshold`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "0"))
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 3, maxAttemptsRaw = "0"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 5, maxAttemptsRaw = "0"))
    }

    @Test
    fun `attempts - a corrupt negative threshold does not fire immediately`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "-2"))
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 1, maxAttemptsRaw = "-2"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 5, maxAttemptsRaw = "-2"))
    }

    @Test
    fun `attempts - the exact boundary at a higher threshold`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 9, maxAttemptsRaw = "10"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 10, maxAttemptsRaw = "10"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 11, maxAttemptsRaw = "10"))
    }

    // ───────────────────────── documented constants ─────────────────────────

    @Test
    fun `constants - documented values`() {
        // The default lock time limit is 0, meaning disabled.
        assertEquals(0, TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS)
        assertEquals(3600L, TriggerLogic.SECONDS_PER_HOUR)
        assertEquals(5, TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS)
    }

    // =================================================================================================
    // lockTimerDecision: re-anchoring the lock timer, symmetrical with the isolation timers
    // =================================================================================================

    private val floor = TriggerLogic.PLAUSIBLE_EPOCH_FLOOR_SECONDS
    private val limit72h = 72 * 3600

    @Test
    fun `lockTimer - NoOp when no lock is in progress`() {
        assertEquals(TriggerLogic.LockDecision.NoOp, TriggerLogic.lockTimerDecision(floor + 100, 0L, limit72h))
    }

    @Test
    fun `lockTimer - NoOp when the trigger is disarmed`() {
        assertEquals(TriggerLogic.LockDecision.NoOp, TriggerLogic.lockTimerDecision(floor + 1_000_000, floor + 1, 0))
    }

    @Test
    fun `lockTimer - Wipe when the limit is reached on a sound base`() {
        val since = floor + 1_000_000
        val now = since + limit72h
        assertEquals(TriggerLogic.LockDecision.Wipe, TriggerLogic.lockTimerDecision(now, since, limit72h))
    }

    @Test
    fun `lockTimer - NoOp before the limit on a sound base`() {
        val since = floor + 1_000_000
        val now = since + limit72h - 1
        assertEquals(TriggerLogic.LockDecision.NoOp, TriggerLogic.lockTimerDecision(now, since, limit72h))
    }

    @Test
    fun `lockTimer - Reanchor when lockedSince is implausible but now is trustworthy`() {
        // Heart of the fix: a clock glitch put lockedSince below the epoch floor while now is correct.
        val now = floor + 5_000_000
        val decision = TriggerLogic.lockTimerDecision(now, 42L, limit72h)
        assertEquals(TriggerLogic.LockDecision.Reanchor(now), decision)
    }

    @Test
    fun `lockTimer - Reanchor when the gap exceeds the plausible ceiling`() {
        val since = floor + 1
        val now = since + TriggerLogic.MAX_PLAUSIBLE_ELAPSED_SECONDS + 1 // an absurd gap, over ten years
        assertEquals(TriggerLogic.LockDecision.Reanchor(now), TriggerLogic.lockTimerDecision(now, since, limit72h))
    }

    @Test
    fun `lockTimer - NoOp when now itself is below the floor, so the clock is untrustworthy`() {
        // An aberrant now, with the real-time clock not yet synchronised: anchor nothing, wait.
        assertEquals(TriggerLogic.LockDecision.NoOp, TriggerLogic.lockTimerDecision(1000L, 500L, limit72h))
    }
}
