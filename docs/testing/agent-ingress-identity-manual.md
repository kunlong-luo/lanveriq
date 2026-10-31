# SRV-02-EDGE — trusted Agent mTLS ingress reference and acceptance gate

**Status (2026-10-31):** staged, **not approved for deployment**. `deploy/caddy/Caddyfile.reference` and the internal `AgentIngressAssertionPolicy` exist, with Java unit tests. No real Caddy adapt/validate, real TLS handshake, authentic mTLS identity forwarding, Docker port isolation or test-server PostgreSQL acceptance has occurred. No Agent REST controller exists and Spring continues to deny protected business paths.

## Trust model and hard stop

- D-07 requires three independent DNS/SNI hosts: `console.lanveriq.internal`, `enroll.lanveriq.internal`, `agent.lanveriq.internal`. The Caddy **reference** deliberately returns 503 on console/enroll rather than deploying a login or pairing flow prematurely.
- Client-identity CA is separate from Caddy's internal **server** CA. The Agent TLS site requires a valid client certificate via `client_auth { mode require_and_verify; trust_pool file ... }`. Pilot explicitly supports **h1/h2 only**, enables `strict_sni_host on` and must not listen on UDP/443 or accept h3/QUIC. Do not change this to `request`, `require`, or `verify_if_given`.
- Before the Agent proxy sets `X-Lanveriq-Verified-Client-Fingerprint` from Caddy's certificate fingerprint placeholder, it strips the entire `X-Lanveriq-Verified-Client-*` family, `X-Forwarded-Client-Cert` and `SSL-Client-Cert`. The backend does **not** trust arbitrary `X-Forwarded-*` headers.
- `AgentIngressAssertionPolicy` is a **default-OFF internal component, not an authentication source by itself**. When explicitly instantiated for synthetic tests, it enforces canonical Agent Host, exact canonical device route and POST, a single lowercase 64-char fingerprint, no duplicate/alias headers, no forwarded client certificate header, rejection of the SHA256 of an empty certificate, and immediate TCP peer `127.0.0.1`. The resulting internal `VerifiedDeviceCertificate` still needs the existing active/pending credential + device binding **in PostgreSQL** every time.
- **Critical limitation:** a local process or unauthorized container sharing the loopback network namespace could forge the header and appear to be Caddy. The reference therefore MUST NOT be considered a secure identity bridge until the Caddy-to-Spring transport is demonstrably exclusive to Caddy (socket/namespace/OS permissions and inbound network policy) with independent bypass tests, and until the exact Caddy fingerprint format is proven to match the leaf's SHA-256 DER fingerprint. Do not open controllers just because the parser's unit tests are green.
- The sample Caddyfile's Agent reverse proxy points to a still-denied Spring backend. It is documentation for the manual synthetic gate, **not a ready deployment configuration**.

## Manual-only D-07 acceptance steps

Only on an **isolated synthetic test host**, after provisioning a public dedicated client CA certificate and separate server TLS trust roots:

1. Install a known pinned Caddy 2.x build. Run `caddy adapt --config deploy/caddy/Caddyfile.reference --pretty`, inspect JSON for **all three** SNI hosts, no overlapping protected handlers, the Agent `require_and_verify` client trust pool, `strict_sni_host`, protocols exactly `h1 h2` (no h3), and header strip/overwrite. Run `caddy validate --config ...` with actual synthetic client CA and server trust provisioned.
2. Verify DNS, server certificate chains, HTTPS trust root, clock and SNI for each hostname; ensure console/enroll **never** accept the protected Agent identity assertion. Test a valid *synthetic* client P-256 leaf and require real TLS handshake success on agent only.
3. Test no certificate, wrong CA, expired/revoked leaf, wrong EKU, mismatched SNI/Host (including h2 connection coalescing), cross-host requests, h3/QUIC and UDP/443. All unauthorized paths must fail before access to any internal business operation. A TLS-trusted-but-DB-revoked credential must be rejected by PostgreSQL-bound service verification.
4. Attempt forged, repeated, upper/lower/underscore alias and malformed identity headers on all three hosts. Verify that Caddy strips client-supplied assertions and **overwrites** them only after actual mTLS proof; observe and compare a nonempty fingerprint with a DER SHA-256 digest of the synthetic leaf. Never log a real certificate or private key.
5. Try connecting **directly** to Spring from another LAN host, other Docker network namespaces, ordinary local user processes, and alternate forwarded Host/X-Forwarded routes. These bypasses must be impossible; merely binding `127.0.0.1` does **not** protect against unauthorized local processes sharing the namespace. If bypass is possible, **BLOCK deployment** and redesign the backend transport/isolation before further work.
6. Run `cd apps/server && ./gradlew integrationTest` using actual disposable Testcontainers PostgreSQL 18.6 (or manually start the dedicated GitHub Action) to verify active/revoked/pending device binding. The normal unit CI does not execute Docker.
7. Maintain sanitized evidence: exact Git SHA, Caddy binary version and adapted config, trust-root identity, h1/h2 and connection-coalescing probes, accepted/denied request matrix, network isolation proof, database run link and image digest. Never upload real employee events or secrets.

## Automated scope (not sufficient)

The Java `AgentIngressAssertionPolicyTest` verifies only synthetic HTTP request parsing. It does **not** perform a TLS handshake, verify any client leaf, authenticate Caddy, prove upstream exclusivity, test Docker networking or authorize the pending REST APIs. All these remain mandatory external acceptance gates.

**Next development step after this reference:** provision and verify an isolated actual Caddy test ingress, eliminate the local spoofing bypass using a restricted transport before binding Agent REST controllers, and run the PostgreSQL 18.6 manual workflow. Until then, no real endpoint monitoring or production rollout.
