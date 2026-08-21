package com.endlesslock.egide.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST tests for [LicenceDecision] (premium activation decision + suppress-only gate, PURE).
 *
 * Covers the tricky cases:
 *  - `/version` parsing: an absent credit key = UNKNOWN (distinct from an explicit `<= 0`);
 *  - the SUSPENDED verdict is computed on the RAW value, BEFORE clamping (a `-1` is not a `0`);
 *  - the GATE ("free at the start"): a PREMIUM source proceeds ONLY if the status is ACTIVE
 *    (validated); UNKNOWN (never reached the server = FREE) as much as SUSPENDED INHIBIT it;
 *  - the free / anti-theft sources (LOCK_TIMEOUT, FAILED_CODE, TAMPER, ADMIN_DISABLE, PANIC) are
 *    NEVER inhibited.
 */
class LicenceDecisionTest {

    private fun json(vararg pairs: Pair<String, Any?>): JSONObject {
        val o = JSONObject()
        for ((k, v) in pairs) if (v == null) o.put(k, JSONObject.NULL) else o.put(k, v)
        return o
    }

    // ---------------- parseVersionCredit ----------------

    @Test
    fun `an absent seconds_remaining key means UNKNOWN (fail-open)`() {
        val c = LicenceDecision.parseVersionCredit(
            json(ApiContract.KEY_DEVICE_UID to "ab", ApiContract.KEY_SERVER_TIME to 123L)
        )
        assertEquals(LicenceDecision.Verdict.INCONNU, c.verdict)
        assertEquals(LicenceDecision.CREDIT_INCONNU, c.secondsRemainingBrut)
        assertEquals("ab", c.deviceUid)
        assertEquals(123L, c.serverTimeMs)
    }

    @Test
    fun `seconds_remaining at zero means SUSPENDED`() {
        val c = LicenceDecision.parseVersionCredit(json(ApiContract.KEY_SECONDS_REMAINING to 0L))
        assertEquals(LicenceDecision.Verdict.SUSPENDU_EXPLICITE, c.verdict)
        assertEquals(0L, c.secondsRemainingBrut)
    }

    @Test
    fun `a negative seconds_remaining stays raw and means SUSPENDED`() {
        val c = LicenceDecision.parseVersionCredit(json(ApiContract.KEY_SECONDS_REMAINING to -1L))
        assertEquals(LicenceDecision.Verdict.SUSPENDU_EXPLICITE, c.verdict)
        // The RAW value is preserved (not clamped to 0): essential to tell it apart from UNKNOWN.
        assertEquals(-1L, c.secondsRemainingBrut)
    }

    @Test
    fun `a positive seconds_remaining means ACTIVE`() {
        val c = LicenceDecision.parseVersionCredit(
            json(ApiContract.KEY_SECONDS_REMAINING to 3600L, ApiContract.KEY_ACTIVE_UNTIL to 999L)
        )
        assertEquals(LicenceDecision.Verdict.ACTIF_EXPLICITE, c.verdict)
        assertEquals(3600L, c.secondsRemainingBrut)
        assertEquals(999L, c.activeUntilSec)
    }

    @Test
    fun `unlimited means ACTIVE even with no seconds_remaining`() {
        val c = LicenceDecision.parseVersionCredit(json(ApiContract.KEY_UNLIMITED to true))
        assertEquals(LicenceDecision.Verdict.ACTIF_EXPLICITE, c.verdict)
        assertTrue(c.unlimited)
    }

    @Test
    fun `a null seconds_remaining (never sent by the server) is treated as absent`() {
        val c = LicenceDecision.parseVersionCredit(json(ApiContract.KEY_SECONDS_REMAINING to null))
        assertEquals(LicenceDecision.Verdict.INCONNU, c.verdict)
        assertEquals(LicenceDecision.CREDIT_INCONNU, c.secondsRemainingBrut)
    }

    @Test
    fun `an empty device_uid is treated as absent`() {
        val c = LicenceDecision.parseVersionCredit(json(ApiContract.KEY_DEVICE_UID to ""))
        assertNull(c.deviceUid)
    }

    // ---------------- clampCreditSeconds ----------------

    @Test
    fun `clamp bounds to zero but only for display`() {
        assertEquals(0L, LicenceDecision.clampCreditSeconds(-5L))
        assertEquals(0L, LicenceDecision.clampCreditSeconds(0L))
        assertEquals(42L, LicenceDecision.clampCreditSeconds(42L))
    }

    // ---------------- isActive ----------------

    @Test
    fun `isActive unlimited is always true`() {
        assertTrue(
            LicenceDecision.isActive(
                unlimited = true, forgeJoignable = true,
                LicenceDecision.Verdict.SUSPENDU_EXPLICITE, dernierEtatConnuActif = false
            )
        )
    }

    @Test
    fun `isActive offline keeps the last known state`() {
        assertTrue(LicenceDecision.isActive(false, forgeJoignable = false, LicenceDecision.Verdict.INCONNU, dernierEtatConnuActif = true))
        assertFalse(LicenceDecision.isActive(false, forgeJoignable = false, LicenceDecision.Verdict.INCONNU, dernierEtatConnuActif = false))
    }

    @Test
    fun `isActive online follows the verdict, UNKNOWN stays ACTIVE`() {
        assertFalse(LicenceDecision.isActive(false, true, LicenceDecision.Verdict.SUSPENDU_EXPLICITE, dernierEtatConnuActif = true))
        assertTrue(LicenceDecision.isActive(false, true, LicenceDecision.Verdict.ACTIF_EXPLICITE, dernierEtatConnuActif = false))
        assertTrue(LicenceDecision.isActive(false, true, LicenceDecision.Verdict.INCONNU, dernierEtatConnuActif = false))
    }

    // ---------------- statutPourGate ----------------

    @Test
    fun `statutPourGate lifetime = ACTIVE`() {
        assertEquals(LicenceDecision.StatutPremium.ACTIF, LicenceDecision.statutPourGate(lifetime = true, LicenceDecision.CREDIT_INCONNU))
    }

    @Test
    fun `statutPourGate never received = UNKNOWN`() {
        assertEquals(LicenceDecision.StatutPremium.INCONNU, LicenceDecision.statutPourGate(false, LicenceDecision.CREDIT_INCONNU))
    }

    @Test
    fun `statutPourGate persisted at zero or below = SUSPENDED`() {
        assertEquals(LicenceDecision.StatutPremium.SUSPENDU, LicenceDecision.statutPourGate(false, 0L))
        assertEquals(LicenceDecision.StatutPremium.SUSPENDU, LicenceDecision.statutPourGate(false, -10L))
    }

    @Test
    fun `statutPourGate persisted positive = ACTIVE`() {
        assertEquals(LicenceDecision.StatutPremium.ACTIF, LicenceDecision.statutPourGate(false, 1L))
    }

    // ---------------- estInhibe (the GATE) ----------------

    private val sourcesPremium = listOf(WipeSource.CONNECTIVITY, WipeSource.ONION, WipeSource.SMS, WipeSource.DEAD_MAN)
    private val sourcesGratuites = listOf(WipeSource.LOCK_TIMEOUT, WipeSource.FAILED_CODE, WipeSource.TAMPER, WipeSource.ADMIN_DISABLE, WipeSource.PANIC)

    @Test
    fun `the gate inhibits the premium sources when SUSPENDED`() {
        for (s in sourcesPremium) {
            assertTrue("$s must be inhibited when SUSPENDED", LicenceDecision.estInhibe(s, LicenceDecision.StatutPremium.SUSPENDU))
        }
    }

    // "Free at the start": a device never validated (UNKNOWN) is FREE, its premium sources are INERT.
    @Test
    fun `the gate inhibits the premium sources when UNKNOWN (never validated = FREE)`() {
        for (s in sourcesPremium) {
            assertTrue("$s must be inhibited when UNKNOWN (FREE)", LicenceDecision.estInhibe(s, LicenceDecision.StatutPremium.INCONNU))
        }
    }

    @Test
    fun `the gate lets the premium sources proceed ONLY if ACTIVE`() {
        for (s in sourcesPremium) {
            assertFalse("$s must proceed when ACTIVE (premium validated)", LicenceDecision.estInhibe(s, LicenceDecision.StatutPremium.ACTIF))
        }
    }

    @Test
    fun `the gate NEVER cuts the free or anti-theft sources, whatever the status`() {
        for (statut in LicenceDecision.StatutPremium.values()) {
            for (s in sourcesGratuites) {
                assertFalse("$s must never be inhibited (status=$statut)", LicenceDecision.estInhibe(s, statut))
            }
        }
    }

    // ---------------- premiumValide (UI mirror of the gate) ----------------

    @Test
    fun `premiumValide is true ONLY if lifetime or persisted seconds is positive`() {
        assertTrue(LicenceDecision.premiumValide(lifetime = true, LicenceDecision.CREDIT_INCONNU))
        assertTrue(LicenceDecision.premiumValide(false, 1L))
        // FREE (never reached) and SUSPENDED (expired): not validated.
        assertFalse(LicenceDecision.premiumValide(false, LicenceDecision.CREDIT_INCONNU))
        assertFalse(LicenceDecision.premiumValide(false, 0L))
        assertFalse(LicenceDecision.premiumValide(false, -5L))
    }
}
