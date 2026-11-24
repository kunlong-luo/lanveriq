# 历史文档索引快照（2026-11-21 整理前）

> **ARCHIVED / 非现行指导。** 本文件是整理前 `docs/README.md` 的完整原文留档，仅供追查当时的阶段叙述与证据链接。其中“当前”“待完成”“CI 待跑”和旧 Vite/Next.js 路径均应按其原始日期理解，不得代替 [唯一当前状态](../CURRENT_STATUS.md)、[精简导航](../README.md) 或真实 GitHub Actions 结果。此归档不改变任何旧提交及运行事实。

---

> **2026-11-21 D4-C10 duplicate FIDO MDS identifiers — 5/5 SAME-SHA SYNTHETIC CI SUCCESS, PRODUCTION OFF:** [functional SHA `2368163e`](https://github.com/kunlong-luo/lanveriq/commit/2368163e588e6e94f74a748bdc6e162b9c4c6b9a) passed **5/5 exact-SHA synthetic CI gates**: [Java 25/Gradle unit and 8 required real JUnit XML methods #44893055170](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055170), [PostgreSQL 18.6 Testcontainers synthetic transactions #44893055206](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055206), [PostgreSQL integration-source compile #44893055072](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055072), [Next.js frozen install/tests/typecheck/build #44893055069](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055069), and [RD-2 default-OFF #44893055089](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055089). These tests use disposable synthetic PKIX/CRLs/signers, NOT official FIDO provenance, production trusted time or live MFA. [D4-C10 exact acceptance and blockers](../security/fido-mds-duplicate-identifiers-d4-c10-2026-11-21.md). No official FIDO trusted root, time, MDS source ingestion, real WebAuthn MFA or Pop!_OS physical acceptance; v0.1 NOT RELEASE READY.

> **2026-11-21 NXT-28 compact navigation — FOUR EXACT-SHA CI SUCCESSES (production OFF):** [Compact-height navigation acceptance](../development/frontend-compact-navigation-nxt28-2026-11-21.md) unified desktop/mobile white/ink navigation and tested internal scrolling/keyboard focus at 1024×520 and 390×520 without APIs or cookies. [functional SHA `e76cd345`](https://github.com/kunlong-luo/lanveriq/commit/e76cd34505dd8e49833ab9c0b61421288c43c353) passed FOUR same-SHA checks: [Next.js L1 #44882686781](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686781), [real Chromium #44882686713](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686713), [disconnected Pages build/deploy #44882686808](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686808), and [RD-2 default-OFF #44882686775](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686775). Chromium screenshot artifact: `nxt18-visual-qa-e76cd34505dd8e49833ab9c0b61421288c43c353` (ID `14698677524`). This accepts only the disconnected UI, not v0.1 production.

> **2026-11-21 A11-D4-C9 current security work (5/5 exact-source CI PASS; PRODUCTION OFF):** [FIDO MDS payload-entry structural screening and signed synthetic JUnit gates](../security/fido-mds-payload-screen-d4-c9-2026-11-21.md) are now the most recent server-side trust-chain work. This is **not** the older D4-C8/V29 database history guard and is **not** official FIDO root approval. Live WebAuthn MFA, Pop!_OS 24.04 hardware acceptance, collection, remote access and CA signing remain blocked.

> **D4-C9 exact-source acceptance — 5/5 SUCCESS:** functional SHA [`4dc3b5f1`](https://github.com/kunlong-luo/lanveriq/commit/4dc3b5f19d8d125d528e2c2eccf1905fb375e34d) passed [Java 25 / Gradle L1 #44881397946](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397946) (real JUnit XML proved **all seven mandatory verifier tests** executed), [PostgreSQL 18.6 Testcontainers real synthetic transactions #44881397931](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397931), [PostgreSQL integration source compile #44881397981](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397981), [Next.js fast source/build/HTTP #44881397980](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397980), and [RD-2 remote default-OFF #44881397956](https://github.com/kunlong-luo/lanveriq/actions/runs/44881397956). These cover **ephemeral synthetic signers/keys, offline signed-time claim and untrusted records only**; they do NOT establish an approved official FIDO signing source, licensed MDS usage, trusted time, source CRL provenance, live WebAuthn MFA or production readiness. **v0.1 NOT RELEASE READY**.

> **NXT-27 frontend evidence-state implementation (FOUR EXACT-SHA CI PASS; non-production):** Shared read-only status components now cover monitoring and device detail without inventing device data. [NXT-27 implementation and acceptance](../development/frontend-evidence-state-nxt27-2026-11-21.md). The only frontend remains `apps/web` Next.js on loopback port 3100, with all production capabilities OFF. [NXT-27 Chromium success #44880424398](https://github.com/kunlong-luo/lanveriq/actions/runs/44880424398); [same-SHA details](../development/frontend-evidence-state-nxt27-2026-11-21.md).

> **NXT-25 docs verification — passed:** **NXT-25 exact-source checks PASSED:** implementation SHA [`b37797d9da5d9d622df39c968b92547c2f00402d`](https://github.com/kunlong-luo/lanveriq/commit/b37797d9da5d9d622df39c968b92547c2f00402d) passed [Next.js L1 with documentation negative tests #44877774039](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774039), [real Chromium/UI/network isolation and 15 screenshots #44877774015](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774015), [disconnected static Pages build+deploy #44877774094](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774094) and [RD-2 remote default-OFF #44877774084](https://github.com/kunlong-luo/lanveriq/actions/runs/44877774084), **all on the same SHA**. Chromium artifact `nxt18-visual-qa-b37797d9da5d9d622df39c968b92547c2f00402d` (ID `14697086467`). This is documentation/preview-source acceptance, NOT release or live system approval.

> **2026-11-21 current Web baseline:** Only `apps/web` is runnable, containing **Next.js 16.4.5 / React 19 / Tailwind 4 / Base UI**, localhost port **3100**, public Pages **disconnected**. The old Vite app was removed at NXT-23, then Next.js moved from temporary `apps/web-next` into `apps/web` at NXT-24. [Exact-five-workflow NXT-24 verification](../development/nextjs-path-rename-nxt24-2026-11-21.md) passed at functional SHA `6ed47c47`; it does **not** prove WebAuthn MFA, FIDO MDS source-chain acceptance, live device API, real Pop!_OS 24.04 acceptance or release readiness. See [canonical UI README](../../apps/web/README.md).

> **2026-11-13 当前状态优先说明：** [Linux-first 上线差距与验收账本](../planning/release-readiness-2026-11-13.md) 是最新证据入口。`main` `758f8d9a` 的 Rust→Caddy→Tomcat TLS1.3→PG18 严格 ACK 已通过 [隔离 L2 #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857)，不应再按旧记录认定“完全未运行”。但整批 A/B/C/D/E、正式 Pop!_OS 设备运行、Next.js/WSS/生产部署仍未完成；首发范围收敛到 **Pop!_OS 24 Linux-only**，Windows 11 另行 L3 验收，远控 RD-2 继续 OFF。本文原有时间线保留为历史，任何生产或真实数据启用须单独获批。

# Lanveriq — documentation index

**Current baseline (2026-11-08):** D0 technical design approved; implemented Java 25/Spring Boot/PG18 unit & synthetic integration slices, Rust SQLx/reqwest + Linux/Windows tests, and canonical Next.js B+C disconnected preview. Batch A1 has a default-OFF Agent heartbeat POST source/strict Rust ACK, **but device registration→Agent runtime→Caddy→PG18→authorized UI is not yet an integrated product**. Full synthetic and real-device legal/operational releases are separate. Active [batch plan](../planning/v0.1-batched-delivery-plan-2026-11-08.md), [actual stack](../development/technology.md) and [2026-11-08 doc audit](../governance/documentation-audit-2026-11-08.md). Historical runbook statuses must be read as timestamped evidence, not the most recent result. See [中文介绍](../../README.zh.md).

## Canonical frontend — current versus historical records

- **[NXT-26 Base UI Select consistency — four same-SHA workflows passed](../development/frontend-base-ui-select-nxt26.md)** — replace browser-native group/sort menus with the existing Base UI component in the shared directory; disabled by default, synthetic fixture only for real interaction verification.


- **[Run the one and only Next.js UI](../../apps/web/README.md)** — Node 24, pinned pnpm 12, `cd apps/web && corepack pnpm dev`, `http://127.0.0.1:3100/overview`; disabled login/API and disconnected public preview.
- **[NXT-24 path rename and five-workflow CI evidence](../development/nextjs-path-rename-nxt24-2026-11-21.md)** — active `apps/web`, `web-unit.yml`, Chromium, GitHub Pages and RD-2/Java safety checks.
- **[NXT-23/NXT-24 one-UI history and current source guards](../development/nextjs-single-ui-consolidation-2026-11-21.md)** — explains why the old Vite `apps/web` was removed and the name later reused by Next.js. This avoids an obsolete instruction to delete the current `web-unit.yml`.
- **[Current stack](../development/technology.md)** · **[engineering conventions](../development/standards.md)** · **[current visual source and tokens](../development/frontend-design-system.md)** · **[ADR-0008 framework authority](../architecture/adrs/0008-nextjs-app-router-console.md)**.
- **Historical only:** 2026-10 and early 2026-11 Vite/NXT-01—03 notes and screenshots are not current launch commands or production acceptance records.

## Product
- [Requirements and scope](../product/requirements.md)
- [Functional closure by milestone](../product/functional-closure.md)
- [User stories and flows](../product/user-stories.md)
- [Quality requirements](../product/nonfunctional.md)

## Architecture
- [System overview and trust boundaries](../architecture/system-overview.md)
- [Component decomposition](../architecture/components.md)
- [Storage and event models](../architecture/data-model.md)
- [Device identity: UUIDv7 enrollment, re-enrollment and replacement](../architecture/device-identity.md)
- [Hardware/OS/network inventory and sampling](../architecture/device-inventory.md)
- [Agent local persistence and outbox](../architecture/agent-persistence.md)
- [Event delivery and idempotency contract](../architecture/event-delivery.md)
- [Configuration and collection policy](../architecture/policy-configuration.md)
- [Independent health and coverage semantics](../architecture/health-and-gap-semantics.md)
- **[Canonical device communication protocol / TLS/ACK review](../architecture/device-communication-stack.md)** — implemented reqwest/rustls + Caddy + Spring/PG18, historical TLS1.2-minimum gap (isolated TLS1.3 L2 now passed), gRPC/MQTT comparison and remaining release gates.
- **[ADR-0010: RustDesk remote session reuse (proposed)](../architecture/adrs/0010-rustdesk-remote-control-integration.md)**, [isolated RD-0—RD-3 plan](../architecture/rustdesk-integration-plan.md) and **[RD-2 server-enforced authorization acceptance](../architecture/rustdesk-rd2-security-acceptance.md)** — genuine OSS/client synthetic CI has passed rendezvous and relay tests, but **no approved remote-desktop session or production integration**; Pro/OSS/AGPL and fail-closed release gates remain open.
- [REST and event API contract](../architecture/api-contract.md)
- [Dashboard WebSocket protocol](../architecture/websocket-contract.md)
- [LAN bootstrap and device enrollment](../architecture/enrollment-bootstrap.md)
- [Screen recording pipeline](../architecture/recording.md)
- [Platform feature matrix](../architecture/platform-matrix.md)
- [Deployment and LAN topology](../architecture/deployment.md)
- [ADR 0001: Monorepo and modular monolith](../architecture/adrs/0001-monorepo.md)
- [ADR 0002: PostgreSQL and SQLite](../architecture/adrs/0002-postgres-sqlite.md)
- [ADR 0003: Authorized recording](../architecture/adrs/0003-disclosed-capture.md)
- [ADR 0004: Historical direct-main CI policy (L1/L2/L3 supersedes test cadence)](../architecture/adrs/0004-rapid-main-development.md)
- [ADR 0005: REST + WebSocket from v0.1](../architecture/adrs/0005-rest-plus-websocket.md)
- [ADR 0006: Agent REST vs dashboard WS responsibilities](../architecture/adrs/0006-agent-rest-dashboard-ws.md)
- [ADR 0007: Mature ecosystem before custom infrastructure](../architecture/adrs/0007-mature-ecosystem-first.md)
- [ADR 0008: Canonical Next.js App Router decision (supersedes old Vite)](../architecture/adrs/0008-nextjs-app-router-console.md)
- [ADR 0009: Device protocol stack recommendation (REST/mTLS vs gRPC/MQTT)](../architecture/adrs/0009-agent-transport-selection.md)

## Product infographics / 产品彩图
- [Eight coordinated editable SVG posters](../visuals/README.md) for non-technical audiences, including overview, flow, stack, features, enrollment, dashboard, recovery and privacy.
- [Asset manifest](../visuals/ASSET_MANIFEST.md) · [Visual design rules](../visuals/STYLE_GUIDE.md) · [Content consistency matrix](../visuals/CONTENT_MATRIX.md). These are **concept images** and not finished UI screenshots.

## Architecture drawings
- [All Mermaid drawings and sources](../diagrams/README.md): system context, components, LAN deployment, enrollment, event delivery, WebSocket, video, RBAC, PostgreSQL/Agent SQLite models, outbox, WSS lifecycle, dashboard navigation, health states, trust boundaries and retention.

## Engineering
- **Mandatory reuse-first principle for future work:** [Repository AGENTS.md](../../AGENTS.md) · [Reusable-component / dependency policy](../development/reuse-first-policy.md) · [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md)
- [Technology selection](../development/technology.md)
- [Spring Boot/Gradle backend stable-version and dependency decisions](../development/backend-stack.md)
- [Gradle 9.8.2 Groovy DSL example (documentation only)](../development/backend-gradle-reference.md)
- [Pagevela reference: reuse and differences (historical Git SHA; removed from current development directory)](https://github.com/kunlong-luo/lanveriq/blob/23c947120d5b58db1f2d9783728d9b3482f277bc/docs/development/pagevela-reference.md)
- [Repository layout and conventions](../development/standards.md)
- [Dashboard UI/UX contract](../development/dashboard-ux.md)
- [Dedicated Monitoring Center design](../development/monitoring-center.md)
- [Recording & Playback Center design](../development/recording-playback-center.md)
- [IntelliJ IDEA import: link the standalone Gradle backend under apps/server](../development/intellij-idea.md)
- [Frontend design system and generated REST client](../development/frontend-design-system.md)
- [Rust Agent dependency stack and service runtime](../development/rust-agent-stack.md) · [AGT-01 source](../../apps/agent/README.md) · [Windows/Linux manual service acceptance](../testing/agent-lifecycle-manual.md) · [SQLite WAL/outbox manual acceptance](../testing/agent-sqlite-manual.md) · [mTLS/heartbeat/batch-ACK manual acceptance](../testing/agent-transport-manual.md) · [SRV-03 PostgreSQL event acceptance](../testing/server-event-ingestion-manual.md) · [HB-01 heartbeat acceptance](../testing/server-heartbeat-manual.md) · [SRV-02 certificate activation acceptance](../testing/server-cert-activation-manual.md) · [SRV-02 signed certificate registration acceptance](../testing/server-cert-registration-manual.md) · [SRV-02 Caddy Agent ingress identity gate](../testing/agent-ingress-identity-manual.md) · [INV-02 latest inventory acceptance](../testing/server-inventory-manual.md)
- **[Approved v0.1 batched delivery / three-tier testing plan (2026-11-08)](../planning/v0.1-batched-delivery-plan-2026-11-08.md)** — feature-first A/B/C/D/E milestones, path-filtered fast CI, batch integration and milestone acceptance.
- [Batch A first heartbeat source and default-off security boundary](../development/batch-a-first-heartbeat-implementation.md) — committed HTTP ingress and Agent typed ACK; full Agent→TLS→PG18 business loop still pending.
- **[Current Linux-first launch readiness, 2026-11-13](../planning/release-readiness-2026-11-13.md)**
- [Development roadmap](../planning/roadmap.md)
- [v0.1 implementation sequence](../planning/v0.1-implementation-sequence.md)
- [Issue-sized backlog](../planning/backlog.md)
- **[L1/L2/L3 active testing and milestone acceptance](../testing/strategy.md)**
- [Hands-on real-device manual acceptance](../testing/manual-acceptance.md)
- [Real disposable PostgreSQL 18.6 Testcontainers acceptance (historical failures superseded by #41290838250; new Batch A heartbeat route NOT yet E2E)](../testing/server-postgres18-manual.md)
- [Cross-platform and WebSocket test matrix](../testing/test-matrix.md)
- [Device inventory unit/manual acceptance cases](../testing/device-inventory-checklist.md) · [Rust INV-01 on-demand asset collector acceptance](../testing/agent-inventory-manual.md)
- [Default-off console browser login/Origin/CSRF manual acceptance (not executed)](../testing/console-session-manual.md)
- [Definition of done](../planning/definition-of-done.md)

## Security and operations
- [Notice, purpose and privacy](../security/privacy-and-consent.md)
- [Device identity and administrative RBAC](../security/identity-and-access.md)
- [SRV-02-REV manual device/certificate revocation acceptance — default OFF, synthetic-only](../testing/server-device-revocation-manual.md)
- [Role/action permission matrix](../security/authorization-matrix.md)
- [Threat model](../security/threat-model.md)
- [Retention and deletion](../security/data-retention.md)
- [Installation and operations](../operations/installation.md)
- [Backup and recovery](../operations/backup.md)
- [Incident and monitoring plan](../operations/monitoring.md)
- [Troubleshooting runbook](../operations/troubleshooting.md)
- [Lightweight release and upgrade plan](../operations/release.md)

## Governance
- [D0 technical decision/sign-off package — **approved for V0.1 coding**](../governance/d0-decision-package-2026-10-30.md): four selected technical baselines, negative tests and dated Git owner approval record.
- [Final documentation closure review: conceptual closure vs D0 blockers (2026-10-30)](../governance/final-closure-review-2026-10-30.md)
- [Cross-document / diagram / SVG consistency review — historical 2026-10-30](../governance/documentation-review-2026-10-30.md)
- **[Document consistency/security audit — 2026-11-08](../governance/documentation-audit-2026-11-08.md)**
- [Open decisions](../governance/open-decisions.md)
- [Docs-before-code gate](../governance/documentation-gate.md)
- [Contributing](../../CONTRIBUTING.md) · [Security reporting](../../SECURITY.md) · [Changelog](../../CHANGELOG.md)

**Working rule (2026-11-08):** v0.1 P0 development in batches A–E; path-filtered quick L1 on every relevant source commit, scoped L2 integration at module boundary, complete L3 Chromium/PG18/Windows/Linux on explicit acceptance tag/dispatch. Historical unit-only/direct-main ADRs are not active instructions. Do not enable real employee/device collection or production enrollment without explicit legal/organization approval, approved key custody and actual PKI/OS/RBAC/restore/performance acceptance. v0.2 audit and v0.3 recording remain independently gated.
