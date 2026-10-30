# Threat model (STRIDE-lite)

## Protected assets
Device credentials and policy, OS event records, application intervals, recordings, user accounts, server database, backup archives, authenticated browser sessions and administrator activity trails.

## Trust boundaries
Managed endpoint → LAN → Caddy → Spring Boot modules → PostgreSQL and NAS; admin browser → TLS API; session-local recorder → storage. Endpoints and local administrators are not treated as tamper-proof; LAN membership is not identity.

| Threat | Risk | Mitigation / acceptance |
|---|---|---|
| Spoofed device upload | fabricated activity | per-device keys, binding to device ID, revocation |
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

## Validation
Threat review is repeated when adding a data category, modifying privileges, enabling capture or broadening admin rights. Track open threats as documentation backlog until a real security test/hardening stage.
