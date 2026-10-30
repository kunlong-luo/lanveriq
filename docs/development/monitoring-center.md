# Monitoring Center — V0.1 screen specification

**Route:** `/monitoring`. **Status:** planned, not implemented. The core meaning of `监控中心` here is **fleet/device and collection health**, not undisclosed screen surveillance.

## Primary users and permissions
IT operator can view assigned device health, connectivity, queue and supported source status. Security auditor sees only permitted groups/metadata. Details of employee application content require separately authorized **Audit Center** access (v0.2); monitoring view must not become a shortcut around RBAC.

## UI composition
- Header: total enrolled, online, stale, offline, revoked; explicitly show last server refresh and WSS connection (connected/reconnecting/REST resync).
- Device grid/list: display label, OS name/version, **primary reported IP (labeled)**, Agent version, assigned group, `last_seen_at`, queue_count/bytes, policy version, CPU/RAM/disk **summary metrics**, collector capability and error state. The server-observed remote IP is shown separately on detail if useful; neither IP nor MAC is used as identity. Default view is **not an employee screen thumbnail grid**.
- Device drawer: heartbeats and gaps, recent upload error categories, source permissions, collection paused/unsupported status; **Hardware/Network tab** with CPU model/core count, total RAM, OS/kernel/build, per-volume total/free space, multiple NICs and IPv4/IPv6 addresses, nullable MAC, source+freshness and approved asset tag. Detailed NIC/MAC values require role/group permission; never publish them in WSS frames.
- Alerts: connection offline, queue quota, revoked credential, audit source unavailable, clock drift, storage capacity. Alerts are **operational signals**, not productivity or misconduct judgments.
- WSS updates are small scoped hints, not durable records; on first connection/reconnect retrieve authorized IDs, subscribe, then take a **post-subscription REST snapshot**.
- Admin actions: enroll/revoke (delegated), acknowledge an operational alert with reason/audit, view source capability; no arbitrary remote shell or hidden capture toggle.

## Initial REST and WSS contracts (draft)
- `GET /api/v1/monitoring/overview` — server-derived counts within authorized device groups; includes `as_of`.
- `GET /api/v1/monitoring/devices?cursor=&limit=` — bounded, scoped device-health summaries; OS/CPU/RAM/disk and optional summary primary IP.
- `GET /api/v1/devices/{id}/inventory` — scoped full hardware, network and storage detail, multiple NICs/IPs.
- `GET /api/v1/devices/{id}/metrics` — time-range and size bounded CPU/RAM/disk trend.
- `GET /api/v1/devices/{id}/collection-gaps` — source coverage and inferred server gaps; health metadata role-only permission distinct from sensitive audit contents.
- WSS `device.status.changed`, `agent.backlog.changed`, `device.enrollment.changed` are already specified; never push employee raw content.

## Data freshness and privacy
Show `inventory_last_received_at` separately from `last_seen_at`. A 30s heartbeat does **not** imply a fresh disk/NIC/OS inventory. A system with multiple NICs may have no unambiguous primary IP; do not fabricate one. MAC/serial values are not broadcast as WSS notifications, and no public support screenshot may contain actual employee device identifiers. See [inventory contract](../architecture/device-inventory.md).

## Acceptance
- Online/stale/offline derives from server receive time; fake clocks cover threshold edges.
- Wrong group yields no rows/counts/pushes. Device credential cannot call admin APIs.
- Disconnection triggers visible degraded status + bounded reconnect + REST reconciliation.
- No device reports "all recorded" merely because heartbeat succeeds.
- Test on managed Windows 11 and supplied Pop!_OS 24.04 KDE/KWin pilot (session type still unconfirmed); mark incomplete real-device results.

**Diagram:** [Monitoring Center closure flow](../diagrams/monitoring-center-flow.md). [Dashboard UX](dashboard-ux.md).
