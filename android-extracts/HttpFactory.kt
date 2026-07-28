package com.endlesslock.egide.core

import android.util.Log
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/*
 * ============================================================================
 * File: HttpFactory.kt
 * ----------------------------------------------------------------------------
 * ROLE
 *   THE CENTRALISED HTTP FACTORY of the application. Two goals:
 *
 *   1) ONE place configures the HTTP clients and the common headers. Every
 *      outgoing call builds its request through [newRequest], which injects the
 *      protocol version header automatically. The version can therefore not be
 *      forgotten, and the server always knows which generation of client it is
 *      talking to.
 *
 *   2) A reusable RESILIENCE policy, [withRetry]: a bounded retry with
 *      exponential backoff, to absorb network flakiness WITHOUT an infinite loop.
 *
 * WHY THIS FILE IS PUBLISHED
 *   It is the file that shows there is exactly ONE outbound HTTP configuration
 *   in this application, with no interceptor and no second exit point. A reader
 *   who wants to know where data could possibly go looks here first.
 *
 * TRANSPORTS
 *   - The update channel, and enrolment with it, reuse the Tor client published
 *     by the Tor manager. Onion traffic must never leave Tor. This module does
 *     not recreate that client; it only supplies [newRequest] so that headers
 *     stay uniform across channels.
 *   - [clearnetClient]: direct HTTPS. NOW UNUSED, since enrolment moved entirely
 *     onto Tor. It is kept for a future clearnet project.
 *
 * This module contains NO business logic: no endpoints, no schemas, no
 * sequencing. Network plumbing only.
 * ============================================================================
 */

/**
 * Centralised network plumbing: a singleton with no business state.
 */
object HttpFactory {

    /** Logging tag. */
    private const val TAG = "HttpFactory"

    /** Default network timeouts, in seconds, shared by the clients built here. */
    private const val TIMEOUT_SECONDS = 15L

    /**
     * CLEARNET HTTP client, direct HTTPS. NOW UNUSED: enrolment, which this client once carried,
     * moved entirely onto Tor, and there is no caller left. Vestigial, kept for a future clearnet
     * project. Built lazily and reused, so it holds one connection pool rather than one instance
     * per call.
     */
    val clearnetClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Builds a [Request.Builder] PRE-FILLED with the protocol version header.
     *
     * Every call, enrolment and update alike, both now over Tor, must go through here so that
     * [ApiContract.HEADER_PROTOCOL] SYSTEMATICALLY accompanies the request. The caller then
     * completes it freely: method, body, further headers.
     *
     * @param url the absolute target URL.
     * @return a builder ready to be completed.
     */
    fun newRequest(url: String): Request.Builder =
        Request.Builder()
            .url(url)
            .header(ApiContract.HEADER_PROTOCOL, ApiContract.PROTOCOL_VERSION.toString())

    /**
     * Runs [block] with a bounded RETRY and exponential backoff.
     *
     * Deliberately simple robustness: at most [maxAttempts] tries, waiting
     * `baseDelayMs * 2^(n-1)` between two of them, so 500 ms, 1 s, 2 s, and so on. [block]
     * receives the attempt number, counted from one. If they all fail, the LAST exception is
     * propagated and the caller decides what to do with it.
     *
     * Use it only for IDEMPOTENT operations. A replayed enrolment is deduplicated server-side by
     * device identifier. Do NOT use it for actions that cannot be replayed.
     *
     * @param maxAttempts maximum number of attempts, at least one.
     * @param baseDelayMs base backoff delay, in milliseconds.
     * @param block       the suspending action to run; receives the attempt number.
     * @return the value produced by the first successful attempt.
     */
    suspend fun <T> withRetry(
        maxAttempts: Int = 3,
        baseDelayMs: Long = 500L,
        block: suspend (attempt: Int) -> T
    ): T {
        var derniereErreur: Throwable? = null
        for (tentative in 1..maxAttempts) {
            try {
                return block(tentative)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Do NOT treat cooperative cancellation as a retryable error. Otherwise a
                // cancelled scope, after the service was destroyed or the screen left, would keep
                // looping and waiting instead of stopping at once. Rethrow immediately.
                throw e
            } catch (e: Exception) {
                derniereErreur = e
                Log.w(TAG, "Attempt $tentative/$maxAttempts failed: ${e.message}")
                // No wait after the last attempt, since we are about to propagate.
                if (tentative < maxAttempts) {
                    delay(baseDelayMs shl (tentative - 1)) // exponential backoff: base * 2^(n-1)
                }
            }
        }
        // Every attempt failed: propagate the last cause.
        throw derniereErreur ?: IllegalStateException("withRetry: failed with no captured exception")
    }
}
