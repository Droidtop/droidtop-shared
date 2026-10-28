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
