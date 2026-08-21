package com.endlesslock.egide.core

/*
 * ============================================================================
 * File: WipeSource.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   The origin of an erase call. The `source` lets the erase router apply the freemium
 *   "suppress-only" gate: only PREMIUM sources ([premium] == true) become INERT when the premium
 *   is CERTAINLY expired; the FREE and anti-theft sources ALWAYS proceed.
 *
 * THE ONE RULE
 *   Everything that saves an offline carrier in custody is FREE for life; premium only sells speed,
 *   reach and remote control on top of that safety net. A lost phone therefore ALWAYS ends up
 *   erasing itself (prolonged-lock), even with premium expired; only the REMOTE/fast erase
 *   (SMS / onion / dead-man / network isolation) switches off.
 *
 *   This file is published because it is the honest, complete list of what pays and what does not:
 *   the anti-coercion floor is free, the remote conveniences are the paid tier. There is no hidden
 *   category. See [LicenceDecision] for the gate itself.
 * ============================================================================
 */

/**
 * Source of an erase trigger.
 *
 * @property premium `true` if the source belongs to the PREMIUM tier (gated by [LicenceDecision]:
 *   inert if the premium is certainly expired). `false` = FREE for life or anti-theft: NEVER gated.
 */
enum class WipeSource(val premium: Boolean) {

    // ---- PREMIUM (speed / reach / remote control) — GATED if premium expired ----

    /** Network isolation. Closes the extraction window before the prolonged-lock net does. */
    CONNECTIVITY(premium = true),

    /** The `.onion` eraser: an anonymous remote erase. */
    ONION(premium = true),

    /** An SMS command carrying the owner's secret: a remote erase. */
    SMS(premium = true),

    /** The dead-man timer: the subscription feature by definition. */
    DEAD_MAN(premium = true),

    // ---- FREE FOR LIFE (the anti-coercion, offline safety net) — NEVER gated ----

    /** Prolonged lock: the net that makes the premium remote timers gateable in the first place. */
    LOCK_TIMEOUT(premium = false),

    /** Failed passcode / duress: the anti-coercion floor. */
    FAILED_CODE(premium = false),

    // ---- VITAL ANTI-THEFT — NEVER gated ----

    /** Tamper guard: without it the failed-code and lock timers can be bypassed. */
    TAMPER(premium = false),

    /** Admin removal: an immediate anti-theft response. */
    ADMIN_DISABLE(premium = false),

    /** The manual panic button (an explicit user action). Never gated. */
    PANIC(premium = false),
}
