package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [ApkVerificationLogic].
 *
 * These lock down the fail-closed update decision. It is the most dangerous path in the project,
 * and these tests would catch a silent regression: `<` instead of `<=` on the rollback check, an
 * intersection instead of set equality, a broken pin, empty sets being accepted.
 *
 * Coverage:
 *  - Rollback protection: candidate `<`, `==`, `>` the installed version.
 *  - A different package name.
 *  - Set equality: co-signed superset (REJECT), subset (REJECT), foreign key (REJECT).
 *  - Fail-closed on empty signatures: both sides, candidate only, installed only.
 *  - The pin: present and satisfied, present and absent, empty and therefore inert, case-insensitive.
 *  - EVALUATION ORDER: a package with several defects is refused on the FIRST failing check.
 */
class ApkVerificationLogicTest {

    // Reference data. The actual package name is irrelevant here; only equality matters.
    private val PKG = "com.example.app"
    // Fictitious fingerprints in the production FORMAT: LOWERCASE hex, no separators.
    private val SIGNER_A = "a".repeat(64)
    private val SIGNER_B = "b".repeat(64)
    private val SIGNER_C = "c".repeat(64)

    /**
     * Readability helper: the defaults describe the NOMINAL acceptable case, that is the same
     * package, a version bump of one, identical signers {A}, and no pin. Each test overrides only
     * the parameters it cares about.
     */
    private fun verdict(
        installedPackage: String = PKG,
        candidatePackage: String = PKG,
        installedVersionCode: Long = 10,
        candidateVersionCode: Long = 11,
        installedSigners: Set<String> = setOf(SIGNER_A),
        candidateSigners: Set<String> = setOf(SIGNER_A),
        expectedSignerPin: String = ""
    ): ApkVerificationLogic.Verdict = ApkVerificationLogic.verdict(
        installedPackage = installedPackage,
        candidatePackage = candidatePackage,
        installedVersionCode = installedVersionCode,
        candidateVersionCode = candidateVersionCode,
        installedSigners = installedSigners,
        candidateSigners = candidateSigners,
        expectedSignerPin = expectedSignerPin
    )

    // Assertion shortcuts on the verdict type.
    private fun assertAccepte(v: ApkVerificationLogic.Verdict) =
        assertTrue("expected Accepte, got $v", v is ApkVerificationLogic.Verdict.Accepte)

    private fun assertRefuse(v: ApkVerificationLogic.Verdict, motif: ApkVerificationLogic.Motif) {
        assertTrue("expected Refuse, got $v", v is ApkVerificationLogic.Verdict.Refuse)
        assertEquals(motif, (v as ApkVerificationLogic.Verdict.Refuse).motif)
    }

    // ---------------------------------------------------------------------------------------------
    // Nominal case
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `nominal - same package, version bump, identical signers - ACCEPTED`() {
        assertAccepte(verdict())
    }

    // ---------------------------------------------------------------------------------------------
    // ROLLBACK PROTECTION: candidate <, ==, >
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `rollback - candidate STRICTLY lower - REFUSED as downgrade`() {
        assertRefuse(
            verdict(installedVersionCode = 11, candidateVersionCode = 10),
            ApkVerificationLogic.Motif.DOWNGRADE
        )
    }

    @Test
    fun `rollback - candidate EQUAL to the installed one - REFUSED as downgrade, the off-by-one boundary`() {
        // This is the case `<=` protects, rather than `<`: replaying an identical version is refused.
        assertRefuse(
            verdict(installedVersionCode = 11, candidateVersionCode = 11),
            ApkVerificationLogic.Motif.DOWNGRADE
        )
    }

    @Test
    fun `rollback - candidate STRICTLY higher - ACCEPTED`() {
        assertAccepte(verdict(installedVersionCode = 11, candidateVersionCode = 12))
    }

    // ---------------------------------------------------------------------------------------------
    // SAME PACKAGE
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `different package - REFUSED as package different`() {
        assertRefuse(
            verdict(candidatePackage = "com.attacker.malware"),
            ApkVerificationLogic.Motif.PACKAGE_DIFFERENT
        )
    }

    // ---------------------------------------------------------------------------------------------
    // SET EQUALITY of the signers, the heart of the anti co-signing defence
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `co-signed superset, candidate carries an extra signer - REFUSED`() {
        // Installed {A}, candidate {A, B}: an intersection test would wrongly accept it. Set
        // equality REJECTS it.
        assertRefuse(
            verdict(installedSigners = setOf(SIGNER_A), candidateSigners = setOf(SIGNER_A, SIGNER_B)),
            ApkVerificationLogic.Motif.SIGNATURES_DIFFERENTES
        )
    }

    @Test
    fun `subset, candidate is missing a signer - REFUSED`() {
        // Installed {A, B}, candidate {A}: also rejected by set equality.
        assertRefuse(
            verdict(installedSigners = setOf(SIGNER_A, SIGNER_B), candidateSigners = setOf(SIGNER_A)),
            ApkVerificationLogic.Motif.SIGNATURES_DIFFERENTES
        )
    }

    @Test
    fun `entirely foreign signer - REFUSED`() {
        assertRefuse(
            verdict(installedSigners = setOf(SIGNER_A), candidateSigners = setOf(SIGNER_B)),
            ApkVerificationLogic.Motif.SIGNATURES_DIFFERENTES
        )
    }

    @Test
    fun `identical multi-signer sets - ACCEPTED`() {
        assertAccepte(
            verdict(
                installedSigners = setOf(SIGNER_A, SIGNER_B),
                candidateSigners = setOf(SIGNER_B, SIGNER_A) // order is irrelevant: these are sets
            )
        )
    }

    // ---------------------------------------------------------------------------------------------
    // FAIL-CLOSED: empty sets
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `signatures empty on both sides - REFUSED, fail-closed`() {
        assertRefuse(
            verdict(installedSigners = emptySet(), candidateSigners = emptySet()),
            ApkVerificationLogic.Motif.SIGNATURES_ABSENTES
        )
    }

    @Test
    fun `candidate with no signer - REFUSED, fail-closed`() {
        assertRefuse(
            verdict(installedSigners = setOf(SIGNER_A), candidateSigners = emptySet()),
            ApkVerificationLogic.Motif.SIGNATURES_ABSENTES
        )
    }

    @Test
    fun `installed with no signer - REFUSED, fail-closed`() {
        assertRefuse(
            verdict(installedSigners = emptySet(), candidateSigners = setOf(SIGNER_A)),
            ApkVerificationLogic.Motif.SIGNATURES_ABSENTES
        )
    }

    // ---------------------------------------------------------------------------------------------
    // THE PIN burnt in at build time
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `pin present and satisfied - ACCEPTED`() {
        assertAccepte(
            verdict(
                installedSigners = setOf(SIGNER_A),
                candidateSigners = setOf(SIGNER_A),
                expectedSignerPin = SIGNER_A
            )
        )
    }

    @Test
    fun `pin present but absent from the sets - REFUSED as pin not satisfied`() {
        // Signers agree, {A} equals {A}, but the pin demands C, a foreign key. Defence in depth refuses.
        assertRefuse(
            verdict(
                installedSigners = setOf(SIGNER_A),
                candidateSigners = setOf(SIGNER_A),
                expectedSignerPin = SIGNER_C
            ),
            ApkVerificationLogic.Motif.PIN_NON_RESPECTE
        )
    }

    @Test
    fun `an empty pin is inert - ACCEPTED`() {
        // A build that configures no pin: no regression, the pin must refuse nothing.
        assertAccepte(verdict(expectedSignerPin = ""))
    }

    @Test
    fun `the pin is case-insensitive through lowercase normalisation - ACCEPTED`() {
        // The caller supplies the raw pin; the logic lowercases it, as the original code did.
        val uppercasePin = SIGNER_A.uppercase()
        assertAccepte(
            verdict(
                installedSigners = setOf(SIGNER_A),
                candidateSigners = setOf(SIGNER_A),
                expectedSignerPin = uppercasePin
            )
        )
    }

    // ---------------------------------------------------------------------------------------------
    // EVALUATION ORDER: several defects at once are refused on the FIRST failing check.
    // ---------------------------------------------------------------------------------------------

    @Test
    fun `order - downgrade takes precedence over a different package`() {
        // The candidate is both older AND from another package, so DOWNGRADE is returned, the first check.
        assertRefuse(
            verdict(
                candidatePackage = "com.attacker.malware",
                installedVersionCode = 11,
                candidateVersionCode = 10
            ),
            ApkVerificationLogic.Motif.DOWNGRADE
        )
    }

    @Test
    fun `order - a different package takes precedence over empty signatures`() {
        // Version is fine, but the package differs AND signatures are empty: PACKAGE_DIFFERENT wins, the second check.
        assertRefuse(
            verdict(
                candidatePackage = "com.attacker.malware",
                installedSigners = emptySet(),
                candidateSigners = emptySet()
            ),
            ApkVerificationLogic.Motif.PACKAGE_DIFFERENT
        )
    }

    @Test
    fun `order - empty signatures take precedence over differing sets`() {
        // Installed {A} but the candidate is empty: fail-closed is returned before set equality.
        assertRefuse(
            verdict(installedSigners = setOf(SIGNER_A), candidateSigners = emptySet()),
            ApkVerificationLogic.Motif.SIGNATURES_ABSENTES
        )
    }
}
