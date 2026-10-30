# Private LAN deployment topology

## Starter host (proposed)
Ubuntu Server 24.04 LTS (or verified compatible server), 4–8 vCPU, 8–16 GiB RAM, 100+ GB SSD for system+PostgreSQL depending on measured event rate, **separate media disk/NAS** sized to codec/retention. Initial 1–10 endpoints. No K8s/Redis/Kafka.

Browser services behind a **single same-origin HTTPS/WSS admin site**: Caddy (TLS + static React app) → Spring Boot REST API + **native WebSocket handler/in-process notification hub**. PostgreSQL stays on an isolated Docker network; media is a restricted mount. Browser WSS uses 443, not another public port. **An enrolled Agent mTLS site may have a separate hostname/vhost on the same Caddy 443 listener** so mandatory client certificates cannot block browser login or pre-enrollment pairing; this is a D0 design candidate, **not yet approved**. Docker Compose for backend only. Rust agent runs natively on clients, not in a desktop Docker container.

## Network trust
- Prefer LAN hostname `lanveriq.internal` or local static mapping with a trusted internal CA certificate deployed **explicitly** to authorized machines. The initial proposal is Caddy `tls internal`; see [LAN bootstrap and trust](enrollment-bootstrap.md) for root certificate provisioning and CA-key backup.
- HTTPS is required even on LAN. Do not disable certificate verification.
- Only HTTPS management/API port available from authorized local subnets. PostgreSQL 5432 must not be published to LAN. Restrict container volumes and admin SSH.
- Device pairing uses a temporary scoped token on a separately protected pre-enrollment path; afterward, enrolled Agent routes use unique revocable **client certificates (preferred mTLS design)** bound to `devices.id`. Caddy must strip untrusted forwarded-identity headers and Spring must trust identities only across the locked-down proxy boundary, then check active credentials against PostgreSQL. Hostname/CA issuance and rotation are **D-07 unresolved**; do not ship an unreviewed certificate forwarding scheme. Per-device policy scopes remain enforced.
- No public ingress or Internet dependence at runtime. Limit or disable outbound network egress where practicable.

## Server configuration
Environment variables/secret files: public bind hostname, database connection/credentials, admin bootstrap, retention, data/media paths, TLS cert and private key path. Do not commit production secrets. First login must require credential change/secure bootstrap. Use local clock sync; time drift is tracked.

## Capacity
At 2 Mbps average, screen video ~21.6 GB/device/day; ten endpoints for 14 days ~3 TB raw before reserve and backup. Video is a separate optional milestone, not part of the first server disk budget.

## Dashboard transport
In v0.1, the dashboard uses scoped HTTPS REST queries for state plus a **same-origin authenticated WSS /ws** connection for metadata change notifications; WS is best-effort and clients resync from REST after reconnect. Caddy forwards WebSocket Upgrade and keeps connection idle timeout above the heartbeat. The in-memory notifier assumes **one Spring Boot instance**; no Redis/broker/WebRTC is needed. See [WS contract](websocket-contract.md) and [ADR-0005](adrs/0005-rest-plus-websocket.md).

## Installation and operations
See [runbook](../operations/installation.md), [backup](../operations/backup.md) and [monitoring](../operations/monitoring.md). The one-command `docker compose up -d` experience is a **future goal**; no compose file exists yet.

**Diagrams:** [private LAN topology](../diagrams/deployment-topology.md) · [trust boundaries](../diagrams/trust-boundaries.md).

## Risk
If a host is compromised, central recordings and audit data are sensitive. Strict RBAC, backups, patching and storage permissions are required before real employee data.
