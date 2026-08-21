package com.endlesslock.egide.core

/**
 * Decision logic for the erase triggers. Deliberately **pure**: no Android dependency, no I/O,
 * no clock of its own.
 *
 * Why this file exists:
 *  - The anti-theft core rests on time comparisons (prolonged lock, prolonged airplane mode,
 *    prolonged absence of network) and on a failed-passcode threshold. As long as that logic
 *    lived inside Android components (which depend on `Context`, `DevicePolicyManager`,
 *    `SharedPreferences`…), it could not be tested on a host JVM without emulating the framework.
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
     * Fallback lock time limit, in seconds, used **only when the persisted value cannot be read**:
     * absent, empty, or non-numeric.
     *
     * The answer is **0, meaning disarmed**, matching the airplane and network timers where an
     * absent value or 0 also means disabled. A threshold of 0 is read as "disarmed" by
     * [deadManTick], so this fallback is SAFE: a missing or unreadable setting NEVER triggers an
     * erase.
     *
     * DELIBERATE TRADE-OFF: the fallback is **fail-open** — an unconfigured threshold (absent,
     * empty, or non-numeric) leaves the lock trigger INACTIVE rather than falling back to some
     * arbitrary value. We favour inter-trigger consistency and predictability ("nothing is armed
     * until something is entered") over a default time-based protection. The other legs of the
     * defence (failed passcode, tamper, the remote channel, the airplane/network timers when they
     * are configured) remain in force.
     */
    const val DEFAULT_LOCK_LIMIT_SECONDS = 0

    /** Number of seconds in an hour (the airplane/network thresholds are configured in HOURS). */
    const val SECONDS_PER_HOUR = 3600L

    /** Default failed-passcode threshold when the persisted value is absent or invalid. */
    const val DEFAULT_MAX_FAILED_ATTEMPTS = 10

    // ── Anti false-positive on a FORWARD CLOCK JUMP ───────────────────────────────────────────────
    // The dead-man timers (TIMESTAMP mode) trust the wall clock (epoch) to count time spent powered
    // off. A device whose RTC was wrong or drained can boot with an ABERRANT clock and then resync
    // (NTP): the `*_SINCE` written under the wrong clock becomes very old relative to the corrected
    // `now`, so `now - since` instantly crosses 72 h → the LEGITIMATE owner's phone is erased.
    // `DISALLOW_CONFIG_DATE_TIME` blocks manual setting, not automatic correction.
    //
    // PURE, SAFE guard (it can NEVER reject a legitimate timer, because these bounds are outside any
    // realistic configuration):
    //  - FLOOR: a timestamp earlier than 2024-01-01 must be a clock-glitch artefact (the app did not
    //    exist) → treated as corrupt.
    //  - CEILING: a gap larger than ~10 years cannot correspond to any enterable threshold (the UI
    //    collects hours) → corruption.
    // In both cases we do NOT trigger, and we re-anchor the timer on `now` (restart the countdown
    // from a sane instant) instead of erasing on an unreliable time base.

    /** Plausible-epoch floor (epoch s): 2024-01-01 UTC. Any earlier `since`/`now` is aberrant. */
    const val PLAUSIBLE_EPOCH_FLOOR_SECONDS = 1_704_067_200L

    /** Maximum plausible timer gap (seconds): ~10 years. Beyond this, the timestamp is corrupt. */
    const val MAX_PLAUSIBLE_ELAPSED_SECONDS = 315_360_000L

    /**
     * Is a (now, since) pair time-plausible enough to decide an erase?
     *
     * @return `false` if `now` OR `since` is earlier than the epoch floor, or if the gap exceeds the
     *         ceiling (clock jump / corrupt timestamp). `true` if the time base is sane.
     */
    fun timestampsPlausibles(now: Long, since: Long): Boolean {
        if (now < PLAUSIBLE_EPOCH_FLOOR_SECONDS) return false
        if (since < PLAUSIBLE_EPOCH_FLOOR_SECONDS) return false
        if (now - since > MAX_PLAUSIBLE_ELAPSED_SECONDS) return false
        return true
    }

    /**
     * Converts the persisted "time before term" value to seconds.
     *
     * Keeps the historical `(value * 60) * 60` computation on purpose: a non-zero value is therefore
     * interpreted in HOURS (×3600). A value of `"72"` always yields 259 200 s.
     *
     * The fallback on absence/error is **`0`** (= trigger DISABLED via the guard in [deadManTick]),
     * to align with the airplane/network timers (where absent/0 = disabled). Consequences:
     *  - `null` / `"abc"` / `""` / `"   "` (non-numeric; `toLongOrNull` does not trim spaces) → `0`.
     *  - `"0"` → `0` (already disabled).
     *  - `"72"` → `259 200` (unchanged); `"1"` → `3 600` (unchanged).
     *  - `"-1"` → `-3 600` (negative → always `<= 0` = disabled in [deadManTick]).
     *
     * DELIBERATE TRADE-OFF: **fail-open** (see [DEFAULT_LOCK_LIMIT_SECONDS]) — an unconfigured
     * threshold does NOT trigger an erase.
     *
     * @param timeBeforeTermRaw persisted value (string), possibly null or non-numeric.
     * @return the time limit in seconds (`0` or negative = trigger disabled).
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

    // ══════════════════════════════════════════════════════════════════════════════════════════════
    //  FORMER CORE, REMOVED
    // ══════════════════════════════════════════════════════════════════════════════════════════════
    //
    // Earlier versions of this file exposed `lockDurationShouldWipe`, `lockTimerDecision`/
    // `LockDecision` and `isolationDecision`/`IsolationDecision`, which decided an erase by comparing
    // two readings of the WALL clock. That is exactly what the core below replaces, and leaving them
    // in place would have been a trap: a future maintainer could re-wire them without seeing that
    // they are defeatable by a simple clock change. The state machine they carried (arm / re-arm /
    // decide) now lives in a single shared place in the Android layer, used by the service and the
    // watchdog alike.

    // ══════════════════════════════════════════════════════════════════════════════════════════════
    //  DEAD-MAN TIMER CORE (TIMESTAMP mode), resistant to clock changes
    // ══════════════════════════════════════════════════════════════════════════════════════════════
    //
    // WHAT THIS REPLACES. The three destructive timers (prolonged lock, airplane mode, absence of
    // network) used to decide on `now - since`, two readings of the WALL clock. That clock is
    // settable: moving it forward four days crossed a 72 h threshold instantly; moving it back froze
    // the countdown. `DISALLOW_CONFIG_DATE_TIME` forbids manual setting, but neither automatic
    // correction (NTP/NITZ, including from a hostile network) nor a tampered RTC on a powered-off
    // device.
    //
    // THE PRINCIPLE. Two clocks, each used only where it can be trusted:
    //  - `SystemClock.elapsedRealtime()` is MONOTONIC, includes deep sleep, and cannot be set. It
    //    therefore measures exactly the time elapsed WITHIN one boot session — but it restarts from
    //    zero at every boot.
    //  - The wall clock is the only witness of time spent POWERED OFF. It is consulted ONLY for that
    //    period, never to measure powered-on time.
    // A boot boundary is detected with `Settings.Global.BOOT_COUNT`; failing that, a monotonic value
    // that restarted lower can only mean a reboot.
    //
    // DIRECTION OF ERRORS. Erasing EARLY destroys a legitimate user's data; erasing LATE only delays
    // a protection. Any doubtful case therefore credits the DEMONSTRABLE MINIMUM — at worst the time
    // elapsed since boot, which no clock change can reduce.
    //
    // OUT OF SCOPE (accepted limit): a tampered RTC while the device is POWERED OFF is
    // indistinguishable from real powered-off time — it is the only measure available. That scenario
    // works AGAINST whoever holds the device: moving the clock forward triggers the erase sooner, so
    // they gain nothing.

    /**
     * Persisted reference of a dead-man timer (TIMESTAMP mode).
     *
     * @param wallS wall clock at the last measurement point (epoch s).
     * @param elapsedS `SystemClock.elapsedRealtime()` in seconds at the same instant.
     * @param bootCount `Settings.Global.BOOT_COUNT` at the same instant; a negative value = unavailable.
     * @param creditedS time already credited and validated (seconds). This is what is compared to the threshold.
     */
    data class DeadManRef(
        val wallS: Long,
        val elapsedS: Long,
        val bootCount: Long,
        val creditedS: Long,
    )

    /** Outcome of a tick: in both cases, [ref] is the state to persist BEFORE any action. */
    sealed class DeadManOutcome {
        abstract val ref: DeadManRef
        /** Threshold not reached (or timer disarmed): persist [ref] and continue. */
        data class Continue(override val ref: DeadManRef) : DeadManOutcome()
        /** Threshold reached: persist [ref], then trigger the erase. */
        data class Fire(override val ref: DeadManRef) : DeadManOutcome()
    }

    /**
     * Tolerance on the reconstructed boot instant (`wall - monotonic`), in seconds.
     *
     * Used ONLY when `BOOT_COUNT` is unreadable. Two readings within the same session give the same
     * value to the granularity of a second (both clocks advance together); we allow two minutes to
     * absorb rounding, a minor clock correction, and the polling interval.
     */
    const val TOLERANCE_INSTANT_DEMARRAGE_S = 120L

    /** Arms a timer: no credited time, references taken at the current instant. */
    fun deadManArm(nowWallS: Long, nowElapsedS: Long, nowBootCount: Long): DeadManRef =
        DeadManRef(wallS = nowWallS, elapsedS = nowElapsedS, bootCount = nowBootCount, creditedS = 0L)

    /**
     * Resumes a timer armed by the OLD format (a single wall-clock timestamp `*_SINCE`).
     *
     * Called once, at the first evaluation after an application update: without it, a running timer
     * would be reset by the update itself (a device already locked for two days would restart from
     * zero). The wall-clock gap is credited only if it is plausible; otherwise it restarts from
     * zero, never on a doubtful base.
     */
    fun deadManReprise(
        ancienSinceWallS: Long,
        nowWallS: Long,
        nowElapsedS: Long,
        nowBootCount: Long,
    ): DeadManRef {
        val credit =
            if (ancienSinceWallS > 0L && timestampsPlausibles(nowWallS, ancienSinceWallS)) {
                (nowWallS - ancienSinceWallS).coerceAtLeast(0L)
            } else {
                0L
            }
        return DeadManRef(nowWallS, nowElapsedS, nowBootCount, credit)
    }

    /**
     * One dead-man timer tick: updates the credited time and says whether to erase.
     *
     * @param ref persisted reference (from [deadManArm], [deadManReprise] or a previous tick).
     * @param nowWallS current wall clock (epoch s).
     * @param nowElapsedS current `SystemClock.elapsedRealtime()`, in seconds.
     * @param nowBootCount current `Settings.Global.BOOT_COUNT`; negative if unreadable.
     * @param limitSeconds threshold in seconds; `<= 0` = timer DISARMED (reference returned intact).
     */
    fun deadManTick(
        ref: DeadManRef,
        nowWallS: Long,
        nowElapsedS: Long,
        nowBootCount: Long,
        limitSeconds: Int,
    ): DeadManOutcome {
        if (limitSeconds <= 0) return DeadManOutcome.Continue(ref)

        val elapsedNow = nowElapsedS.coerceAtLeast(0L)
        val memeSession =
            if (ref.bootCount >= 0L && nowBootCount >= 0L) {
                ref.bootCount == nowBootCount
            } else {
                // Fallback with no boot counter. A monotonic clock that goes BACKWARDS proves a
                // reboot, but the reverse proves nothing: after a reboot, the new session quickly
                // exceeds the last tick of the previous one. Keeping only that test left a
                // REPEATABLE evasion (last tick at 30 s, five days off, resume at 60 s: 30 s
                // credited instead of five days, indefinitely).
                //
                // Second witness: the reconstructed BOOT INSTANT, `wall - monotonic`. It is constant
                // within a session and shifts by the whole off period at reboot. A drift beyond the
                // tolerance therefore counts as a reboot. It also counts for a clock change, and that
                // is the accepted trade-off of this fallback: without a boot counter, the two cannot
                // be told apart, and we choose not to leave a trivial evasion open. The nominal path
                // (readable counter) stays strictly clock-insensitive.
                val demarrageRef = ref.wallS - ref.elapsedS
                val demarrageNow = nowWallS - elapsedNow
                elapsedNow >= ref.elapsedS &&
                    kotlin.math.abs(demarrageNow - demarrageRef) <= TOLERANCE_INSTANT_DEMARRAGE_S
            }

        val credit = if (memeSession) {
            // POWERED-ON time, measured by the one clock nobody sets. The wall clock plays no part:
            // that is what makes a four-day jump strictly without effect.
            (elapsedNow - ref.elapsedS).coerceAtLeast(0L)
        } else {
            // One or more reboots since the last tick: `elapsedRealtime` restarted from zero and no
            // longer measures the off period. The wall-clock gap is the only witness of that period
            // — retained if it is plausible, and never less than the time elapsed since the current
            // boot, which no clock change can erase.
            val ecartMurale = nowWallS - ref.wallS
            if (timestampsPlausibles(nowWallS, ref.wallS) && ecartMurale >= 0L) {
                maxOf(ecartMurale, elapsedNow)
            } else {
                elapsedNow
            }
        }

        val cumul = ref.creditedS.coerceAtLeast(0L).let { acc ->
            if (Long.MAX_VALUE - acc < credit) Long.MAX_VALUE else acc + credit
        }
        val nouveau = DeadManRef(nowWallS, elapsedNow, nowBootCount, cumul)
        return if (cumul >= limitSeconds.toLong()) {
            DeadManOutcome.Fire(nouveau)
        } else {
            DeadManOutcome.Continue(nouveau)
        }
    }

    /** Result of the COUNTER mode: new counter (seconds) + whether to erase. */
    data class AccResult(val newAccSeconds: Long, val wipe: Boolean)

    /**
     * Core of the COUNTER mode ("pause while off"): accumulates ONLY powered-on time.
     *
     * Unlike the TIMESTAMP decisions (where off time counts), this mode adds only the time actually
     * elapsed while powered on: the caller supplies the `deltaSeconds` measured between two checks
     * with a MONOTONIC clock (`SystemClock.elapsedRealtime()`), which resets at boot. Since the gap
     * corresponding to being off is never passed in here, the counter effectively pauses while the
     * device is off, while surviving a reboot (the counter is persisted). If the condition
     * disappears, the counter is reset to 0.
     *
     * @param active watched condition active (airplane ON / no network / screen locked).
     * @param limitSeconds threshold in seconds; `<= 0` = trigger disabled.
     * @param accSeconds current counter (seconds), persisted.
     * @param deltaSeconds powered-on time elapsed since the last check (≥ 0; 0 at the first tick / after boot).
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
     * COUNTER mode for a network-isolation trigger (threshold expressed in HOURS).
     *
     * @param active condition active (airplane ON / no network).
     * @param hours threshold in hours; `<= 0` = disabled.
     * @param accSeconds current counter (seconds).
     * @param deltaSeconds powered-on time elapsed since the last check.
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
     * @param maxAttemptsRaw persisted value (string), possibly null or non-numeric.
     * @return the effective threshold (fallback [DEFAULT_MAX_FAILED_ATTEMPTS] if absent/invalid).
     */
    fun maxFailedAttempts(maxAttemptsRaw: String?): Int {
        return maxAttemptsRaw?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_FAILED_ATTEMPTS
    }

    /**
     * Says whether the number of failed passcode attempts has reached the configured threshold.
     *
     * @param attempts number of consecutive failures reported by the system.
     * @param maxAttemptsRaw persisted threshold value (string).
     * @return `true` if the erase must be triggered.
     */
    fun failedAttemptsShouldWipe(attempts: Int, maxAttemptsRaw: String?): Boolean {
        return attempts >= maxFailedAttempts(maxAttemptsRaw)
    }
}
