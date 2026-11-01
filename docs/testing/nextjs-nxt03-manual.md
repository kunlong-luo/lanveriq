# NXT-03 — source-generated contract and live read-only browser gate

**2026-11-01 first manual Console HTTPS session run FAILED BEFORE TLS (not an auth-policy result).** [Actions #41301011192](https://github.com/kunlong-luo/lanveriq/actions/runs/41301011192) ran on SHA `14304018260ad15e7bce186dba42eeb7d61360d6`, successfully built the real Spring Boot JAR and started the disposable PostgreSQL 18.6 fixture, but stopped at `FAIL: isolated offline first-admin bootstrap failed; see private runner log`. The private bootstrap transcript was not part of the Actions log, so the original exception cannot be reliably determined from that run. **No Console HTTPS login, CSRF, Cookie, scoped device read or revocation check was reached.** This is *not* a failure of the previously passed separate Caddy↔real Tomcat transport acceptance.

**Bootstrap diagnostic hardening, execution STILL PENDING:** the manual fixture now uses util-linux `script --echo never` so piped, synthetic password input cannot be terminal-echoed into the private log, and a limited, secret-free diagnostic helper emits only fixed error *category labels* on failure (never raw logs, SQL, usernames/passwords, session cookies, exception messages or certificate bytes). Shell/source regressions and synthetic canary tests check the non-disclosure rule. The JPA first-owner bootstrap's independent PostgreSQL 18 transaction tests previously passed, but do not prove this CLI/PTY launch path. Re-dispatch the **new** [Console manual workflow](../../.github/workflows/console-https-synthetic.yml) on updated `main`; the next observed result, including only sanitized diagnostic labels if it fails, will determine any remaining runtime fix. No authorization gate or UI feature is opened by these changes.


**NXT-04-B / AUTH-01 test-harness implementation (2026-11-01): NEW, MANUAL EXECUTION PENDING.** The opt-in [Console HTTPS synthetic workflow](../../.github/workflows/console-https-synthetic.yml) runs the independently tested Caddy 2.11.7 → real Java 25 Spring/Tomcat TLSv1.3 → disposable PostgreSQL 18.6 bridge, but adds a **throwaway loopback-only console site** *only to a generated temporary Caddyfile*; `deploy/caddy/Caddyfile.reference` still responds 503 for console/enroll, and normal Spring and Next.js startup still default OFF. In `LANVERIQ_SYNTHETIC_CONSOLE_SESSION=1` mode (requires `LANVERIQ_SYNTHETIC_CADDY_BRIDGE=1`), the runner generates an ephemeral password and uses the real **offline, PTY-backed first-admin bootstrap** into empty PG18. Password travels through pipes (not argv, env, repository or Actions logs); its private bootstrap log, session cookies, CSRF tokens, PKI and DB are destroyed with the runner fixture. Then it opts into Spring's existing narrowly scoped `/auth/**`, GET-only device list and monitoring overview **in this isolated test**.

The new [HTTP session verifier](../../apps/server/scripts/verify-synthetic-console-session.sh) requires an independently CA-verified Caddy HTTPS response, exact secure host-only `__Host-lanveriq-session` cookie flags, pre-login 401, CSRF bootstrap, no-token and wrong-Origin login 403, incorrect password 401, valid form login 204 with session-id rotation, NEW CSRF token after login, authenticated no-store session JSON 200, empty authorized PG18 device page 200, scoped overview 200, cross-Origin read 403, logout 204 with subsequent 401, re-login and PostgreSQL `session_version` increment causing next session and device GET to return 401. A failed positive or negative stops the manual job. Existing real Agent two-way TLS/default-deny tests continue in the same isolated harness; no Agent API is switched on. **This script has only source/syntax/unit CI proof until its explicit manual workflow is dispatched and observed. Even a successful curl-based HTTP run does NOT prove browser-origin-generated headers, a real browser's cookie policy, Next.js runtime routes, multi-group device RBAC or deployed CA/OS controls.** Never mount `staged-console-auth.ts` or `staged-console-read.ts` into Next.js pages until separate real-browser session/authorization acceptance.


**2026-11-01 ACTUAL COMBINED SYNTHETIC TLS BRIDGE: PASSED.** [GitHub Actions #41294808939](https://github.com/kunlong-luo/lanveriq/actions/runs/41294808939), tested SHA `3154835297246232a94ff007d1f766cf4c20855f`. Actual Caddy 2.11.7 (image digest `sha256:36c24ad91a7ff7bc4c87fe22bfa8df3c4b18da0773d73fc12ee2c52b98c0bb7e`) → real Java 25 Spring Boot/Tomcat `client-auth=need` TLSv1.3 → ephemeral PostgreSQL 18.6 (image digest `sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81`) and Flyway startup. Positive Agent-client-authenticated HTTPS request through Caddy to real Tomcat returned health HTTP 200/UP; authenticated Agent business path still returned 403 by default. Missing/wrong-CA Agent certificates failed the TLS handshake; cross-host HTTP/2 Host/SNI returned 421; wrong Caddy-to-Tomcat client leaf and wrong trusted backend CA each produced 502 while real Tomcat stayed healthy; restoring the original trust settings restored the positive path. The test uses a throwaway loopback-only Caddyfile, synthetic CA and identity, no agent controller, no installed Rust collector, no production data. **This closes ONLY the manually tested combined transport/default-deny subgate.** Actual browser console TLS, authenticated session cookies/CSRF/Origin, RBAC scoped device REST, per-request device certificate revocation, OS key custody and production release acceptance remain OPEN.

**NXT-04-A staged Next.js session transport (source only, DEFAULT OFF):** `apps/web-next/src/lib/staged-console-auth.ts` reuses the previously tested Vite form-login/HttpSession/CSRF protocol with stricter matching against the expected canonical browser HTTPS origin. On login it discards prior authorized TanStack Query rows before CSRF bootstrap; performs CSRF GET → form-urlencoded POST → fresh CSRF GET after session fixation → GET session; only a validated 200/no-store session response with a login string counts as authenticated. Every failed session read and each logout clears prior scoped cache; 401/403/429/non-JSON/cacheable/offline responses fail closed. All requests are `same-origin` credentials, `no-store`, no redirects, no URL credentials. Synthetic fake-fetch tests are in `staged-console-auth.test.ts`; Next route-contract guard forbids mounting the module into any runtime component or adding rewrites/proxy. **This is NOT a real browser/login activation.** The existing `/login` preview stays disabled; no real passwords are accepted and no device data is fetched by the deployed Next app. Next required exit evidence: a separate real HTTPS browser + PostgreSQL 18 test of Caddy console Host/SNI, Spring session cookie Secure/HttpOnly/SameSite, pre- and post-fixation CSRF, Origin/fetch metadata, actual admin bootstrap/login/logout and revocation, role/group scoped read-only endpoints, invalidating old cache, with no live Agent collection or remote operations.


**Status: NXT-03A compile/unit stage PASSED; NXT-03B generated-hook compile and offline cache tests PASSED; NXT-03B real browser/runtime acceptance NOT EXECUTED.** This is not an endorsement of production
login, real devices or continuous monitoring. NXT-01/02 UI remains
synthetic/unconnected by design.

## Build evidence

- [Java CI #41288584413](https://github.com/kunlong-luo/lanveriq/actions/runs/41288584413):
  actual Springdoc OpenAPI generation, Orval 8 model/client generation,
  original Web TanStack Query typecheck and new Next.js
  `tsconfig.contract.json` model-to-display typecheck, no database.
- [Next.js CI #41288628345](https://github.com/kunlong-luo/lanveriq/actions/runs/41288628345):
  Node 24 / frozen pnpm 12, staged projection and fake-fetch unit
  tests, production Next.js build and synthetic route smoke only.
- [Java CI #41289190664](https://github.com/kunlong-luo/lanveriq/actions/runs/41289190664):
  generated **complete Orval Fetch/React Query client** typechecks against
  the locked Next.js app's TanStack Query 5.105 and TypeScript runtime
  dependency graph (not merely the DTOs).
- [Next.js CI #41289252650](https://github.com/kunlong-luo/lanveriq/actions/runs/41289252650):
  real production build and synthetic fake-fetch tests show 401/403/429/503
  transitions can erase all prior session-scoped cached data.
- The candidate transport and generated client are **not imported** by any Next.js page.
  Route guard rejects mounting it prior to manual approval.

**Caddy-only Agent transport (separate from browser console):** [Caddy-only real 2.11.7 double-mTLS run #41293595396](https://github.com/kunlong-luo/lanveriq/actions/runs/41293595396) passed real Caddy→synthetic Python TLS. This says nothing about browser console HTTPS, session/CSRF, real Spring/Tomcat or Next.js runtime and does not relax any of the following blockers.

## Database milestone and remaining release-blocking checks

**PostgreSQL 18.6 database integration gate: PASSED (2026-11-01).** [PostgreSQL 18.6 manual run #41290838250](https://github.com/kunlong-luo/lanveriq/actions/runs/41290838250) ran the *actual* seven-class Testcontainers `integrationTest` suite on Git SHA `9b78641965a6599a8267649756b974afa9003e9f`. Gradle passed, and the mandatory JUnit evidence checker accepted **58 passing tests, zero failures/errors/skips**, including the five previously failing cases, Flyway V1–V8, database locking, event immutability, inventory rollback, certificate revocation and administrator audit atomicity. Captured PostgreSQL image digest: `sha256:3a4264520e121176619f54ff867d12774bc81e95e7b84db58d63bccdb6144b81`; JUnit archive `srv03-pg18-synthetic-junit` (artifact ID `13076977475`, archive SHA-256 `sha256:0ba4dc9ad78b217a5b764112ba7224179029e76894b547d29c908e051dd57909`, short retention through 2026-11-08). **This is not a Caddy TLS, browser Cookie/CSRF/RBAC, actual Agent REST/ACK, LAN or production approval.**

The remaining rows are still open unless specifically marked as passed.

| Acceptance area | Required observation |
| --- | --- |
| PostgreSQL 18 | **PASSED on reviewed SHA** — [PostgreSQL 18.6 manual run #41290838250](https://github.com/kunlong-luo/lanveriq/actions/runs/41290838250), 58/58, 7 classes, JUnit and image provenance. This does not prove HTTPS/browser operation |
| Caddy HTTPS (Agent transport) | **PASSED synthetic-only bridge** in [#41294808939](https://github.com/kunlong-luo/lanveriq/actions/runs/41294808939); **console browser** canonical HTTPS Host/SNI/Origin/Cookie remains untested |
| Administrator login | Actual browser CSRF pre-login and post-fixation refresh, secure host-only HttpOnly SameSite session cookie, 401/403/429, logout and epoch revocation |
| Scope | A current enabled administrator only reads current authorized device groups/categories; disabled/revoked/wrong-group must show no rows |
| Empty vs unavailable | A genuine scoped database-empty list displays none; error/offline/invalid JSON never becomes empty or mock data |
| Browser origin | No credential-bearing network requests from localhost, alternate origin, public domain, or Next.js SSR/Server Actions |
| Projection | Never copy secret IP/MAC/employee IDs or unrelated inventory; no fabricated online/queue/heartbeat counts |
| Pagination | Bounded max 100, stable keyset, no cross-group leaks or duplicate/cursor/order errors |
| Reconnect | Session invalidation must clear queries, avoid stale data and require new authorization before resuming |
| Accessibility | Keyboard navigation, labels, responsive table and reduced-motion browser evidence |
| Next.js cutover | Switch default frontend only after route parity and separate browser security tests; remove Vite/proxy deliberately |

**Do not enable Orval React Query hooks or the staged transport in
pages before these release gates pass.** CI typechecking only is not
evidence of an authenticated HTTPS browser, real PostgreSQL browser access, cookie
isolation or a connected device. The independent database JPA gate now passed.
