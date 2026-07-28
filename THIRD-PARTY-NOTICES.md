# Third-party notices

The published core depends on very little, deliberately. Every dependency is a place where trust has
to be extended, so the list is kept short enough to read in full.

## Runtime dependencies of the published core

| Component | Version | Licence | Why it is here |
|---|---|---|---|
| Kotlin standard library (`org.jetbrains.kotlin:kotlin-stdlib`) | 2.2.10 | Apache License 2.0 | The language runtime. |
| JetBrains annotations (`org.jetbrains:annotations`) | 13.0 | Apache License 2.0 | Pulled in transitively by the Kotlin standard library. |
| JSON in Java (`org.json:json`) | 20250517 | Public Domain | Serialises the enrolment and verification request bodies. It is the same implementation the Android platform provides, so the published code and the shipped application behave identically. |

That is the entire runtime dependency tree. There is no HTTP client, no logging framework, no
analytics, no crash reporter, and no dependency injection container in the published core.

## Test-only dependencies

| Component | Version | Licence |
|---|---|---|
| JUnit (`junit:junit`) | 4.13.2 | Eclipse Public License 1.0 |
| Hamcrest Core (`org.hamcrest:hamcrest-core`) | 1.3 | BSD 3-Clause | 

These are used by `./gradlew test` and are not part of anything that ships.

## Build tooling

The Gradle wrapper (`gradle/wrapper/`) is distributed under the Apache License 2.0. The wrapper JAR
is committed to this repository, which is the upstream-recommended practice; its expected
distribution is pinned in `gradle/wrapper/gradle-wrapper.properties` and served over HTTPS from
`services.gradle.org`.

## About the shipped application

The application itself carries dependencies that do not appear here, because the code that uses them
is not published. Two are worth naming, since they matter to anyone assessing the product:

- **Tor** is embedded in the application package (`info.guardianproject:tor-android`, BSD 3-Clause).
  Nothing is downloaded at runtime; the binary ships inside the package.
- **OkHttp** (Apache License 2.0) is the HTTP client. Its single configuration point is
  `HttpFactory.kt`, published for reading in `android-extracts/`.

The complete dependency set of a shipped release can be read from the package you were given.

## Attribution of this repository

The code in `src/` and `android-extracts/` is Copyright (c) 2026 EndlessLock and is published under
the terms in `LICENSE`, which is **not** an open source licence. The third-party components listed
above keep their own licences, which are unaffected by ours.
