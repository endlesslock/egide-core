package com.endlesslock.egide.core

/**
 * The closed surface, declared so that the published flow is complete.
 *
 * ## Why this file exists
 *
 * Publishing only the decision logic leaves a reader with a gap: they can see what Egide *decides*,
 * but not what it then *does*, so they cannot rule out a step that quietly sends something
 * somewhere. That gap is exactly where a reasonable person stops trusting.
 *
 * This file closes it. Every operation the application performs after a decision is declared here,
 * with its full contract in the documentation: what it touches, what it leaves alone, and what it
 * sends over the network. **The implementations are not published.** The bodies below throw.
 *
 * So you get the complete list of what this software is capable of doing to a device, and you do not
 * get the recipe for how each step is carried out. The first is what an honest user needs. The
 * second is what an attacker would need, and what a competitor would want.
 *
 * ## The thing to check, if you check nothing else
 *
 * Read every declaration below and look at the "sends" line of each. The application talks to three
 * onion services, all over Tor through the single HTTP configuration in
 * `android-extracts/HttpFactory.kt`, and **every** outbound operation is listed here: the enrolment
 * and update server (the enrolment challenge, then registration, whose complete body is pinned in
 * [ApiContract] and its tests; the update check; the account lookup), the eraser (which the app
 * polls but to which it sends nothing), and the licensing/recharge portal (whose paths and fields
 * are in [PortailContract]).
 *
 * What none of them carries is anything about **you as a person**, or anything about the **contents
 * of your device**: no telemetry, no crash reporting, no analytics, no location, no contacts, no
 * message contents, no file contents, no list of installed applications. What some of them do carry
 * is an identity for the **device** or its **account**: the enrolment-specific id, and the
 * `device_uid` account handle. Because those are stable, linking identifiers, each declaration says
 * so rather than glossing them as "opaque". The portal also carries the web password **you** chose.
 * The `README` spells the full list out in prose; the declarations below are the exhaustive map.
 *
 * ## How to use this file
 *
 * As a map, not as a library. If you are auditing, walk the flow: a trigger in [TriggerLogic] fires,
 * the caller applies the decision, and the decision leads to exactly one of the operations declared
 * below. Nothing else happens.
 */
object ClosedSurface {

    private fun closed(): Nothing =
        throw NotImplementedError("Implementation is not published. See ARCHITECTURE.md.")

    // ─────────────────────────────────────────────────────────────────────────────
    // What happens when a trigger fires
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Erases the protected profile.
     *
     * This is the default outcome of every trigger. It removes the secondary Android user profile
     * that holds the data the owner chose to protect, which destroys that profile's own encryption
     * key. Each profile on the device is encrypted under a separate key, so removing the profile
     * makes its contents unrecoverable rather than merely deleted.
     *
     * - Touches: the secondary user profile and nothing else.
     * - Leaves alone: the main profile, its applications, its accounts, and the device itself, all
     *   of which keep working normally afterwards.
     * - Sends: **nothing**. No notification of the erase leaves the device, not even to us. We do
     *   not learn that it happened.
     */
    fun eraseProtectedProfile(): Unit = closed()

    /**
     * Erases the entire device.
     *
     * This is the escalation, off by default, and the application requires an explicit confirmation
     * before it can even be switched on. It performs a factory reset with external storage, the
     * factory-reset protection data and the embedded SIM included.
     *
     * - Touches: everything on the device.
     * - Leaves alone: nothing.
     * - Sends: **nothing**.
     */
    fun eraseEntireDevice(): Unit = closed()

    /**
     * Switches the application to a secondary visible identity.
     *
     * After a targeted erase, and only if the owner enabled the option, the application stops
     * presenting itself under its own name and icon, and removes its own data from the device. What
     * it presents instead, and how the switch is performed, are not published: that detail protects
     * the people who carry the product, and publishing it would work against every one of them at
     * once while telling an honest reader nothing they need.
     *
     * What matters here, and what you can hold us to, is the boundary: this operation changes how
     * the application appears and deletes its own files. It does not read, collect, copy or transmit
     * anything belonging to the user.
     *
     * - Touches: the application's own visible identity and its own stored data.
     * - Leaves alone: everything else on the device.
     * - Sends: **nothing**.
     */
    fun switchToSecondaryIdentity(): Unit = closed()

    // ─────────────────────────────────────────────────────────────────────────────
    // What watches, and what it is allowed to see
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Reads the state the triggers are evaluated against, and feeds it to [TriggerLogic].
     *
     * - Reads: whether airplane mode is on, whether a network is available, whether the screen is
     *   locked and since when, and the count of failed unlock attempts as reported by the system.
     * - Reads, for the SMS trigger only: incoming message bodies, and the inbox, to catch a command
     *   that arrived while the device was off. A body is compared against the configured secret by
     *   [SmsTriggerLogic.bodyContainsHash] and then dropped. It is not stored, not indexed, and not
     *   transmitted.
     * - Sends: **nothing**.
     */
    fun observeTriggerConditions(): Unit = closed()

    /**
     * Detects attempts to disable the application, and applies the configured erase if it finds one.
     *
     * The conditions it checks are deliberately not published: listed, they become a checklist of
     * things to avoid for someone who has taken a device and wants to neutralise its protection.
     *
     * - Reads: the application's own privilege state on the device.
     * - Sends: **nothing**.
     */
    fun detectTampering(): Unit = closed()

    /**
     * Keeps the watcher alive across reboots and process kills.
     *
     * - Sends: **nothing**.
     */
    fun keepWatcherAlive(): Unit = closed()

    // ─────────────────────────────────────────────────────────────────────────────
    // What leaves the device — the enrolment and update server
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Asks the enrolment server for the challenge that registration must answer. Once, at setup,
     * immediately before [enrolDevice].
     *
     * It exists because of a platform constraint worth stating plainly: a hardware attestation can
     * only be bound to a challenge at the instant the key is created. A challenge that changed on
     * every registration would force a new key, and so a new public key, every single time.
     *
     * - Sends, over Tor: exactly the body defined by [ApiContract.ChallengeRequest], that is the
     *   enrolment-specific id and nothing else. It is pinned by `ApiContractTest`.
     * - Receives: a short-lived random value.
     * - Does not send: any personal identifier, phone number, contact, location, message, file, or
     *   anything describing what is on the device.
     */
    fun requestEnrolmentChallenge(): Unit = closed()

    /**
     * Registers the device with the enrolment server. Once, at setup, right after
     * [requestEnrolmentChallenge].
     *
     * - Sends, over Tor: exactly the body defined by [ApiContract.EnrollRequest], that is the
     *   enrolment-specific id, the device's public key, and one proof that the challenge was
     *   answered: either the hardware attestation chain of a freshly created key, or a signature
     *   over the challenge with the key the device already holds. There is no enrolment token and
     *   no fallback identifier: a device that cannot produce a usable enrolment-specific id is
     *   refused, and nothing is registered. The complete field list is pinned by `ApiContractTest`,
     *   so this claim is checkable rather than asserted.
     * - Does not send: any device identifier, personal identifier, phone number, contact, location,
     *   message, file, or anything describing what is on the device.
     */
    fun enrolDevice(): Unit = closed()

    /**
     * Checks for and installs an update. Roughly once a day, over Tor.
     *
     * The candidate package is accepted only if [ApkVerificationLogic.verdict] returns
     * `Accepte`, under the bounds in [OtaLimits]. That rule is published in full, and it is what
     * stops a compromised server from installing a downgrade or a package signed by anyone else.
     *
     * It does not stop **us**. This is the residual power described in the README, and it is the
     * honest reason to distrust any vendor-updated security product, including this one.
     *
     * - Sends, over Tor: the account identifier (`device_uid`), a signature over a server-supplied
     *   nonce, and the currently installed version number.
     * - Does not send: anything about the user or the contents of the device.
     */
    fun checkForUpdate(): Unit = closed()

    /**
     * Looks up the device's account. `POST /api/account`, over Tor, under the session token.
     *
     * - Sends: the `esid` (an enrolment-specific id), so the server can compute or return the
     *   account identifier for this device.
     * - Receives: the account identifier `device_uid` (the non-secret web-login handle) and a
     *   short-lived possession proof.
     * - Does not send: anything about the user or the contents of the device.
     */
    fun lookUpAccount(): Unit = closed()

    // ─────────────────────────────────────────────────────────────────────────────
    // What leaves the device — the eraser
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Polls the eraser onion service for a pending erase order. Recurring, over Tor.
     *
     * This is the remote trigger. The onion address is self-authenticating (reaching it proves you
     * are talking to the holder of its key), so a non-empty response is treated as the order to
     * erase; the decision that reads that response is published in [EraserResponseLogic].
     *
     * - Sends: **nothing**. The request carries no body and no identifier; it only reads whether an
     *   order is waiting. Failing to reach the address is the normal, quiet state.
     */
    fun pollEraseOrder(): Unit = closed()

    // ─────────────────────────────────────────────────────────────────────────────
    // What leaves the device — the licensing / recharge portal
    // ─────────────────────────────────────────────────────────────────────────────
    // A SEPARATE onion service. Its paths and fields are published in [PortailContract]; the bodies
    // are assembled by this closed client. The app authenticates to it with the non-secret account
    // identifier `device_uid` alone (plus a solved proof-of-work token where the portal asks for one).

    /**
     * Fetches the recharge tiers (`GET /paliers`) and a captcha challenge (`GET /captcha`). Public GETs.
     *
     * - Sends: **nothing**. Neither carries the account identifier.
     * - The captcha challenge is then solved on-device, burning CPU, by [McaptchaSolver]; the proof
     *   of work leaves nothing about the device — it is a computation, not a fingerprint.
     */
    fun fetchPortalPublic(): Unit = closed()

    /**
     * Sets or changes the web-login password. `POST /compte/motdepasse`, over Tor.
     *
     * - Sends: the account identifier `device_uid`, the password **the owner chose** for their web
     *   account, a solved proof-of-work token, and — on the first set — a one-time possession proof.
     * - Does not send: anything about the person or the contents of the device. The password is the
     *   owner's own choice for their web account, not a device secret, and no trigger secret is ever
     *   sent here.
     */
    fun setPortalPassword(): Unit = closed()

    /**
     * Creates a recharge and checks its payment. `POST /recharge` then `POST /recharge/verifier`, over Tor.
     *
     * - Sends: the account identifier `device_uid`, the chosen tier, the payment rail (the app
     *   hard-codes Monero and never opens a card/clearnet checkout URL), and then the payment
     *   reference to verify it.
     * - Does not send: anything about the person or the contents of the device.
     */
    fun rechargeCredit(): Unit = closed()

    // ─────────────────────────────────────────────────────────────────────────────
    // Setup
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Establishes the device-owner privileges the protections depend on, once, before the device
     * reaches its owner.
     *
     * How this is done is the part that took longest to get right, and it is not published. It runs
     * before there is any user data on the device to be read.
     *
     * - Sends: **nothing** beyond the enrolment described above.
     */
    fun provisionDevice(): Unit = closed()

    /**
     * Stores the settings and timer state locally.
     *
     * - Touches: local storage private to the application.
     * - Sends: **nothing**. No setting, no threshold and no secret ever leaves the device. In
     *   particular, the SMS secret is stored encrypted on the device and is never transmitted: we
     *   cannot trigger anyone's erase by SMS, because we do not have it.
     */
    fun persistSettings(): Unit = closed()
}
