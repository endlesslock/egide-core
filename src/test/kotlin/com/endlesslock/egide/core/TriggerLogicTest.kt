package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [TriggerLogic].
 *
 * Covers the erase-trigger decision logic extracted from the Android components, including the
 * evasion edge cases (clock rollback, absent or non-numeric values) — exactly the behaviours that
 * matter for the anti-theft core, now checkable with no device and no emulator.
 *
 * The dead-man TIMESTAMP core (arm / resume / tick, clock-jump resistance) has its own file,
 * [DeadManTickTest]. The plausibility guard has [TriggerLogicClockJumpTest].
 *
 * Scope here:
 *  - [TriggerLogic.lockTimeLimitSeconds]: null, "", non-numeric, "0", negative, "1", "72", very
 *    large (no Int overflow), spaces (not trimmed by `toLongOrNull`). The fallback for absent or
 *    invalid values is `0` (= trigger DISABLED, aligned with the airplane/network timers), a
 *    fail-open choice.
 *  - [TriggerLogic.accumulate]: active/inactive, threshold <= 0, negative/zero/positive delta, the
 *    `>=` boundary, multi-tick accumulation, a counter already past the threshold.
 *  - [TriggerLogic.accumulateIsolation]: hours→seconds conversion, `hours <= 0`.
 *  - [TriggerLogic.maxFailedAttempts] / [TriggerLogic.failedAttemptsShouldWipe]: fallback, boundaries.
 */
class TriggerLogicTest {

    // ───────────────────────── lockTimeLimitSeconds ─────────────────────────

    @Test
    fun `lock limit — an hours value is converted with the historical computation`() {
        // 72 → 72 * 60 * 60 = 259 200 s (72 h). Historical computation (×60 then ×60) preserved.
        assertEquals(259_200, TriggerLogic.lockTimeLimitSeconds("72"))
        assertEquals(3_600, TriggerLogic.lockTimeLimitSeconds("1"))
    }

    @Test
    fun `lock limit — an absent or invalid value disables the trigger`() {
        // Fail-open fallback → 0 (= trigger DISABLED, aligned with the airplane/network timers).
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(null))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("abc"))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds(null))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds("abc"))
        assertEquals(0, TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS)
    }

    @Test
    fun `lock limit — an empty string disables the trigger`() {
        // "" → toLongOrNull() == null → 0 (trigger disabled).
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(""))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds(""))
    }

    @Test
    fun `lock limit — untrimmed spaces disable the trigger`() {
        // `toLongOrNull` does NOT trim spaces → " 72 ", " 1" and "1 " are invalid → 0 (disabled).
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(" 72 "))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds(" 1"))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("1 "))
        assertEquals(0, TriggerLogic.lockTimeLimitSeconds("   "))
        assertEquals(TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS, TriggerLogic.lockTimeLimitSeconds("   "))
    }

    @Test
    fun `lock limit — a large value does not overflow Int`() {
        // 100000 h : 100000 * 60 = 6 000 000, * 60 = 360 000 000 (< Int.MAX_VALUE) → no overflow.
        assertEquals(360_000_000, TriggerLogic.lockTimeLimitSeconds("100000"))
        // A corrupt input must never wrap back to negative through overflow.
        assertEquals(Int.MAX_VALUE, TriggerLogic.lockTimeLimitSeconds(Int.MAX_VALUE.toString()))
        assertEquals(Int.MAX_VALUE, TriggerLogic.lockTimeLimitSeconds("999999999999999999"))
    }

    @Test
    fun `lock limit — one (1) gives exactly one hour`() {
        assertEquals(3_600, TriggerLogic.lockTimeLimitSeconds("1"))
        assertEquals(TriggerLogic.SECONDS_PER_HOUR, TriggerLogic.lockTimeLimitSeconds("1").toLong())
    }

    // ───────────────────────── COUNTER mode (accumulate) ─────────────────────────

    @Test
    fun `counter — accumulates the delta while the condition is active`() {
        val r1 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 0, deltaSeconds = 30)
        assertEquals(30L, r1.newAccSeconds)
        assertFalse(r1.wipe)
        val r2 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 30, deltaSeconds = 30)
        assertEquals(60L, r2.newAccSeconds)
        assertFalse(r2.wipe)
    }

    @Test
    fun `counter — wipes when the counter reaches the threshold`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        assertEquals(100L, r.newAccSeconds)
        assertTrue(r.wipe)
    }

    @Test
    fun `counter — an inactive condition resets the counter to zero (no wipe)`() {
        val r = TriggerLogic.accumulate(active = false, limitSeconds = 100, accSeconds = 90, deltaSeconds = 30)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter — disabled (threshold 0) resets to zero`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 0, accSeconds = 50, deltaSeconds = 30)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter — a zero or negative delta does not increment (first tick or after reboot)`() {
        // deltaSeconds = 0 (first tick) → the counter does not move: off time is never added.
        val r0 = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 40, deltaSeconds = 0)
        assertEquals(40L, r0.newAccSeconds)
        assertFalse(r0.wipe)
        // negative deltaSeconds (clock anomaly) → ignored, no jump.
        val rNeg = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 40, deltaSeconds = -50)
        assertEquals(40L, rNeg.newAccSeconds)
    }

    @Test
    fun `isolation counter — hours to seconds conversion`() {
        // 1 h = 3600 s. acc = 3590, delta = 10 → 3600 ≥ 3600 → wipe.
        val r = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_590, deltaSeconds = 10)
        assertEquals(3_600L, r.newAccSeconds)
        assertTrue(r.wipe)
        // Below the threshold.
        val r2 = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = 30)
        assertFalse(r2.wipe)
    }

    @Test
    fun `counter — a negative threshold disarms and resets to zero`() {
        val r = TriggerLogic.accumulate(active = true, limitSeconds = -10, accSeconds = 999, deltaSeconds = 5)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter — inactivity wins over a counter already past the threshold`() {
        // Even if acc >= limit, inactivity re-arms to 0 with no wipe (the condition disappeared).
        val r = TriggerLogic.accumulate(active = false, limitSeconds = 50, accSeconds = 500, deltaSeconds = 0)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `counter — boundary just below then exactly at the threshold`() {
        // acc 90 + delta 9 = 99 < 100 → no wipe.
        val below = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 9)
        assertEquals(99L, below.newAccSeconds)
        assertFalse(below.wipe)
        // acc 90 + delta 10 = 100 == 100 → wipe (>= boundary).
        val exact = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 10)
        assertEquals(100L, exact.newAccSeconds)
        assertTrue(exact.wipe)
        // acc 90 + delta 11 = 101 > 100 → wipe.
        val above = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 90, deltaSeconds = 11)
        assertEquals(101L, above.newAccSeconds)
        assertTrue(above.wipe)
    }

    @Test
    fun `counter — a counter already past the threshold fires even with a zero delta`() {
        // If the persisted counter is already >= threshold (active, threshold > 0), a delta-0 tick fires.
        val r = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 150, deltaSeconds = 0)
        assertEquals(150L, r.newAccSeconds)
        assertTrue(r.wipe)
    }

    @Test
    fun `counter — multi-tick accumulation reaches the threshold progressively`() {
        // Simulates several successive checks (each delta = powered-on time elapsed).
        var acc = 0L
        val limit = 100
        // Tick 1: +40 → 40 (no wipe).
        val t1 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 40)
        acc = t1.newAccSeconds
        assertEquals(40L, acc)
        assertFalse(t1.wipe)
        // Tick 2: +40 → 80 (no wipe).
        val t2 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 40)
        acc = t2.newAccSeconds
        assertEquals(80L, acc)
        assertFalse(t2.wipe)
        // Tick 3: +25 → 105 >= 100 → wipe.
        val t3 = TriggerLogic.accumulate(active = true, limitSeconds = limit, accSeconds = acc, deltaSeconds = 25)
        acc = t3.newAccSeconds
        assertEquals(105L, acc)
        assertTrue(t3.wipe)
    }

    @Test
    fun `counter — an interruption (inactive) in the middle re-arms everything`() {
        // Accumulate 60, then the condition goes inactive (re-arm to 0), then re-accumulate from 0.
        val a = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = 0, deltaSeconds = 60)
        assertEquals(60L, a.newAccSeconds)
        val b = TriggerLogic.accumulate(active = false, limitSeconds = 100, accSeconds = a.newAccSeconds, deltaSeconds = 30)
        assertEquals(0L, b.newAccSeconds) // re-armed
        val c = TriggerLogic.accumulate(active = true, limitSeconds = 100, accSeconds = b.newAccSeconds, deltaSeconds = 30)
        assertEquals(30L, c.newAccSeconds) // restarts from 0
        assertFalse(c.wipe)
    }

    @Test
    fun `counter — AccResult equality by value (data class)`() {
        // Checks the structural equality of the result (useful to callers comparing the object).
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
    fun `counter — the addition saturates instead of overflowing to negative`() {
        val result = TriggerLogic.accumulate(
            active = true,
            limitSeconds = Int.MAX_VALUE,
            accSeconds = Long.MAX_VALUE - 2,
            deltaSeconds = 10
        )
        assertEquals(Long.MAX_VALUE, result.newAccSeconds)
        assertTrue(result.wipe)
    }

    // ───────────────────────── accumulateIsolation (hours → seconds) ─────────────────────────

    @Test
    fun `isolation counter — hours 0 disarms (returns 0, no wipe)`() {
        // hours <= 0 → limit 0 → accumulate disarmed even if the condition is active.
        val r = TriggerLogic.accumulateIsolation(active = true, hours = 0, accSeconds = 5_000, deltaSeconds = 100)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter — negative hours disarms`() {
        val r = TriggerLogic.accumulateIsolation(active = true, hours = -3, accSeconds = 5_000, deltaSeconds = 100)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter — an inactive condition resets to zero even with a valid threshold`() {
        val r = TriggerLogic.accumulateIsolation(active = false, hours = 72, accSeconds = 200_000, deltaSeconds = 50)
        assertEquals(0L, r.newAccSeconds)
        assertFalse(r.wipe)
    }

    @Test
    fun `isolation counter — exact boundary at 1 hour`() {
        // 1 h = 3600 s. acc 3599 + delta 1 = 3600 == 3600 → wipe.
        val exact = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_599, deltaSeconds = 1)
        assertEquals(3_600L, exact.newAccSeconds)
        assertTrue(exact.wipe)
        // acc 3598 + delta 1 = 3599 < 3600 → no wipe.
        val below = TriggerLogic.accumulateIsolation(active = true, hours = 1, accSeconds = 3_598, deltaSeconds = 1)
        assertEquals(3_599L, below.newAccSeconds)
        assertFalse(below.wipe)
    }

    @Test
    fun `isolation counter — a zero or negative delta does not accumulate`() {
        val r0 = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = 0)
        assertEquals(1_000L, r0.newAccSeconds)
        assertFalse(r0.wipe)
        val rNeg = TriggerLogic.accumulateIsolation(active = true, hours = 72, accSeconds = 1_000, deltaSeconds = -999)
        assertEquals(1_000L, rNeg.newAccSeconds)
        assertFalse(rNeg.wipe)
    }

    // ───────────────────────── failed passcode ─────────────────────────

    @Test
    fun `failed — the default threshold applies when the value is absent or invalid`() {
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(null))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("xx"))
        assertEquals(10, TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS)
    }

    @Test
    fun `failed — wipes at the threshold and above, not below`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 4, maxAttemptsRaw = "5"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 5, maxAttemptsRaw = "5"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 9, maxAttemptsRaw = "5"))
    }

    @Test
    fun `failed — falls back to the default when the persisted threshold is invalid`() {
        // Default = 10: below it we do not erase, at the threshold we do.
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 9, maxAttemptsRaw = null))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 10, maxAttemptsRaw = null))
    }

    @Test
    fun `failed — an empty string and spaces fall back to the default`() {
        // "" and " 5 " are not numeric (toIntOrNull does not trim) → default 10.
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(""))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts(" 5 "))
    }

    @Test
    fun `failed — positive values read as is and non-positive ones made safe`() {
        assertEquals(1, TriggerLogic.maxFailedAttempts("1"))
        assertEquals(3, TriggerLogic.maxFailedAttempts("3"))
        assertEquals(10, TriggerLogic.maxFailedAttempts("10"))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("0"))
        assertEquals(TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS, TriggerLogic.maxFailedAttempts("-2"))
    }

    @Test
    fun `failed — a threshold of 1 fires on the first failure`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "1"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 1, maxAttemptsRaw = "1"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 2, maxAttemptsRaw = "1"))
    }

    @Test
    fun `failed — a corrupt threshold of 0 falls back to the safe default`() {
        // "0" is non-positive → fall back to the default (10): we only erase from 10 on.
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "0"))
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 3, maxAttemptsRaw = "0"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 10, maxAttemptsRaw = "0"))
    }

    @Test
    fun `failed — a corrupt negative threshold does not fire immediately`() {
        // "-2" is non-positive → fall back to the default (10).
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 0, maxAttemptsRaw = "-2"))
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 1, maxAttemptsRaw = "-2"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 10, maxAttemptsRaw = "-2"))
    }

    @Test
    fun `failed — exact boundary at a high threshold`() {
        assertFalse(TriggerLogic.failedAttemptsShouldWipe(attempts = 9, maxAttemptsRaw = "10"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 10, maxAttemptsRaw = "10"))
        assertTrue(TriggerLogic.failedAttemptsShouldWipe(attempts = 11, maxAttemptsRaw = "10"))
    }

    // ───────────────────────── documented constants ─────────────────────────

    @Test
    fun `constants — documented values`() {
        // The default lock time limit is now 0 (= disabled).
        assertEquals(0, TriggerLogic.DEFAULT_LOCK_LIMIT_SECONDS)
        assertEquals(3600L, TriggerLogic.SECONDS_PER_HOUR)
        assertEquals(10, TriggerLogic.DEFAULT_MAX_FAILED_ATTEMPTS)
    }
}
