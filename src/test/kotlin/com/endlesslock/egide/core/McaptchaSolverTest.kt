package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST tests for the mCaptcha PoW solver ([McaptchaSolver]).
 *
 * CROSS-IMPLEMENTATION EQUALITY: the vectors (digest, nonce, result, token) are produced by the
 * server's REFERENCE implementation and frozen here. Any drift in the Kotlin solver — digest
 * formula, bit counting, token format — breaks one of these tests. That is the "server first"
 * guard: the solver MUST reproduce the computation the server will revalidate, otherwise every
 * `captcha_token` is rejected (400 captcha_invalide).
 */
class McaptchaSolverTest {

    // Frozen vectors (produced by the reference server solver).
    private val chaine = "EgideTest01"
    private val salt = "sel-abcdef"
    private val difficulty = 12
    private val nonceAttendu = 11291L
    private val resultAttendu = "000b8855a8dbdaaca9e34976013f3976f6710eb8683946c0f4b84e16bea486c6"
    private val tokenAttendu =
        "eyJjaGFsbGVuZ2VfaWQiOiJjaGFsLTAwMSIsIm5vbmNlIjoxMTI5MSwicmVzdWx0IjoiMDAwYjg4NTVhOGRiZGFhY2E5ZTM0OTc2MDEzZjM5NzZmNjcxMGViODY4Mzk0NmMwZjRiODRlMTZiZWE0ODZjNiJ9"

    @Test
    fun `empreinte reproduces the server SHA-256(string+salt+nonce)`() {
        // Minimal independent vector: SHA-256("a"+"b"+"0").
        assertEquals(
            "498867a250a343479dddb42d5e86fcb243d037162d10d31e266ca5d3525015ee",
            McaptchaSolver.empreinte("a", "b", 0L).joinToString("") { "%02x".format(it) },
        )
    }

    @Test
    fun `zerosDeTete counts the leading zero bits`() {
        // 0x00 0x0b ... -> 8 (null byte) + 4 (0x0b = 0000_1011, bit_length 4 -> 4 zeros) = 12.
        val emp = ByteArray(32).also { it[1] = 0x0b }
        assertEquals(12, McaptchaSolver.zerosDeTete(emp))
        // Reference digest: exactly 12 leading bits.
        assertEquals(12, McaptchaSolver.zerosDeTete(McaptchaSolver.empreinte(chaine, salt, nonceAttendu)))
        // A full non-null leading byte (0xFF) -> 0.
        assertEquals(0, McaptchaSolver.zerosDeTete(ByteArray(32).also { it[0] = 0xFF.toByte() }))
    }

    @Test
    fun `resoudre finds the minimal nonce of the golden vector`() {
        val defi = McaptchaSolver.Defi("chal-001", chaine, salt, difficulty)
        val sol = McaptchaSolver.resoudre(defi)
        assertNotNull(sol)
        assertEquals(nonceAttendu, sol!!.nonce)
        assertEquals(resultAttendu, sol.resultHex)
        // The found digest carries >= difficulty leading bits.
        assertTrue(McaptchaSolver.zerosDeTete(McaptchaSolver.empreinte(chaine, salt, sol.nonce)) >= difficulty)
    }

    @Test
    fun `encoderToken produces the expected base64url without padding`() {
        val sol = McaptchaSolver.Solution("chal-001", nonceAttendu, resultAttendu)
        assertEquals(tokenAttendu, McaptchaSolver.encoderToken(sol))
    }

    @Test
    fun `resoudreEtEncoder chains solving and encoding`() {
        val defi = McaptchaSolver.Defi("chal-001", chaine, salt, difficulty)
        assertEquals(tokenAttendu, McaptchaSolver.resoudreEtEncoder(defi))
    }

    @Test
    fun `an out-of-bounds difficulty returns null without looping`() {
        assertNull(McaptchaSolver.resoudre(McaptchaSolver.Defi("c", "s", "salt", 0)))
        assertNull(McaptchaSolver.resoudre(McaptchaSolver.Defi("c", "s", "salt", McaptchaSolver.DIFFICULTE_MAX + 1)))
    }

    @Test
    fun `a very easy difficulty (1 bit) is solved`() {
        val sol = McaptchaSolver.resoudre(McaptchaSolver.Defi("c", "x", "y", 1))
        assertNotNull(sol)
        assertTrue(McaptchaSolver.zerosDeTete(McaptchaSolver.empreinte("x", "y", sol!!.nonce)) >= 1)
    }
}
