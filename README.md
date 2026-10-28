# droidtop-shared

Small Kotlin libraries that droidtop and Enginehost each used to carry their own copy of
(Droidtop/tracker#184, #188, #191). Each app **vendors** this repository and neither depends on
the other; this repository depends on neither.

Every module is plain JVM: the Kotlin standard library and `java.security` only, no Android
API. Its tests run on a build machine with no device and no SDK.

| Module | Owns |
|---|---|
| [`:version`](version/README.md) | Version parsing and ordering: strict dotted versions with a build counter, and loose ordering of free-form strings |
| [`:net`](net/README.md) | One GitHub client for both apps: which requests may carry a token and redirects decided per hop, conditional release listings with pagination and rate-limit errors, bounded downloads |
| [`:report`](report/README.md) | A bug reporter that needs no permission: one report model the person previews and edits, a redactor, and senders (Bugzilla REST, GitHub/Gitea issue form, email). No key ships in an app |
| [`:trust`](trust/README.md) | Checking signatures: the certificate-chain JWS both apps use, and SHA-256 helpers. Pins no key |
| `:testpki` | Test-only: throwaway certificate chains and signatures for the tests |

Licence: GPL-3.0 (see `LICENSE`).

## Build

CI is `./gradlew test` on JDK 17 (`.github/workflows/build.yml`). Work lands on `main` from `ci/*`
branches once CI is green.

## Versions

The library's version is `shared.version` in `gradle.properties`. Each app pins a tag.
