package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST tests for [WipeSource] — the honest, complete list of what the paid tier gates and what it
 * never gates.
 *
 * This pins the freemium boundary so a reader does not have to take the README's word for it: the
 * offline anti-coercion net (prolonged lock, failed passcode) and the vital anti-theft sources
 * (tamper, admin removal, panic) are FREE for life and NEVER gated; only the REMOTE / fast erase
 * (network isolation, onion, SMS, dead-man) is PREMIUM. There is no fourth, hidden category.
 */
class WipeSourceTest {

    private val expectedPremium = setOf(
        WipeSource.CONNECTIVITY, WipeSource.ONION, WipeSource.SMS, WipeSource.DEAD_MAN
    )
    private val expectedFree = setOf(
        WipeSource.LOCK_TIMEOUT, WipeSource.FAILED_CODE, WipeSource.TAMPER,
        WipeSource.ADMIN_DISABLE, WipeSource.PANIC
    )

    @Test
    fun `exactly the remote sources are premium`() {
        val premium = WipeSource.values().filter { it.premium }.toSet()
        assertEquals(expectedPremium, premium)
    }

    @Test
    fun `exactly the offline and anti-theft sources are free`() {
        val free = WipeSource.values().filter { !it.premium }.toSet()
        assertEquals(expectedFree, free)
    }

    @Test
    fun `the premium and free sets partition every source`() {
        // No source is left out, none is in both: the classification is total and exclusive.
        assertEquals(WipeSource.values().size, expectedPremium.size + expectedFree.size)
        assertTrue((expectedPremium intersect expectedFree).isEmpty())
    }

    @Test
    fun `the prolonged-lock net is free — a lost phone always ends up erasing itself`() {
        assertFalse(WipeSource.LOCK_TIMEOUT.premium)
    }

    @Test
    fun `the anti-coercion floor is free`() {
        assertFalse(WipeSource.FAILED_CODE.premium)
        assertFalse(WipeSource.PANIC.premium)
    }

    @Test
    fun `the vital anti-theft guards are free`() {
        assertFalse(WipeSource.TAMPER.premium)
        assertFalse(WipeSource.ADMIN_DISABLE.premium)
    }
}
