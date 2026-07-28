package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM) for [OtaLimits]: the package size ceiling and the session token clamp.
 */
class OtaLimitsTest {

    // --- Package size ceiling, against filling the disk ---

    @Test
    fun `a size below the ceiling is not exceeded`() {
        assertFalse(OtaLimits.exceedsMaxApkBytes(50L * 1024 * 1024)) // 50 MB
    }

    @Test
    fun `a size equal to the ceiling is not exceeded`() {
        assertFalse(OtaLimits.exceedsMaxApkBytes(OtaLimits.MAX_APK_BYTES))
    }

    @Test
    fun `a size beyond the ceiling is exceeded`() {
        assertTrue(OtaLimits.exceedsMaxApkBytes(OtaLimits.MAX_APK_BYTES + 1))
    }

    // --- Clamping the session token lifetime, against an eternal token ---

    @Test
    fun `a null or negative expiry yields zero`() {
        assertEquals(0L, OtaLimits.clampJwtExpirySeconds(0L))
        assertEquals(0L, OtaLimits.clampJwtExpirySeconds(-42L))
    }

    @Test
    fun `an expiry below the maximum is kept`() {
        assertEquals(600L, OtaLimits.clampJwtExpirySeconds(600L)) // 10 minutes
    }

    @Test
    fun `an expiry beyond the maximum is clamped`() {
        assertEquals(OtaLimits.MAX_JWT_EXPIRY_SECONDS, OtaLimits.clampJwtExpirySeconds(999_999L))
    }

    @Test
    fun `an expiry equal to the maximum is kept`() {
        assertEquals(OtaLimits.MAX_JWT_EXPIRY_SECONDS, OtaLimits.clampJwtExpirySeconds(OtaLimits.MAX_JWT_EXPIRY_SECONDS))
    }
}
