package com.endlesslock.egide.core

import org.json.JSONArray
import org.json.JSONObject

/*
 * ============================================================================
 * File: ApiContract.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   THE SINGLE SOURCE OF TRUTH for the contract between the application and its
 *   server: enrolment and software updates, both over Tor, on a shared onion
 *   service. There is no clearnet HTTPS path; it was removed.
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
 *   - The remote-erase channel. Its semantics are "a non-empty response means
 *     erase", with no application-layer protocol on top: the endpoint is a bare
 *     responder, authenticated by the onion address itself. This contract does
 *     not apply to it, and its behaviour is frozen. It is still an outbound
 *     network operation, and it is declared as one in `ClosedSurface.kt`.
 *   - The SMS channel, which is not HTTP at all.
 *   - The licensing/recharge portal, a SEPARATE onion service with its own
 *     contract in `PortailContract.kt`.
 * ============================================================================
 */

/**
 * The centralised API contract for enrolment and updates: a stateless singleton.
 */
object ApiContract {

    // =================== PROTOCOL VERSION ===================

    /**
     * Version of the application protocol between app and server. Incremented on EVERY breaking
     * contract change. Carried by [HEADER_PROTOCOL].
     *
     * Evolution rule: backwards-compatible additions are OPTIONAL fields and need no bump; any
     * breaking change requires a bump, and the server keeps accepting the older version for as
     * long as it takes the fleet to update.
     *
     * Version 2 removed a field the server used to require: enrolment no longer carries a device
     * identifier at all. The device is identified by its enrolment-specific id (`esid`) and by
     * nothing else. That is a breaking change under the rule above, so the number moved.
     */
    const val PROTOCOL_VERSION = 2

    // =================== HTTP HEADERS ===================

    /** Header carrying [PROTOCOL_VERSION] on every request, for both enrolment and updates. */
    const val HEADER_PROTOCOL = "X-Proto-Version"

    /** Header identifying the sending device; used by the update path to bind the nonce to it. */
    const val HEADER_DEVICE_ID = "Device-ID"

    /** Bearer authorization header for the authenticated update endpoints. */
    const val HEADER_AUTHORIZATION = "Authorization"

    // =================== ENDPOINT PATHS ===================
    // All RELATIVE: they are prefixed with the base URL of the shared onion service. Enrolment goes
    // through that SAME hidden service as the update channel; there is no clearnet path left.

    /** Enrolment: registers the (device id, public key) pair. POST, over Tor. */
    const val PATH_ENROLL = "/enroll"

    /**
     * Enrolment challenge: the device asks for the value its registration must answer. POST, over
     * Tor, on the same onion service as everything else here.
     *
     * It exists because of a platform constraint that is worth stating plainly: an attestation
     * challenge can only be sealed into a key at the instant that key is CREATED. A challenge that
     * changed on every registration would therefore force a new key, and so a new public key, every
     * single time. So the challenge is asked for first, and the registration that follows answers
     * it, either with a fresh attested key or with a signature from the key already held.
     *
     * The reply carries one field, [KEY_CHALLENGE]. Its value carries a fixed domain prefix, so a
     * challenge issued here can never be presented as a nonce from [PATH_NONCE], nor the reverse.
     */
    const val PATH_ENROLL_CHALLENGE = "/enroll/challenge"

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
     * Account lookup for an ALREADY ENROLLED device. POST, Bearer JWT required.
     *
     * This is NOT a re-enrolment: the device identity (`device_id`, public key, Keystore key) is
     * never touched. It returns the device's account identifier ([KEY_DEVICE_UID], the non-secret
     * web-login handle) and a short-lived [KEY_BOOTSTRAP_TOKEN]. The optional [KEY_ESID] in the
     * body lets the server compute the reset-proof account identifier for a device enrolled before
     * the account system existed; a missing account identifier with no `esid` supplied is answered
     * with [ERREUR_DEVICE_UID_ABSENT], and the app retries with its `esid`.
     */
    const val PATH_ACCOUNT = "/api/account"

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

    /**
     * Identifier of the device on the UPDATE path only: the [HEADER_DEVICE_ID] header and the body
     * of [PATH_VERIFY]. The wire name has not changed; the VALUE it carries has. It used to be the
     * system Android ID. It is now the account identifier [KEY_DEVICE_UID], which the device
     * persists when it registers.
     *
     * The system Android ID no longer appears in ANY request this application makes. Enrolment does
     * not carry it, and neither does anything else.
     *
     * It is a STABLE per-device value. It is not personal data and says nothing about the contents
     * of the device, but it is a linking identifier: the server can tell that two requests carrying
     * the same value came from the same device. That is stated plainly here rather than glossed as
     * "opaque".
     */
    const val KEY_DEVICE_ID = "device_id"

    /** Device public key, X.509 SubjectPublicKeyInfo, Base64. Enrolment. */
    const val KEY_PUBLIC_KEY = "public_key"

    /**
     * Chain of **hardware attestation** certificates for the key, each certificate Base64-encoded
     * DER. Enrolment, FIRST proof path.
     *
     * It is sent when the device has no key yet, which is the case after a factory reset or on a
     * first install: the key is created with [KEY_CHALLENGE] sealed into it, and the chain proves
     * both that the key is backed by the secure hardware of a genuine device and that this exact
     * challenge was answered. It is absent on the second proof path, where the key already exists
     * and [KEY_CHALLENGE_SIGNATURE] is sent instead. Exactly one of the two travels.
     */
    const val KEY_ATTESTATION_CHAIN = "attestation_chain"

    /**
     * The enrolment challenge returned by [PATH_ENROLL_CHALLENGE]. The device answers it either by
     * sealing it into a freshly created attested key, or by signing it with the key it already
     * holds. Enrolment.
     */
    const val KEY_CHALLENGE = "challenge"

    /**
     * Signature over [KEY_CHALLENGE] with the key the device ALREADY holds. Enrolment, SECOND
     * proof path.
     *
     * Keystore keys survive an application reinstall and a clearing of its data, so a device in
     * that situation still owns its key and its public key has not changed. Re-creating the key
     * just to seal a fresh challenge into it would change the public key for nothing, and the
     * server would read that as a rotation. So the device signs the challenge instead, which proves
     * possession without touching the key. ECDSA P-256, DER, Base64.
     */
    const val KEY_CHALLENGE_SIGNATURE = "challenge_signature"

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

    /** Response key: the device's COMPLETE set of entitlements, not a delta. Response to `/version`. */
    const val KEY_ENTITLEMENTS = "entitlements"

    /**
     * Response key: the release channel the server resolved for this device. Present on `/version`.
     *
     * It is the only way a device learns that an entitlement was REVOKED server-side. Without it, a
     * downgraded device would keep showing itself as a tester while receiving stable builds.
     */
    const val KEY_CHANNEL = "channel"

    // =================== PREPAID CREDIT (licensing) ===================
    // OPTIONAL fields at PROTOCOL_VERSION = 1 (no bump). The server defines these keys first; the
    // app MIRRORS them here, to the byte. ⚠️ UNITS ARE NORMATIVE AND FROZEN: a seconds/milliseconds
    // mismatch would break the credit countdown, hence the premium gate.
    //
    // The server OMITS an absent credit key rather than sending `null` or `0`: an absent key means
    // "no verdict" = UNKNOWN (the app fails OPEN, it does not lock you out); a `seconds_remaining`
    // that is PRESENT and <= 0 means EXPLICITLY suspended. Parsing MUST therefore tell absent from
    // present-and-zero (see [LicenceDecision]).

    /** Remaining credit, in SECONDS (integer). Authenticated response to `/version`. Absent = UNKNOWN. */
    const val KEY_SECONDS_REMAINING = "seconds_remaining"

    /** Instant the credit expires, epoch SECONDS UTC (integer). Response to `/version`. */
    const val KEY_ACTIVE_UNTIL = "active_until"

    /**
     * SERVER timestamp of the check-in, epoch **MILLISECONDS** UTC (integer). ⚠️ ms, not s.
     *
     * This is the TIME ANCHOR the app persists to count the credit down against a monotonic clock
     * (elapsed real time), never against the wall clock a thief can set back. It is returned as soon
     * as a `device_uid` is known, even with no credit line: the anchor depends on no account.
     */
    const val KEY_SERVER_TIME = "server_time"

    /** Lifetime licence (BOOLEAN). App-facing plan name: `unlimited`. Response to `/version`. */
    const val KEY_UNLIMITED = "unlimited"

    /**
     * Account identifier, NOT secret (it doubles as the web-login handle). Hex string (HMAC-SHA256,
     * 64 characters). Response to `/version` AND `/api/account`.
     *
     * It is a linking identifier: it ties this device's version checks, its portal password and its
     * recharges to the same device. It is derived from the device, carries nothing personal, and is
     * declared here for exactly that reason.
     */
    const val KEY_DEVICE_UID = "device_uid"

    /**
     * ESID, the enrolment-specific id. The **only** identity of a device, for enrolment and for the
     * account alike, and a REQUIRED field of the enrolment body.
     *
     * The platform guarantees the same value for the same device, the same organisation and the
     * same application, and that value survives a factory reset. That is what makes prepaid credit
     * survive a reformat, and it is also why a reformat can never buy a second free trial.
     *
     * There is no fallback. A device that cannot produce a usable one is refused, the refusal is
     * final, and nothing is registered on the server. It is also sent to [PATH_ACCOUNT]. The name
     * is fixed by the contract.
     */
    const val KEY_ESID = "esid"

    /**
     * Proof of DEVICE POSSESSION, single-use, short TTL, bound to the `device_uid`. Returned by
     * `/api/account` (the app has already proved its StrongBox key to obtain the JWT). It is handed
     * to the portal on the first password set (see `PortailContract`); it is never sent back to the
     * enrolment/update server by the app.
     */
    const val KEY_BOOTSTRAP_TOKEN = "bootstrap_token"

    /** 409 body on `/api/account`: `device_uid` is null and no `esid` was supplied; the app must resend with its ESID. */
    const val ERREUR_DEVICE_UID_ABSENT = "device_uid_absent"

    /** Key of the server's normalised error body (`{erreur: "<code>"}`). */
    const val KEY_ERREUR = "erreur"

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
     * Body of the enrolment challenge request: `POST` over **Tor** to [PATH_ENROLL_CHALLENGE].
     *
     * It carries ONE field, and there is nothing else it could usefully carry: the challenge is
     * bound to the enrolment-specific id, which is the only identity the device has before it is
     * registered. No device identifier, no public key, nothing about the person, nothing about the
     * contents of the device.
     *
     * @property esid the enrolment-specific id the challenge will be bound to.
     */
    data class ChallengeRequest(
        val esid: String
    ) {
        /** Serialises to JSON conforming to the contract, using the centralised key above. */
        fun toJson(): String = JSONObject().apply {
            put(KEY_ESID, esid)
        }.toString()
    }

    /**
     * Body of the enrolment request: `POST` over **Tor** to [PATH_ENROLL] on the shared onion
     * service, answering the challenge obtained from [PATH_ENROLL_CHALLENGE].
     *
     * Note what this body does NOT contain: no device identifier, no phone number, no contacts, no
     * location, nothing about the person, nothing about the contents of the device. The system
     * Android ID used to be here and is gone. What identifies the device is its enrolment-specific
     * id, and nothing else.
     *
     * Exactly one of the two proofs travels, and which one depends on whether the device still
     * holds its key:
     *  - no key held, that is a fresh install or a device just reformatted: the key is created with
     *    the challenge sealed into it, and [attestationChain] carries the proof;
     *  - key still held, that is the application reinstalled or its data cleared: the public key has
     *    not changed, so re-creating it would look like a rotation for nothing. The device signs the
     *    challenge instead and [challengeSignature] carries the proof.
     *
     * @property esid       the enrolment-specific id. REQUIRED; there is no fallback identifier.
     * @property publicKey  EC P-256 public key, X.509 SPKI, Base64. Not sensitive.
     */
    data class EnrollRequest(
        val esid: String,
        val publicKey: String,
        val attestationChain: List<String>? = null,
        val challengeSignature: String? = null
    ) {
        /**
         * Serialises to JSON conforming to the contract, using the centralised keys above. The two
         * mandatory fields are always written, blank included: the esid is not an optional field
         * that disappears when empty, it is the identity, and an unusable one has to reach the
         * server to be refused. Each proof field is written only when present.
         */
        fun toJson(): String = JSONObject().apply {
            put(KEY_ESID, esid)
            put(KEY_PUBLIC_KEY, publicKey)
            if (!attestationChain.isNullOrEmpty()) {
                put(KEY_ATTESTATION_CHAIN, JSONArray(attestationChain))
            }
            if (!challengeSignature.isNullOrEmpty()) {
                put(KEY_CHALLENGE_SIGNATURE, challengeSignature)
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
}
