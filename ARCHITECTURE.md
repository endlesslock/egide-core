# Architecture, and where the published boundary falls

This document explains how Egide is put together, which parts of it are in this repository, and why
the line was drawn where it was. It is written for someone deciding whether to trust the product,
not for someone trying to rebuild it.

## The shape of the system

Egide has five moving parts. Only the first is partly published.

**The application**, on the phone. It runs as device owner on GrapheneOS, which is what gives it the
authority to erase data and to resist being removed. A resident foreground service watches the
trigger conditions; a set of pure decision functions, published here, turn observations into
decisions; and a separate layer, not published, carries them out.

**The enrolment and update server**, reachable only as a Tor onion service. It holds one row per
phone, for the life of that phone: an account identifier derived one way from the enrolment-specific
id, and a public key. It holds no secret belonging to any device, which is why a breach of its
database does not compromise a phone, and it never stores the enrolment-specific id itself.

**The remote erase endpoint**, one per phone, also an onion service. It does one thing: when the
owner starts it, it answers, and the phone that polls it erases itself. The phone sends it nothing.

**The licensing and recharge portal**, a separate onion service. The app talks to it to keep its
prepaid credit and to set the web-login password for the account. Its complete app-facing contract
is published in `PortailContract.kt`; the credit and premium-gate decisions are in
`LicenceDecision.kt`, and the captcha proof-of-work solver in `McaptchaSolver.kt`. The client that
performs those calls is not published.

**The provisioning tooling**, used once per device before it reaches the customer. Not published.

## The published boundary

The rule is simple to state. **What decides is published. What acts is not.**

Everything in `src/main/kotlin` is a pure function: same inputs, same output, no clock of its own, no
storage, no network. Those files cannot erase anything even if you call them in a loop. They return
a decision, and something else applies it.

That is not a stylistic preference. It is what makes the rules auditable on their own, and it is
also what makes them safe to publish: a reader learns exactly when Egide erases, and learns nothing
about how to stop it from erasing.

### Published

| Concern | File | What a reader gets from it |
|---|---|---|
| Erase triggers | `TriggerLogic.kt` | Every timer, every threshold, the clock-jump guard, the exact second at which each fires. |
| SMS command | `SmsTriggerLogic.kt` | How a message is recognised as a command, and the replay boundary. |
| Remote erase | `EraserResponseLogic.kt` | Which server response counts as an order, and which does not. |
| Update safety | `ApkVerificationLogic.kt` | The complete fail-closed rule for accepting an update. |
| Attestation | `AttestationDecision.kt` | When a key is recorded as hardware-backed. |
| Update bounds | `OtaLimits.kt` | The ceilings on download size and session token lifetime. |
| Settings | `SettingsValidation.kt` | Every bound on every setting that can lead to an erase. |
| Tor plumbing | `TorParsing.kt` | How the SOCKS port and bootstrap state are parsed. |
| Server contract | `ApiContract.kt` | The enrolment and update server: every endpoint, header and JSON field, including the enrolment body in full. |
| Portal contract | `PortailContract.kt` | The licensing/recharge portal: every path and JSON field. |
| Licensing | `LicenceDecision.kt` | How prepaid credit is read and which triggers the premium gate inhibits. |
| Freemium split | `WipeSource.kt` | Which erase triggers are free for life and which are premium. |
| Captcha | `McaptchaSolver.kt` | The portal's on-device proof-of-work solver. |
| The closed part, mapped | `ClosedSurface.kt` | Every operation the application can perform, what it touches, what it sends — across all three onion services. Declarations only. |

Three further files sit in `android-extracts/`. They depend on the Android framework, so they cannot
be compiled in this project and are published for reading only: `DeviceKey.kt` (the identity key
lives in the secure element and never leaves it), `HttpFactory.kt` (there is one outbound HTTP
configuration and no second one), and `NetworkUtils.kt`.

### Not published, and why

**The erase itself.** The code that removes the protected profile and tears the application down.
Publishing it would describe, step by step, what has to be interrupted to prevent an erase.

**Device-owner provisioning.** How the privileges are established and locked. This is the part that
took the longest to get right and the part a competitor would want most. It is also the part whose
publication would help someone attack a device rather than evaluate the product.

**Tamper detection.** What Egide checks, how often, and what it concludes. Published, it becomes a
checklist of conditions to avoid.

**How the application behaves on a device after it has fired.** This is the one exclusion that
protects users rather than a business. Anything published here would be equally available to someone
holding a device they did not pay for, and would work against every customer at once rather than
against us. We are deliberately vague on this point, and we are telling you that we are being vague
rather than pretending the section is complete.

A review recommended publishing the Android manifest, on the grounds that it is already extractable
from any installed package. That is true, and it was still refused, for a narrower reason than
secrecy: a customer can already read the manifest of their own device, so nothing is being kept from
them. What a reader actually needs from the manifest is the permission list, and the permission list
is published in full in the README. Republishing the manifest itself would add nothing for that
reader. So the permission list is published and the manifest is not.

## The trust that remains

Read the boundary honestly and you get this. You can verify **what Egide decides**. You cannot
verify, from this repository, **that the binary on your phone contains these decisions**, because
this is not a reproducible build. And you cannot verify the closed part at all without decompiling
what you were given.

Above all, the update channel means the publisher retains the power to replace the application on a
customer's device. `ApkVerificationLogic.kt` bounds what a compromised *server* can do. It does not
bound what the publisher can do. If that residual power is unacceptable to you, no amount of
published source will fix it, and you should not use the product.

We would rather write that down here than let you find it out later.

## How to read the code, if you only have twenty minutes

1. `ApiContract.kt`, the `ChallengeRequest` and `EnrollRequest` classes, then `PortailContract.kt`,
   and the *What the device sends* section of the README, which lists all fourteen outbound requests
   across the three onion services.
   Check that no personal data appears anywhere in it.
2. `HttpFactory.kt` in `android-extracts/`. That is the only outbound HTTP configuration; all three
   services go through it.
3. `TriggerLogic.kt`, `SmsTriggerLogic.kt` and `EraserResponseLogic.kt`: the timer, SMS-command
   and remote-order decisions. `WipeSource.kt` names every erase source and the tier it falls in.
4. `ApkVerificationLogic.kt`, then the "Honest limitations" section of the README, in that order.

Then run `./gradlew test` and watch the boundaries hold.
