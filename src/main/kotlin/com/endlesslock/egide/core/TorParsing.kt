package com.endlesslock.egide.core

/**
 * Pure parsing of Tor control-port responses. Deliberately free of any Android dependency, like
 * [TriggerLogic] and [ApkVerificationLogic], so it is unit-testable on a host JVM (see
 * `TorParsingTest`).
 *
 * These two functions used to live inside the Tor manager, which depends on Android, and were
 * therefore untestable without a device. Resolving the SOCKS port is security-critical: a
 * mis-parsed port breaks the onion channel, and that channel carries both the remote erase order
 * and the software updates. The pure parsing is isolated here; the manager only fetches the raw
 * string from the control port and delegates its interpretation.
 */
object TorParsing {

    /**
     * Extracts the SOCKS port from the control port's `net/listeners/socks` response.
     *
     * The response looks like `"127.0.0.1:9050"`, sometimes quoted, sometimes several listeners
     * separated by spaces. We take the LAST segment after `:`, which is the port, strip quotes and
     * spaces, and keep it only if it is a valid port in `1..65535`. Otherwise we fall back to
     * [default]: a corrupt response must never make the socket address constructor throw, and must
     * never point at an out-of-range port.
     *
     * @param listener raw response from the control port. May be `null` or blank.
     * @param default fallback port, typically 9050.
     * @return a port in `1..65535`, or [default] when it cannot be resolved.
     */
    fun parseSocksPort(listener: String?, default: Int): Int {
        if (listener.isNullOrBlank()) return default
        return listener.trim()
            .substringAfterLast(':')
            .trim('"', ' ')
            .toIntOrNull()
            ?.takeIf { it in 1..65535 }
            ?: default
    }

    /**
     * Reduces the raw `status/bootstrap-phase` response to a compact form for support diagnostics.
     * The response looks like:
     *   `NOTICE BOOTSTRAP PROGRESS=45 TAG=conn_done SUMMARY="Connecting to a relay"`
     * We keep `PROGRESS=.. TAG=..`, plus the summary when it fits, without breaking on an
     * unexpected format.
     */
    fun compactBootstrapPhase(raw: String): String {
        val progress = Regex("PROGRESS=(\\d+)").find(raw)?.value
        val tag = Regex("TAG=(\\S+)").find(raw)?.value
        val summary = Regex("SUMMARY=\"([^\"]*)\"").find(raw)?.groupValues?.getOrNull(1)
        val compact = listOfNotNull(progress, tag).joinToString(" ")
        return when {
            compact.isNotBlank() && !summary.isNullOrBlank() -> "$compact ($summary)"
            compact.isNotBlank() -> compact
            else -> raw.trim().take(120)   // unexpected format: keep the raw string, bounded
        }
    }
}
