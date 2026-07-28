package com.endlesslock.egide.core

/**
 * Safety bounds on the update chain. Pure, with no Android dependency, unit-testable on a host JVM
 * (see `OtaLimitsTest`).
 *
 * These bounds limit how much harm a compromised update server, or a party sitting in the middle
 * of the Tor circuit, can do EVEN after obtaining a valid session token: no filling the disk, no
 * eternal token.
 */
object OtaLimits {

    /** Hard ceiling on the size of a downloaded package: 200 MB. */
    const val MAX_APK_BYTES: Long = 209_715_200L

    /** Maximum accepted lifetime for an update session token: one hour, in seconds. */
    const val MAX_JWT_EXPIRY_SECONDS: Long = 3_600L

    /** Has the number of bytes already written passed the package size ceiling? */
    fun exceedsMaxApkBytes(bytesWritten: Long): Boolean {
        return bytesWritten > MAX_APK_BYTES
    }

    /** Clamps the lifetime announced by the server to [MAX_JWT_EXPIRY_SECONDS], and to 0 if not positive. */
    fun clampJwtExpirySeconds(rawExpiresInSeconds: Long): Long {
        if (rawExpiresInSeconds <= 0L) return 0L
        return minOf(rawExpiresInSeconds, MAX_JWT_EXPIRY_SECONDS)
    }
}
