# Monitoring Center — V0.1 screen specification

**Route:** `/monitoring`. **Status:** planned, not implemented. The core meaning of `监控中心` here is **fleet/device and collection health**, not undisclosed screen surveillance.

## Primary users and permissions
IT operator can view assigned device health, connectivity, queue and supported source status. Security auditor sees only permitted groups/metadata. Details of employee application content require separately authorized **Audit Center** access (v0.2); monitoring view must not become a shortcut around RBAC.

## UI composition
- Header: total enrolled, online, stale, offline, revoked; explicitly show last server refresh and WSS connection (connected/reconnecting/REST resync).
- Device grid/list: display label, OS, Agent version, allowed device group, `last_seen_at`, queue_count/bytes, policy digest/version, collector capability and error state. Default view is **not an employee screen thumbnail grid**.
- Device drawer: heartbeats and gaps, recent upload error categories, source permissions, collection paused/unsupported status; links to device inventory.
- Alerts: connection offline, queue quota, revoked credential, audit source unavailable, clock drift, storage capacity. Alerts are **operational signals**, not productivity or misconduct judgments.
- WSS updates are small scoped hints, not durable records; on first connection/reconnect retrieve authorized IDs, subscribe, then take a **post-subscription REST snapshot**.
- Admin actions: enroll/revoke (delegated), acknowledge an operational alert with reason/audit, view source capability; no arbitrary remote shell or hidden capture toggle.

## Initial REST and WSS contracts (draft)
- `GET /api/v1/monitoring/overview` — server-derived counts within authorized device groups; includes `as_of`.
- `GET /api/v1/monitoring/devices?cursor=&limit=` — bounded, scoped device-health summaries.
- `GET /api/v1/devices/{id}/collection-gaps` — source coverage and inferred server gaps; health metadata role-only permission distinct from sensitive audit contents.
- WSS `device.status.changed`, `agent.backlog.changed`, `device.enrollment.changed` are already specified; never push employee raw content.

## Acceptance
- Online/stale/offline derives from server receive time; fake clocks cover threshold edges.
- Wrong group yields no rows/counts/pushes. Device credential cannot call admin APIs.
- Disconnection triggers visible degraded status + bounded reconnect + REST reconciliation.
- No device reports "all recorded" merely because heartbeat succeeds.
- Test on managed Windows 11 and supplied Pop!_OS 24.04 KDE/KWin pilot (session type still unconfirmed); mark incomplete real-device results.

**Diagram:** [Monitoring Center closure flow](../diagrams/monitoring-center-flow.md). [Dashboard UX](dashboard-ux.md).
