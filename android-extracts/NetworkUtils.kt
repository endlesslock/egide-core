package com.endlesslock.egide.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/*
 * ============================================================================
 * File: NetworkUtils.kt
 * ----------------------------------------------------------------------------
 * SHARED network utilities, a single source of truth.
 *
 * The check "is an internet-capable network available?" used to be DUPLICATED
 * identically in THREE places: the network dead-man trigger, the enrolment
 * preflight, and the update preflight. Three copies mean three chances to
 * DIVERGE silently, by hardening the criterion in one place and forgetting the
 * others. The single implementation now lives here and is reused by all three.
 * ============================================================================
 */

/**
 * Is a **usable** network, one carrying internet capability, currently available?
 *
 * The semantics are deliberately permissive, because this is only a preflight: there is an ACTIVE
 * network AND that network carries the internet capability. No active network, or a network
 * without that capability, counts as "no network".
 *
 * It says NOTHING about Tor, and nothing about connectivity being actually *validated*. It is a
 * presence test, not a quality test.
 *
 * IRREVERSIBLE DECISION DOWNSTREAM: this function feeds, among others, the "prolonged absence of
 * network" dead-man trigger, which ERASES the device once the configured delay expires. Any future
 * change to the criterion, such as requiring validated connectivity or distinguishing a captive
 * portal, MUST be made deliberately:
 *   - HARDENING it could count a connected device as offline, a false positive leading to an erase;
 *   - LOOSENING it could mask a genuine isolation, a false negative and a missed protection.
 *
 * @receiver any [Context]: application, service, job.
 * @return `true` when an internet-capable network is available, `false` otherwise.
 */
fun Context.hasInternet(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
    val activeNetwork = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
