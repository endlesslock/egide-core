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
 *  - [ApiContract.EnrollRequest.toJson]: the mandatory fields are present, and the semantics of the
 *    OPTIONAL `attestation_chain` and `esid` fields:
 *      - a null (or empty) chain means the key is ABSENT,
 *      - a non-empty list means the key is PRESENT, as a JSON array with the right content in the
 *        right order,
 *      - a null or blank `esid` means the key is ABSENT, a non-blank one means it is PRESENT.
 *    There is NO enrolment token any more: the body must never carry an `enroll_token`.
 *  - [ApiContract.VerifyRequest.toJson]: the three fields are present, and no unexpected key.
 *  - The prepaid-credit and account keys added with the licensing/portal work.
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
    fun `the account path is slash api slash account`() {
        assertEquals("/api/account", ApiContract.PATH_ACCOUNT)
    }

    @Test
    fun `every path starts with a slash`() {
        val paths = listOf(
            ApiContract.PATH_ENROLL,
            ApiContract.PATH_HEALTH,
            ApiContract.PATH_NONCE,
            ApiContract.PATH_VERIFY,
            ApiContract.PATH_VERSION,
            ApiContract.PATH_DOWNLOAD,
            ApiContract.PATH_ACCOUNT
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

    @Test
    fun `the channel parameter is channel`() {
        assertEquals("channel", ApiContract.PARAM_CHANNEL)
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
    fun `the entitlements key is entitlements`() {
        assertEquals("entitlements", ApiContract.KEY_ENTITLEMENTS)
    }

    @Test
    fun `the channel key is channel`() {
        assertEquals("channel", ApiContract.KEY_CHANNEL)
    }

    // -- Prepaid credit and account keys (licensing) --

    @Test
    fun `the seconds_remaining key is seconds_remaining`() {
        assertEquals("seconds_remaining", ApiContract.KEY_SECONDS_REMAINING)
    }

    @Test
    fun `the active_until key is active_until`() {
        assertEquals("active_until", ApiContract.KEY_ACTIVE_UNTIL)
    }

    @Test
    fun `the server_time key is server_time`() {
        assertEquals("server_time", ApiContract.KEY_SERVER_TIME)
    }

    @Test
    fun `the unlimited key is unlimited`() {
        assertEquals("unlimited", ApiContract.KEY_UNLIMITED)
    }

    @Test
    fun `the device_uid key is device_uid`() {
        assertEquals("device_uid", ApiContract.KEY_DEVICE_UID)
    }

    @Test
    fun `the esid key is esid`() {
        assertEquals("esid", ApiContract.KEY_ESID)
    }

    @Test
    fun `the bootstrap_token key is bootstrap_token`() {
        assertEquals("bootstrap_token", ApiContract.KEY_BOOTSTRAP_TOKEN)
    }

    @Test
    fun `the device_uid_absent error code is device_uid_absent`() {
        assertEquals("device_uid_absent", ApiContract.ERREUR_DEVICE_UID_ABSENT)
    }

    @Test
    fun `the error key is erreur`() {
        assertEquals("erreur", ApiContract.KEY_ERREUR)
    }

    @Test
    fun `there is no enrolment token key on the contract`() {
        // The enrolment token was removed on 2026-08-17: enrolment is authorised by hardware
        // attestation plus the esid, not a token. This guards against it being reintroduced.
        val fields = ApiContract::class.java.declaredFields.map { it.name }
        assertFalse("An enrol-token constant reappeared", fields.any { it.contains("ENROLL_TOKEN") })
        assertFalse("A redeem path constant reappeared", fields.any { it == "PATH_REDEEM" })
    }

    @Test
    fun `every JSON key is distinct`() {
        val keys = listOf(
            ApiContract.KEY_DEVICE_ID,
            ApiContract.KEY_PUBLIC_KEY,
            ApiContract.KEY_ATTESTATION_CHAIN,
            ApiContract.KEY_NONCE,
            ApiContract.KEY_SIGNATURE,
            ApiContract.KEY_JWT,
            ApiContract.KEY_EXPIRES_IN,
            ApiContract.KEY_LATEST_VERSION,
            ApiContract.KEY_UPDATE_NEEDED,
            ApiContract.KEY_ENTITLEMENTS,
            ApiContract.KEY_CHANNEL,
            ApiContract.KEY_SECONDS_REMAINING,
            ApiContract.KEY_ACTIVE_UNTIL,
            ApiContract.KEY_SERVER_TIME,
            ApiContract.KEY_UNLIMITED,
            ApiContract.KEY_DEVICE_UID,
            ApiContract.KEY_ESID,
            ApiContract.KEY_BOOTSTRAP_TOKEN,
            ApiContract.KEY_ERREUR
        )
        assertEquals("Some JSON keys are duplicated", keys.size, keys.toSet().size)
    }

    // ==================================================================
    // EnrollRequest.toJson()
    // ==================================================================

    @Test
    fun `enroll toJson produces valid, parseable JSON`() {
        val json = ApiContract.EnrollRequest("dev", "pub").toJson()
        // Must not throw: this is a well-formed JSON object.
        val obj = JSONObject(json)
        assertEquals("dev", obj.getString("device_id"))
    }

    @Test
    fun `enroll toJson carries the two mandatory fields with the right values`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                deviceId = "device-42",
                publicKey = "PUBLIC_KEY_BASE64"
            ).toJson()
        )
        assertEquals("device-42", obj.getString("device_id"))
        assertEquals("PUBLIC_KEY_BASE64", obj.getString("public_key"))
    }

    @Test
    fun `enroll toJson never carries an enrolment token`() {
        // There is no token any more; the body must not resurrect one.
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = listOf("c"), esid = "e").toJson()
        )
        assertFalse(obj.has("enroll_token"))
        assertFalse(obj.has("token"))
    }

    @Test
    fun `enroll toJson with no optional field carries exactly the two mandatory keys`() {
        val obj = JSONObject(ApiContract.EnrollRequest("d", "p").toJson())
        assertFalse(obj.has("attestation_chain"))
        assertFalse(obj.has("esid"))
        assertEquals(setOf("device_id", "public_key"), keysOf(obj))
    }

    @Test
    fun `enroll toJson with an EMPTY attestation list omits the key`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = emptyList()).toJson()
        )
        assertFalse(obj.has("attestation_chain"))
        assertEquals(2, obj.length())
    }

    @Test
    fun `enroll toJson with a single attestation certificate exposes a correct JSON array`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = listOf("CERT_A")).toJson()
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
            ApiContract.EnrollRequest("d", "p", attestationChain = chain).toJson()
        )
        val arr: JSONArray = obj.getJSONArray("attestation_chain")
        assertEquals(3, arr.length())
        // The order carries meaning, from leaf to root, so it must be preserved as is.
        assertEquals("leaf", arr.getString(0))
        assertEquals("intermediate", arr.getString(1))
        assertEquals("root", arr.getString(2))
    }

    @Test
    fun `enroll toJson with a blank esid omits the esid key`() {
        val obj = JSONObject(ApiContract.EnrollRequest("d", "p", esid = "").toJson())
        assertFalse(obj.has("esid"))
    }

    @Test
    fun `enroll toJson with an esid carries it`() {
        val obj = JSONObject(ApiContract.EnrollRequest("d", "p", esid = "ESID-99").toJson())
        assertTrue(obj.has("esid"))
        assertEquals("ESID-99", obj.getString("esid"))
    }

    @Test
    fun `enroll toJson with attestation AND esid carries exactly four keys`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest(
                "d", "p", attestationChain = listOf("c1", "c2"), esid = "e"
            ).toJson()
        )
        assertEquals(
            setOf("device_id", "public_key", "attestation_chain", "esid"),
            keysOf(obj)
        )
    }

    @Test
    fun `enroll toJson introduces no unexpected key`() {
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = listOf("c"), esid = "e").toJson()
        )
        val allowed = setOf("device_id", "public_key", "attestation_chain", "esid")
        keysOf(obj).forEach { assertTrue("Unexpected key: $it", it in allowed) }
    }

    @Test
    fun `enroll toJson preserves special characters in the values`() {
        // Quotes, backslash, braces, newline, unicode: all must survive a round trip.
        val specialDevice = "id-special-€\"\\{}"
        val specialPub = "+/=base64==e\n\t"
        val obj = JSONObject(ApiContract.EnrollRequest(specialDevice, specialPub).toJson())
        assertEquals(specialDevice, obj.getString("device_id"))
        assertEquals(specialPub, obj.getString("public_key"))
    }

    @Test
    fun `enroll toJson preserves special characters inside the attestation chain`() {
        val specialCert = "MIIB\"\\/\n+=="
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = listOf(specialCert, "plain")).toJson()
        )
        val arr = obj.getJSONArray("attestation_chain")
        assertEquals(specialCert, arr.getString(0))
        assertEquals("plain", arr.getString(1))
    }

    @Test
    fun `enroll toJson accepts empty mandatory values without breaking`() {
        // Empty strings are valid; only an absent or empty LIST, or a blank esid, triggers omission.
        val obj = JSONObject(ApiContract.EnrollRequest("", "").toJson())
        assertEquals("", obj.getString("device_id"))
        assertEquals("", obj.getString("public_key"))
        assertFalse(obj.has("attestation_chain"))
        assertFalse(obj.has("esid"))
    }

    @Test
    fun `enroll toJson really uses the contract key constants`() {
        // Guarantees the serialisation references the KEY_ constants, with no divergent literal.
        val obj = JSONObject(
            ApiContract.EnrollRequest("d", "p", attestationChain = listOf("c"), esid = "e").toJson()
        )
        assertTrue(obj.has(ApiContract.KEY_DEVICE_ID))
        assertTrue(obj.has(ApiContract.KEY_PUBLIC_KEY))
        assertTrue(obj.has(ApiContract.KEY_ATTESTATION_CHAIN))
        assertTrue(obj.has(ApiContract.KEY_ESID))
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

    // ---------------------------------------------------------------------
    //  Media type
    // ---------------------------------------------------------------------

    @Test
    fun `the APK media type is the Android package archive type`() {
        // The device REFUSES a download whose type differs, so this string is part of the wire
        // contract. It lived only as a literal in the calling code until now — the one wire value
        // no contract test could see.
        assertEquals("application/vnd.android.package-archive", ApiContract.MEDIA_TYPE_APK)
    }

    @Test
    fun `the protocol version stays at 1 despite the additions`() {
        // Adding an optional header, a JSON key or a whole endpoint is backwards compatible:
        // older clients ignore what they do not know. No bump, by design.
        assertEquals(1, ApiContract.PROTOCOL_VERSION)
    }
}
