package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [SmsTriggerLogic].
 *
 * Key point of the fix: a `null` body, which happens when the cursor's body column is empty, must
 * no longer throw. Throwing aborted the walk over every following message. It now simply returns
 * `false`.
 */
class SmsTriggerLogicTest {

    private val hash = "WIPE-ME-42"

    // ---------------------------------------------------------------------------------------------
    // Nominal matching
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a body equal to the secret triggers`() {
        assertTrue(SmsTriggerLogic.bodyContainsHash(hash, hash))
    }

    @Test
    fun `the secret present as a substring triggers`() {
        assertTrue(SmsTriggerLogic.bodyContainsHash("hello $hash goodbye", hash))
    }

    @Test
    fun `the comparison is case-insensitive`() {
        assertTrue(SmsTriggerLogic.bodyContainsHash("wipe-me-42", hash))
        assertTrue(SmsTriggerLogic.bodyContainsHash(hash.uppercase(), hash.lowercase()))
    }

    // ---------------------------------------------------------------------------------------------
    // The fix: a null or empty body yields false, and never throws.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a null body does NOT trigger and does NOT throw`() {
        assertFalse(SmsTriggerLogic.bodyContainsHash(null, hash))
    }

    @Test
    fun `an empty body does NOT trigger`() {
        assertFalse(SmsTriggerLogic.bodyContainsHash("", hash))
    }

    // ---------------------------------------------------------------------------------------------
    // Absent or empty secret: never a trigger.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `a null secret never triggers`() {
        assertFalse(SmsTriggerLogic.bodyContainsHash("some message", null))
    }

    @Test
    fun `an empty secret never triggers`() {
        // Guard rail: otherwise an empty secret would be "contained" in every message.
        assertFalse(SmsTriggerLogic.bodyContainsHash("some message", ""))
    }

    @Test
    fun `a message without the secret does NOT trigger`() {
        assertFalse(SmsTriggerLogic.bodyContainsHash("a perfectly ordinary message", hash))
    }

    // ---------------------------------------------------------------------------------------------
    // Replay boundary clamped to the local clock: a future instant is never written.
    // ---------------------------------------------------------------------------------------------

    private val now = 1_800_000_000_000L // reference local clock, in milliseconds

    @Test
    fun `boundary equals the carrier timestamp when it lies in the past`() {
        val smsc = now - 60_000L // received a minute ago according to the carrier
        assertEquals(smsc, SmsTriggerLogic.nouvelleFrontiere(listOf(smsc), now))
    }

    @Test
    fun `a carrier timestamp running ahead does NOT write a future instant`() {
        // Heart of the fix: carrier clock skew puts the timestamp six hours in the future. Without
        // the clamp, that future boundary would blind the real erase command received while the
        // phone was powered off.
        val smscFutur = now + 6L * 3600_000L
        assertEquals(now, SmsTriggerLogic.nouvelleFrontiere(listOf(smscFutur), now))
    }

    @Test
    fun `a multipart message takes the maximum of the clamped parts`() {
        val past = now - 120_000L
        val future = now + 3600_000L
        // max(min(past, now) = past, min(future, now) = now) = now
        assertEquals(now, SmsTriggerLogic.nouvelleFrontiere(listOf(past, future), now))
    }

    @Test
    fun `absent or non-positive timestamps are ignored, falling back to now`() {
        assertEquals(now, SmsTriggerLogic.nouvelleFrontiere(listOf(0L, -5L), now))
        assertEquals(now, SmsTriggerLogic.nouvelleFrontiere(emptyList(), now))
    }
}
