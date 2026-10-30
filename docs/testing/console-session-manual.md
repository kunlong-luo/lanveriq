# Staged console browser-authentication acceptance (NOT EXECUTED)

**Status:** implementation and unit tests exist; the console authentication feature is **disabled by default**, and enabling it is **not yet approved for a managed-device pilot or production**. The real PostgreSQL 18 Flyway V1–V4 and first-admin bootstrapping checks, real TLS/DNS and strict Caddy host isolation, login throttling, browser CSRF/session verification and revocation tests remain separate acceptance requirements.

## Code and trust boundary

- `ConsoleSessionSecurityConfiguration` is conditional on `lanveriq.console.auth.enabled=true` AND a Servlet web application. This is a tightly scoped `/api/v1/auth/**` security filter chain; other business endpoints continue to hit the independent **deny-all** chain even if auth is switched on.
- **Default:** `LANVERIQ_CONSOLE_AUTH_ENABLED=false`. If an isolated synthetic lab explicitly enables it, it must also configure `LANVERIQ_CONSOLE_ORIGIN=https://console.lanveriq.internal` (or a separately reviewed canonical console hostname). The startup refuses empty, HTTP, userinfo, path/query, noncanonical ports or ambiguous origins.
- The browser login/logout uses **Spring Security built-in** session authentication and form login. `GET /api/v1/auth/csrf` returns a session-bound CSRF token in a `Cache-Control: no-store` JSON response; the browser must submit it in `X-CSRF-TOKEN` on `POST /api/v1/auth/login` and `POST /api/v1/auth/logout` (form-urlencoded username/password for login). Login success responds HTTP 204 and invalid credentials HTTP 401; logout responds 204. **Never send admin passwords through URLs, logs, terminal command-line arguments or CI.**
- Before CSRF, logout or login handlers run, `ConsoleRequestGuardFilter` requires exactly one matching console `Host`; state-changing requests require exactly one **exact HTTPS `Origin`** and reject any supplied `Sec-Fetch-Site` value other than `same-origin` or `none`. Cross-site/same-site origins, duplicate headers and mismatched vhosts fail with 403.
- Session cookie: `__Host-lanveriq-session`, `Secure`, `HttpOnly`, host-only, `Path=/`, `SameSite=Strict`, 15 minute inactivity timeout. Session fixation rotates the identifier on successful authentication. `server.forward-headers-strategy=none` avoids automatically trusting forwarded client assertions. Proxy must preserve the authoritative **console Host** and terminate trusted HTTPS, not forward arbitrary user-supplied identity headers.
- This does **not** grant access to `/api/v1/devices`, inventory or WSS. No role scope is inferred from an authenticated session. Operations still require per-request server-side group/category authorization.
- **Missing before use:** bounded login attempt throttling/abuse handling, login/logout/security-access audit, explicit active/disabled account revalidation on session reuse, role/grant revocation propagation, CSRF token lifecycle and Caddy h1/h2/strict SNI/Host connection reuse verification, secure browser test, WSS scope. Do not enable the feature flag beyond an isolated synthetic manual test until these checks are implemented and recorded.

## Manual test matrix — evidence NOT YET COLLECTED

Use only a synthetic admin account and an isolated test-only PostgreSQL 18 instance. Run the separate [PostgreSQL/first-admin runbook](server-postgres18-manual.md) first. Test via a browser with the independently trusted Caddy **server CA** installed and out-of-band verified; do not use a browser TLS exception or `curl -k`.

| Test | Expected |
|---|---|
| No feature flag, GET /api/v1/auth/csrf and POST login | No publicly exposed authentication; deny-all behavior |
| Feature opt-in with missing/HTTP/invalid console origin | Fail application startup rather than accepting login |
| Trusted HTTPS console, same-origin GET csrf | JSON CSRF token, `Cache-Control: no-store`, host-only Secure/HttpOnly/SameSite cookie |
| POST login with no/invalid CSRF token | 403; no authenticated session |
| POST login with wrong password and valid CSRF/Origin | 401; no authenticated session |
| Valid synthetic admin login with CSRF and exact Origin | 204; fresh session identifier (old fixation ID invalid) |
| Cross-origin, repeated Origin, agent/enroll Host, wrong port, `Sec-Fetch-Site: cross-site` or `same-site` | 403 before login/logout processing |
| POST logout with correct CSRF/Origin | 204; session invalidated and CSRF cleared |
| Login then access protected `/api/v1/devices` or sensitive IP/MAC | Still denied (no dashboard permission exposed) |
| Revoke/disable account after login, expired sessions, Caddy host+SNI mismatches, h2 connection reuse | **NOT YET IMPLEMENTED/ACCEPTED**; manual security gate must remain open |

Evidence must include the Git SHA, tested OS/browser, Java/PG18/Caddy versions, sanitized HTTP statuses, TLS/CA fingerprint provenance and any denial logs **without passwords, session cookies, CSRF tokens or sensitive employee data**. The Java 25 CI runs only unit tests and `bootJar`; it does not start an authenticated HTTPS browser or database.
