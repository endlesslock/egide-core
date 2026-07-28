package com.endlesslock.egide.core

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings

/*
 * ============================================================================
 * File: DeviceIdentity.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   The SINGLE resolver of the device identifier for the whole application. It
 *   centralises logic that used to be DUPLICATED in two places, with DIVERGENT
 *   sentinel values:
 *     - one caller returned the identifier or `""`, the empty string
 *     - the other returned the identifier or `"UNKNOWN_DEVICE"`
 *
 *   Those sentinels caused two problems:
 *     1. INCONSISTENCY: two different fallbacks for one concept, absent identity.
 *     2. DANGER: persisting `""` as the REFERENCE identity meant writing an
 *        "empty" identity that was later compared against the real one, risking
 *        a lockout, that is the refusal of a legitimate device.
 *
 * CONTRACT
 *   [resolve] returns either the REAL identifier, a non-empty value as seen by
 *   this application, or `null`. NEVER a sentinel string. Each caller DECIDES
 *   explicitly what to do about `null` instead of inheriting a silent and
 *   divergent fallback:
 *     - the provisioning path does NOT persist a reference identity when null;
 *     - the identity check allows first pairing when no reference exists yet.
 *
 * SECURITY
 *   - This identifier is SENSITIVE, being specific to the device, the profile
 *     and the signing key. It must NEVER be logged in clear. This object logs
 *     nothing.
 *   - Note that the value seen by an application is derived from its signing
 *     key, so it differs from what a shell command reports. Only the
 *     application itself can reveal its own.
 * ============================================================================
 */

/**
 * Centralised resolver of the device identifier.
 *
 * A stateless singleton: it relies solely on the [Context] passed in to query the setting through
 * the content resolver.
 */
object DeviceIdentity {

    /**
     * Resolves the device identifier.
     *
     * @param context context giving access to the content resolver.
     * @return the NON-EMPTY identifier as seen by the application, or `null` when the value is
     *         unavailable (early at boot, or in particular profiles) OR empty. Never a sentinel
     *         string: the fallback is the caller's responsibility, per the contract above.
     *
     * Robustness: any read exception becomes `null`, since the platform can in rare cases throw
     * while accessing the provider, rather than propagating and interrupting a critical call.
     */
    @SuppressLint("HardwareIds") // Device identity required by the enrolment and update protocols.
    fun resolve(context: Context): String? {
        return try {
            // The value is a platform type and can be null without throwing. We ALSO normalise the
            // empty string to `null`: an "empty" identity is not a valid identity, and must never
            // be persisted as a reference.
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
                ?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            // Unreadable: return `null`, meaning no identity. Never a sentinel.
            null
        }
    }
}
