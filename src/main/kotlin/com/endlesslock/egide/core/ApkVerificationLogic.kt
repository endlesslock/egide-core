package com.endlesslock.egide.core

/**
 * Decision logic that verifies a candidate package before an over-the-air install. Deliberately
 * **pure**, with NO Android dependency, so it is unit-testable on a host JVM (see
 * `ApkVerificationLogicTest`).
 *
 * This verdict used to be buried, and untested, inside the update client, coupled to the Android
 * package manager. The update path is the most dangerous one in the project: a silent regression
 * there (an intersection test instead of set equality, or `<` instead of `<=` on the rollback
 * check) would open the door to a downgrade or to a co-signed package being installed with device
 * owner privileges. Isolating the decision in this pure object locks it down with a table-driven
 * battery of tests.
 *
 * Its role is to reproduce the security semantics EXACTLY. The update client only COLLECTS the
 * inputs (lowercase hex SHA-256 fingerprints of the signers, version codes, package names, and the
 * optional pin burnt in at build time) and then DELEGATES here. No security decision may be
 * re-implemented anywhere else.
 *
 * Threat model: **the update server is assumed to be untrusted.** A package is accepted only if,
 * in this order:
 *   1. it is STRICTLY newer than the installed one (rollback protection);
 *   2. it carries the SAME package name;
 *   3. signers are present on BOTH sides, an empty set being a refusal (fail-closed);
 *   4. it is signed by EXACTLY the same SET of signers, neither a co-signed superset nor a subset;
 *   5. it satisfies the optional pin burnt in at build time, when one is configured.
 */
object ApkVerificationLogic {

    /**
     * Outcome of the decision. A sealed type, so the caller has to handle every case and the CAUSE
     * of a refusal is explicit, which helps both the logs and the tests.
     */
    sealed class Verdict {
        /** The candidate passes EVERY check: install allowed. */
        object Accepte : Verdict()

        /** The candidate is rejected; [motif] names the check that failed (fail-closed). */
        data class Refuse(val motif: Motif) : Verdict()
    }

    /**
     * Precise cause of a refusal. Declaration order follows the EVALUATION ORDER in [verdict]: a
     * package with several defects is refused on the FIRST failing check.
     */
    enum class Motif {
        /** Candidate version code not STRICTLY greater than the installed one (replay or downgrade). */
        DOWNGRADE,
        /** Candidate package name differs from the installed application. */
        PACKAGE_DIFFERENT,
        /** Signer set empty on either side, meaning unreadable signatures: fail-closed. */
        SIGNATURES_ABSENTES,
        /** Signer sets NOT identical: co-signed superset, subset, or a foreign key. */
        SIGNATURES_DIFFERENTES,
        /** The pin burnt in at build time is absent from the signer sets (defence in depth). */
        PIN_NON_RESPECTE
    }

    /**
     * Returns the accept or refuse verdict for a candidate package. A PURE decision, a faithful
     * reproduction of the historical checks, in the SAME ORDER.
     *
     * @param installedPackage      package name of the currently installed application.
     * @param candidatePackage      package name read from the candidate archive.
     * @param installedVersionCode  version code of the installed application.
     * @param candidateVersionCode  version code of the candidate.
     * @param installedSigners      SHA-256 fingerprints (LOWERCASE hex, no separators) of the
     *                              installed application's signers, already NORMALISED by the caller.
     * @param candidateSigners      SHA-256 fingerprints, same normalisation, of the candidate's signers.
     * @param expectedSignerPin     reference fingerprint optionally burnt in at build time. An
     *                              EMPTY string means the pin is inert.
     * @return [Verdict.Accepte] when every check passes, otherwise [Verdict.Refuse] with its [Motif].
     */
    fun verdict(
        installedPackage: String,
        candidatePackage: String,
        installedVersionCode: Long,
        candidateVersionCode: Long,
        installedSigners: Set<String>,
        candidateSigners: Set<String>,
        expectedSignerPin: String
    ): Verdict {
        // 1. ROLLBACK PROTECTION (OWASP MASTG-TEST-0036): refuse any candidate whose version code
        //    is not STRICTLY greater than the installed one. Using `<=` rather than `<` also
        //    forbids re-installing an identical, possibly vulnerable, version replayed by a
        //    compromised server.
        if (candidateVersionCode <= installedVersionCode) {
            return Verdict.Refuse(Motif.DOWNGRADE)
        }
        // 2. SAME PACKAGE: prevents installing a package under a different name, in particular
        //    through the system installer fallback, which does not constrain the target itself.
        if (candidatePackage != installedPackage) {
            return Verdict.Refuse(Motif.PACKAGE_DIFFERENT)
        }
        // 3. FAIL-CLOSED on missing signatures: an empty set, meaning unreadable signatures, is a refusal.
        if (installedSigners.isEmpty() || candidateSigners.isEmpty()) {
            return Verdict.Refuse(Motif.SIGNATURES_ABSENTES)
        }
        // 4. SET EQUALITY, and NOT a non-empty intersection: we require EXACTLY the same set of
        //    signers. An intersection test would accept a package co-signed by {legitimate cert +
        //    attacker cert}; set equality rejects the superset and the subset alike.
        if (installedSigners != candidateSigners) {
            return Verdict.Refuse(Motif.SIGNATURES_DIFFERENTES)
        }
        // 5. HARD PIN (defence in depth): when a reference fingerprint was burnt in at build time,
        //    it must belong to BOTH sets. Inert when the constant is empty, so no regression on
        //    builds that do not configure it.
        val ancre = expectedSignerPin.lowercase()
        if (ancre.isNotEmpty() && (ancre !in installedSigners || ancre !in candidateSigners)) {
            return Verdict.Refuse(Motif.PIN_NON_RESPECTE)
        }
        return Verdict.Accepte
    }
}
