package com.endlesslock.egide.core

/**
 * Decision logic for the SMS trigger. Deliberately **pure**: no Android dependency, so it is
 * unit-testable on a host JVM (see `SmsTriggerLogicTest`).
 *
 * Robustness note: the polling loop used to lowercase the message body coming back from the
 * inbox cursor WITHOUT handling the `null` case, which happens when the body column is empty. The
 * resulting exception was swallowed by an outer `try`, which aborted the walk over **every**
 * message: a single null-bodied SMS at the head of the list hid all the ones behind it, including
 * a legitimate erase command. The real-time receiver already handled the null. The comparison is
 * centralised here so that both channels share one source of truth.
 *
 * **Design note on authentication, and it is deliberate.** The command is authenticated by the
 * secret contained in the message body, not by the sender's number. This is not an oversight and
 * it is not a weakness: the owner has to be able to send the command from any handset at all,
 * including a borrowed phone or a foreign SIM, precisely because the phone that was stolen is
 * their own. Pinning the sender would make the feature useless in the only situation where it is
 * needed. The secret is the key, and the key is the only secret. Replay protection over time is
 * handled by the callers through the persisted scan boundary below.
 */
object SmsTriggerLogic {

    /**
     * Does the body of a message contain the trigger secret?
     *
     * @param body the message body. May be `null` when the column is empty or a part failed to decode.
     * @param smsHash the configured trigger secret. May be `null` or empty when unconfigured.
     * @return `true` if and only if both are non-empty AND `body` contains `smsHash`,
     *         case-insensitively. `false` as soon as either is null or empty.
     */
    fun bodyContainsHash(body: String?, smsHash: String?): Boolean {
        if (body.isNullOrEmpty()) return false
        if (smsHash.isNullOrEmpty()) return false
        return body.lowercase().contains(smsHash.lowercase())
    }

    /**
     * Computes the new replay boundary for the SMS channel from the timestamps of received messages.
     *
     * Correctness note. The receiver used to advance the boundary with the message timestamp
     * reported by the **carrier's SMS centre**, which runs on the carrier's clock, outside the
     * device's control. The polling safety net, however, compares that boundary against the `date`
     * column of the inbox provider, which is the instant of **local reception**, on the device's
     * clock. The two writers were therefore working from different clock bases. Since the stored
     * boundary is **monotonic** and never moves backwards, a harmless message whose carrier
     * timestamp ran ahead (clock skew at the carrier) wrote a boundary **permanently into the
     * future**. A genuine erase command arriving later while the phone was off, and therefore
     * handled by the boot-time poll rather than the real-time receiver, then carried a local `date`
     * earlier than that future boundary, was filtered out, and **never** executed. A durable false
     * negative.
     *
     * The counter-measure is to clamp every carrier timestamp to the local clock ([nowMs]), so a
     * future instant is never written. The residual worst case is the harmless re-inspection of a
     * message already handled, and re-triggering is guarded elsewhere.
     *
     * @param smscTimestampsMs carrier timestamps of the message parts. Values `<= 0` mean absent
     *        or undecoded and are ignored.
     * @param nowMs current instant on the local clock, the same base as the provider's `date` column.
     * @return the new boundary, never later than [nowMs], falling back to [nowMs] when no usable
     *         timestamp is available.
     */
    fun nouvelleFrontiere(smscTimestampsMs: List<Long>, nowMs: Long): Long =
        smscTimestampsMs.filter { it > 0L }.map { minOf(it, nowMs) }.maxOrNull() ?: nowMs
}
