> **2026-11-13 当前状态优先说明：** [Linux-first 上线差距与验收账本](release-readiness-2026-11-13.md) 是最新证据入口。`main` `758f8d9a` 的 Rust→Caddy→Tomcat TLS1.3→PG18 严格 ACK 已通过 [隔离 L2 #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857)，不应再按旧记录认定“完全未运行”。但整批 A/B/C/D/E、正式 Pop!_OS 设备运行、Next.js/WSS/生产部署仍未完成；首发范围收敛到 **Pop!_OS 24 Linux-only**，Windows 11 另行 L3 验收，远控 RD-2 继续 OFF。本文原有时间线保留为历史，任何生产或真实数据启用须单独获批。

# Lanveriq delivery roadmap — v0.1 business-loop priority

**Status as of 2026-11-08:** D0 technical baseline approved, code development underway through NXT-20; static Next.js B+C preview, source-only Agent/Java services and opt-in synthetic PG18/Caddy/Chromium checks exist. **The first real Agent → Spring/PG18 → authorized frontend business loop is NOT complete**, and public preview remains disconnected. The product owner approved **batched P0 feature development with three-level CI**, replacing a full synthetic E2E run on every small commit. See [new executable v0.1 delivery plan](v0.1-batched-delivery-plan-2026-11-08.md).

## Owner platform priority change — 2026-11-11

**Pop!_OS 24.04 LTS (COSMIC desktop) / Linux is the primary Agent development and acceptance target.** Focus v0.1 Batch A first on Linux `systemd` non-root state/key custody, secure Linux CSR→approved certificate, Caddy→Tomcat TLS 1.3 and PostgreSQL18 commit/ACK; then the approved COSMIC/Wayland-facing client workflows. Windows service SID/DPAPI/CNG packaging becomes **secondary cross-platform compatibility work**, not the critical path for Linux-first acceptance. Preserve prior Windows synthetic evidence, but do not spend the next milestones on Windows-only hardening while Linux business-loop acceptance is incomplete. Linux server/desktop behavior must be accepted on a real authorized Pop!_OS machine only after consent; Ubuntu 24.04 CI is a *proxy*, not a certified Pop!_OS runner. See [Linux-first systemd and private-key acceptance](../testing/linux-popos24-agent-acceptance.md).

## Documentation visuals
The [eight-image stakeholder gallery](../visuals/README.md) and [20 Mermaid engineering diagrams](../diagrams/README.md) are maintained alongside architecture decisions. **They are design proposals, not implemented features**; keep milestone labels and status synchronized with PRD and code as implementation proceeds.

## Gate D0 — document baseline (approved for coding 2026-10-30; real-data authorization OPEN)
Deliver/review PRD, user roles, data fields, platform capabilities, component boundaries, atomic event delivery, PG and SQLite models, policy configuration, administrator permission matrix, retention, threat model, manual test plan and development rules. See the [v0.1 step-by-step implementation plan](v0.1-implementation-sequence.md). Confirm operating jurisdiction/monitoring notice scope. D0 technical review is recorded; legal/organizational real-data permissions remain separate open decisions.

## v0.1 — end-to-end skeleton (priority P0)
1. Server bootstrap: Java 25/Spring Boot 4, PostgreSQL/Flyway migrations, Caddy HTTPS, Next.js 16+ App Router (canonical B+C UI; legacy Vite kept as reference).
2. Rust agent skeleton, embedded SQLite store with migrations and single writer.
3. Server-assigned UUIDv7 device identity, short-lived pairing, revocation, heartbeat, **hardware/OS/NIC/IP/MAC/volume inventory** and capability reporting; full inventory changes only at startup/change/24h, performance metrics sampled separately.
4. Durable event outbox, all-or-nothing bounded batch ingestion, immutable device sequence and per-sequence retry acknowledgments.
5. **Dedicated Monitoring Center** and Device Detail showing device group, last seen, CPU/RAM/disk usage, OS and role-restricted network interfaces, plus queue/error status via **scoped REST + authenticated WSS live notifications**, with REST snapshot/resync on connection/reconnect **and low-frequency visible-page/tab-resume reconciliation against silently lost WS hints**; [ADR-0005](../architecture/adrs/0005-rest-plus-websocket.md).
**Linux-only v0.1 exit:** an authorized Pop!_OS 24.04 real device enrolls using verified local CA trust, report CPU/RAM/disk/OS and multiple NIC/IP address inventory with freshness, show heartbeat, survive server outage, replay once; WSS pushes a scoped online/offline status change to the authenticated dashboard, which refetches REST after reconnect. Run applicable Linux MAN-001, MAN-003–013, inventory and WSS checks in an approved Pop!_OS pilot; MAN-002/Windows is a separate later platform release gate. Daily CI is path-scoped rapid checks; batch/acceptance tags run the larger synthetic integration and browser suite.

## v0.2 — activity and audit
Allowlisted Windows Event Log and Linux auditd adapters, supported application activity metadata, gap semantics, server timeline search and RBAC, event retention, administrator activity audit. Backend tests cover authorization, dedup, retention and schema constraints. **Exit:** a recorded event is searchable with correct source/device/time and unauthorized access is blocked in manual checks.

## v0.3 — authorized video (separate safety gate)
Tauri companion, policy display/capture status, OS permission adaptation, chunked segments/sha256, local quota and upload retry, and a **dedicated Recording & Playback Center** covering session catalog, approved case with device/time limits, gap-aware playback and view/export audit; retention and recording gaps. **Exit:** recording is observable and can be stopped, denied OS permission cannot be bypassed, permissions and playback require authorization, actual Windows/Linux target manually verified. No promise of full 24/7 coverage.

## v0.4 — usability and maintainability
Extend the required Linux v0.1 DEB/systemd/rollback with separately accepted Windows MSI (RPM after verified demand), log diagnostics, backup/restore, local offline upgrade procedure, secure defaults, multilingual entry docs and failure UX. Keep dependencies stable and code modular.

## v1.0 — production readiness (explicit future scope)
Reevaluate CI, add integration and E2E/platform tests, security audit and penetration/permission review, full installer verification, signed binaries, restore drills, issue triage, legal/privacy sign-off. Do **not** claim production readiness while CI is unit-only and cross-platform recording remains untested.

## Proposed milestone order
D0 → v0.1 → v0.2 → v0.3 → v0.4 → v1.0. Milestones are outcomes, not fixed time estimates. Each item links to an Issue when development starts. No AI or public cloud in roadmap.
