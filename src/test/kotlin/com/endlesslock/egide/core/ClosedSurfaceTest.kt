package com.endlesslock.egide.core

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ClosedSurface].
 *
 * There is nothing to test about behaviour here: the implementations are not published. What these
 * tests do assert is the property that makes the file honest, namely that **every declaration in it
 * is genuinely empty**. Nobody can slip a working implementation into the published map and have it
 * pass unnoticed, and no reader can mistake this file for something that runs.
 */
class ClosedSurfaceTest {

    private fun assertClosed(name: String, call: () -> Unit) {
        try {
            call()
            throw AssertionError("$name should be closed, but it ran")
        } catch (e: NotImplementedError) {
            assertTrue(
                "$name should point the reader at the architecture document",
                e.message.orEmpty().contains("not published")
            )
        }
    }

    @Test
    fun `every declared operation is closed and says so`() {
        assertClosed("eraseProtectedProfile") { ClosedSurface.eraseProtectedProfile() }
        assertClosed("eraseEntireDevice") { ClosedSurface.eraseEntireDevice() }
        assertClosed("switchToSecondaryIdentity") { ClosedSurface.switchToSecondaryIdentity() }
        assertClosed("observeTriggerConditions") { ClosedSurface.observeTriggerConditions() }
        assertClosed("detectTampering") { ClosedSurface.detectTampering() }
        assertClosed("keepWatcherAlive") { ClosedSurface.keepWatcherAlive() }
        assertClosed("enrolDevice") { ClosedSurface.enrolDevice() }
        assertClosed("checkForUpdate") { ClosedSurface.checkForUpdate() }
        assertClosed("lookUpAccount") { ClosedSurface.lookUpAccount() }
        assertClosed("pollEraseOrder") { ClosedSurface.pollEraseOrder() }
        assertClosed("fetchPortalPublic") { ClosedSurface.fetchPortalPublic() }
        assertClosed("setPortalPassword") { ClosedSurface.setPortalPassword() }
        assertClosed("rechargeCredit") { ClosedSurface.rechargeCredit() }
        assertClosed("provisionDevice") { ClosedSurface.provisionDevice() }
        assertClosed("persistSettings") { ClosedSurface.persistSettings() }
    }
}
