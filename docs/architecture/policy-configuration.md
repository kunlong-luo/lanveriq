# Configuration and collection policy contract

**Status:** proposed, not implemented. This document separates **local Agent runtime settings** from **organization collection policy**. A server administrator may not silently expand private data collection beyond a previously published notice and OS permissions.

## Configuration ownership
| Category | Owner | Examples | Sensitive rule |
|---|---|---|---|
| Build defaults | app release | queue cap, timeouts, schema compatibility | never include credentials |
| Machine bootstrap | local installer | server URL, internal CA trust, enrollment token | token single-use; avoid command-line leakage |
| Agent runtime config | authenticated admin / machine | heartbeat, upload retry, storage path, logging | schema-validated; safe ranges |
| Organization collection policy | designated security admin | inventory field allowlist (including optional MAC/IP), allowed event sources, app metadata, working hours, retention, recording approval | versioned, disclosed, audited and least-privilege |
| OS recording permission | interactive OS/user session | screen source, portal session grant | cannot be overridden by server policy |

## Proposed initial values (not production defaults)
- Heartbeat interval: 30 seconds, adaptive retry on outage; stale >90 seconds; offline >300 seconds of **server-received** heartbeat absence.
- Batch cap: 100 events or 512 KiB (whichever lower). HTTP retry bounded exponential backoff with jitter and maximum delay; avoid storms after server restart.
- SQLite outbox: proposed max 1 GiB / 7 days with reserved gap metadata capacity. Warning thresholds at 70/90%, explicit degraded state at full.
- **V0.1 is not a real activity collector:** authenticated heartbeat, operational health and approved inventory use dedicated endpoints; synthetic test-only events exercise the bounded audit outbox. V0.2 adds **explicitly allowlisted** OS audit and coarse application-activity event sources. No real activity scope is enabled merely because test ingestion exists.
- Recording: **disabled by default**; v0.3 separate approved policy with display of current capture state; audio off.
- All values belong to one typed, documented schema with min/max and source of truth. Production values may change after pilot measurement.

## Policy lifecycle
1. Security admin drafts policy -> identifies source fields, purposes, devices, schedule, retention and notice version.
2. Organization completes required employee notice and approval before activating more sensitive collection.
3. Server publishes immutable `policy_version` and hash; Agent verifies signature/authenticated transport and validates scope.
4. Agent stores last accepted policy digest. On a broader policy, update requires documented approval and any OS authorization; if unavailable, **do not collect newly requested category**.
5. On network outage continue only previously authorized, nonexpired scope as local policy permits; **never treat missing configuration as permission to capture**. Missing or invalid policy causes sensitive collectors to stop and reports degraded state.
6. Revocation/denial disables specified source, records a policy state transition and prevents future collection. Data already retained follows organizational deletion rules.

## Upgrade/downgrade
Each policy and Agent configuration advertises `min_agent_version`, supported capabilities, policy hash and timestamp. Unsupported fields must be rejected or disabled explicitly, not silently interpreted as permission. No downgrade that broadens collection scope.

## Data classification
All endpoints/exports use classifications `public_metadata`, `internal_operational`, `sensitive_activity`, `restricted_media`. Roles and device scopes apply in addition to classification. Secret material is never included in policy/heartbeat or client logs.

## Operational validation
Schema roundtrip; invalid policy rejection; scope expansion fails closed; disconnected policy handling; device group reassignment; clock skew; OS permission revoked; no false positive "recording" status.
