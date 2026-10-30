# LAN operations troubleshooting — proposed runbook

**No production installation exists yet.** These are expected diagnostic paths, not observed incidents.

## Quick triage
1. Confirm server health, TLS hostname and certificate chain; never use `--insecure`.
2. Check PostgreSQL connectivity/migration state, Caddy reverse proxy and disk space.
3. Check Agent heartbeat, enrollment state, service version, SQLite queue size and error code.
4. Distinguish **Agent offline**, **collector unsupported**, **event backlog** and **recording permission denied**; they are different problems.
5. Check user role/device group scope; avoid broadening permissions just to "make the dashboard work".
6. Capture only synthetic/redacted diagnostics in issues; no passwords, recorded screens or employee metadata.

## Failure matrix
| Symptom | Likely causes | Safe next action |
|---|---|---|
| Dashboard not reachable | host resolution, Caddy/TLS/port 443 | inspect local DNS, Caddy status and firewall |
| Login works but WSS fails | Origin mismatch, expired session, proxy upgrade, cookie path | check exact Origin/session and proxy; do not disable validation |
| WSS connects but stale data | client missed push, query cache not invalidated | manually refresh scoped REST; reconnect/refetch logic |
| Other-group event visible | RBAC bug | stop pilot immediately, revoke access, preserve admin logs |
| Device cannot enroll | expired token, wrong trust root, duplicate enrollment | use new single-use token and verified CA |
| Online but no events | source disabled/unsupported, no policy grant | inspect capability/policy/gaps, not employee intent |
| Agent queue grows | network outage, server 429, DB rejection | inspect retry/backoff, space and event schema |
| Database integrity degraded | local SQLite corruption/full volume | quarantine and repair from known procedure; do not wipe evidence blindly |
| All clients show offline | server receiver failure/clock, LAN outage | check server received timestamps, Caddy and app health |
| Missing video (later) | OS grant revoked, lock/logout, disk full | report gap, **never bypass** OS capture protection |
| Video upload checksum fails | incomplete segment or corruption | re-finalize/retry only closed segment; audit failure |

## WSS-specific diagnostics
Check HTTP upgrade result (401/403), cookie validity, exact approved Origin, reverse proxy support for Upgrade/Connection, app handler limits, last server heartbeat and client exponential backoff. Avoid logging entire frames or sensitive payloads; log connection/request IDs and bounded error codes.

## Incident record
Record time in UTC, device pseudonym, affected role/group, failure category, last confirmed upload/WS notification and remediation. Keep log retention proportional and access controlled.
