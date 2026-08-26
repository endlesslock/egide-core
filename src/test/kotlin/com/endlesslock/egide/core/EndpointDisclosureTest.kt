package com.endlesslock.egide.core

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The published endpoint count, held to the published prose.
 *
 * The README tells a reader how many requests this application can make, across which services.
 * That number is a disclosure, not a comment: someone deciding whether to trust the product reads
 * it and takes it at face value. Until now it was implicit, spread over a bullet list, a set of
 * path constants and a map of declarations, so adding an endpoint could leave the prose behind and
 * nothing would notice.
 *
 * This test notices. It counts the endpoints from the constants themselves, and it reads the README
 * to check that the prose says the same thing. Add an endpoint without touching the prose and the
 * build fails, which is the only way a promise of this kind survives contact with a year of edits.
 */
class EndpointDisclosureTest {

    /** Every endpoint of the enrolment and update server, as published in [ApiContract]. */
    private val serverEndpoints = listOf(
        ApiContract.PATH_ENROLL,
        ApiContract.PATH_ENROLL_CHALLENGE,
        ApiContract.PATH_HEALTH,
        ApiContract.PATH_NONCE,
        ApiContract.PATH_VERIFY,
        ApiContract.PATH_VERSION,
        ApiContract.PATH_DOWNLOAD,
        ApiContract.PATH_ACCOUNT
    )

    /** Every endpoint of the licensing / recharge portal, as published in [PortailContract]. */
    private val portalEndpoints = listOf(
        PortailContract.PATH_PALIERS,
        PortailContract.PATH_CAPTCHA,
        PortailContract.PATH_COMPTE_MOTDEPASSE,
        PortailContract.PATH_RECHARGE,
        PortailContract.PATH_RECHARGE_VERIFIER
    )

    /**
     * The eraser has no path constant: the application polls the root of its onion address, and
     * that single request is the whole contract with it. It still counts as an endpoint, and it is
     * declared in [ClosedSurface.pollEraseOrder].
     */
    private val eraserEndpoints = 1

    private fun total(): Int = serverEndpoints.size + portalEndpoints.size + eraserEndpoints

    private fun readme(): String {
        val file = File("README.md")
        assertTrue(
            "README.md was not found in the working directory. Run the suite from the project root.",
            file.isFile
        )
        return file.readText()
    }

    @Test
    fun `the published contract exposes fourteen endpoints across three onion services`() {
        assertEquals("Two server endpoints share a path", serverEndpoints.size, serverEndpoints.toSet().size)
        assertEquals("Two portal endpoints share a path", portalEndpoints.size, portalEndpoints.toSet().size)
        assertEquals(8, serverEndpoints.size)
        assertEquals(5, portalEndpoints.size)
        assertEquals(14, total())
    }

    @Test
    fun `the README discloses the same number of endpoints as the code`() {
        assertTrue(
            "The endpoint count moved and the README still says something else. The prose is the " +
                "disclosure a reader trusts, so it moves in the SAME commit as the code, or the " +
                "published contract is false.",
            readme().contains("**fourteen** requests in total")
        )
        assertEquals(14, total())
    }

    @Test
    fun `the README no longer claims the device sends a system identifier`() {
        // The system Android ID left the contract. A published document that still describes it is
        // not out of date, it is wrong about what leaves a customer's phone.
        val text = readme()
        assertTrue(
            "The README still describes a stable device identifier leaving the device",
            !text.contains("a stable device identifier")
        )
    }
}
