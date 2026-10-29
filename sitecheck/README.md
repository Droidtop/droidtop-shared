# :sitecheck

Browser checks (a Cloudflare challenge, DDoS-Guard), recognised the same way in droidtop and Enginehost. Plain JVM,
no Android API. Droidtop/tracker#751 (lifted from droidtop's `SiteChecks`, #600).

The owner: "don't force past a cloudflare approval, but do let the browser try to solve the captcha itself if it's an
option". So nothing here solves anything. It recognises a check, keeps what passing one earned, and names the two ways
a pass happens; an app provides both:

- `SiteCheck`: `isChallenge(status, headers, body)` (Cloudflare's `cf-mitigated: challenge`, or a 403/429/503 whose
  top carries a challenge marker; an ordinary refusal, or a 200 that quotes the words, is not one), `isClearanceCookie`
  / `clearanceOf` / `mergeCookies` (a check's clearance, never an account's cookie; the newest clearance replaces an
  older one), `isCheckTitle` / `pagePassed` (a web view has passed when it shows the site's own page and a pass cookie),
  `covers` (a site and its subdomains).
- `Clearance`: the user agent of the web view that passed (the clearance is bound to it) and, per site, the clearance
  cookies; `forHost`, `plus`, `without`, `from`. Storage is the app's.
- `SiteSolver`: the browser's own try with no one asked: load the page in a web view nobody sees and wait a bounded time
  for its own script to pass it. It never clicks, types, uses a solving service or forges an answer.
- `SiteAsker`: the person passing the check, in place on the screen that needed it or behind a notice.
- `CheckOutcome`: `Passed(clearance)`, `NeedsPerson`, `Failed(reason)`.

Whether a request is retried, how requests to one site queue behind one check, and where a clearance is kept belong to
the app (droidtop's `:net-core` does them for every request it makes). Enginehost uses the recognition now and can plug
in its own solver and asker later.
