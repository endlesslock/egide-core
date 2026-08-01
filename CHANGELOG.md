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
- The redeem path in the server contract, with the `entitlements` and `channel` response keys. A
  token only ever adds an entitlement to an already enrolled device; the device identity is never
  touched, and `channel` is how a device learns that an entitlement was revoked server-side.
- 202 host tests covering the boundaries of every published decision.
- `ARCHITECTURE.md`, explaining where the published boundary falls and what trust remains.
- `SECURITY.md`, with a disclosure policy that explicitly does not require our permission to publish.
- `VERIFY.md`, for checking a received package against its published hash.

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
