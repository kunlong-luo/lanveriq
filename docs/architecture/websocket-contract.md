# Dashboard WebSocket protocol — v0.1 design

**Status:** Proposed contract, not implemented. **Endpoint:** `wss://<configured-lan-host>/ws` (same-origin as the dashboard). **Scope:** server → authenticated browser notifications. **Agent traffic remains HTTPS REST.** See [ADR-0005](adrs/0005-rest-plus-websocket.md), [ADR-0006](adrs/0006-agent-rest-dashboard-ws.md) and [REST API](api-contract.md).

## Why and what is out of scope
The WebSocket channel reduces device-status/alert latency for 1–10 machines. It **does not** carry raw activity logs, screen recordings, video frames, arbitrary remote commands, authentication secrets or durable event acknowledgments. PostgreSQL + scoped REST queries remain the system of record; notifications are ephemeral hints to invalidate/refetch React Query data.

## Transport and authentication
- Caddy terminates TLS and forwards authenticated upgrade requests to Spring Boot's **native WebSocket handler** (no separate broker, STOMP service or Redis for v0.1).
- Browser uses an existing **Secure, HttpOnly, SameSite** admin session cookie. The server checks authentication during upgrade, exact allowed `Origin`, active session, role and device-group permissions; reject unauthorized upgrades with an HTTP error. Do **not** send access tokens in query parameters, logs or browser localStorage.
- WSS only; reject `ws://` outside localhost-only synthetic development. Restrict Caddy to authorized LAN networks.
- Cookie-based, read-only WS messages do not mutate server business state; **all** policy, pairing and other writes go through CSRF-protected REST APIs. Client requests to subscribe/unsubscribe are never authorization grants.
- Apply per-user connection count (proposal: 5), message-size cap (proposal: 64 KiB), outbound queue cap, and rate limits. Close slow clients rather than allowing unlimited memory.
- Revalidate auth/permissions on subscription, device/group changes, periodic lease and before every event dispatch. Close connections on logout, account/session revocation and permission changes (as practical); client must refetch after reconnect.

## Message envelope (server → browser)
~~~json
{
  "protocol_version": 1,
  "type": "device.status.changed",
  "notification_id": "bf4c1a31-417f-4fe0-9b18-1d4e144e15c0",
  "server_time": "2026-10-30T08:40:00Z",
  "device_id": "a0e51319-7c8c-4557-85d3-187d91ee5213",
  "payload": {
    "status": "online",
    "last_seen_at": "2026-10-30T08:39:58Z"
  }
}
~~~

- JSON UTF-8; `protocol_version` is a **number**. Every message includes `type`, `notification_id`, `server_time`, and `payload`. `device_id` is included for device-scoped messages; omit for global/system messages. Additional fields must be additive and optional within the same major protocol version.
- Payloads carry **minimal metadata** (status, counts, timestamps), not user activity titles/URLs/content or videos. Device/group names are scope-filtered.
- Notifications are not guaranteed to arrive in order, exactly once, or at all. `notification_id` helps clients deduplicate within a connection, but must not be used to acknowledge or purge Agent events.

## Initial message types
| Type | Milestone | Minimal payload / effect |
|---|---|---|
| `system.ready` | v0.1 | `{connection_id, server_time, protocol_version}` for connection health; then REST refresh |
| `device.status.changed` | v0.1 | `{status, last_seen_at}` refresh scoped device list/detail |
| `agent.backlog.changed` | v0.1 | `{queue_count, queue_bytes, integrity_degraded}` refresh device health; throttle/coalesce |
| `device.enrollment.changed` | v0.1 | `{enrollment_state}` after admin enrollment/revoke transaction |
| `collector.state.changed` | v0.2 | `{source, state, observed_at}` refresh collector status |
| `audit.ingest.available` | v0.2 | `{latest_received_at}` refresh visible timeline; never include raw event |
| `recording.session.changed` | v0.3 | `{state, session_id}` only for separately entitled, case-scoped viewers |
| `recording.upload.failed` | v0.3 | `{session_id, failure_code}` restricted video-health notification |
| `system.resync_required` | v0.1 | `{reason}` client must refetch all authorized REST resources |

`device.heartbeat.received` may be **coalesced** into device-status/health updates rather than pushed every 30s. For an offline transition with no incoming HTTP request, a server scheduler detects the state change and emits `device.status.changed` only **after** state transition is determined.

## Browser → server frames
Initial version only supports:
~~~json
{"protocol_version":1,"type":"subscribe","request_id":"req-001","device_ids":["a0e51319-7c8c-4557-85d3-187d91ee5213"]}
~~~
- `device_ids` is a bounded allowlist of requested visible devices (max 10 in v0.1); empty means no device-specific subscription. Server intersects request with active server-side scope. Do not allow wildcards, raw SQL filters, arbitrary topic names or admin commands.
- Reply `subscription.accepted` with only authorized `device_ids`, or `subscription.rejected` without leaking hidden device existence. Refresh subscription after any role/group change. A dashboard overview may request all **currently entitled** devices using REST first; it never subscribes to the entire LAN by client claim.
- Browser may send `unsubscribe` (same request_id/device_ids shape); all other inbound message types are unsupported. Browser WebSocket Ping is protocol-level, not an application event.

## Connection and resynchronization
1. Load authenticated dashboard shell. Open WSS and wait for `system.ready`.
2. **Close the initial snapshot/subscription gap deliberately:** after `system.ready`, fetch an initial scope-filtered REST device-ID list, send `subscribe` for those IDs and wait for `subscription.accepted`, **then fetch the authoritative REST snapshot again**. While that final snapshot is in flight, buffer/coalesce any matching WS hints and invalidate/refetch once it settles. Do not declare the view synchronized from a snapshot taken before the subscription was active.
3. On a notification, invalidate matching TanStack Query keys and fetch on demand; coalesce bursts (draft: 250–1000 ms).
4. On disconnect or server restart, mark feed as `reconnecting`; retry with exponential backoff + jitter (draft base 1 s, cap 30 s), stop after explicit 401/403 until re-login.
5. After **every** successful reconnect, re-authenticate, refresh the authorized device list, resubscribe and take a **post-subscription REST snapshot** of affected resources. Notifications can be lost/reordered; no replay cursor is promised.
6. Server sends WS protocol ping periodically (draft: 30 s) and closes idle/dead connections (draft: 90 s). Proxies must permit idle timeout longer than ping interval.
7. If server outbound queue exceeds its per-connection cap, issue `system.resync_required` when possible, close with appropriate code and rely on REST reconnect; do not block ingestion.

## Publishing rules
The Spring service uses an **in-process dispatcher** on a single app instance. Dispatch notifications **only after the PostgreSQL commit succeeds** (e.g. transaction-after-commit callback); never publish if transaction rolls back. A periodic timer detects expired heartbeats; read-only device status can be computed from latest server receive time. No event bus or queue in v0.1.

## Security and privacy tests
- Rejected anonymous/expired session; rejected foreign `Origin`; no cookie/token in URL.
- Cross-group subscription denied, and no cross-group events leak when group changes mid-session.
- Revoke/logout terminates delivery, recording notifications remain case scoped.
- Inbound payload size cap, malformed JSON, unsupported version, flood/slow consumer limits.
- Server emits after commit only; failure/rollback emits nothing.
- Client initial connect/reconnect closes the snapshot-to-subscribe race using a post-subscription REST refresh; duplicate/out-of-order events are harmless. Test a status update occurring after the first REST list response but before `subscription.accepted`.

## Diagram
See [WebSocket sequence](../diagrams/websocket-flow.md) and [overall component architecture](../diagrams/component-architecture.md).
