package com.endlesslock.egide.core

import com.endlesslock.egide.core.TriggerLogic.DeadManOutcome
import com.endlesslock.egide.core.TriggerLogic.DeadManRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST tests for the dead-man timer core in TIMESTAMP mode.
 *
 * What this core replaces: the old `now - since` computation on the wall clock alone. Moving the
 * clock forward four days crossed a 72 h threshold instantly; moving it back froze the countdown.
 * `DISALLOW_CONFIG_DATE_TIME` blocks manual setting, but neither automatic correction (NTP/NITZ)
 * nor a tampered RTC.
 *
 * The principle tested here:
 *  - WITHIN one boot session, only `SystemClock.elapsedRealtime()` counts. It is monotonic and out
 *    of reach of any clock setting: a wall-clock jump credits NOTHING.
 *  - BETWEEN two boots, `elapsedRealtime` restarted from zero and no longer measures off time. The
 *    wall clock is then the ONLY witness of that period: it is credited, but floored by the time
 *    elapsed since boot, and only if the gap stays plausible.
 *
 * Direction of false positives: erasing EARLY destroys a legitimate user's data, erasing LATE only
 * delays a protection. Every doubtful case therefore credits the DEMONSTRABLE MINIMUM.
 */
class DeadManTickTest {

    private val limit72h = 72 * 3600           // default threshold of the three timers
    private val wall0 = 1_800_000_000L         // plausible epoch s (2027), above the floor

    private fun ref(wall: Long, elapsed: Long, boot: Long, credited: Long = 0L) =
        DeadManRef(wallS = wall, elapsedS = elapsed, bootCount = boot, creditedS = credited)

    private fun tick(
        r: DeadManRef, wall: Long, elapsed: Long, boot: Long, limit: Int = limit72h
    ) = TriggerLogic.deadManTick(
        r, nowWallS = wall, nowElapsedS = elapsed, nowBootCount = boot, limitSeconds = limit
    )

    // ── Nominal case ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `same session — credited time is that of the monotonic clock`() {
        val r = ref(wall0, 100, 7)
        val out = tick(r, wall0 + 30, 130, 7)
        assertTrue(out is DeadManOutcome.Continue)
        assertEquals(30L, out.ref.creditedS)
        assertEquals(130L, out.ref.elapsedS)
    }

    @Test
    fun `same session — reaching the threshold fires`() {
        val r = ref(wall0, 0, 7, credited = limit72h - 5L)
        val out = tick(r, wall0 + 10, 10, 7)
        assertTrue("the threshold is crossed, the erase must be decided", out is DeadManOutcome.Fire)
    }

    // ── Clock tampering DURING a session (the case that fired wrongly) ────────────────────────────

    @Test
    fun `same session — a four-day clock advance credits nothing extra`() {
        val r = ref(wall0, 100, 7)
        val fourDays = 4L * 24 * 3600
        val out = tick(r, wall0 + fourDays, 105, 7)   // 5 real seconds, 4 days announced
        assertTrue("a clock jump must never fire", out is DeadManOutcome.Continue)
        assertEquals(5L, out.ref.creditedS)
    }

    @Test
    fun `same session — a clock rollback does not freeze the counter`() {
        val r = ref(wall0, 100, 7, credited = 1_000L)
        val out = tick(r, wall0 - 4L * 24 * 3600, 160, 7)
        assertTrue(out is DeadManOutcome.Continue)
        assertEquals("monotonic time keeps running", 1_060L, out.ref.creditedS)
    }

    @Test
    fun `same session — a monotonic rollback credits nothing negative`() {
        val r = ref(wall0, 500, 7, credited = 42L)
        val out = tick(r, wall0 + 10, 300, 7)   // incoherent monotonic value (glitch)
        assertEquals(42L, out.ref.creditedS)
    }

    // ── Boot boundary (the only case where the wall clock is needed) ──────────────────────────────

    @Test
    fun `after reboot — off time is credited from the wall clock`() {
        val r = ref(wall0, 3_600, 7, credited = 600L)
        val fourDays = 4L * 24 * 3600
        val out = tick(r, wall0 + fourDays, 60, 8)   // booted 60 s ago
        assertTrue(
            "a device off for four days must cross a 72 h threshold",
            out is DeadManOutcome.Fire
        )
        assertEquals(600L + fourDays, out.ref.creditedS)
    }

    @Test
    fun `after reboot — clock set back, we credit at least the time since boot`() {
        val r = ref(wall0, 3_600, 7, credited = 600L)
        val out = tick(r, wall0 - 4L * 24 * 3600, 900, 8)
        assertTrue(out is DeadManOutcome.Continue)
        assertEquals("floor = time elapsed since boot", 600L + 900L, out.ref.creditedS)
    }

    @Test
    fun `after reboot — an aberrant gap credits only the time since boot`() {
        val r = ref(wall0, 3_600, 7)
        val twentyYears = 20L * 365 * 24 * 3600
        val out = tick(r, wall0 + twentyYears, 120, 8)
        assertEquals(120L, out.ref.creditedS)
    }

    @Test
    fun `after reboot — current clock below the epoch floor, time since boot`() {
        // Drained RTC: the phone boots in 1970. We credit only what is demonstrable.
        val r = ref(wall0, 3_600, 7, credited = 100L)
        val out = tick(r, 42L, 300, 8)
        assertEquals(400L, out.ref.creditedS)
    }

    @Test
    fun `boot counter unavailable — a monotonic rollback counts as a reboot`() {
        // Without a readable BOOT_COUNT (negative value), a monotonic value that restarted lower can
        // only mean a reboot: this is the detection fallback.
        val r = ref(wall0, 10_000, -1, credited = 50L)
        val twoDays = 2L * 24 * 3600
        val out = tick(r, wall0 + twoDays, 30, -1)
        assertEquals(50L + twoDays, out.ref.creditedS)
    }

    @Test
    fun `boot counter unavailable — same session recognised by the boot instant`() {
        // Continuous session: both clocks advance together, so `wall - monotonic` does not move. We
        // credit monotonic time and NOTHING from the wall clock.
        val r = ref(wall0, 10_000, -1, credited = 50L)
        val out = tick(r, wall0 + 60, 10_060, -1)
        assertEquals("no trust in the wall clock within a session", 110L, out.ref.creditedS)
    }

    @Test
    fun `boot counter unavailable — reboot detected despite a larger monotonic value`() {
        // The evasion that a "monotonic increasing" test alone left open: last tick at 30 s, five
        // days off, resume after 60 s of running. The current monotonic (60) exceeds the reference
        // (30), but the reconstructed boot instant moved by five days.
        val fiveDays = 5L * 24 * 3600
        val r = ref(wall0, 30, -1, credited = 50L)
        val out = tick(r, wall0 + fiveDays, 60, -1)
        assertEquals(50L + fiveDays, out.ref.creditedS)
    }

    // ── Disarming and bounds ──────────────────────────────────────────────────────────────────────

    @Test
    fun `zero threshold — the timer is disarmed and the reference stays intact`() {
        val r = ref(wall0, 100, 7, credited = 999_999L)
        val out = tick(r, wall0 + 10_000, 10_100, 7, limit = 0)
        assertTrue(out is DeadManOutcome.Continue)
        assertEquals(r, out.ref)
    }

    @Test
    fun `saturation — the accumulation never overflows`() {
        val r = ref(wall0, 0, 7, credited = Long.MAX_VALUE - 3)
        val out = tick(r, wall0 + 100, 100, 7)
        assertEquals(Long.MAX_VALUE, out.ref.creditedS)
    }

    // ── Arming and resuming an old timestamp ──────────────────────────────────────────────────────

    @Test
    fun `arming — the reference starts at zero credit`() {
        val r = TriggerLogic.deadManArm(nowWallS = wall0, nowElapsedS = 77, nowBootCount = 3)
        assertEquals(DeadManRef(wall0, 77, 3, 0L), r)
    }

    @Test
    fun `resume — a lone old timestamp is converted to a plausible credit`() {
        val twelveHours = 12L * 3600
        val r = TriggerLogic.deadManReprise(
            ancienSinceWallS = wall0 - twelveHours,
            nowWallS = wall0, nowElapsedS = 500, nowBootCount = 4
        )
        assertEquals(twelveHours, r.creditedS)
        assertEquals(wall0, r.wallS)
    }

    @Test
    fun `resume — an aberrant old timestamp credits nothing`() {
        val r = TriggerLogic.deadManReprise(
            ancienSinceWallS = 42L, nowWallS = wall0, nowElapsedS = 500, nowBootCount = 4
        )
        assertEquals(0L, r.creditedS)
    }
}
