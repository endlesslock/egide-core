package com.endlesslock.egide.core

import org.json.JSONObject

/*
 * ============================================================================
 * File: LicenceDecision.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   The PREMIUM activation decision and the suppress-only gate — PURE decisions, no Android
 *   dependency, tested on the host.
 *
 * ⚠️ POLARITY ("free at the start"): the erase gate ([estInhibe]) requires a POSITIVE validation
 *   for PREMIUM sources — a device that has NEVER received a verdict (UNKNOWN) is FREE, and its
 *   premium watchers are INERT until the first contact with the server (a 30-day trial or a granted
 *   premium). This is NOT the old fail-open. Once validated, however, a LOSS of network cuts
 *   nothing: `seconds_remaining` stays PERSISTED (the countdown runs on the monotonic anchor), so
 *   "validated then offline" stays ACTIVE until an EXPLICIT `seconds_remaining <= 0` is received from
 *   an authenticated `/version`. The FREE / anti-theft net (failed code, prolonged lock, tamper)
 *   stays armed at all times, before and after validation.
 *
 * FREEMIUM BOUNDARY
 *   `isActive` = "is premium active?", NOT a global app state. The FREE features IGNORE it (always
 *   on). Only the PREMIUM features (network isolation, SMS, onion, dead-man) consult this verdict.
 *
 * NO "revoked device" branch here. An inactive account is refused at `/api/nonce` / `/api/verify`
 *   (401): the app never gets a JWT, never receives `seconds_remaining`, and falls back to UNKNOWN =
 *   ACTIVE (fail-open). The ONLY way to suspend the credit is server-side (the account stays active,
 *   auth still succeeds, and an explicit `seconds_remaining = 0` is returned).
 * ============================================================================
 */
object LicenceDecision {

    /**
     * Sentinel meaning "no credit verdict ever received", for the persisted `seconds_remaining`
     * value. Distinct from any real credit value, including a negative one: it means UNKNOWN, never
     * SUSPENDED.
     */
    const val CREDIT_INCONNU: Long = Long.MIN_VALUE

    /** ONLINE server verdict from an authenticated `/version` (three cases, not two). */
    enum class Verdict {
        /** `unlimited=true`, or `seconds_remaining > 0` received explicitly. */
        ACTIF_EXPLICITE,

        /** `seconds_remaining <= 0` received explicitly (the ONLY way to suspend). */
        SUSPENDU_EXPLICITE,

        /** No verdict received (key absent / never reached the server). We do not cut on this. */
        INCONNU,
    }

    /** Premium status resolved for the GATE, from the PERSISTED state (last authenticated verdict). */
    enum class StatutPremium {
        /** Certainly active (lifetime, or last verdict `seconds_remaining > 0`). */
        ACTIF,

        /** Certainly suspended (last verdict `seconds_remaining <= 0`). Gate: INHIBIT the premium. */
        SUSPENDU,

        /** Undetermined (never a verdict). Gate: DO NOT inhibit (fail-open, anti-theft wins). */
        INCONNU,
    }

    /**
     * Credit display bound: 0 if negative. ⚠️ For DISPLAY ONLY. The SUSPENDED verdict is computed on
     * the RAW value, BEFORE this clamp — otherwise a hostile `-1` would become 0 and suspend wrongly
     * instead of being told apart from "never received".
     */
    fun clampCreditSeconds(brut: Long): Long = if (brut < 0L) 0L else brut

    /** Credit read from an authenticated `/version` response. */
    data class CreditServeur(
        val verdict: Verdict,
        /** RAW value of `seconds_remaining` (may be < 0); [CREDIT_INCONNU] if the key is absent. */
        val secondsRemainingBrut: Long,
        /** `active_until` in epoch SECONDS, `0` if absent. */
        val activeUntilSec: Long,
        val unlimited: Boolean,
        /** `device_uid` (64-char hex), `null` if absent/empty. */
        val deviceUid: String?,
        /** `server_time` in epoch MILLISECONDS (the time anchor), `0` if absent. */
        val serverTimeMs: Long,
    )

    /**
     * Parses the credit of an AUTHENTICATED `/version` response.
     *
     * ⚠️ `optLong(key, 0)` is FORBIDDEN here: we explicitly tell an ABSENT key (UNKNOWN, do not cut)
     * from a PRESENT value <= 0 (SUSPENDED), via `has()` + `isNull()`. The server OMITS a null credit
     * key, so absent = "no verdict".
     *
     * Call this ONLY on a response obtained with a valid Bearer JWT: without authentication, the
     * server serves `stable` with no credit field → everything would be UNKNOWN (correct, but useless).
     */
    fun parseVersionCredit(json: JSONObject): CreditServeur {
        val unlimited = json.has(ApiContract.KEY_UNLIMITED) &&
            !json.isNull(ApiContract.KEY_UNLIMITED) &&
            json.optBoolean(ApiContract.KEY_UNLIMITED, false)

        val deviceUid = if (json.has(ApiContract.KEY_DEVICE_UID) && !json.isNull(ApiContract.KEY_DEVICE_UID)) {
            json.optString(ApiContract.KEY_DEVICE_UID, "").takeIf { it.isNotEmpty() }
        } else null

        val serverTimeMs = if (json.has(ApiContract.KEY_SERVER_TIME) && !json.isNull(ApiContract.KEY_SERVER_TIME)) {
            json.optLong(ApiContract.KEY_SERVER_TIME, 0L)
        } else 0L

        val aSeconds = json.has(ApiContract.KEY_SECONDS_REMAINING) && !json.isNull(ApiContract.KEY_SECONDS_REMAINING)
        val secondsBrut = if (aSeconds) json.optLong(ApiContract.KEY_SECONDS_REMAINING, 0L) else CREDIT_INCONNU

        val activeUntilSec = if (json.has(ApiContract.KEY_ACTIVE_UNTIL) && !json.isNull(ApiContract.KEY_ACTIVE_UNTIL)) {
            json.optLong(ApiContract.KEY_ACTIVE_UNTIL, 0L)
        } else 0L

        // Verdict computed on the RAW value (before any clamp).
        val verdict = when {
            unlimited -> Verdict.ACTIF_EXPLICITE
            !aSeconds -> Verdict.INCONNU
            secondsBrut <= 0L -> Verdict.SUSPENDU_EXPLICITE
            else -> Verdict.ACTIF_EXPLICITE
        }

        return CreditServeur(verdict, secondsBrut, activeUntilSec, unlimited, deviceUid, serverTimeMs)
    }

    /**
     * ONLINE activation decision, for the UI and for inhibiting the premium watchers.
     *
     * Table (fail-open):
     *  - `unlimited == true`                   -> ACTIVE
     *  - `forgeJoignable == false`             -> `dernierEtatConnuActif` (airplane, Tor down)
     *  - verdict SUSPENDU_EXPLICITE            -> SUSPENDED
     *  - verdict ACTIF_EXPLICITE               -> ACTIVE
     *  - verdict INCONNU                       -> ACTIVE (we do not cut on ignorance)
     *
     * ⚠️ No comparison to the wall clock: the server verdict (`seconds_remaining`) already carries the
     * expiry. NEVER suspend the premium on the local clock alone.
     */
    fun isActive(
        unlimited: Boolean,
        forgeJoignable: Boolean,
        verdictEnLigne: Verdict,
        dernierEtatConnuActif: Boolean,
    ): Boolean = when {
        unlimited -> true
        !forgeJoignable -> dernierEtatConnuActif
        verdictEnLigne == Verdict.SUSPENDU_EXPLICITE -> false
        verdictEnLigne == Verdict.ACTIF_EXPLICITE -> true
        else -> true // INCONNU
    }

    /**
     * Premium status FOR THE GATE, computed on the PERSISTED state (premium active <=> last
     * authenticated `/version` `seconds_remaining > 0`, or lifetime). The local clock NEVER enters
     * this computation — suspension is server-driven.
     *
     * @param lifetime the persisted lifetime-licence flag.
     * @param secondsRemainingPersiste the RAW persisted `seconds_remaining` ([CREDIT_INCONNU] if never received).
     */
    fun statutPourGate(lifetime: Boolean, secondsRemainingPersiste: Long): StatutPremium = when {
        lifetime -> StatutPremium.ACTIF
        secondsRemainingPersiste == CREDIT_INCONNU -> StatutPremium.INCONNU
        secondsRemainingPersiste <= 0L -> StatutPremium.SUSPENDU
        else -> StatutPremium.ACTIF
    }

    /**
     * Suppress-only GATE: is the erase from [source] INHIBITED?
     *
     * ⚠️ POLARITY ("free at the start"): a PREMIUM source proceeds ONLY if the premium is POSITIVELY
     * validated by the server (status [StatutPremium.ACTIF]: lifetime licence, or last authenticated
     * verdict `seconds_remaining > 0` — the 30-day trial included). Everything else inhibits the
     * premium source: UNKNOWN (NEVER reached the server) as much as SUSPENDED (an expiry verdict). A
     * new device is therefore FREE until the server validates it — no remote/fast erase (.onion, SMS,
     * network isolation, dead-man) before that first contact. The FREE and anti-theft sources
     * (TAMPER, ADMIN_DISABLE, FAILED_CODE, LOCK_TIMEOUT, PANIC) are NEVER inhibited: the offline net
     * (a lost phone ALWAYS ends up erasing itself via prolonged lock) stays whole, before and after
     * validation.
     */
    fun estInhibe(source: WipeSource, statut: StatutPremium): Boolean =
        source.premium && statut != StatutPremium.ACTIF

    /**
     * Is the premium POSITIVELY validated by the server? (lifetime licence, or last authenticated
     * verdict `seconds_remaining > 0` — the 30-day trial is one of these). Exact mirror of the
     * condition that lets a premium source proceed in [estInhibe] (same [statutPourGate], one source
     * of truth). Used by the UI to grey out the premium options until the device is validated.
     */
    fun premiumValide(lifetime: Boolean, secondsRemainingPersiste: Long): Boolean =
        statutPourGate(lifetime, secondsRemainingPersiste) == StatutPremium.ACTIF
}
