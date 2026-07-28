# Security policy

## Reporting a vulnerability

Write to **support@endlesslock.com**.

If you would rather not be readable in transit, send a first message asking for a key, and you will
get an OpenPGP public key back. Say nothing sensitive in that first message.

Please include what you need to include and nothing more: the affected file or behaviour, what an
attacker gains, and how you got there. A proof of concept helps but is not required. Reports in
French or English are equally welcome.

## What you can expect

- An acknowledgement within **72 hours**. If you do not hear back, resend once; mail sometimes fails
  quietly.
- An assessment within **14 days**, saying whether the report is accepted, and if so how severe we
  think it is and why.
- A fix, or a written explanation of why there will not be one, within **90 days** of the
  acknowledgement.

We will tell you when the fix ships. Devices update over the air, so a fix reaches the fleet without
anyone having to act.

## Disclosure

**You may publish.** You do not need our permission, and you do not need to wait for us. Section
2(d) of the licence grants that right explicitly, and section 6 keeps it alive even if we terminate
your other permissions. We will not use the licence, or anything else, to suppress a security
report.

What we ask, and it is a request rather than a condition: give us 90 days before publishing details
that would let someone attack a device in the field. If you decide to publish sooner, tell us the
date so we can prepare our customers rather than learn about it from them.

If you want your name, handle or organisation credited in the fix notes, say so. If you want the
opposite, say that instead, and it will be respected.

## What this repository can and cannot tell you

This repository publishes the decision core: the rules that determine when data is erased, the
contract with the server, and the device identity cryptography. It does not publish the code that
performs the erase, the device-owner provisioning, or the tamper detection.

So a finding in the published code can be verified here by anyone. A finding about the closed part
cannot, and you may have to work from the binary. That asymmetry is real. If you are auditing
seriously and the closed part is blocking you, write to us and say what you need; we would rather
negotiate access under an agreement than have you give up.

## Out of scope

These are known, deliberate, and documented in the README. Reporting them is not a finding:

- **The update channel lets the publisher replace the application on a device.** That is inherent to
  shipping updates. The guard rails are in `ApkVerificationLogic.kt`.
- **The SMS command does not check the sender's number.** The secret in the message body is the
  authentication. Pinning the sender would break the feature in the only situation where it is
  needed, since the phone that was stolen is the owner's own.
- **The remote erase response carries no application-layer signature.** It arrives from an onion
  address, which is self-authenticating, so Tor already authenticates the server.
- **Cleartext HTTP is permitted towards onion services.** Confidentiality and authentication come
  from Tor, not from TLS on top of it.

A demonstration that one of these assumptions is *wrong*, rather than merely present, is very much
in scope.
