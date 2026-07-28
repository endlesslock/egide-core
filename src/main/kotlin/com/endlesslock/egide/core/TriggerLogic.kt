package com.endlesslock.egide.core

/**
 * Decision logic for the erase triggers. Deliberately **pure**: no Android dependency, no I/O,
 * no clock of its own.
 *
 * Why this file exists:
 *  - The anti-theft core rests on time comparisons (prolonged lock, prolonged airplane mode,
 *    prolonged absence of network) and on a failed-passcode threshold. As long as that logic
 *    lived inside Android components, it could not be tested on a host JVM without emulating
 *    the framework.
 *  - Isolated here as pure functions (inputs in, deterministic output out), it can be covered by
 *    ordinary JUnit tests, including the evasion edge cases (clock rollback, missing or invalid
 *    values), with no device and no emulator involved.
 *
 * The Android components are left with plumbing only: read the persisted values, call these
 * functions, apply the returned decision.
 *
 * Safety property: **these functions never erase anything.** They return a decision. The
 * destructive, irreversible act stays with the caller. That separation is what makes the rules
 * auditable in isolation, and it is the reason this file can be published while the code that
 * performs the erase is not.
 */
object TriggerLogic {

    /**
     * Default lock time limit, in seconds, when no value has been persisted.
     *
     * Set to **0, meaning the trigger is DISABLED**, to match the airplane and network timers
     * where an absent value or 0 also means disabled. A threshold of 0 is read by
     * [lockDurationShouldWipe] as "disarmed", so this fallback is safe: a configuration that is
     * missing or unreadable NEVER erases anything.
     *
     * Deliberate trade-off, **fail-open**: an unconfigured threshold (absent, empty, or
     * non-numeric) leaves the lock trigger inactive rather than falling back to an arbitrary
     * duration. We favour consistency across triggers and predictability ("nothing is armed until
     * something is entered") at the cost of having no time-based protection by default. The other
     * defence legs (failed passcode attempts, integrity checks, the remote signal, and the
     * airplane and network timers once configured) remain in force.
     */
    const val DEFAULT_LOCK_LIMIT_SECONDS = 0

    /** Seconds in an hour. The airplane and network thresholds are configured in HOURS. */
    const val SECONDS_PER_HOUR = 3600L

    /** Default failed-passcode threshold when the persisted value is absent or invalid. */
    const val DEFAULT_MAX_FAILED_ATTEMPTS = 5

    // Guard against a FORWARD CLOCK JUMP producing a false positive.
    //
    // The dead-man timers trust the wall clock to count time spent powered off. A device whose
    // real-time clock was wrong or fully discharged can boot with an absurd clock and then
    // resynchronise over the network. The "since" timestamp written under the wrong clock then
    // looks very old against the corrected "now": the elapsed time crosses the threshold
    // instantly, and the LEGITIMATE owner's phone gets erased. Blocking manual date changes does
    // not help here, because the correction is automatic.
    //
    // The counter-measure is pure and conservative. It can never reject a legitimate timer,
    // because both bounds sit far outside any realistic configuration:
    //  - FLOOR: a timestamp earlier than 2024-01-01 can only be a clock-glitch artefact, since
    //    the application did not exist then. Treat it as corrupt.
    //  - CEILING: a gap larger than about ten years cannot correspond to any threshold the user
    //    interface can collect, since it collects hours. Treat it as corrupt.
    // In both cases we do NOT trigger, and we re-anchor the timer on "now", restarting the count
    // from a sane instant instead of erasing on an untrustworthy time base.

    /** Plausible epoch floor, in seconds: 2024-01-01 UTC. Anything earlier is aberrant. */
    const val PLAUSIBLE_EPOCH_FLOOR_SECONDS = 1_704_067_200L

    /** Maximum plausible elapsed time for a timer, in seconds: about ten years. */
    const val MAX_PLAUSIBLE_ELAPSED_SECONDS = 315_360_000L

    /**
     * Is the (now, since) pair temporally plausible enough to decide on an erase?
     *
     * @return `false` if either `now` or `since` predates the epoch floor, or if the gap exceeds
     *         the ceiling (clock jump or corrupt timestamp). `true` if the time base is sound.
     */
    fun timestampsPlausibles(now: Long, since: Long): Boolean {
        if (now < PLAUSIBLE_EPOCH_FLOOR_SECONDS) return false
        if (since < PLAUSIBLE_EPOCH_FLOOR_SECONDS) return false
        if (now - since > MAX_PLAUSIBLE_ELAPSED_SECONDS) return false
        return true
    }

    /**
     * Converts the persisted "time before term" value into seconds.
     *
     * Deliberately keeps the historical `(value * 60) * 60` computation: a non-zero value is
     * therefore read as HOURS. A value of `"72"` still yields 259 200 seconds.
     *
     * The fallback on an absent or unparseable value is **`0`**, which [lockDurationShouldWipe]
     * treats as disabled, matching the airplane and network timers. Consequences:
     *  - `null`, `"abc"`, `""`, `"   "` (non-numeric; parsing does not trim) map to `0`.
     *  - `"0"` maps to `0`, already disabled.
     *  - `"72"` maps to `259 200`; `"1"` maps to `3 600`.
     *  - `"-1"` maps to `-3 600`, negative, hence still disabled downstream.
     *
     * Deliberate trade-off, **fail-open**: see [DEFAULT_LOCK_LIMIT_SECONDS].
     *
     * @param timeBeforeTermRaw the persisted value, possibly null or non-numeric.
     * @return the limit in seconds. Zero or negative means the trigger is disabled.
     */
    fun lockTimeLimitSeconds(timeBeforeTermRaw: String?): Int {
        val hours = timeBeforeTermRaw?.toLongOrNull() ?: return DEFAULT_LOCK_LIMIT_SECONDS
        val seconds = when {
            hours > Int.MAX_VALUE / SECONDS_PER_HOUR -> Int.MAX_VALUE.toLong()
            hours < Int.MIN_VALUE / SECONDS_PER_HOUR -> Int.MIN_VALUE.toLong()
            else -> hours * SECONDS_PER_HOUR
        }
        return seconds.toInt()
    }

    /**
     * Has the device stayed locked long enough to reach the limit? This is the dead-man's switch.
     *
     * @param now current instant, epoch seconds.
     * @param lockedSince instant of the last lock, epoch seconds. Zero or less means not locked.
     * @param timeLimitSeconds the limit in seconds, see [lockTimeLimitSeconds].
     * @return `true` if the erase should be triggered.
     *
     * Anti-evasion: if `now < lockedSince` (clock rolled backwards), elapsed time is negative, so
     * nothing fires and nothing behaves erratically.
     *
     * Anti-false-positive: a limit of zero or less means "disarmed", consistent with the airplane
     * and network timers where 0 hours means disabled. Without this guard, a threshold of 0, which
     * a user may well enter believing it disables the feature, made `(now - lockedSince) >= 0`
     * always true, erasing the device almost the moment the screen turned off.
     */
    fun lockDurationShouldWipe(now: Long, lockedSince: Long, timeLimitSeconds: Int): Boolean {
        if (lockedSince <= 0L) return false
        if (timeLimitSeconds <= 0) return false
        // Never erase on an untrustworthy time base. The lock timer re-anchors on the next
        // screen-off anyway.
        if (!timestampsPlausibles(now, lockedSince)) return false
        return (now - lockedSince) >= timeLimitSeconds
    }

    /**
     * Decision of the prolonged-lock timer in TIMESTAMP mode, symmetrical to [isolationDecision].
     */
    sealed class LockDecision {
        /** Nothing to do: not locked, trigger disarmed, or limit not reached. */
        object NoOp : LockDecision()
        /** Time base corrupt (clock jump): RE-ANCHOR the timer by persisting [since]. */
        data class Reanchor(val since: Long) : LockDecision()
        /** The limit has been reached: trigger the erase. */
        object Wipe : LockDecision()
    }

    /**
     * Computes the prolonged-lock timer decision, mirroring [isolationDecision].
     *
     * Robustness note: [lockDurationShouldWipe] returns `false` when the time base is aberrant,
     * which prevents the clock-jump false positive, but on its own it never re-anchored the stored
     * lock timestamp, unlike the airplane and network timers which heal themselves through
     * [IsolationDecision.StartTimer]. After a clock glitch had written an implausible
     * `lockedSince`, the lock dead-man therefore stayed **disarmed** for as long as that value
     * persisted, that is until an unlock, which presupposes the very access the timer exists to
     * deny. This decision restores the re-anchoring.
     *
     * @param now current instant, epoch seconds.
     * @param lockedSince instant of the last lock, epoch seconds. Zero or less means not locked.
     * @param timeLimitSeconds the limit in seconds. Zero or less means disarmed.
     * @return [LockDecision.Wipe] when the limit is reached on a sound time base;
     *         [LockDecision.Reanchor] when the base is corrupt but `now` is trustworthy;
     *         [LockDecision.NoOp] otherwise, including when `now` itself sits below the epoch
     *         floor, in which case the current clock is not trustworthy and we simply wait.
     */
    fun lockTimerDecision(now: Long, lockedSince: Long, timeLimitSeconds: Int): LockDecision {
        if (lockedSince <= 0L) return LockDecision.NoOp
        if (timeLimitSeconds <= 0) return LockDecision.NoOp
        if (!timestampsPlausibles(now, lockedSince)) {
            // Exact mirror of the "corrupt timestamp" branch in isolationDecision: if `now` is
            // itself below the floor, the clock is not yet trustworthy, so anchor nothing.
            return if (now < PLAUSIBLE_EPOCH_FLOOR_SECONDS) LockDecision.NoOp else LockDecision.Reanchor(now)
        }
        return if ((now - lockedSince) >= timeLimitSeconds) LockDecision.Wipe else LockDecision.NoOp
    }

    /**
     * Decision for a network-isolation trigger: airplane mode, or absence of any network.
     *
     * Models the state transitions of a persisted timer without touching storage. The caller
     * applies whatever decision comes back.
     */
    sealed class IsolationDecision {
        /** Nothing to do: condition active but limit not reached, or trigger disabled with no timer. */
        object NoOp : IsolationDecision()
        /** First detection: start the timer by persisting [since]. */
        data class StartTimer(val since: Long) : IsolationDecision()
        /** The condition is gone, or the trigger is disabled: reset the timer to zero. */
        object ClearTimer : IsolationDecision()
        /** The limit has been reached: trigger the erase. */
        object Wipe : IsolationDecision()
    }

    /**
     * Computes the decision for a network-isolation trigger.
     *
     * @param now current instant, epoch seconds.
     * @param conditionActive `true` when the watched condition holds (airplane on, or no network).
     * @param hours threshold in hours. Zero or less means the trigger is disabled.
     * @param currentSince persisted start timestamp, epoch seconds. Zero means the timer is unarmed.
     * @return the decision to apply, see [IsolationDecision].
     *
     * Anti-evasion: `since` is persisted by the caller, so the measured duration spans time the
     * device spent powered off, and a reboot does not rearm the timer. If `now < currentSince`
     * (clock rolled backwards) the limit is never reached, so nothing fires and the timer stays
     * armed.
     */
    fun isolationDecision(
        now: Long,
        conditionActive: Boolean,
        hours: Int,
        currentSince: Long
    ): IsolationDecision {
        if (hours > 0 && conditionActive) {
            // If the timer IS armed but the time base is aberrant (clock jump, glitched RTC), do
            // NOT trigger. Either the current `now` is itself aberrant, in which case the clock is
            // not yet synchronised and we wait, or the stored `currentSince` is corrupt and the
            // gap is absurd, in which case we re-anchor on `now` and restart a sane count.
            if (currentSince != 0L && !timestampsPlausibles(now, currentSince)) {
                return if (now < PLAUSIBLE_EPOCH_FLOOR_SECONDS) {
                    IsolationDecision.NoOp                     // current clock untrustworthy: decide nothing
                } else {
                    IsolationDecision.StartTimer(now)          // corrupt timestamp: re-anchor cleanly
                }
            }
            return when {
                currentSince == 0L -> IsolationDecision.StartTimer(now)
                now - currentSince >= hours * SECONDS_PER_HOUR -> IsolationDecision.Wipe
                else -> IsolationDecision.NoOp
            }
        }
        // Condition inactive or trigger disabled: if a timer was armed, rearm it.
        return if (currentSince != 0L) IsolationDecision.ClearTimer else IsolationDecision.NoOp
    }

    /** Result of COUNTER mode: the new accumulator, in seconds, and whether to erase. */
    data class AccResult(val newAccSeconds: Long, val wipe: Boolean)

    /**
     * Core of COUNTER mode ("pause while powered off"): accumulates powered-on time only.
     *
     * Unlike the TIMESTAMP decisions, where time spent powered off counts, this mode only adds
     * time that actually elapsed while the device was on. The caller supplies the `deltaSeconds`
     * measured between two checks using a MONOTONIC clock, one that resets at boot. Since the gap
     * corresponding to being powered off is never passed in here, the counter effectively pauses
     * while the device is off, yet survives a reboot because the counter itself is persisted. If
     * the condition disappears, the counter resets to zero.
     *
     * @param active the watched condition holds (airplane on, no network, or screen locked).
     * @param limitSeconds threshold in seconds. Zero or less means the trigger is disabled.
     * @param accSeconds the persisted current counter, in seconds.
     * @param deltaSeconds powered-on time since the last check. Never negative; zero on the first
     *        tick and after a boot.
     * @return the new counter and the erase decision.
     */
    fun accumulate(
        active: Boolean,
        limitSeconds: Int,
        accSeconds: Long,
        deltaSeconds: Long
    ): AccResult {
        if (limitSeconds <= 0 || !active) return AccResult(0L, false)
        val delta = if (deltaSeconds > 0L) deltaSeconds else 0L
        val safeAcc = accSeconds.coerceAtLeast(0L)
        val newAcc = if (Long.MAX_VALUE - safeAcc < delta) Long.MAX_VALUE else safeAcc + delta
        return AccResult(newAcc, newAcc >= limitSeconds.toLong())
    }

    /**
     * COUNTER mode for a network-isolation trigger, where the threshold is expressed in HOURS.
     *
     * @param active the condition holds (airplane on, or no network).
     * @param hours threshold in hours. Zero or less means disabled.
     * @param accSeconds the current counter, in seconds.
     * @param deltaSeconds powered-on time since the last check.
     * @return the new counter and the erase decision.
     */
    fun accumulateIsolation(
        active: Boolean,
        hours: Int,
        accSeconds: Long,
        deltaSeconds: Long
    ): AccResult {
        val limit = if (hours > 0) {
            (hours.toLong() * SECONDS_PER_HOUR).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        } else {
            0
        }
        return accumulate(active, limit, accSeconds, deltaSeconds)
    }

    /**
     * Reads the failed-passcode threshold from the persisted value, with a safe fallback.
     *
     * @param maxAttemptsRaw the persisted value, possibly null or non-numeric.
     * @return the effective threshold, falling back to [DEFAULT_MAX_FAILED_ATTEMPTS].
     */
    fun maxFailedAttempts(maxAttemptsRaw: String?): Int {
        return maxAttemptsRaw?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_FAILED_ATTEMPTS
    }

    /**
     * Has the number of failed passcode attempts reached the configured threshold?
     *
     * @param attempts consecutive failures as reported by the system.
     * @param maxAttemptsRaw the persisted threshold value.
     * @return `true` if the erase should be triggered.
     */
    fun failedAttemptsShouldWipe(attempts: Int, maxAttemptsRaw: String?): Boolean {
        return attempts >= maxFailedAttempts(maxAttemptsRaw)
    }
}
