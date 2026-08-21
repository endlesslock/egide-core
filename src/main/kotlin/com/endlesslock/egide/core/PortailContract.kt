package com.endlesslock.egide.core

/*
 * ============================================================================
 * File: PortailContract.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   The app-facing contract of the LICENSING / RECHARGE PORTAL — a SEPARATE onion service from the
 *   enrolment/update server of [ApiContract].
 *
 *   Beyond enrolment and updates, the app also talks to a public portal to top up its prepaid
 *   credit: it creates a payment intent, asks the portal to check a payment, and sets the web-login
 *   password. The portal DEFINES this contract; the app MIRRORS it here (same "server first" rule as
 *   for the enrolment/update contract). This file is published for the same reason as [ApiContract]:
 *   so a reader can see every portal path and every field the app can send or receive, with no
 *   hidden call.
 *
 * WHICH ONION
 *   Only the PUBLIC, app-facing portal (a plain onion surface, no client-auth). The app's own
 *   authentication to it is the `device_uid` ALONE for `/recharge` and `/recharge/verifier`;
 *   `/compte/motdepasse` also carries `captcha_token` and (a first-time possession proof | the old
 *   password on a change). `GET /paliers` and `GET /captcha` are PUBLIC (no `device_uid`). No
 *   signing key is embedded in the app for any of this.
 *
 * DELIBERATELY NOT HERE
 *   - A login path: the app NEVER logs itself in to the portal. The web login is a HUMAN action on a
 *     web page. The app only RECHARGES and SETS the password.
 * ============================================================================
 */

/**
 * The app-facing portal contract (a stateless singleton).
 *
 * The captcha PoW solver ([McaptchaSolver]) goes with this contract: `GET /captcha` returns the
 * challenge, the app solves it (in Kotlin, burning CPU) and attaches the `captcha_token` to
 * `POST /compte/motdepasse`.
 */
object PortailContract {

    // =================== PATHS (portal onion service) ===================

    /** GET, PUBLIC: the tier grid `[{palier, montant, devise, jours}]`. */
    const val PATH_PALIERS = "/paliers"

    /** GET, PUBLIC: the PoW challenge `{challenge_id, string, salt, difficulty}`. */
    const val PATH_CAPTCHA = "/captcha"

    /**
     * POST: sets/changes the web-login password, keyed by `device_uid`. Body:
     * `{device_uid, nouveau, captcha_token, preuve_pose_initiale}` (first set) or
     * `{device_uid, nouveau, ancien, captcha_token}` (change). Response `{ok: true}`.
     */
    const val PATH_COMPTE_MOTDEPASSE = "/compte/motdepasse"

    /**
     * POST: creates the payment intent. Auth: `device_uid` alone. Body `{device_uid, palier, rail}`.
     * Card response: `{pi_ref, url_checkout}`; monero response: `{pi_ref, sous_adresse, montant_xmr}`.
     */
    const val PATH_RECHARGE = "/recharge"

    /**
     * POST: triggers the pull-verification of a payment. Auth: `device_uid` alone. Body
     * `{device_uid, pi_ref}`. Response `{statut, credit_days_appliques?}`.
     */
    const val PATH_RECHARGE_VERIFIER = "/recharge/verifier"

    // =================== REQUEST JSON KEYS ===================

    /** Account identifier (not secret), the app's authentication to the portal. */
    const val KEY_DEVICE_UID = "device_uid"

    /** Solved PoW token, produced by [McaptchaSolver.encoderToken]. */
    const val KEY_CAPTCHA_TOKEN = "captcha_token"

    /** New web-login password (free length, may be random). */
    const val KEY_NOUVEAU = "nouveau"

    /** Old password (a change is authenticated by itself, with no device proof). */
    const val KEY_ANCIEN = "ancien"

    /**
     * First set: `preuve_pose_initiale` = the `bootstrap_token` obtained from the account server
     * ([ApiContract.KEY_BOOTSTRAP_TOKEN]). Since `device_uid` is not secret, this device-possession
     * proof is what the portal has the account server validate.
     */
    const val KEY_PREUVE_POSE = "preuve_pose_initiale"

    /** Requested payment rail: [RAIL_CARTE] | [RAIL_MONERO]. */
    const val KEY_RAIL = "rail"

    /** Purchased tier (a STABLE string identifier, never an index): see [PALIERS_CONNUS]. */
    const val KEY_PALIER = "palier"

    /** Payment-intent reference (returned by /recharge, replayed to /recharge/verifier). */
    const val KEY_PI_REF = "pi_ref"

    // =================== RESPONSE JSON KEYS ===================

    /** GET /paliers: amount, an INTEGER in CENTS of [KEY_DEVISE]. */
    const val KEY_MONTANT = "montant"

    /** GET /paliers: ISO 4217 code (a server parameter, never hard-coded). */
    const val KEY_DEVISE = "devise"

    /** GET /paliers: credited duration, an INTEGER in DAYS. */
    const val KEY_JOURS = "jours"

    /**
     * POST /recharge (card): a `checkout.stripe.com` URL that would be opened in the SYSTEM BROWSER.
     * The published app never emits the card rail (it hard-codes monero, see [RAIL_MONERO]), so this
     * field is part of the server contract but is not exercised by the app.
     */
    const val KEY_URL_CHECKOUT = "url_checkout"

    /** POST /recharge (monero): a dedicated deposit subaddress. */
    const val KEY_SOUS_ADRESSE = "sous_adresse"

    /** POST /recharge (monero): the EXACT amount to send, an INTEGER in PICONERO. */
    const val KEY_MONTANT_XMR = "montant_xmr"

    /** POST /recharge/verifier: payment status, see [STATUTS_CONNUS]. */
    const val KEY_STATUT = "statut"

    /** POST /recharge/verifier: days actually credited (may be absent while pending). */
    const val KEY_CREDIT_DAYS_APPLIQUES = "credit_days_appliques"

    /** POST /compte/motdepasse: the acknowledgement `{ok: true}`. */
    const val KEY_OK = "ok"

    // --- GET /captcha (PoW challenge) — keys mirrored for [McaptchaSolver] ---

    /** Challenge identifier, to return inside the token. */
    const val KEY_CHALLENGE_ID = "challenge_id"

    /** The challenge's random string (to hash). */
    const val KEY_STRING = "string"

    /** The challenge's salt (to hash). */
    const val KEY_SALT = "salt"

    /** Difficulty in leading ZERO BITS (⚠️ bits, not hex chars: 4 bits = 1 hex zero). */
    const val KEY_DIFFICULTY = "difficulty"

    /** Key of the `nonce` INSIDE the token JSON (base64url). */
    const val KEY_CAPTCHA_NONCE = "nonce"

    /** Key of the `result` (hex digest) INSIDE the token JSON. */
    const val KEY_CAPTCHA_RESULT = "result"

    // =================== FROZEN ENUMERATIONS ===================

    const val PALIER_SEMAINE = "sem"
    const val PALIER_MOIS = "mois"
    const val PALIER_6MOIS = "6mois"
    const val PALIER_AN = "an"

    /** Known tiers (stable string identifiers). A tier outside this set = display ignored. */
    val PALIERS_CONNUS: Set<String> = setOf(PALIER_SEMAINE, PALIER_MOIS, PALIER_6MOIS, PALIER_AN)

    /** Portal-side rail identifier. */
    const val RAIL_CARTE = "carte"
    const val RAIL_MONERO = "monero"
    val RAILS_CONNUS: Set<String> = setOf(RAIL_CARTE, RAIL_MONERO)

    const val STATUT_EN_ATTENTE = "en_attente"
    const val STATUT_CREDITE = "credite"
    const val STATUT_ECHOUE = "echoue"
    val STATUTS_CONNUS: Set<String> = setOf(STATUT_EN_ATTENTE, STATUT_CREDITE, STATUT_ECHOUE)

    // =================== ERROR BODY (never a 422) ===================

    /** Key of the error body: `{erreur: "<code>"}`. */
    const val KEY_ERREUR = "erreur"

    /** UNIFORM 401: unknown device_uid, wrong password, no password, wrong old password, first set with no proof. */
    const val ERREUR_REFUS = "refus"

    /** 429 + a Retry-After header (anti-brute-force). */
    const val ERREUR_VERROUILLE = "verrouille"

    /** 400: invalid / expired / replayed PoW token. */
    const val ERREUR_CAPTCHA_INVALIDE = "captcha_invalide"

    /** 409: frozen account (pre-check). */
    const val ERREUR_FROZEN = "frozen"

    /** 400: unknown tier. */
    const val ERREUR_PALIER_INVALIDE = "palier_invalide"

    /** 400: unavailable rail. */
    const val ERREUR_RAIL_INDISPO = "rail_indispo"

    /** 404: unknown payment intent at verification. */
    const val ERREUR_PAIEMENT_INCONNU = "paiement_inconnu"

    /** 400: malformed body. */
    const val ERREUR_REQUETE_INVALIDE = "requete_invalide"

    /**
     * 503: TRANSIENT upstream failure (the account server or the tier grid is unreachable). Not to be
     * confused with [ERREUR_REFUS]: prompt a retry, never show "wrong credentials" on a network-outage day.
     */
    const val ERREUR_INDISPONIBLE = "indisponible"

    /** The back-off header returned with [ERREUR_VERROUILLE]. */
    const val HEADER_RETRY_AFTER = "Retry-After"
}
