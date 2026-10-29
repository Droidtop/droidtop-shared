# Changelog

All notable changes to droidtop-shared. Versions follow [Semantic Versioning](https://semver.org/)
(0.x releases may change anything).

## [Unreleased]

### Added
- `:launch-contract`: `EnginehostIntents` (actions, extras, reasons, the capabilities provider) and
  `EnginehostConfig` (the `enginehost.json` keys and `problems`, a shape check), every string as released builds
  send it. Droidtop/tracker#204.
- `:report`: `ReportDraft`/`Section`, `ReportComposer`, `Redactor` (from Enginehost's `scrub`; a folder
  name with spaces no longer leaks past the first space), and `ReportSender` with `BugzillaSender`,
  `IssueFormSender` and `EmailSender`. Droidtop/tracker#655.
- `:net`: `GitHubAuth` (token hosts matched exactly, redirects followed by hand with the header decided
  per hop), `GitHubClient` (`get`, `listReleases`, `download`), `GitHubHttpException` and
  `GitHubReleases`, lifted from droidtop's `GitHubAuth`/`Http` and Enginehost's `PluginCatalog`.
  Where the two disagreed on the token hosts, droidtop's three exact hosts win: Enginehost also
  sent the token to `objects.githubusercontent.com` and to subdomains of the three.
  Droidtop/tracker#194.
- `:version`: `Version`, `VersionSeries` and `VersionConstraint` (from Enginehost, with their
  tests), and `Versions.compareLoose` (from droidtop, with its tests). Droidtop/tracker#191.
- `:trust`: `ChainSignature` (the certificate-chain JWS verifier, from droidtop) and `Sha256`,
  with droidtop's tests ported onto a test-only `:testpki`. Droidtop/tracker#191.
- `:trust` `BundleTar`: streaming reader for tar.xz bundles with path, link, duplicate, size,
  count and memory refusals. Droidtop/tracker#191.
- `:trust` `PublishedKeys` (key-file addresses, reading a published key, anchor keys, third-party
  origin ids, and `select` over fetched bytes), `KeyRotation` and `Origins.normalizeGithub`, from
  droidtop's `PluginSourceKeys` and `UserOriginKeys` and Enginehost's `OfficialKeyRotation`.
  Droidtop/tracker#191.

