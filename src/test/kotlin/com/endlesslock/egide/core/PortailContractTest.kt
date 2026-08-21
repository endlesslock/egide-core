package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST tests for [PortailContract]: the app-facing licensing/recharge portal contract.
 *
 * The server is built as a mirror of this file, so every path and every JSON key must equal EXACTLY
 * the expected string. This is also where a reader can check what the portal endpoints are, and read
 * off (from the documented bodies) what each call carries — `device_uid` and, for the password set,
 * the password the user chose. The request bodies themselves are assembled in the (unpublished)
 * client that performs the calls; what is pinned here is the wire vocabulary they must use.
 */
class PortailContractTest {

    @Test
    fun `the portal paths are exactly the expected strings`() {
        assertEquals("/paliers", PortailContract.PATH_PALIERS)
        assertEquals("/captcha", PortailContract.PATH_CAPTCHA)
        assertEquals("/compte/motdepasse", PortailContract.PATH_COMPTE_MOTDEPASSE)
        assertEquals("/recharge", PortailContract.PATH_RECHARGE)
        assertEquals("/recharge/verifier", PortailContract.PATH_RECHARGE_VERIFIER)
    }

    @Test
    fun `every portal path starts with a slash`() {
        listOf(
            PortailContract.PATH_PALIERS,
            PortailContract.PATH_CAPTCHA,
            PortailContract.PATH_COMPTE_MOTDEPASSE,
            PortailContract.PATH_RECHARGE,
            PortailContract.PATH_RECHARGE_VERIFIER,
        ).forEach { assertTrue("Path without a leading slash: $it", it.startsWith("/")) }
    }

    @Test
    fun `the request keys are exactly the expected strings`() {
        assertEquals("device_uid", PortailContract.KEY_DEVICE_UID)
        assertEquals("captcha_token", PortailContract.KEY_CAPTCHA_TOKEN)
        assertEquals("nouveau", PortailContract.KEY_NOUVEAU)
        assertEquals("ancien", PortailContract.KEY_ANCIEN)
        assertEquals("preuve_pose_initiale", PortailContract.KEY_PREUVE_POSE)
        assertEquals("rail", PortailContract.KEY_RAIL)
        assertEquals("palier", PortailContract.KEY_PALIER)
        assertEquals("pi_ref", PortailContract.KEY_PI_REF)
    }

    @Test
    fun `the device account handle is the same key as on the account server`() {
        // The portal authenticates the app by `device_uid`, the very handle the account server
        // returns on `/version` and `/api/account`. This is the linking identifier, named identically.
        assertEquals(ApiContract.KEY_DEVICE_UID, PortailContract.KEY_DEVICE_UID)
    }

    @Test
    fun `the response keys are exactly the expected strings`() {
        assertEquals("montant", PortailContract.KEY_MONTANT)
        assertEquals("devise", PortailContract.KEY_DEVISE)
        assertEquals("jours", PortailContract.KEY_JOURS)
        assertEquals("url_checkout", PortailContract.KEY_URL_CHECKOUT)
        assertEquals("sous_adresse", PortailContract.KEY_SOUS_ADRESSE)
        assertEquals("montant_xmr", PortailContract.KEY_MONTANT_XMR)
        assertEquals("statut", PortailContract.KEY_STATUT)
        assertEquals("credit_days_appliques", PortailContract.KEY_CREDIT_DAYS_APPLIQUES)
        assertEquals("ok", PortailContract.KEY_OK)
    }

    @Test
    fun `the captcha challenge keys are exactly the expected strings`() {
        assertEquals("challenge_id", PortailContract.KEY_CHALLENGE_ID)
        assertEquals("string", PortailContract.KEY_STRING)
        assertEquals("salt", PortailContract.KEY_SALT)
        assertEquals("difficulty", PortailContract.KEY_DIFFICULTY)
        assertEquals("nonce", PortailContract.KEY_CAPTCHA_NONCE)
        assertEquals("result", PortailContract.KEY_CAPTCHA_RESULT)
    }

    @Test
    fun `the frozen tier identifiers are stable strings`() {
        assertEquals(setOf("sem", "mois", "6mois", "an"), PortailContract.PALIERS_CONNUS)
        assertEquals("sem", PortailContract.PALIER_SEMAINE)
        assertEquals("mois", PortailContract.PALIER_MOIS)
        assertEquals("6mois", PortailContract.PALIER_6MOIS)
        assertEquals("an", PortailContract.PALIER_AN)
    }

    @Test
    fun `the payment rails are card and monero`() {
        assertEquals("carte", PortailContract.RAIL_CARTE)
        assertEquals("monero", PortailContract.RAIL_MONERO)
        assertEquals(setOf("carte", "monero"), PortailContract.RAILS_CONNUS)
    }

    @Test
    fun `the payment statuses are the three frozen values`() {
        assertEquals(setOf("en_attente", "credite", "echoue"), PortailContract.STATUTS_CONNUS)
    }

    @Test
    fun `the error codes are exactly the expected strings`() {
        assertEquals("erreur", PortailContract.KEY_ERREUR)
        assertEquals("refus", PortailContract.ERREUR_REFUS)
        assertEquals("verrouille", PortailContract.ERREUR_VERROUILLE)
        assertEquals("captcha_invalide", PortailContract.ERREUR_CAPTCHA_INVALIDE)
        assertEquals("frozen", PortailContract.ERREUR_FROZEN)
        assertEquals("palier_invalide", PortailContract.ERREUR_PALIER_INVALIDE)
        assertEquals("rail_indispo", PortailContract.ERREUR_RAIL_INDISPO)
        assertEquals("paiement_inconnu", PortailContract.ERREUR_PAIEMENT_INCONNU)
        assertEquals("requete_invalide", PortailContract.ERREUR_REQUETE_INVALIDE)
        assertEquals("indisponible", PortailContract.ERREUR_INDISPONIBLE)
        assertEquals("Retry-After", PortailContract.HEADER_RETRY_AFTER)
    }

    @Test
    fun `every declared JSON key is distinct`() {
        val keys = listOf(
            PortailContract.KEY_DEVICE_UID,
            PortailContract.KEY_CAPTCHA_TOKEN,
            PortailContract.KEY_NOUVEAU,
            PortailContract.KEY_ANCIEN,
            PortailContract.KEY_PREUVE_POSE,
            PortailContract.KEY_RAIL,
            PortailContract.KEY_PALIER,
            PortailContract.KEY_PI_REF,
            PortailContract.KEY_MONTANT,
            PortailContract.KEY_DEVISE,
            PortailContract.KEY_JOURS,
            PortailContract.KEY_URL_CHECKOUT,
            PortailContract.KEY_SOUS_ADRESSE,
            PortailContract.KEY_MONTANT_XMR,
            PortailContract.KEY_STATUT,
            PortailContract.KEY_CREDIT_DAYS_APPLIQUES,
            PortailContract.KEY_OK,
            PortailContract.KEY_CHALLENGE_ID,
            PortailContract.KEY_STRING,
            PortailContract.KEY_SALT,
            PortailContract.KEY_DIFFICULTY,
            PortailContract.KEY_ERREUR,
        )
        assertEquals("Some portal JSON keys are duplicated", keys.size, keys.toSet().size)
    }
}
