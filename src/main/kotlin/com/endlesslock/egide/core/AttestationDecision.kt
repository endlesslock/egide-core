package com.endlesslock.egide.core

/**
 * Decision logic for marking a device key as hardware-attested. Deliberately **pure**: no Android
 * dependency, so it is unit-testable on a host JVM (see `AttestationDecisionTest`).
 *
 * At enrolment, the one-time "key attested" flag used to be set **even when hardware attestation
 * had FAILED** and the code had fallen back to a non-attested key for older hardware. The flag
 * then read as true although no attestation chain existed anywhere, and attestation was **never
 * retried**, not even after a new token or challenge, nor after a change in hardware policy. A
 * server requiring hardware proof saw a device permanently stuck in "attested, but no chain".
 *
 * The rule: remember "key attested", which freezes the public key and stops regenerating it on
 * every retry, **only when attestation actually succeeded**. On failure the flag stays `false`, so
 * a later enrolment tries again.
 */
object AttestationDecision {

    /**
     * Should the persistent "key attested" flag be set?
     *
     * @param attestationSucceeded did the attested-key call succeed, that is was a hardware
     *        attestation chain actually produced?
     * @return `true` only when attestation succeeded. `false` otherwise, leaving the flag down so
     *         the next enrolment can try again.
     */
    fun shouldMarkAttested(attestationSucceeded: Boolean): Boolean = attestationSucceeded
}
