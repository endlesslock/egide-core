# Changelog

This file tracks what changes in the **published core**, which is a subset of Egide. It is not the
release history of the product; for that, and for the hash of each shipped package, see `VERIFY.md`.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added

- Initial publication of the decision core: the erase triggers, the SMS and remote-erase decisions,
  the update verification rules, attestation marking, update bounds, settings validation, Tor
  control-port parsing, and the complete server contract.
- Four Android-dependent files published for reading in `android-extracts/`: the device identity
  key, the device identifier resolver, the HTTP factory, and the network availability check.
- `ClosedSurface.kt`: a declaration-only map of the closed part, so the published flow is
  complete end to end. Every operation the application can perform is listed with what it touches
  and what it sends; none of them is implemented, and a test asserts that none of them is.
- The `entitlements` and `channel` response keys in the server contract. `channel` is how a device
  learns that an entitlement was revoked server-side.
- The complete app-facing contract of the licensing/recharge portal (`PortailContract.kt`), the
  prepaid-credit and premium-gate decisions (`LicenceDecision.kt`), the honest free/premium split of
  the erase triggers (`WipeSource.kt`), and the portal's on-device proof-of-work captcha solver
  (`McaptchaSolver.kt`), each with host tests.
- The monotonic dead-man timer core (`DeadManRef` / `deadManTick`) and its clock-tampering tests
  (`DeadManTickTest`), which prove that a wall-clock change credits nothing within a boot session.
- The account-lookup operation (`/api/account`) and the eraser poll are now declared in
  `ClosedSurface.kt`, so the outbound map covers all three onion services end to end.
- 232 host tests covering the boundaries of every published decision.
- `ARCHITECTURE.md`, explaining where the published boundary falls and what trust remains.
- `SECURITY.md`, with a disclosure policy that explicitly does not require our permission to publish.
- `VERIFY.md`, for checking a received package against its published hash.

### Changed

- The "What the device sends" accounting is now honest about scale: the application talks to **three**
  onion services (enrolment/update, eraser, portal), and every outbound operation is listed. The
  earlier claim of "exactly two outbound operations" was true of an older shape and is gone; nothing
  that leaves the device carries personal data or device contents, but the device and account
  identifiers (`device_id`, `device_uid`) are named plainly as the linking identifiers they are.
- Enrolment is now authorised by hardware attestation plus an `esid`, not an enrolment token.
- The default failed-passcode threshold documented and pinned as **10**.

### Removed

- The enrolment token and the `/api/redeem` entitlement path: superseded by the attestation-based
  enrolment and the `/api/account` lookup.
- The wall-clock lock and network-isolation timer decisions (`lockDurationShouldWipe`,
  `lockTimerDecision`, `isolationDecision`): replaced by the monotonic dead-man core, which a clock
  change cannot defeat. Leaving them published would have shown rules the shipping app no longer runs.

### Reviewed before publication

An adversarial review was run over this repository and over the vocabulary of the private source
before anything was published. It found, among other things, a false statement in this README about
trigger defaults, a promise of release hashes that pointed nowhere, a licence with no archival right,
and a commit message that named something it should not have. All of it was corrected first. The
review is why the "Honest limitations" section says what it says.

### Note on history

This repository was created without upstream history, by copying a reviewed list of files. It does
not share commits with the private development repository, and it never will: an inherited history
would carry material that has no business being public.

Its own history was squashed once, before the repository was made public, to remove a commit message
that described the published boundary in terms that did not belong in a public log. Nothing was
removed from the files themselves.
