package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [SettingsValidation].
 *
 * These bounds sit in front of an irreversible action, so the interface must never correct an
 * invalid entry silently: it either saves exactly what was validated, or refuses and writes
 * nothing.
 */
class SettingsValidationTest {

    private fun validate(
        failed: String = "5",
        lock: String = "72",
        airplane: String = "72",
        network: String = "72",
        sms: String = "0123456789abcdef",
        smsEnabled: Boolean = true,
        lockEnabled: Boolean = true,
        onionInterval: String = "60"
    ) = SettingsValidation.validate(failed, lock, airplane, network, sms, smsEnabled, lockEnabled, onionInterval)

    @Test
    fun `a nominal configuration keeps exactly the values given`() {
        val result = validate() as SettingsValidation.Result.Valid
        assertEquals(5, result.values.failedAttempts)
        assertEquals(72, result.values.lockHours)
        assertEquals(72, result.values.airplaneHours)
        assertEquals(72, result.values.networkHours)
        assertEquals("0123456789abcdef", result.values.smsSecret)
    }

    @Test
    fun `zero, or an overflowing integer, cannot arm the failed-attempts threshold`() {
        assertEquals(
            SettingsValidation.Error.FAILED_ATTEMPTS,
            (validate(failed = "0") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.FAILED_ATTEMPTS,
            (validate(failed = "999999999999999999999") as SettingsValidation.Result.Invalid).error
        )
    }

    @Test
    fun `the delays respect their explicit bounds`() {
        assertEquals(
            SettingsValidation.Error.LOCK_HOURS,
            (validate(lock = "0") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.AIRPLANE_HOURS,
            (validate(airplane = "8761") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.NETWORK_HOURS,
            (validate(network = "") as SettingsValidation.Result.Invalid).error
        )
        assertTrue(validate(airplane = "0", network = "0") is SettingsValidation.Result.Valid)
    }

    // The lock threshold is bounded only when the trigger is ACTIVE.
    @Test
    fun `a disabled lock tolerates an empty entry and does not block the save`() {
        val r = validate(lock = "", lockEnabled = false) as SettingsValidation.Result.Valid
        assertEquals(null, r.values.lockHours) // nothing to persist: the caller keeps the stored value
    }

    @Test
    fun `a disabled lock keeps a valid value when one is present`() {
        val r = validate(lock = "48", lockEnabled = false) as SettingsValidation.Result.Valid
        assertEquals(48, r.values.lockHours)
    }

    @Test
    fun `an ACTIVE lock always enforces the strict bound`() {
        assertEquals(
            SettingsValidation.Error.LOCK_HOURS,
            (validate(lock = "0", lockEnabled = true) as SettingsValidation.Result.Invalid).error
        )
    }

    @Test
    fun `an empty SMS secret requires disabling the channel`() {
        assertEquals(
            SettingsValidation.Error.SMS_REQUIRED,
            (validate(sms = "") as SettingsValidation.Result.Invalid).error
        )
        assertTrue(validate(sms = "", smsEnabled = false) is SettingsValidation.Result.Valid)
    }

    @Test
    fun `a non-empty SMS secret must carry a reasonable minimum of entropy`() {
        assertEquals(
            SettingsValidation.Error.SMS_LENGTH,
            (validate(sms = "too-short") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.SMS_LENGTH,
            (validate(sms = "x".repeat(129)) as SettingsValidation.Result.Invalid).error
        )
        assertTrue(validate(sms = " xxxxxxxxxxxx ") is SettingsValidation.Result.Valid)
    }

    // Polling cadence in seconds, bounded to [60, 43200], defaulting to 60.
    @Test
    fun `the default polling interval is 60`() {
        assertEquals(60, (validate() as SettingsValidation.Result.Valid).values.onionIntervalSeconds)
    }

    @Test
    fun `a valid polling interval is kept`() {
        assertEquals(300, (validate(onionInterval = "300") as SettingsValidation.Result.Valid).values.onionIntervalSeconds)
        assertEquals(43200, (validate(onionInterval = "43200") as SettingsValidation.Result.Valid).values.onionIntervalSeconds)
    }

    @Test
    fun `an out-of-bounds polling interval is rejected`() {
        assertEquals(
            SettingsValidation.Error.ONION_INTERVAL,
            (validate(onionInterval = "59") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.ONION_INTERVAL,
            (validate(onionInterval = "43201") as SettingsValidation.Result.Invalid).error
        )
        assertEquals(
            SettingsValidation.Error.ONION_INTERVAL,
            (validate(onionInterval = "abc") as SettingsValidation.Result.Invalid).error
        )
    }
}
