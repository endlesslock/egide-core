package com.endlesslock.egide.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * HOST unit tests (JVM, no Android) for [TorParsing].
 *
 * Focused on the SAFETY of SOCKS port parsing: a misread port breaks the onion channel, which
 * carries both the remote erase order and the software updates. The fallback MUST be safe for
 * every corrupt response.
 */
class TorParsingTest {

    private val default = 9050

    // parseSocksPort

    @Test fun `nominal port`() =
        assertEquals(9050, TorParsing.parseSocksPort("127.0.0.1:9050", default))

    @Test fun `quoted port`() =
        assertEquals(9150, TorParsing.parseSocksPort("\"127.0.0.1:9150\"", default))

    @Test fun `several listeners - take the last segment after the colon`() =
        assertEquals(9052, TorParsing.parseSocksPort("127.0.0.1:9051 127.0.0.1:9052", default))

    @Test fun `IPv6 - last segment after the colon`() =
        assertEquals(9050, TorParsing.parseSocksPort("[::1]:9050", default))

    @Test fun `null or empty - fallback`() {
        assertEquals(default, TorParsing.parseSocksPort(null, default))
        assertEquals(default, TorParsing.parseSocksPort("", default))
        assertEquals(default, TorParsing.parseSocksPort("   ", default))
    }

    @Test fun `port above the valid range - fallback to the default`() =
        assertEquals(default, TorParsing.parseSocksPort("127.0.0.1:70000", default))

    @Test fun `port zero - fallback`() =
        assertEquals(default, TorParsing.parseSocksPort("127.0.0.1:0", default))

    @Test fun `non-numeric port - fallback`() =
        assertEquals(default, TorParsing.parseSocksPort("127.0.0.1:abc", default))

    @Test fun `unix socket listener - non-numeric after the colon - fallback`() =
        assertEquals(default, TorParsing.parseSocksPort("unix:/run/tor/socks.sock", default))

    // compactBootstrapPhase

    @Test fun `full phase - progress plus tag plus summary`() =
        assertEquals(
            "PROGRESS=45 TAG=conn_done (Connecting to a relay)",
            TorParsing.compactBootstrapPhase("NOTICE BOOTSTRAP PROGRESS=45 TAG=conn_done SUMMARY=\"Connecting to a relay\"")
        )

    @Test fun `phase without a summary`() =
        assertEquals("PROGRESS=100 TAG=done", TorParsing.compactBootstrapPhase("BOOTSTRAP PROGRESS=100 TAG=done"))

    @Test fun `phase without progress - fall back to the bounded raw string`() =
        assertEquals("unexpected format", TorParsing.compactBootstrapPhase("  unexpected format  "))
}
