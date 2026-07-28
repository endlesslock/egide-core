package com.endlesslock.egide.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (pure JVM plus the REAL org.json) for [ApiContract].
 *
 * Coverage:
 *  - The protocol number, [ApiContract.PROTOCOL_VERSION] equals 1.
 *  - Every constant for HTTP headers, endpoint paths, parameters and JSON keys: each must equal
 *    EXACTLY the expected string. The server is built as a mirror of this file, so any drift in a
 *    literal would break the contract between them.
 *  - [ApiContract.EnrollRequest.toJson]: the mandatory fields are present, and above all the
 *    semantics of the OPTIONAL `attestation_chain` field:
 *      - a null chain means the key is ABSENT,
 *      - an empty list also means the key is ABSENT,
 *      - a non-empty list means the key is PRESENT, as a JSON array with the right content in the
 *        right order.
 *    Special characters survive; no unexpected key appears.
 *  - [ApiContract.VerifyRequest.toJson]: the three fields are present, and no unexpected key.
 *
 * Everything here is PURE, with no Android context and no keystore.
 */
class ApiContractTest {

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Returns the set of top-level key names of a JSON object. */
    private fun keysOf(json: JSONObject): Set<String> {
        val result = mutableSetOf<String>()
        val it = json.keys()
        while (it.hasNext()) result.add(it.next())
        return result
    }

    // ==================================================================
    // PROTOCOL VERSION
    // ==================================================================

    @Test
    fun `the protocol version is 1`() {
        assertEquals(1, ApiContract.PROTOCOL_VERSION)
    }

    // ==================================================================
    // HTTP HEADERS
    // ==================================================================

    @Test
    fun `the protocol header is X-Proto-Version`() {
        assertEquals("X-Proto-Version", ApiContract.HEADER_PROTOCOL)
    }

    @Test
    fun `the device identifier header is Device-ID`() {
        assertEquals("Device-ID", ApiContract.HEADER_DEVICE_ID)
    }

    @Test
    fun `the authorization header is Authorization`() {
        assertEquals("Authorization", ApiContract.HEADER_AUTHORIZATION)
    }

    // ==================================================================
    // ENDPOINT PATHS
    // ==================================================================

    @Test
    fun `the enrolment path is slash enroll`() {
        assertEquals("/enroll", ApiContract.PATH_ENROLL)
    }

    @Test
    fun `the health path is slash health`() {
        assertEquals("/health", ApiContract.PATH_HEALTH)
    }

    @Test
    fun `the nonce path is slash api slash nonce`() {
        assertEquals("/api/nonce", ApiContract.PATH_NONCE)
    }

    @Test
    fun `the verify path is slash api slash verify`() {
        assertEquals("/api/verify", ApiContract.PATH_VERIFY)
    }

    @Test
    fun `the version path is slash version`() {
        assertEquals("/version", ApiContract.PATH_VERSION)
    }

    @Test
    fun `the download path is slash download`() {
        assertEquals("/download", ApiContract.PATH_DOWNLOAD)
    }

    @Test
    fun `every path starts with a slash`() {
        val paths = listOf(
            ApiContract.PATH_ENROLL,
            ApiContract.PATH_HEALTH,
            ApiContract.PATH_NONCE,
            ApiContract.PATH_VERIFY,
            ApiContract.PATH_VERSION,
            ApiContract.PATH_DOWNLOAD
        )
        paths.forEach { assertTrue("Path without a leading slash: $it", it.startsWith("/")) }
    }

    // ==================================================================
    // QUERY PARAMETERS
    // ==================================================================

    @Test
    fun `the current version parameter is current_version`() {
        assertEquals("current_version", ApiContract.PARAM_CURRENT_VERSION)
    }

    // ==================================================================
    // JSON KEYS
    // ==================================================================

    @Test
    fun `the device_id key is device_id`() {
        assertEquals("device_id", ApiContract.KEY_DEVICE_ID)
    }

    @Test
    fun `the public_key key is public_key`() {
        assertEquals("public_key", ApiContract.KEY_PUBLIC_KEY)
    }

    @Test
    fun `the enroll_token key is enroll_token`() {
        assertEquals("enroll_token", ApiContract.KEY_ENROLL_TOKEN)
    }

    @Test
    fun `the attestation_chain key is attestation_chain`() {
        assertEquals("attestation_chain", ApiContract.KEY_ATTESTATION_CHAIN)
    }

    @Test
    fun `the nonce key is nonce`() {
        assertEquals("nonce", ApiContract.KEY_NONCE)
    }

    @Test
    fun `the signature key is signature`() {
        assertEquals("signature", ApiContract.KEY_SIGNATURE)
    }

    @Test
    fun `the jwt key is jwt`() {
        assertEquals("jwt", ApiContract.KEY_JWT)
    }

    @Test
    fun `the expires_in key is expires_in`() {
        assertEquals("expires_in", ApiContract.KEY_EXPIRES_IN)
    }

    @Test
    fun `the latest_version key is latest_version`() {
        assertEquals("latest_version", ApiContract.KEY_LATEST_VERSION)
    }

    @Test
    fun `the update_needed key is update_needed`() {
        assertEquals("update_needed", ApiContract.KEY_UPDATE_NEEDED)
    }

    @Test
    fun `every JSON key is distinct`() {
        val keys = listOf(
            ApiContract.KEY_DEVICE_ID,
            ApiContract.KEY_PUBLIC_KEY,
            ApiContract.KEY_ENROLL_TOKEN,
            ApiContract.KEY_ATTESTATION_CHAIN,
            ApiContract.KEY_NONCE,
            ApiContract.KEY_SIGNATURE,
            ApiContract.KEY_JWT,
            ApiContract.KEY_EXPIRES_IN,
            ApiContract.KEY_LATEST_VERSION,
            ApiContract.KEY_UPDATE_NEEDED
        )
        assertEquals("Some JSON keys are duplicated", keys.size, keys.toSet().size)
    }

    // ==================================================================
    // EnrollRequest.toJson()
    // ==================================================================

    @Test
    fun `enroll toJson produces valid, parseable JSON`() {
        val json = ApiContract.EnrollRequest("tok", "dev", "pub").toJson()
        // Must not throw: this is a well-formed JSON object.
        val obj = JSONObject(json)
        assertEquals("tok", obj.getString("enroll_token"))
    }

    @Test
    fun `enroll toJson carries the three mandatory fields with the right values`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                enrollToken = "token-XYZ",
                deviceId = "device-42",
                publicKey = "PUBLIC_KEY_BASE64"
            ).toJson()
        )
        assertEquals("token-XYZ", obj.getString("enroll_token"))
        assertEquals("device-42", obj.getString("device_id"))
        assertEquals("PUBLIC_KEY_BASE64", obj.getString("public_key"))
    }

    @Test
    fun `enroll toJson with a null attestation omits the attestation_chain key`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest("t", "d", "p", attestationChain = null).toJson()
        )
        assertFalse(obj.has("attestation_chain"))
        // Exactly the three mandatory fields.
        assertEquals(setOf("enroll_token", "device_id", "public_key"), keysOf(obj))
    }

    @Test
    fun `enroll toJson by default, with no attestation, omits the attestation_chain key`() {
        // The attestationChain parameter defaults to null.
        val obj = JSONObject(ApiContract.EnrollRequest("t", "d", "p").toJson())
        assertFalse(obj.has("attestation_chain"))
        assertEquals(3, obj.length())
    }

    @Test
    fun `enroll toJson with an EMPTY attestation list omits the key`() {
        // Edge case: a list is supplied but is empty, so the emptiness check must exclude it.
        val obj = JSONObject(
            ApiContract.EnrollRequest("t", "d", "p", attestationChain = emptyList()).toJson()
        )
        assertFalse(obj.has("attestation_chain"))
        assertEquals(3, obj.length())
    }

    @Test
    fun `enroll toJson with a single attestation certificate exposes a correct JSON array`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                "t", "d", "p", attestationChain = listOf("CERT_A")
            ).toJson()
        )
        assertTrue(obj.has("attestation_chain"))
        val arr = obj.getJSONArray("attestation_chain")
        assertEquals(1, arr.length())
        assertEquals("CERT_A", arr.getString(0))
    }

    @Test
    fun `enroll toJson preserves both the content AND the order of the attestation chain`() {
        val chain = listOf("leaf", "intermediate", "root")
        val obj = JSONObject(
            ApiContract.EnrollRequest("t", "d", "p", attestationChain = chain).toJson()
        )
        val arr: JSONArray = obj.getJSONArray("attestation_chain")
        assertEquals(3, arr.length())
        // The order carries meaning, from leaf to root, so it must be preserved as is.
        assertEquals("leaf", arr.getString(0))
        assertEquals("intermediate", arr.getString(1))
        assertEquals("root", arr.getString(2))
    }

    @Test
    fun `enroll toJson with an attestation carries exactly four keys`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                "t", "d", "p", attestationChain = listOf("c1", "c2")
            ).toJson()
        )
        assertEquals(
            setOf("enroll_token", "device_id", "public_key", "attestation_chain"),
            keysOf(obj)
        )
    }

    @Test
    fun `enroll toJson introduces no unexpected key when there is no attestation`() {
        val obj = JSONObject(ApiContract.EnrollRequest("t", "d", "p").toJson())
        val allowed = setOf("enroll_token", "device_id", "public_key", "attestation_chain")
        keysOf(obj).forEach { assertTrue("Unexpected key: $it", it in allowed) }
    }

    @Test
    fun `enroll toJson preserves special characters in the values`() {
        // Quotes, backslash, braces, newline, unicode: all must survive a round trip.
        val specialToken = "a\"b\\c{}/e\n\t€"
        val specialDevice = "id-special-€"
        val specialPub = "+/=base64==e"
        val obj = JSONObject(
            ApiContract.EnrollRequest(specialToken, specialDevice, specialPub).toJson()
        )
        assertEquals(specialToken, obj.getString("enroll_token"))
        assertEquals(specialDevice, obj.getString("device_id"))
        assertEquals(specialPub, obj.getString("public_key"))
    }

    @Test
    fun `enroll toJson preserves special characters inside the attestation chain`() {
        val specialCert = "MIIB\"\\/\n+=="
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                "t", "d", "p", attestationChain = listOf(specialCert, "plain")
            ).toJson()
        )
        val arr = obj.getJSONArray("attestation_chain")
        assertEquals(specialCert, arr.getString(0))
        assertEquals("plain", arr.getString(1))
    }

    @Test
    fun `enroll toJson accepts empty values without breaking`() {
        // Empty strings are valid; only an absent or empty LIST triggers the omission.
        val obj = JSONObject(ApiContract.EnrollRequest("", "", "").toJson())
        assertEquals("", obj.getString("enroll_token"))
        assertEquals("", obj.getString("device_id"))
        assertEquals("", obj.getString("public_key"))
        assertFalse(obj.has("attestation_chain"))
    }

    @Test
    fun `enroll toJson really uses the contract key constants`() {
        // Guarantees the serialisation references the KEY_ constants, with no divergent literal.
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                "t", "d", "p", attestationChain = listOf("c")
            ).toJson()
        )
        assertTrue(obj.has(ApiContract.KEY_ENROLL_TOKEN))
        assertTrue(obj.has(ApiContract.KEY_DEVICE_ID))
        assertTrue(obj.has(ApiContract.KEY_PUBLIC_KEY))
        assertTrue(obj.has(ApiContract.KEY_ATTESTATION_CHAIN))
    }

    // ==================================================================
    // VerifyRequest.toJson()
    // ==================================================================

    @Test
    fun `verify toJson produces valid, parseable JSON`() {
        val json = ApiContract.VerifyRequest("dev", "n0nce", "sig").toJson()
        val obj = JSONObject(json)
        assertEquals("n0nce", obj.getString("nonce"))
    }

    @Test
    fun `verify toJson carries device_id, nonce and signature with the right values`() {
        val obj = JSONObject(
            ApiContract.VerifyRequest(
                deviceId = "device-7",
                nonce = "NONCE-123",
                signature = "SIG-DER-BASE64"
            ).toJson()
        )
        assertEquals("device-7", obj.getString("device_id"))
        assertEquals("NONCE-123", obj.getString("nonce"))
        assertEquals("SIG-DER-BASE64", obj.getString("signature"))
    }

    @Test
    fun `verify toJson carries exactly three keys`() {
        val obj = JSONObject(ApiContract.VerifyRequest("d", "n", "s").toJson())
        assertEquals(3, obj.length())
        assertEquals(setOf("device_id", "nonce", "signature"), keysOf(obj))
    }

    @Test
    fun `verify toJson introduces no unexpected key`() {
        val obj = JSONObject(ApiContract.VerifyRequest("d", "n", "s").toJson())
        val allowed = setOf("device_id", "nonce", "signature")
        keysOf(obj).forEach { assertTrue("Unexpected key: $it", it in allowed) }
    }

    @Test
    fun `verify toJson preserves special characters`() {
        val specialSig = "MEUCIQD\"\\/+=\n=="
        val specialNonce = "nonce-€-{}"
        val obj = JSONObject(
            ApiContract.VerifyRequest("d", specialNonce, specialSig).toJson()
        )
        assertEquals(specialNonce, obj.getString("nonce"))
        assertEquals(specialSig, obj.getString("signature"))
    }

    @Test
    fun `verify toJson accepts empty values`() {
        val obj = JSONObject(ApiContract.VerifyRequest("", "", "").toJson())
        assertEquals("", obj.getString("device_id"))
        assertEquals("", obj.getString("nonce"))
        assertEquals("", obj.getString("signature"))
        assertEquals(3, obj.length())
    }

    @Test
    fun `verify toJson really uses the contract key constants`() {
        val obj = JSONObject(ApiContract.VerifyRequest("d", "n", "s").toJson())
        assertTrue(obj.has(ApiContract.KEY_DEVICE_ID))
        assertTrue(obj.has(ApiContract.KEY_NONCE))
        assertTrue(obj.has(ApiContract.KEY_SIGNATURE))
    }
}
