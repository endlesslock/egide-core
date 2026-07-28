# Verifying what you received

This page exists so that you do not have to take our word for the file on your phone.

## What this does and does not prove

It proves that the package you hold is **byte for byte the package we published**. That defeats a
tampered download, a substituted file, and a middlebox that rewrote the transfer.

It does **not** prove that the package was built from the source in this repository. Egide is not a
reproducible build. See the "Honest limitations" section of the README, which says so plainly rather
than leaving you to work it out.

## Checking a release package

Take the SHA-256 of the file you were given and compare it, character by character, with the entry
in the table below for the same version.

On Linux or macOS:

```
sha256sum egide-<version>.apk
```

On Windows, in PowerShell:

```
Get-FileHash -Algorithm SHA256 egide-<version>.apk
```

If the two strings differ in any position, **stop**. Do not install it. Write to
support@endlesslock.com and say where you obtained the file. A mismatch is either a corrupted
download or something worth knowing about, and we would rather investigate a false alarm than miss
the other case.

## Published releases

| Version | Date | SHA-256 of the package | Commit |
|---|---|---|---|
| _none published yet_ | | | |

The table is filled in as releases ship. The **Commit** column gives the revision of this repository
that corresponds to the release, so you can read the decision logic that was current at the time.

## Two sources, on purpose

The same hashes are published on the EndlessLock site, at https://endlesslock.com, and in this
repository, which is hosted elsewhere. Compare both. They are under different control, so an
attacker who can rewrite one still has to rewrite the other, and a silent change becomes a visible
contradiction.

If the two ever disagree, trust neither, and tell us.

## Signed tags

Release tags in this repository are signed with the maintainer's OpenPGP key. To check a tag:

```
git verify-tag <tag>
```

The key fingerprint is published at https://endlesslock.com. Fetch it from there, not from a
keyserver and not from this file: a fingerprint quoted inside the very repository it is meant to
authenticate proves nothing on its own.
