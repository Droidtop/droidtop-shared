# Changelog

All notable changes to droidtop-shared. Versions follow [Semantic Versioning](https://semver.org/)
(0.x releases may change anything).

## [Unreleased]

### Added
- `:version`: `Version`, `VersionSeries` and `VersionConstraint` (from Enginehost, with their
  tests), and `Versions.compareLoose` (from droidtop, with its tests). Droidtop/tracker#191.
- `:trust`: `ChainSignature` (the certificate-chain JWS verifier, from droidtop) and `Sha256`,
  with droidtop's tests ported onto a test-only `:testpki`. Droidtop/tracker#191.
- `:trust` `BundleTar`: streaming reader for tar.xz bundles with path, link, duplicate, size,
  count and memory refusals. Droidtop/tracker#191.
