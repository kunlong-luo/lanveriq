# Private LAN deployment topology

**2026-11-08 部署状态更新：** 正式发布方案仍只是候选；`apps/web-next` 是主 Next.js，GitHub Pages 是断开式静态预览，Caddy 参考模板不授权直接部署。完整 Caddy/Tomcat/PG18/Chromium 有以前的**合成安全成功记录**，但新 Batch A1 heartbeat POST **未进行真正 Agent 网络 L2**。代理服务器到 Spring 的 TLSv1.3-only **不能证明** Agent→Caddy 也只协商 TLSv1.3（Rust 客户端目前 min TLS1.2，Caddy 外部版本策略待验收）；上线前要经受控 CA/网络隔离、双向握手/降级、撤销/重放、真实设备/隐私和恢复演练。具体见 [设备协议与 P0 差距](device-communication-stack.md) 与 [v0.1 发布门槛](../operations/release.md)。


## Starter host (proposed)
Ubuntu Server 24.04 LTS (or verified compatible server), 4–8 vCPU, 8–16 GiB RAM, 100+ GB SSD for system+PostgreSQL depending on measured event rate, **separate media disk/NAS** sized to codec/retention. Initial 1–10 endpoints. No K8s/Kafka. **Optional Redis 8** may reuse the existing private `spark-redis` solely for best-effort short-lived hints and connectivity health; PostgreSQL remains authoritative.

Browser services remain behind a **single same-origin HTTPS/WSS admin site**. The original D0 architecture assumed Caddy-hosted React/Vite static files. The maintainer subsequently selected **Next.js App Router** as the front-end target: its SSR/RSC features require a **private Next.js runtime**, not a static-file-only Caddy deployment. The current isolated test topology is Caddy (HTTPS) → private Next.js for page/RSC/static routes, and separately Caddy → Spring Boot (mutually authenticated upstream TLS) for `/api/v1/*` plus server-side session authorization of protected HTML/RSC. Spring owns role/group authorization and its native WebSocket/in-process notification hub; PostgreSQL remains isolated. **This is a reviewed candidate for future topology, not an approved production Caddyfile or deployment.** An enrolled Agent mTLS site uses a separate hostname/vhost on the Caddy TCP 443 listener so its client-certificate requirement does not block browser login or enrollment. Require h1/h2 and strict SNI/Host. Rust Agent runs natively on clients, never in the server Compose stack.

## Network trust
- D0-selected pilot default hostnames (not yet provisioned): `console.lanveriq.internal` for browser + WSS, `enroll.lanveriq.internal` for one-use pairing/CSR, and `agent.lanveriq.internal` for **client-certificate-required** Agent REST. All are LAN-local HTTPS hosts; the browser remains same-origin within its console host. Use trusted internal server CA roots deployed **explicitly** to authorized machines. The dedicated Agent client-identity CA is a distinct trust purpose, with operator-mediated CSR signing in the proposed low-volume pilot. The initial proposal is Caddy `tls internal`; see [LAN bootstrap and trust](enrollment-bootstrap.md) for root certificate provisioning and CA-key backup.
- HTTPS is required even on LAN. Do not disable certificate verification.
- Only HTTPS management/API port available from authorized local subnets. PostgreSQL 5432 must not be published to LAN. Restrict container volumes and admin SSH.
- Device pairing uses a temporary scoped token + CSR on a separately protected **pre-enrollment TLS host**. The provisional device remains `pending_certificate` until an authorized operator signs/records/delivers a clientAuth leaf and the Agent proves mTLS possession. Thereafter, enrolled Agent routes use unique revocable **client certificates (preferred mTLS design)** bound to `devices.id`. Caddy must strip untrusted forwarded-identity headers and Spring must trust identities only across the locked-down proxy boundary, then check active credentials against PostgreSQL. Hostname/CA issuance and rotation are **D0 design-selected but still unimplemented/unverified**; do not ship an untested certificate forwarding scheme. Per-device policy scopes remain enforced.
- No public ingress or Internet dependence at runtime. Limit or disable outbound network egress where practicable.

## NXT-11 Next.js deployment isolation review (OPEN; synthetic-only)

- **Exclusive ingress:** only authorized LAN clients may reach Caddy TCP 443. Never publish the Next.js listener, Spring upstream TLS listener, PostgreSQL, or optional Redis on a user-accessible interface. The NXT-10 disposable runner proves `next start --hostname 127.0.0.1 --port 3100` and nonloopback refusal **only in its runner topology**. A production process/network-namespace design has **not** been selected or verified. Docker `127.0.0.1` inside two different containers is not shared; any future private container network must explicitly prove Caddy-only reachability, firewall rules and direct-origin denial without publicly published ports.
- **Separate traffic classes:** Caddy must route `/api/v1/*` and authenticated WebSocket requests to Spring under its own session/CSRF/Origin/RBAC policy. It must authorize protected Next.js HTML and RSC requests **before** forwarding, strip caller-supplied internal proof headers, and apply a secret only on the private Caddy→Next hop. A Next.js internal guard is defense-in-depth, never a replacement for Spring authorization.
- **Public assets vs. private responses:** versioned `/_next/static/*` JS/CSS/fonts may be public and immutable if they contain **no session, internal proof, credential, scoped data or private source map** and do not vary across administrator identities. All authenticated HTML, RSC (`text/x-component`), API JSON and session responses must be non-shared and `no-store`. Audit redirects, rewrites, caching intermediaries, path normalization, asset routes and source map exposure independently.
- **Production blockers:** the checked-in `deploy/caddy/Caddyfile.reference` intentionally returns 503 for Console; the committed App Router does not mount real auth/data routes; no production Node/Caddy/process unit, container deployment, secret injection, rollback or actual account UX has been approved. Synthetic CI is not authorization to enable real administrators, employee data, Agent enrollment or monitoring.
- **Acceptance required before any activation:** document and exercise actual namespace/firewall listeners, direct-origin and forged-identity negatives, CDN/proxy cache behavior (if introduced), real HTTPS cookie/CSRF/session/permission revocation, keyboard/screen-reader/visual login usability, deploy/rollback and database backup/restore. Retain independent organizational/privacy sign-offs.

## Server configuration
Environment variables/secret files: public bind hostname, database connection/credentials, admin bootstrap, retention, data/media paths, TLS cert and private key path. Do not commit production secrets. First login must require credential change/secure bootstrap. Use local clock sync; time drift is tracked.

## Capacity
At 2 Mbps average, screen video ~21.6 GB/device/day; ten endpoints for 14 days ~3 TB raw before reserve and backup. Video is a separate optional milestone, not part of the first server disk budget.

## Dashboard transport
In v0.1, the dashboard uses scoped HTTPS REST queries for state plus a **same-origin authenticated WSS /ws** connection for metadata change notifications; WS is best-effort and clients resync from REST after reconnect. Caddy forwards WebSocket Upgrade and keeps connection idle timeout above the heartbeat. The in-memory notifier assumes **one Spring Boot instance**; the optional Redis dependency is **not** a WSS broker, presence authority or substitute for after-commit scoped notifications. No Redis Pub/Sub, STOMP, Kafka or WebRTC is enabled. See [WS contract](websocket-contract.md) and [ADR-0005](adrs/0005-rest-plus-websocket.md).

## Installation and operations
See [runbook](../operations/installation.md), [backup](../operations/backup.md) and [monitoring](../operations/monitoring.md). The one-command `docker compose up -d` experience is a **future goal**; no compose file exists yet.

**Diagrams:** [private LAN topology](../diagrams/deployment-topology.md) · [trust boundaries](../diagrams/trust-boundaries.md).

## Risk
If a host is compromised, central recordings and audit data are sensitive. Strict RBAC, backups, patching and storage permissions are required before real employee data.
