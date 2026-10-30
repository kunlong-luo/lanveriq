# Endpoint health, data completeness and incident semantics

**Status:** proposal. Do not equate a connected Agent with healthy collectors or uninterrupted recording.

## Independent states
- **Connectivity:** online, stale, offline, revoked; derived from latest authenticated **server received** heartbeat (not endpoint wall clock).
- **Collection by source:** supported, permission_required, collecting, paused_by_policy, permission_denied, degraded, unsupported.
- **Transport:** healthy, backlog, backoff, rejected, quota_exceeded, database_corrupt.
- **Recording (later):** disabled, awaiting_permission, recording, stopping, finalizing, uploading, paused, failed, unsupported.
- **Coverage:** complete_for_supported_source, partially_observed, gap_detected, unknown. Never label entire day "complete" because heartbeats exist.

## Initial heartbeat SLA (design defaults)
Heartbeat every 30 s. `online` if last receipt <=90 s; `stale` if >90 and <=300 s; `offline` if >300 s, unless device revoked. Server timestamps determine state. Separate last event ingestion timestamp and last successful recording upload timestamp. Clock drift warning: event times more than five minutes ahead/behind server time (pilot threshold only) marked suspect rather than silently adjusted.

## Gap origins
| Cause | Visible status |
|---|---|
| Service stopped/crash | connectivity stale/offline; server observed gap |
| User session unavailable | recording paused/gap while service may stay online |
| Wayland portal permission withdrawn | recording permission_denied; collection continues independently |
| Event source unsupported | unsupported, not zero usage |
| SQLite full or corruption | transport degraded and explicit collection gap as possible |
| OS suspend/restart | coverage unknown or gap with inferred range |
| Credentials revoked | revoked / upload rejected |
| Capped audit collector/permission denied | degraded source with reason |

## Chronology
Every event carries occurred_at (endpoint clock) and received_at (server clock). Display local timezone and source. An operator can see later arrivals, duplicate retries, source uncertainty and device clock offset. Gaps carry detected range, source and reason, including when only inferred by server.

## UI treatment
Use neutral labels and clear uncertainty: "No observations recorded" rather than "No activity"; "Screen recording unavailable" rather than "User idle"; never infer intent/productivity from absence of records. Exports include source, UTC offset, filter and coverage warnings.

## Tests
Use fake clock for heartbeat thresholds, crash/outage/recovery transitions, source state isolation and retention. Manual: lock screen, disconnect network, stop agent, deny portal and full disk; verify displayed status and audit trail.
