# :trust

Owns checking a signature: the one signature format droidtop and Enginehost both use (a JWS in
compact serialization whose protected header carries the signer's X.509 chain in `x5c`, verified
up to one trust anchor and no further), and the hashing it needs. **Nothing in it holds a real
signing key or pins an anchor**: the apps keep their official roots and the keys their users
confirmed, and hand the verifier an `Anchor`.

- `ChainSignature`: `verify(jws, purpose, anchor, detached, revoked)`, the certificate and key
  helpers (`parseCertificate`, `parseSpki`, `acceptableKey`) and the leaf checks (`names`,
  `coversPlugin`). A signature is for one `Purpose` (plugin, catalog or revocation list) and cannot
  be used for another. Expiry never invalidates a signature; only `revoked` does. Lifted from
  droidtop's `ChainSignature`.
- `Sha256`: lowercase hex of bytes, a file or a stream.

Uses `java.security` and `kotlinx-serialization-json` (to read the JWS header). No Android API.

Tests build their keys and chains in the test run with `:testpki` (BouncyCastle, test-only);
no official key appears in the repository.

- `BundleTar`: reads a bundle (tar inside xz) as a stream of regular files, refusing a path that
  could leave the folder it is extracted into, links and special files, a repeated path, an entry
  over a size cap, too many entries, and an xz stream that asks for more memory than a limit.
  What an entry means (manifest, signature, payload) and the order it must come in stay with the
  app; the manifest formats differ on purpose.

- `PublishedKeys`: finding and reading the key a plugin source publishes for itself: the address
  its key file lives at (`keyUrlsFor`; plaintext http refused), reading the file (`parse`), the
  anchor key from a certificate or bare public key (`anchorKey`), the origin ids a third party
  may hold (`originProblem`), and `select`, which runs all of it over bytes the app fetched. The
  library never fetches; the app's own HTTP client does.
- `KeyRotation.decide` (a key offered for an origin is new, unchanged, a rotation by a higher
  serial, or refused) and `Origins.normalizeGithub`.

