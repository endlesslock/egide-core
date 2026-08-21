# Egide core

This repository publishes the decision core of **Egide**, an anti-theft application for Android
devices, so that anyone can read the rules that govern when it erases data, and run the tests that
lock those rules down.

Egide is made and sold by EndlessLock: **https://endlesslock.com**
Contact: support@endlesslock.com

## What Egide does

Egide protects the data on a phone that gets stolen, taken, or simply lost. It watches
for signs that the device has left its owner's hands, and past a threshold the owner configured, it
erases the protected data. It runs as a device owner on GrapheneOS, which is what gives it the
authority to do that.

The triggers are:

- **Failed passcode attempts** above a configured threshold.
- **Prolonged lock**: the screen has stayed locked longer than the owner allowed.
- **Prolonged airplane mode**, and **prolonged absence of any network**, which is what a thief does
  first to stop the phone being traced.
- **A remote order**, sent by the owner over Tor.
- **An SMS command** containing a secret only the owner knows.

**Some of these are armed by default, and you should know which.** At setup, the airplane-mode,
no-network and prolonged-lock timers are armed at **72 hours**, and the failed-attempt threshold at
**10**. The remote and SMS triggers do nothing until the owner configures a secret. The setup screen
states the 72-hour defaults explicitly before handing over, and every threshold can be changed or
switched off afterwards.

**Some triggers are free, some are paid, and here is the split.** The offline safety net — a
prolonged-lock erase, and the failed-passcode and tamper defences — is **free for life and never
gated**, so a lost or seized phone always ends up protecting itself even with no subscription. The
**remote and fast** triggers — a remote onion order, an SMS command, the dead-man timer, and the
network-isolation timer — are the **paid** tier. Which trigger falls on which side is not a marketing
line you have to trust: it is pinned, source and test, in `WipeSource.kt` and `WipeSourceTest.kt`.

We arm them because a device that is stolen on day one, from an owner who never opened the settings,
is the case the product exists for. You may disagree with that choice. You should at least not be
surprised by it.

## Why only part of the code is here

The full source is not published, and this file will not pretend otherwise.

What is here is the part that answers the question a user actually has: *does this application spy
on me, and can it be made to turn against me?* That question is answered by reading what the
application decides, what it sends, and to whom. So this repository contains the decision logic, the
network contract, and the cryptography of the device identity, with their tests.

What is not here is the part that would help a thief rather than a reader: how the erase is
performed, how the device-owner privileges are established, and how the application resists being
tampered with. Publishing those would hand an attacker a map, and would not tell an honest user
anything they need.

The gap that leaves is filled by `ClosedSurface.kt`, which declares **every** operation the
application can perform on a device, with what each one touches and what each one sends, and
implements none of them. You get the full list of what this software can do to a phone; you do not
get the recipe for each step. In particular, that file is where you can check the claim that matters
most: **every** outbound network operation is listed, across the three onion services the app talks
to, and none of them carries anything about **you as a person** or the **contents of your device**.
What some of them do carry is an identity for the device or its account — spelled out, not glossed
as "opaque", in the section *What the device sends* below.

That is a real limitation and it cuts both ways. A reader of this repository can verify the rules,
and cannot verify that the binary they were given contains exactly these rules. See the honest
limitations section below.

## What is in this repository

Everything under `src/main/kotlin` is pure Kotlin. No Android, no input or output, no clock of its
own. That is what makes it testable on any machine, and it is also why it can be published: these
files decide, they never act.

| File | What it settles |
|---|---|
| `TriggerLogic.kt` | Every erase decision: the timers, the clock-jump guard, the failed-attempt threshold. |
| `SmsTriggerLogic.kt` | Whether an incoming message is the erase command, and the replay boundary. |
| `EraserResponseLogic.kt` | Whether a response from the remote endpoint is an erase order. |
| `ApkVerificationLogic.kt` | Whether an update package may be installed: rollback protection, signer identity, pinning. |
| `AttestationDecision.kt` | When a device key may be recorded as hardware-attested. |
| `OtaLimits.kt` | Hard ceilings on update size and session token lifetime. |
| `SettingsValidation.kt` | The bounds on every setting that can lead to an erase. |
| `TorParsing.kt` | Parsing of the Tor control port responses. |
| `ApiContract.kt` | The complete contract with the enrolment and update server: every endpoint, every header, every JSON field. |
| `PortailContract.kt` | The complete contract with the licensing / recharge portal: its paths, its JSON fields, and what each call carries. |
| `LicenceDecision.kt` | How the prepaid credit is read, and the premium gate: which triggers a paid tier unlocks, and why an unpaid device still protects itself. |
| `WipeSource.kt` | The honest, complete list of which erase triggers are free for life and which are premium. |
| `McaptchaSolver.kt` | The portal's proof-of-work captcha solver: it burns the device's own CPU and sends nothing about you. |
| `ClosedSurface.kt` | The map of the closed part: every operation the application can perform on a device, what each one touches, and what each one sends. Declarations only, no implementations. |

`android-extracts/` holds four files that depend on the Android framework and therefore cannot be
compiled here. They are published for reading, not for running:

| File | Why it is worth reading |
|---|---|
| `DeviceKey.kt` | The device identity key. It shows the private key is generated inside the secure element and never leaves it. |
| `DeviceIdentity.kt` | How the device identifier is resolved, and that no sentinel value is ever persisted as an identity. |
| `HttpFactory.kt` | That there is exactly one outbound HTTP configuration, with no interceptor and no second exit. |
| `NetworkUtils.kt` | The single definition of "a network is available", which feeds an irreversible trigger. |

## Run the tests yourself

```
./gradlew test
```

232 tests, no network access, no device, no emulator. They pin the exact boundaries: the second at
which a timer fires, the version code that is refused, the response that does not erase, the credit
verdict that suspends the paid tier, the captcha proof-of-work that matches the server byte for byte.

## What the device sends

Egide talks to **three** onion services, all over Tor, and every request it can make is listed
here. **None of them carries anything about you as a person, or anything about the contents of your
device.** What some of them do carry is an identity for the **device** or its **account** — and
because that is a stable, linking identifier, we spell it out rather than call it "opaque".

**The enrolment and update server.**

- *Registration*, once at setup: a stable device identifier, the device's public key, and optionally
  the hardware attestation chain for that key and an enrolment-specific id (`esid`). There is no
  enrolment token any more; the registration is authorised by the hardware attestation. This body is
  readable in `ApiContract.kt` and pinned by `ApiContractTest.kt`.
- *Update authentication and download*, roughly once a day: the device identifier, a signature over
  a server-supplied nonce, the installed version number, and the session token. A health probe and a
  nonce request carry nothing but the device identifier.
- *Account lookup*: the `esid`, under the session token, to obtain the account identifier
  (`device_uid`) and a short-lived possession proof.

**The eraser.** A single onion address the app polls on a recurring basis. It **sends nothing**; it
only reads whether an erase order is waiting. This is the remote trigger. Failing to reach the
address is the normal, quiet state.

**The licensing / recharge portal.** A separate onion service the app uses to keep its prepaid
credit. Its paths and fields are in `PortailContract.kt`; the request bodies are assembled by the
closed client that performs the calls, so — unlike registration — they are described here rather than
pinned by a published test.

- *Tiers and captcha*, public GETs: send nothing. The captcha challenge is then solved on-device,
  burning CPU (`McaptchaSolver.kt`); the proof of work is a computation, not a fingerprint.
- *Set the web password*: the account identifier `device_uid`, the password **you** chose for your
  web account, a solved proof-of-work token, and a one-time possession proof.
- *Recharge and verify*: the account identifier `device_uid`, the tier, the payment rail (the app
  hard-codes Monero and never opens a card or clearnet checkout URL), a solved proof-of-work token,
  and then the payment reference to check it.

So the **complete** list of what ever leaves the device is: a device identifier, an account
identifier, a public key, an attestation chain, signatures over server nonces, version numbers,
session tokens, a payment tier and reference, and the web password you set. The device identifier
and the account identifier are **stable and linking** — the server can tell that two requests came
from the same device, and the `device_uid` ties your update checks, your recharges and your password
to that one device. That is the honest boundary. What **never** leaves: your name, your phone
number, your contacts, your location, your messages, your files, or any list of what is installed.

## Two design choices that will look like bugs

Both are deliberate, both are visible in the published code, and we would rather explain them here
than in a comment thread under someone's write-up.

**The SMS command does not check the sender's number.** The secret inside the message body is the
authentication; the number is not. This is not an oversight. The owner has to be able to send the
command from any handset at all, a borrowed phone, a hotel landline gateway, a foreign SIM, because
the phone that was stolen is their own. Pinning a sender would break the feature in the exact
situation it exists for, and would buy nothing: sender numbers are trivially spoofable, so a check
would add a step for the owner and no obstacle for an attacker. See `SmsTriggerLogic.kt`.

**The remote erase response carries no application-layer signature.** It does not need one. The
endpoint is a Tor onion address, and an onion address is self-authenticating: the address *is*
derived from the service's public key, so reaching it at all proves you are talking to the holder of
the matching private key. Tor already provides the server authentication a signature would add. See
`EraserResponseLogic.kt`.

If you can show that either assumption is **wrong**, rather than merely present, that is a real
finding and we want it. See `SECURITY.md`.

## Honest limitations

**We assert things about the server that you cannot check here.** The server stores only public keys
and holds no secret belonging to any device, so a breach of its database should not compromise a
phone. That claim follows from the protocol you can read in `ApiContract.kt`, but no server code is
published in this repository, so on this specific point you are taking our word for it. We would
rather label it than let it pass as verified.

**The update channel is a residual power, and it is ours.** Egide updates itself over Tor. That
means the publisher can replace this application on a customer's device. The guard rails are in
`ApkVerificationLogic.kt`: a package is refused unless it is strictly newer, carries the same
package name, and is signed by exactly the same key set, with an optional pin on top. Those rails
stop a compromised server, a downgrade, and a co-signed package. They do not stop us. If you are not
willing to extend that trust to the publisher, do not use this product. We would rather say so here
than have you discover it later.

**This is not a reproducible build.** Nothing in this repository proves that the binary you received
was built from this source. What you can do is check that the file you received is the file we
published, by comparing its SHA-256 against the table in `VERIFY.md`. That defeats a tampered
download. It does not prove the source-to-binary link, and we are not going to pretend it does.

**The published subset is not the whole application.** The erase mechanism, the device-owner
provisioning and the tamper detection are closed. You are reading the rules, not the entire program.
`ARCHITECTURE.md` says exactly where the line falls and why.

**The permissions are broader than most applications', and here they are in full.** Do not take our
word for it, and do not rely on the system application information screen either: it shows runtime
permission groups, not the complete list. Extract the manifest from the package you were given, or
run `adb shell dumpsys package <package>`.

| Permission | Why it is requested |
|---|---|
| `INTERNET`, `ACCESS_NETWORK_STATE` | Reaching the onion services, and the network-availability check that feeds the no-network timer. |
| `RECEIVE_SMS`, `READ_SMS` | The SMS erase command, both in real time and through the inbox poll that catches a command received while the phone was off. |
| `CAMERA` | Scanning the enrolment QR code at setup. Nothing else uses it. |
| `BIND_DEVICE_ADMIN`, `MANAGE_PROFILE_AND_DEVICE_OWNERS` | Device owner status, without which none of the protections can act. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`, `WAKE_LOCK` | Keeping the watcher alive and able to run its checks. |
| `RECEIVE_BOOT_COMPLETED` | Resuming the timers after a restart, so a reboot does not reset them. |
| `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION` | Installing updates over the air. This is the permission pair behind the residual power described above. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Preventing the system from suspending the watcher. |
| `POST_NOTIFICATIONS` | The persistent notification that says the service is running. |

There is no location permission, no contacts permission, no microphone permission, and no storage
permission for reading your files.

## Where to go next

| If you want to | Read |
|---|---|
| Understand what is published and what is not, and why | `ARCHITECTURE.md` |
| Check that the package you received is the one we published | `VERIFY.md` |
| Report a vulnerability | `SECURITY.md` |
| Report a bug, or a mistake in these documents | `CONTRIBUTING.md` |
| Know what this code depends on | `THIRD-PARTY-NOTICES.md` |
| See what changed in the published core | `CHANGELOG.md` |

## Licence

Source-available, not open source. You may read, study, audit, run and modify this code, quote it,
archive a verbatim copy so your analysis stays checkable, publish a modified proof of concept
alongside a security report, and publish your findings without asking us or telling us first. You
may not ship a product, a service or a release built from it. See `LICENSE`.

We publish this to be verifiable, not to be resold.

## Reporting a security issue

Write to support@endlesslock.com, and see `SECURITY.md` for what to expect. If you would rather not
be readable in transit, our OpenPGP public key is in `SECURITY.md`; you do not have to ask for it.
