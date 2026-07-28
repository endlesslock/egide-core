package com.endlesslock.egide.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [AttestationDecision].
 *
 * Invariant: the one-time "key attested" flag is set ONLY when hardware attestation actually
 * succeeded. On failure the flag stays down, so a later enrolment can try again.
 */
class AttestationDecisionTest {

    @Test
    fun `successful attestation marks the key as attested`() {
        assertTrue(AttestationDecision.shouldMarkAttested(attestationSucceeded = true))
    }

    @Test
    fun `failed attestation does NOT mark the key`() {
        // The historical bug: the flag was set even here, so attestation was never retried.
        assertFalse(AttestationDecision.shouldMarkAttested(attestationSucceeded = false))
    }
}
