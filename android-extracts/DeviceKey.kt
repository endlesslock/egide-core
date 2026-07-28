package com.endlesslock.egide.core

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.security.keystore.StrongBoxUnavailableException
import android.util.Base64
import android.util.Log
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * The device identity key: ASYMMETRIC authentication of software updates.
 *
 * The protocol: the server sends a nonce, the device SIGNS it with its private key, and the server
 * verifies the signature using the device's **public key**, which is all it stores. The server
 * therefore holds **no secret at all**. A breach of its database has no impact on any device.
 *
 * Cryptographic details:
 *  - Algorithm: **ECDSA P-256** (`SHA256withECDSA`), the best supported by the Android keystore,
 *    including in **StrongBox**, the dedicated secure chip, on Pixel and GrapheneOS.
 *  - The private key is generated and kept inside the **Android keystore**, in the trusted
 *    execution environment or in StrongBox when available. It is **non-exportable** and never
 *    leaves the secure element. Not even this application can read it; it can only ask the secure
 *    element to sign with it.
 *  - Signatures are **DER**-encoded, then **Base64**.
 *  - The public key is exported as **X.509 SubjectPublicKeyInfo** (DER), then Base64. That string
 *    is what gets registered on the server.
 *
 * On the server side, symmetrically: decode the Base64 public key into an X509EncodedKeySpec for
 * EC, take `Signature.getInstance("SHA256withECDSA")`, and verify the decoded signature over the
 * UTF-8 bytes of the nonce.
 */
object DeviceKey {

    private const val TAG = "DeviceKey"
    private const val KEYSTORE = "AndroidKeyStore"
    private const val ALIAS = "device_identity_key"
    private const val SIGN_ALGO = "SHA256withECDSA"

    /**
     * Ensures the key pair exists, generating it when absent. Idempotent.
     *
     * @return `true` if the key exists or has just been created, `false` on failure.
     */
    fun ensureKeyPair(): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) return true
            genererPaire(strongBox = true)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot generate or verify the device key: ${e.message}")
            false
        }
    }

    /**
     * Signs the nonce, as UTF-8 bytes, with the private key held in the keystore.
     *
     * @param nonce the nonce supplied by the server.
     * @return the DER signature, Base64-encoded, or `null` on failure such as a missing key.
     */
    fun signNonceBase64(nonce: String): String? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val entry = ks.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry ?: return null
            val signature = Signature.getInstance(SIGN_ALGO).apply {
                initSign(entry.privateKey)        // the signing operation runs inside the secure element
                update(nonce.toByteArray(Charsets.UTF_8))
            }
            Base64.encodeToString(signature.sign(), Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot sign the nonce: ${e.message}")
            null
        }
    }

    /**
     * Exports the device's PUBLIC key, X.509 SubjectPublicKeyInfo, Base64, to be registered on the
     * server. It is not a secret.
     *
     * @return the Base64 public key, or `null` when no key exists or on error.
     */
    fun getPublicKeyBase64(): String? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val cert = ks.getCertificate(ALIAS) ?: return null
            Base64.encodeToString(cert.publicKey.encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot export the public key: ${e.message}")
            null
        }
    }

    /**
     * DELIBERATE re-provisioning: deletes the existing key and generates a new one. The new public
     * key has to be registered on the server, otherwise authentication will fail.
     *
     * @return the new Base64 public key, or `null` on failure.
     */
    fun regenerate(): String? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            ensureKeyPair()
            getPublicKeyBase64()
        } catch (e: Exception) {
            Log.e(TAG, "Cannot regenerate the key: ${e.message}")
            null
        }
    }

    /**
     * (Re)generates the identity key WITH a **hardware attestation challenge**.
     *
     * A key created this way produces a **certificate chain** (see [getAttestationChainBase64])
     * signed up to Google's attestation root, proving that the key really is backed by the secure
     * hardware of a genuine device, and embedding the supplied `challenge` as a replay anchor: the
     * server checks that the challenge equals the enrolment token it issued.
     *
     * The challenge can only be set when the key is CREATED, so this method **deletes** the
     * existing key and regenerates one. The public key changes and must be registered again. It is
     * meant to be called once.
     *
     * @param challenge the challenge bytes to seal into the attestation certificate.
     * @return `true` if the attested key was generated, `false` otherwise. After a failure a
     *         non-attested fallback key is recreated, so the local identity is never left without
     *         a key pair.
     */
    fun ensureAttestedKey(challenge: ByteArray): Boolean {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            if (ks.containsAlias(ALIAS)) ks.deleteEntry(ALIAS)
            genererPaire(strongBox = true, challenge = challenge)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Cannot generate the attested key: ${e.message}")
            // The previous key has already been deleted: restore a minimal identity at once.
            runCatching { ensureKeyPair() }
            false
        }
    }

    /**
     * Exports the key's **attestation certificate chain**, each certificate as DER then Base64.
     *
     * The first element is the leaf certificate, which is our key; the following ones climb toward
     * Google's attestation root. It is sent to the server, which verifies the chain up to that
     * root, the hardware anchoring, and the challenge.
     *
     * @return the list of Base64 certificates, or `null` when no attestation chain exists. A
     *         non-attested key has a single self-referential certificate, and that yields `null`.
     */
    fun getAttestationChainBase64(): List<String>? {
        return try {
            val ks = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            val chain = ks.getCertificateChain(ALIAS) ?: return null
            // A genuine attestation chain has several certificates: leaf, intermediates, root. A
            // non-attested key has only one, self-signed, which means no attestation.
            if (chain.size <= 1) return null
            chain.map { Base64.encodeToString(it.encoded, Base64.NO_WRAP) }
        } catch (e: Exception) {
            Log.e(TAG, "Cannot export the attestation chain: ${e.message}")
            null
        }
    }

    /**
     * Generates the EC P-256 pair inside the Android keystore. Tries StrongBox, the dedicated
     * secure chip, first, and falls back to the trusted execution environment when unavailable.
     *
     * @param strongBox `true` to require StrongBox on the first attempt.
     */
    private fun genererPaire(strongBox: Boolean, challenge: ByteArray? = null) {
        val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")) // the P-256 curve
            .setDigests(KeyProperties.DIGEST_SHA256)
        // No user authentication required: the update service signs in the background.
        if (strongBox) {
            builder.setIsStrongBoxBacked(true)
        }
        // When a challenge is supplied, the key produces a certificate chain attesting its
        // hardware anchoring together with that challenge.
        if (challenge != null) {
            builder.setAttestationChallenge(challenge)
        }
        kpg.initialize(builder.build())
        try {
            kpg.generateKeyPair()
            Log.d(TAG, buildString {
                append(if (strongBox) "Key generated (StrongBox requested)" else "Key generated (TEE)")
                if (challenge != null) append(" with attestation")
            })
        } catch (e: StrongBoxUnavailableException) {
            // No StrongBox on this device: fall back to the TEE, keeping the challenge.
            Log.w(TAG, "StrongBox unavailable, falling back to the TEE")
            genererPaire(strongBox = false, challenge = challenge)
        }
    }
}
