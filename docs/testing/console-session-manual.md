# Staged console browser-authentication acceptance (NOT EXECUTED)

**Status:** implementation and unit tests exist; the console authentication feature is **disabled by default**, and enabling it is **not yet approved for a managed-device pilot or production**. The real PostgreSQL 18 Flyway V1–V5 and first-admin bootstrapping checks, real TLS/DNS and strict Caddy host isolation, browser CSRF/session verification, throttle behavior and session revocation integration tests remain separate acceptance requirements.

**Additional synthetic Web-client evidence (2026-11-01):** A non-imported `apps/web/src/auth/sessionTransport.ts` provides a staged, native-fetch HTTPS same-origin + CSRF/session sequence with in-memory fake-fetch negative tests in `apps/web/src/auth/sessionTransport.test.ts`. No credentials are accepted on the `/login` preview; none of the functions are called by runtime UI. These tests validate request construction and fail-closed response handling only, **not a browser's automatic Origin header, cookies, session fixation over actual TLS, Caddy isolation, PostgreSQL transactions or any real login**. Manual evidence in the table below is still **NOT YET COLLECTED**.

## Code and trust boundary

- `ConsoleSessionSecurityConfiguration` is conditional on `lanveriq.console.auth.enabled=true` AND a Servlet web application. This is a tightly scoped `/api/v1/auth/**` security filter chain; other business endpoints continue to hit the independent **deny-all** chain even if auth is switched on.
- **Default:** `LANVERIQ_CONSOLE_AUTH_ENABLED=false`. If an isolated synthetic lab explicitly enables it, it must also configure `LANVERIQ_CONSOLE_ORIGIN=https://console.lanveriq.internal` (or a separately reviewed canonical console hostname). The startup refuses empty, HTTP, userinfo, path/query, noncanonical ports or ambiguous origins.
- The browser login/logout uses **Spring Security built-in** session authentication and form login. `GET /api/v1/auth/csrf` returns a session-bound CSRF token in a `Cache-Control: no-store` JSON response; the browser must submit it in `X-CSRF-TOKEN` on `POST /api/v1/auth/login` and `POST /api/v1/auth/logout` (form-urlencoded username/password for login). Login success responds HTTP 204 and invalid credentials HTTP 401; logout responds 204. **Never send admin passwords through URLs, logs, terminal command-line arguments or CI.**
- Before CSRF, logout or login handlers run, `ConsoleRequestGuardFilter` requires exactly one matching console `Host`; state-changing requests require exactly one **exact HTTPS `Origin`** and reject any supplied `Sec-Fetch-Site` value other than `same-origin` or `none`. Cross-site/same-site origins, duplicate headers and mismatched vhosts fail with 403.
- Session cookie: `__Host-lanveriq-session`, `Secure`, `HttpOnly`, host-only, `Path=/`, `SameSite=Strict`, 15 minute inactivity timeout. Session fixation rotates the identifier on successful authentication. `server.forward-headers-strategy=none` avoids automatically trusting forwarded client assertions. Proxy must preserve the authoritative **console Host** and terminate trusted HTTPS, not forward arbitrary user-supplied identity headers.
- This does **not** grant access to `/api/v1/devices`, inventory or WSS. No role scope is inferred from an authenticated session. Operations still require per-request server-side group/category authorization.
- **Source now includes, but is NOT runtime-verified:** Bucket4j 8.21.0 + Caffeine 3.3.1 bounded, single-instance login limits (5 attempts per canonical lowercase account/10 min, 30 attempts per transport peer/min); 8 KiB known-length form body gate; `429 Retry-After: 60` on token-bucket throttle and **429 Retry-After: 1** when two concurrent Argon2id verifications are already in progress; independent `admin_auth_log` for allowed, denied and throttled login and known-user logout. Authentication audit stores only verified UUIDs for successful actions; unknown login attempts remain unlinked. Login fails closed if success audit cannot commit.
- **Source also includes, but is NOT runtime-verified:** `users.session_version` V5 monotonic epoch. Browser-auth requests check live enabled account and matching version after context restoration; mismatches invalidate the session and return 401. `AdminSessionRevocationService` can transactionally rotate the target's epoch only if an active owner and target belong to the same group; it logs an allowed admin action. No network revocation endpoint is currently exposed. Multi-node rate limiting requires a separate reviewed distributed backend.
- **Missing before use:** real browser login/logout and CSRF token lifecycle, actual Caddy h1/h2/strict SNI/Host connection reuse verification, real PostgreSQL rate-limited/denial audit and version-revocation integration, session-state revalidation on future REST/WSS business chains, IP-based ingress throttling behind Caddy, failure-mode and abuse testing, security-audit retention and endpoint scope tests, WSS scope. Do not enable the feature flag beyond an isolated synthetic manual test until these checks are implemented and recorded.

## Manual test matrix — evidence NOT YET COLLECTED

Use only a synthetic admin account and an isolated test-only PostgreSQL 18 instance. Run the separate [PostgreSQL/first-admin runbook](server-postgres18-manual.md) first. Test via a browser with the independently trusted Caddy **server CA** installed and out-of-band verified; do not use a browser TLS exception or `curl -k`.

| Test | Expected |
|---|---|
| No feature flag, GET /api/v1/auth/csrf and POST login | No publicly exposed authentication; deny-all behavior |
| Feature opt-in with missing/HTTP/invalid console origin | Fail application startup rather than accepting login |
| Trusted HTTPS console, same-origin GET csrf | JSON CSRF token, `Cache-Control: no-store`, host-only Secure/HttpOnly/SameSite cookie |
| POST login with no/invalid CSRF token | 403; no authenticated session |
| POST login with wrong password and valid CSRF/Origin | 401; no authenticated session; anonymous `admin_auth_log` denied row |
| Sixth login for one normalized account inside 10 minutes | 429, Retry-After 60; anonymous rate_limited audit |
| 31st login from same transport peer inside one minute across distinct accounts | 429; Caddy shared-peer behavior must be reviewed |
| Three parallel valid-length, valid-CSRF login attempts | Only two may enter memory-hard Argon2id verification simultaneously; third receives 429, Retry-After 1 |
| Invalid padded username `" owner "` five times then canonical `"owner"` | The legitimate canonical login retains its own separate per-account budget; both still consume the shared-peer budget |
| Login request with missing Content-Length or >8 KiB | 413 before parameter parsing; no login |
| Disable account or increment session_version after login; next /auth/csrf | 401, prior session invalidated |
| Invalid/mismatched group or inactive owner attempts session epoch rotation | No epoch change, no allowed access audit |
| Auth audit database failure during successful login | 503 and session invalidated (not false 204 success) |
| Valid synthetic admin login with CSRF and exact Origin | 204; fresh session identifier (old fixation ID invalid) |
| Cross-origin, repeated Origin, agent/enroll Host, wrong port, `Sec-Fetch-Site: cross-site` or `same-site` | 403 before login/logout processing |
| POST logout with correct CSRF/Origin | 204; session invalidated and CSRF cleared |
| Login then access protected `/api/v1/devices` or sensitive IP/MAC | Still denied (no dashboard permission exposed) |
| Revoke/disable account after login, expired sessions, Caddy host+SNI mismatches, h2 connection reuse | **NOT YET IMPLEMENTED/ACCEPTED**; manual security gate must remain open |

Evidence must include the Git SHA, tested OS/browser, Java/PG18/Caddy versions, sanitized HTTP statuses, TLS/CA fingerprint provenance and any denial logs **without passwords, session cookies, CSRF tokens or sensitive employee data**. The Java 25 CI runs only unit tests and `bootJar`; it does not start an authenticated HTTPS browser or database.
