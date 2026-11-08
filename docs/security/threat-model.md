# Threat model (STRIDE-lite)

## Protected assets
Device credentials and policy, OS event records, application intervals, recordings, user accounts, server database, backup archives, authenticated browser sessions and administrator activity trails.

## Trust boundaries
Managed endpoint → LAN → Caddy → Spring Boot modules → PostgreSQL and NAS; admin browser → TLS API; session-local recorder → storage. Endpoints and local administrators are not treated as tamper-proof; LAN membership is not identity.

| Threat | Risk | Mitigation / acceptance |
|---|---|---|
| Spoofed device upload | fabricated activity | per-device keys, binding to device ID, revocation |
| Trusted proxy assertion spoofing | forging Agent identity by direct backend/extra client headers | server pinned upstream mTLS leaf, `client-auth=need`, loopback-only Spring TLS, strict SNI/Host/URI; Caddy strip untrusted identity headers **before** overwriting verified fingerprint; real direct-backend and spoofed-header negatives at Batch A L2 |
| TLS version downgrade | device→Caddy negotiates weaker TLS than intended | **P0 open:** Rust Reqwest currently sets TLS1.2 minimum; verify and lock approved TLS1.3-only policy at both Agent→Caddy and Caddy→Tomcat, test rejection of TLS1.2 independently (see [protocol stack](../architecture/device-communication-stack.md)) |
| PKI key theft / unapproved signing | adversary enrolls or reuses a device certificate | separate offline client CA, per-device CSR and OS secret custody, human-reviewed scope, expired/revoked/fingerprint negative tests, CA backup/rotation/incident recovery; real signer/device key persistence **not implemented** |
| False delivery confirmation | outbox deletion after HTTP 200/partial/stale/malformed ACK | strict Serde DTO (no extra fields for heartbeat/inventory), exact event batch accepted sequences, one local SQLite CAS transaction, negative timeout/replay/conflict tests; broker QoS is not DB-commit evidence |
| Cross-session or stale cache leakage | one admin sees revoked devices/cross-group rows | current DB scope checks, deny after revocation, cancel+purge Next.js Query cache, no-store, no sensitive WSS payload, synthetic A/B multi-admin/HTML/RSC negatives |

| Repeated event delivery | duplicate audit record | UNIQUE(device, sequence), idempotent ack |
| Compromised device | forged/deleted local logs | server last-seen, gaps, sequence discontinuity; no tamper-proof claim |
| LAN sniffing/MITM | stolen telemetry or tokens | verified TLS/internal CA; never plaintext HTTP |
| Pairing token theft | unauthorized enrollment | single-use, short TTL, limited scope, rate limiting |
| Administrator overreach | privacy violation | RBAC/device group, case approval, access audit, minimum necessary |
| Browser XSS/CSRF | session compromise | output encoding, cookie hardening, CSRF protections, CSP |
| File-path traversal | read arbitrary NAS files | opaque server-controlled object IDs, root confinement, auth |
| Media upload tampering | corrupt/replaced segment | hash/size/manifest validation and no arbitrary paths |
| DoS from compromised agent | logs or disk fill | quotas, batch caps, throttling, retention |
| DB or backup theft | sensitive data disclosure | host encryption, OS ACLs, protected backups, secret rotation |
| Dependency compromise | arbitrary code execution | lockfiles, patch review, checksum/signature validation |
| Wayland/OS bypass attempts | unauthorized capture | fail closed; never use bypass or hidden recording |

## Boundary on surveillance
No stealth install, covert webcam/voice or keylogging; explicitly exclude secret screen capture and unbounded personal content collection. Redacted security telemetry can run unattended **only after appropriate notice**.

## Dashboard WebSocket risks
- Cross-site WebSocket hijacking: strict Origin allowlist and Secure SameSite session-cookie validation.
- Improper device subscriptions: enforce role/group scope on subscribe **and each dispatch**; revoke stale subscriptions after group/session changes.
- Status leakage and memory exhaustion: only metadata, bounded frames and outbound queues, rate limits and slow-client closure.
- Missed or reordered notifications: treat WSS as hints only; reconnect/refetch authoritative REST state.

## Batch A protocol security gate (2026-11-08)

No real deployment may rely solely on a HTTP `X-Lanveriq-Verified-Client-Fingerprint` header, device UUID, MAC/IP, `X-Forwarded-*` values or loopback source address. A trustworthy mTLS identity requires **actual** Caddy peer validation, **actual** distinct Caddy upstream mTLS client certificate pinned in Spring Tomcat, exact Host/path/SNI + clean overwritten fingerprint, and a **live PostgreSQL** device-key/expiry/revocation check inside the same heartbeat transaction. Verify these under real synthetic Caddy/Tomcat/PG18/Agent networking, not only MockMvc. Check TLS version downgrade, wrong CA, same-group wrong leaf, revoked cert during retry, malformed/oversized JSON, replay and cache invalidation. The implementation currently exposes the heartbeat HTTP controller source only under **two explicit default-OFF flags**; no actual Agent service networking is authorized.

## Validation
Threat review is repeated when adding a data category, modifying privileges, enabling capture or broadening admin rights. Track open threats as documentation backlog until a real security test/hardening stage.
