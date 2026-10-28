# droidtop-shared

Small Kotlin libraries that droidtop and Enginehost each used to carry their own copy of
(Droidtop/tracker#184, #188, #191). Each app **vendors** this repository and neither depends on
the other; this repository depends on neither.

Every module is plain JVM: the Kotlin standard library and `java.security` only, no Android
API. Its tests run on a build machine with no device and no SDK.

| Module | Owns |
|---|---|
| [`:version`](version/README.md) | Version parsing and ordering: strict dotted versions with a build counter, and loose ordering of free-form strings |

Licence: GPL-3.0 (see `LICENSE`).

## Build

CI is `./gradlew test` on JDK 17 (`.github/workflows/build.yml`). Work lands on `main` from `ci/*`
branches once CI is green.

## Versions

The library's version is `shared.version` in `gradle.properties`. Each app pins a tag.
