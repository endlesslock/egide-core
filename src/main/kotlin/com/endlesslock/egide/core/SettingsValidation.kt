package com.endlesslock.egide.core

/**
 * Pure validation of the settings that can lead to an irreversible erase.
 *
 * The user interface must never silently "fix" an invalid entry. The user either saves exactly one
 * safe configuration, or gets an explicit error and nothing is written to storage. This class has
 * no Android dependency so that every bound is covered by host JVM tests.
 */
object SettingsValidation {

    const val MIN_FAILED_ATTEMPTS = 1
    const val MAX_FAILED_ATTEMPTS = 100
    const val MIN_LOCK_HOURS = 1
    const val MAX_DELAY_HOURS = 8_760 // one year; also prevents any hours-to-seconds overflow
    const val MIN_SMS_SECRET_LENGTH = 12
    const val MAX_SMS_SECRET_LENGTH = 128

    // Polling cadence for the remote-erase channel, in SECONDS. The minimum of 60 is never more
    // aggressive than the historical constant: it bounds battery use, and it keeps the regularity
    // of the Tor traffic from becoming a signal of its own. The maximum is 12 hours.
    const val MIN_ONION_INTERVAL_S = 60
    const val MAX_ONION_INTERVAL_S = 43_200
    const val DEFAULT_ONION_INTERVAL_S = 60

    enum class Error {
        FAILED_ATTEMPTS,
        LOCK_HOURS,
        AIRPLANE_HOURS,
        NETWORK_HOURS,
        SMS_REQUIRED,
        SMS_LENGTH,
        ONION_INTERVAL
    }

    data class Values(
        val failedAttempts: Int,
        /**
         * Lock threshold in hours, or `null` when the trigger is DISABLED and the field does not
         * hold a valid value, an empty or out-of-range entry being tolerated in that case. The
         * caller then does NOT persist this field and keeps whatever was already stored. See
         * [validate] and its `lockEnabled` parameter.
         */
        val lockHours: Int?,
        val airplaneHours: Int,
        val networkHours: Int,
        val smsSecret: String,
        val onionIntervalSeconds: Int = DEFAULT_ONION_INTERVAL_S
    )

    sealed class Result {
        data class Valid(val values: Values) : Result()
        data class Invalid(val error: Error) : Result()
    }

    fun validate(
        failedAttemptsRaw: String,
        lockHoursRaw: String,
        airplaneHoursRaw: String,
        networkHoursRaw: String,
        smsSecretRaw: String,
        smsEnabled: Boolean,
        lockEnabled: Boolean,
        onionIntervalRaw: String = DEFAULT_ONION_INTERVAL_S.toString()
    ): Result {
        val failedAttempts = failedAttemptsRaw.toIntOrNull()
            ?.takeIf { it in MIN_FAILED_ATTEMPTS..MAX_FAILED_ATTEMPTS }
            ?: return Result.Invalid(Error.FAILED_ATTEMPTS)

        // Enforce the 1..MAX bound on the lock threshold ONLY when the trigger is ACTIVE, exactly
        // mirroring how `smsEnabled` gates the SMS secret. Previously `lockHours` was bounded
        // UNCONDITIONALLY, so a disabled lock trigger with an empty or zero field produced
        // `Error.LOCK_HOURS`, which BLOCKED the whole save and made it impossible to change any
        // other setting. When disabled, an invalid entry now becomes `null`, the caller leaves the
        // stored value alone, and no error is raised.
        //
        // Why not the "0 means disabled" convention used by the airplane and network fields: here
        // a switch left ON together with 0 hours would be a silent false negative, since the
        // interface would show the trigger as armed. So when it is ON, the bound stays strict.
        val lockHours: Int? = if (lockEnabled) {
            lockHoursRaw.toIntOrNull()?.takeIf { it in MIN_LOCK_HOURS..MAX_DELAY_HOURS }
                ?: return Result.Invalid(Error.LOCK_HOURS)
        } else {
            lockHoursRaw.toIntOrNull()?.takeIf { it in MIN_LOCK_HOURS..MAX_DELAY_HOURS }
        }

        val airplaneHours = airplaneHoursRaw.toIntOrNull()
            ?.takeIf { it in 0..MAX_DELAY_HOURS }
            ?: return Result.Invalid(Error.AIRPLANE_HOURS)

        val networkHours = networkHoursRaw.toIntOrNull()
            ?.takeIf { it in 0..MAX_DELAY_HOURS }
            ?: return Result.Invalid(Error.NETWORK_HOURS)

        val smsSecret = smsSecretRaw.trim()
        if (smsEnabled && smsSecret.isEmpty()) {
            return Result.Invalid(Error.SMS_REQUIRED)
        }
        if (smsSecret.isNotEmpty() && smsSecret.length !in MIN_SMS_SECRET_LENGTH..MAX_SMS_SECRET_LENGTH) {
            return Result.Invalid(Error.SMS_LENGTH)
        }

        // Polling interval, strictly bounded, with no silent correction in the interface.
        val onionIntervalSeconds = onionIntervalRaw.toIntOrNull()
            ?.takeIf { it in MIN_ONION_INTERVAL_S..MAX_ONION_INTERVAL_S }
            ?: return Result.Invalid(Error.ONION_INTERVAL)

        return Result.Valid(
            Values(
                failedAttempts = failedAttempts,
                lockHours = lockHours,
                airplaneHours = airplaneHours,
                networkHours = networkHours,
                smsSecret = smsSecret,
                onionIntervalSeconds = onionIntervalSeconds
            )
        )
    }
}
