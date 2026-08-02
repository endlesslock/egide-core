package com.endlesslock.egide.core

import org.json.JSONArray
import org.json.JSONObject

/*
 * ============================================================================
 * File: ApiContract.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   THE SINGLE SOURCE OF TRUTH for the contract between the application and its
 *   server infrastructure: enrolment and software updates, both over Tor, on a
 *   shared onion service. There is no clearnet HTTPS path; it was removed.
 *
 *   Everything that defines "how the app talks to the server" lives here:
 *   protocol number, headers, endpoint paths, JSON field NAMES and the transfer
 *   objects. Other modules must NOT hard-code a URL string or a JSON key; they
 *   reference this contract. One file to read, one file to change, and a single
 *   anchor point for building the server as a mirror of it.
 *
 * WHY IT IS BUILT TO BE VERSIONED
 *   - [PROTOCOL_VERSION] goes out on EVERY request, in the [HEADER_PROTOCOL]
 *     header. The server can therefore serve several generations of clients at
 *     once and adapt its response to the version it sees. Evolving the protocol
 *     means bumping this constant and extending the contract, deliberately and
 *     in writing.
 *   - Centralised transfer objects and JSON keys guarantee that app and server
 *     share EXACTLY the same schema, which is what makes a fleet of devices on
 *     mixed versions workable.
 *
 * DELIBERATELY OUT OF SCOPE HERE
 *   - The remote-erase channel. Its semantics are "a success response with a
 *     body means erase", with no application-layer protocol on top: the
 *     endpoint is a bare responder, authenticated by the onion address itself.
 *     This contract does not apply to it, and its behaviour is frozen.
 *   - The SMS channel, which is not HTTP at all.
 * ============================================================================
 */

/**
 * The centralised API contract: a stateless singleton.
 */
object ApiContract {

    // =================== PROTOCOL VERSION ===================

    /**
     * Version of the application protocol between app and server. Incremented on EVERY contract
     * change: a new field, a new endpoint, a changed meaning. Carried by [HEADER_PROTOCOL].
     *
     * Evolution rule: backwards-compatible additions are OPTIONAL fields and need no bump; any
     * breaking change requires a bump, and the server keeps accepting the older version for as
     * long as it takes the fleet to update.
     */
    const val PROTOCOL_VERSION = 1

    // =================== HTTP HEADERS ===================

    /** Header carrying [PROTOCOL_VERSION] on every request, for both enrolment and updates. */
    const val HEADER_PROTOCOL = "X-Proto-Version"

    /** Header identifying the sending device; used by the update path to bind the nonce to it. */
    const val HEADER_DEVICE_ID = "Device-ID"

    /** Bearer authorization header for the authenticated update endpoints. */
    const val HEADER_AUTHORIZATION = "Authorization"

    // =================== ENDPOINT PATHS ===================
    // All RELATIVE: they are prefixed with the base URL of the shared onion service. Enrolment now
    // goes through that SAME hidden service as the update channel; there is no clearnet path left.

    /** Enrolment: registers the (device id, public key) pair. POST, over Tor. */
    const val PATH_ENROLL = "/enroll"

    /** Update: reachability probe. GET; a 2xx means the server is up. */
    const val PATH_HEALTH = "/health"

    /** Update: obtain a nonce to sign. GET, with the [HEADER_DEVICE_ID] header. */
    const val PATH_NONCE = "/api/nonce"

    /** Update: verify the ECDSA signature over the nonce. POST; returns a session token. */
    const val PATH_VERIFY = "/api/verify"

    /** Update: the available version. GET, with the [PARAM_CURRENT_VERSION] parameter. */
    const val PATH_VERSION = "/version"

    /** Update: download the package. GET, authenticated by the session token. */
    const val PATH_DOWNLOAD = "/download"

    /**
     * Update: add entitlements to an ALREADY ENROLLED device. POST, Bearer JWT required.
     *
     * This is NOT a re-enrolment. The device identity (`device_id`, public key, Keystore key)
     * is never touched: the refusal to rotate an identity key stands. The device hands over a
     * fresh single-use token and the server ADDS whatever that token grants.
     *
     * A token only ever ADDS. It never removes nor replaces an entitlement: otherwise anyone
     * handing a key to a device holder could downgrade them. Removal is an operator action.
     */
    const val PATH_REDEEM = "/api/redeem"

    // =================== QUERY PARAMETERS ===================

    /** Query parameter: the currently installed version, for the server to compare against. */
    const val PARAM_CURRENT_VERSION = "current_version"

    /**
     * The channel `/version` announced, echoed back to `/download`.
     *
     * This is what makes the invariant "both routes serve the same channel" structural. The
     * authorization header is OPTIONAL on `/version` and MANDATORY on `/download`: without this
     * echo, a client that omitted it on the former was told `stable` and then handed the bytes
     * of a pre-release. It can only RESTRICT: the server never uses it to grant a channel the
     * device is not entitled to.
     */
    const val PARAM_CHANNEL = "channel"

    // =================== JSON KEYS (request and response bodies) ===================
    // Centralised so that app and server share an identical schema, with no magic strings.

    /** Device identifier, shared by enrolment and update authentication. */
    const val KEY_DEVICE_ID = "device_id"

    /** Device public key, X.509 SubjectPublicKeyInfo, Base64. Enrolment. */
    const val KEY_PUBLIC_KEY = "public_key"

    /** Single-use enrolment token, burnt in at build time or entered by the customer. Enrolment. */
    const val KEY_ENROLL_TOKEN = "enroll_token"

    /**
     * Chain of **hardware attestation** certificates for the key, each certificate Base64-encoded
     * DER. OPTIONAL. It lets the server verify that the key really is backed by the secure
     * hardware of a genuine device, and that the attestation challenge equals the token that was
     * issued. Enrolment.
     */
    const val KEY_ATTESTATION_CHAIN = "attestation_chain"

    /** Single-use nonce supplied by the server. Update authentication. */
    const val KEY_NONCE = "nonce"

    /** ECDSA P-256 signature over the nonce, DER, Base64. Update authentication. */
    const val KEY_SIGNATURE = "signature"

    /** Session token returned once the signature has been verified. Update authentication. */
    const val KEY_JWT = "jwt"

    /** Lifetime of the session token, in seconds. Update authentication. */
    const val KEY_EXPIRES_IN = "expires_in"

    /** Latest version announced by the server. Update. */
    const val KEY_LATEST_VERSION = "latest_version"

    /** Boolean: is an update needed? Update. */
    const val KEY_UPDATE_NEEDED = "update_needed"

    /** Response key: the device's COMPLETE set of entitlements after the call, not a delta. */
    const val KEY_ENTITLEMENTS = "entitlements"

    /**
     * Response key: the release channel the server resolved for this device.
     *
     * Present on both `/version` and `/api/redeem`. It is the only way a device learns that an
     * entitlement was REVOKED server-side. Without it, a downgraded device would keep showing
     * itself as a tester while receiving stable builds.
     */
    const val KEY_CHANNEL = "channel"

    // =================== MEDIA TYPES ===================

    /**
     * MIME type of the APK served by `/download`.
     *
     * The device REFUSES a download whose type differs. Published here because it is part of the
     * wire contract like any path or JSON key, and it had until now lived only as a literal in
     * the calling code — the one wire value no contract test could see.
     */
    const val MEDIA_TYPE_APK = "application/vnd.android.package-archive"

    // =================== TRANSFER OBJECTS ===================

    /**
     * Body of the enrolment request: `POST` over **Tor** to [PATH_ENROLL] on the shared onion service.
     *
     * Note what this body does NOT contain: no phone number, no contacts, no location, no
     * identifier of the person, nothing about the contents of the device. It carries a token, an
     * opaque device identifier, a public key, and optionally the hardware attestation chain for
     * that key. That is the whole of what the device sends when it registers.
     *
     * @property enrollToken single-use token authorising the registration, entered or scanned by
     *           the customer, falling back to the one burnt in at build time.
     * @property deviceId    device identifier, the SAME one used by the update channel.
     * @property publicKey   EC P-256 public key, X.509 SPKI, Base64. Not sensitive.
     */
    data class EnrollRequest(
        val enrollToken: String,
        val deviceId: String,
        val publicKey: String,
        val attestationChain: List<String>? = null
    ) {
        /**
         * Serialises to JSON conforming to the contract, using the centralised keys above. The
         * attestation chain is included only when present, since the field is optional.
         */
        fun toJson(): String = JSONObject().apply {
            put(KEY_ENROLL_TOKEN, enrollToken)
            put(KEY_DEVICE_ID, deviceId)
            put(KEY_PUBLIC_KEY, publicKey)
            if (!attestationChain.isNullOrEmpty()) {
                put(KEY_ATTESTATION_CHAIN, JSONArray(attestationChain))
            }
        }.toString()
    }

    /**
     * Body of the update authentication request: `POST` over Tor to [PATH_VERIFY].
     *
     * @property deviceId  device identifier.
     * @property nonce     nonce received from the server.
     * @property signature ECDSA signature over the nonce, DER, Base64.
     */
    data class VerifyRequest(
        val deviceId: String,
        val nonce: String,
        val signature: String
    ) {
        /** Serialises to JSON conforming to the contract. */
        fun toJson(): String = JSONObject().apply {
            put(KEY_DEVICE_ID, deviceId)
            put(KEY_NONCE, nonce)
            put(KEY_SIGNATURE, signature)
        }.toString()
    }

    /**
     * Body of the entitlement request: `POST` over Tor to [PATH_REDEEM], on a device that is
     * ALREADY registered.
     *
     * The token, and nothing else. The device is identified by the `sub` claim of its bearer
     * token, which the server itself signed.
     *
     * ⚠️ **Never add a device identifier to this body.** Doing so would let a caller name a
     * device OTHER than itself, and hand out entitlements it has no claim to. The absence of that
     * field is the safeguard, so it is documented here rather than left to be rediscovered.
     *
     * @property enrollToken single-use token carrying the entitlements to add.
     */
    data class RedeemRequest(
        val enrollToken: String
    ) {
        /** Serialises to JSON conforming to the contract. */
        fun toJson(): String = JSONObject().apply {
            put(KEY_ENROLL_TOKEN, enrollToken)
        }.toString()
    }
}
