# :net

The one way either app talks to GitHub. Plain JVM (`HttpURLConnection`, `kotlinx-serialization-json`);
no Android API, and nothing in it stores a token.

- `GitHubAuth`: which requests may carry the user's token and how a request with one is opened.
  A token goes only to https requests for `api.github.com`, `github.com` and
  `raw.githubusercontent.com`, matched exactly (a subdomain is not a token host). Redirects are
  followed by hand, to https addresses only, and the header is decided again for every hop, because
  a private release asset redirects to a pre-signed `objects.githubusercontent.com` URL and that
  host rejects a request carrying both a signature and a bearer token. So `objects.githubusercontent.com`
  is deliberately not a token host.
- `GitHubClient`: `get` (a JSON request, conditional with `If-None-Match`, null for a 304, bounded
  body), `listReleases` (pages by `Link`, ETag on the first page, drafts included), and `download`
  (to an `OutputStream`, bounded, identity encoding and no cache, progress callback). Each app gives
  its own `userAgent` and a `token` supplier.
- `GitHubHttpException`: a non-success status with `X-RateLimit-Remaining` and `X-RateLimit-Reset`,
  which is what tells "asked too often" from "will not answer you".
- `GitHubReleases`: reading the `releases` JSON (`Release`, `ReleaseAsset`) and the `Link` header.

Where a token is kept stays with each app (Android Keystore and a private preferences file, under
that app's own alias and file name, never in a backup): it needs the Android SDK, which no module
here uses.

Tests run against a loopback server (`GitHubAuth`'s test constructor allows plain http and a chosen
host set), including a redirect from a token host to another host that must not receive the bearer.
