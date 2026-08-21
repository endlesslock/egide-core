package com.endlesslock.egide.core

import java.security.MessageDigest
import java.util.Base64

/*
 * ============================================================================
 * File: McaptchaSolver.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   Kotlin solver for the self-hosted PROOF-OF-WORK captcha (mCaptcha pattern) of the portal. A
 *   self-hosted PoW rather than reCAPTCHA/hCaptcha: it works over Tor, does not tie the IP, and does
 *   not track the carrier. Publishing this is how a reader can check that the captcha only burns the
 *   device's own CPU and sends nothing about the user.
 *
 * NORMATIVE PROTOCOL (the app mirrors what the server fixes):
 *   1. GET /captcha -> {challenge_id, string, salt, difficulty}.
 *   2. Find an integer `nonce >= 0` such that
 *          digest = SHA-256( (string + salt + str(nonce)).encode("utf-8") )
 *      has at least `difficulty` LEADING ZERO BITS.
 *      ⚠️ "difficulty" is counted in BITS, not hex characters: 4 bits = 1 hex zero.
 *   3. result = the found digest, in LOWERCASE hex (64 characters).
 *   4. captcha_token = base64url WITHOUT padding of the COMPACT JSON
 *          {"challenge_id":"...","nonce":<integer>,"result":"<hex>"}
 *      (key order copied from the server for a byte-identical token; the server re-decodes the JSON
 *      anyway, so order is not required on the acceptance side).
 *   5. The server REVALIDATES by recomputing (it trusts neither the `result` nor the `nonce`), and
 *      enforces single use and a TTL.
 *
 * ⚠️ SEQUENCING: the server CLAIMS the challenge (single use) BEFORE recomputing — a token with a
 *   wrong PoW still consumes its challenge. One challenge = one attempt. The app must therefore SET
 *   the password only with a FRESHLY solved token, never a reused one.
 *
 * PURE (no Android dependency): SHA-256 via [MessageDigest], base64url via [Base64]. Host-testable,
 * pinned by a shared golden vector.
 * ============================================================================
 */
object McaptchaSolver {

    /** Max difficulty the solver accepts (the server also caps at 32 bits). Beyond this: refuse. */
    const val DIFFICULTE_MAX = 32

    /**
     * Iteration ceiling before giving up (a hostile challenge / too high a difficulty). Aligned with
     * the reference server solver's ceiling (`1 << 26`). At difficulty 12 (typical), the solution
     * falls in a few thousand tries; this ceiling is reached only abnormally.
     */
    const val PLAFOND_NONCE = 1 shl 26

    /** A challenge as delivered by `GET /captcha`. */
    data class Defi(
        val challengeId: String,
        val string: String,
        val salt: String,
        val difficulty: Int,
    )

    /** A found solution (before encoding into a token). */
    data class Solution(
        val challengeId: String,
        val nonce: Long,
        val resultHex: String,
    )

    /** SHA-256( string + salt + str(nonce) ), UTF-8 bytes. Frozen protocol formula (step 2). */
    fun empreinte(chaine: String, salt: String, nonce: Long): ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest("$chaine$salt$nonce".toByteArray(Charsets.UTF_8))
    }

    /**
     * Number of LEADING ZERO BITS in the digest. We count the null bytes (8 bits each), then, at the
     * first non-null byte, `8 - bit_length`.
     */
    fun zerosDeTete(empreinte: ByteArray): Int {
        var total = 0
        for (octet in empreinte) {
            val v = octet.toInt() and 0xFF
            if (v == 0) {
                total += 8
                continue
            }
            // bit_length(v) for v in [1,255] = 32 - numberOfLeadingZeros(v).
            total += 8 - (32 - Integer.numberOfLeadingZeros(v))
            break
        }
        return total
    }

    /**
     * Solves the challenge: iterates `nonce` from 0 until the digest carries enough zero bits.
     *
     * @return the [Solution], or `null` if the difficulty is out of bounds ([1, DIFFICULTE_MAX]) or if
     *   no solution is found under [PLAFOND_NONCE]. A `null` must be handled by the caller as "captcha
     *   not solved" (do not set the password), not as a silent success.
     *
     * Performance note: on mobile, this computation is intentionally bounded and must run OFF the main
     * thread (an IO/Default dispatcher) — the caller is responsible for that.
     */
    fun resoudre(defi: Defi): Solution? {
        if (defi.difficulty < 1 || defi.difficulty > DIFFICULTE_MAX) return null
        var nonce = 0L
        while (nonce < PLAFOND_NONCE) {
            val emp = empreinte(defi.string, defi.salt, nonce)
            if (zerosDeTete(emp) >= defi.difficulty) {
                return Solution(defi.challengeId, nonce, versHex(emp))
            }
            nonce++
        }
        return null
    }

    /** Encodes the `captcha_token`: base64url WITHOUT padding of the compact JSON (step 4). */
    fun encoderToken(solution: Solution): String {
        val json = buildString {
            append("{\"")
            append(PortailContract.KEY_CHALLENGE_ID)
            append("\":\"")
            append(echapperJson(solution.challengeId))
            append("\",\"")
            append(PortailContract.KEY_CAPTCHA_NONCE)
            append("\":")
            append(solution.nonce)
            append(",\"")
            append(PortailContract.KEY_CAPTCHA_RESULT)
            append("\":\"")
            append(solution.resultHex)
            append("\"}")
        }
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.toByteArray(Charsets.UTF_8))
    }

    /** Shortcut: solve then encode. `null` if the challenge could not be solved. */
    fun resoudreEtEncoder(defi: Defi): String? = resoudre(defi)?.let { encoderToken(it) }

    /** Digest -> LOWERCASE hex (64 characters). */
    private fun versHex(octets: ByteArray): String {
        val sb = StringBuilder(octets.size * 2)
        for (o in octets) {
            val v = o.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()

    /**
     * Minimal JSON escaping of the `challenge_id`. Server challenges (`secrets.token_urlsafe`) are
     * [A-Za-z0-9_-], so nothing needs escaping; the escaping is a defensive belt in case the format
     * ever changes, so a malformed JSON token can never be produced.
     */
    private fun echapperJson(s: String): String {
        val sb = StringBuilder(s.length)
        for (c in s) {
            when (c) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
            }
        }
        return sb.toString()
    }
}
