package com.endlesslock.egide.core

/**
 * Decision logic for the remote-erase trigger. Deliberately **pure**: no Android and no network
 * dependency, so it is unit-testable on a host JVM (see `EraserResponseLogicTest`).
 *
 * Historically this trigger erased the device as soon as the HTTP response carried a **non-empty
 * body**, without ever looking at the **status code**. An error page returned by the hidden
 * service (a restart, a misconfigured reverse proxy, a default banner) also has a non-empty body,
 * which meant an **unwanted and irreversible** erase of a legitimate user's phone.
 *
 * The server contract is explicit: the erase endpoint **answers 200, and that means erase**. The
 * code now matches the contract. An erase is decided only when the response is an HTTP **success**
 * (2xx) AND carries a non-empty body. This removes the "error page means erase" case and nothing
 * else. Nominal behaviour, 200 with a body, is unchanged.
 *
 * **Design note on authentication, and it is deliberate.** The response is not signed at the
 * application layer. It does not need to be: the endpoint is a Tor onion address, and an onion
 * address is self-authenticating, since the address is derived from the service's public key.
 * Reaching that address at all is proof of talking to the holder of the corresponding private key.
 * Tor already provides the server authentication that an application-layer signature would add.
 *
 * Applying the decision, that is performing the erase, remains the caller's responsibility.
 */
object EraserResponseLogic {

    /**
     * Does a response from the remote-erase endpoint constitute an erase order?
     *
     * @param httpCode the HTTP status code of the response, for example 200, 404, 500.
     * @param body the response body. May be `null` when absent or unreadable.
     * @return `true` if and only if the response is an HTTP **success** (2xx) AND has a
     *         **non-empty body**, that is the legitimate "answers 200, so erase" order. `false`
     *         otherwise, so any non-2xx response is safe, including an error page with a body.
     */
    fun shouldTriggerWipe(httpCode: Int, body: String?): Boolean {
        val isSuccess = httpCode in 200..299
        return isSuccess && !body.isNullOrEmpty()
    }
}
