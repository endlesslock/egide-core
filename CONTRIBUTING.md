# Contributing

Short version: **we want your findings, not your patches.**

## Pull requests are not accepted

This is a source-available repository, not an open source project. Every line of Egide that ships
has to be signed by a key that only we hold, and merged code would have to be relicensed, attributed
and vouched for. We are not set up to do that responsibly, so rather than leave a pull request open
for months, we say no here.

If you opened one anyway, it will be closed with a pointer to this file. That is not a judgement on
the code you wrote.

## What is genuinely useful

**Security findings.** See `SECURITY.md`. That is the contribution that matters most, and the one we
answer fastest.

**Bugs in the published logic.** If a decision function returns the wrong answer for some input, say
so, with the input. A failing test case is the ideal form: the tests in `src/test` are readable, and
one more in the same style tells us more than any description.

**Errors in the documentation.** If the README, this file or `ARCHITECTURE.md` claims something the
code does not do, that is a real problem. We would rather be corrected in public than be quietly
wrong.

**Translation mistakes.** The user-facing strings ship in English, French and German. If something
reads badly, or worse, means something we did not intend, tell us.

## Opening an issue

Say what you observed, what you expected, and how to reproduce it. If it touches security, do not
open an issue at all: write to support@endlesslock.com instead.

Do not include anything that identifies you unless you want it public. Issues are visible to
everyone and are indexed. If you are one of the people this product is built for, use mail.

## Questions about the product

Support, purchases and anything about a specific device: **support@endlesslock.com**. Not the issue
tracker.
