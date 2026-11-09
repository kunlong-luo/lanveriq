# Lanveriq — documentation index

**Current baseline (2026-11-08):** D0 technical design approved; implemented Java 25/Spring Boot/PG18 unit & synthetic integration slices, Rust SQLx/reqwest + Linux/Windows tests, and canonical Next.js B+C disconnected preview. Batch A1 has a default-OFF Agent heartbeat POST source/strict Rust ACK, **but device registration→Agent runtime→Caddy→PG18→authorized UI is not yet an integrated product**. Full synthetic and real-device legal/operational releases are separate. Active [batch plan](planning/v0.1-batched-delivery-plan-2026-11-08.md), [actual stack](development/technology.md) and [2026-11-08 doc audit](governance/documentation-audit-2026-11-08.md). Historical runbook statuses must be read as timestamped evidence, not the most recent result. See [中文介绍](../README.zh.md).

## Product
- [Requirements and scope](product/requirements.md)
- [Functional closure by milestone](product/functional-closure.md)
- [User stories and flows](product/user-stories.md)
- [Quality requirements](product/nonfunctional.md)

## Architecture
- [System overview and trust boundaries](architecture/system-overview.md)
- [Component decomposition](architecture/components.md)
- [Storage and event models](architecture/data-model.md)
- [Device identity: UUIDv7 enrollment, re-enrollment and replacement](architecture/device-identity.md)
- [Hardware/OS/network inventory and sampling](architecture/device-inventory.md)
- [Agent local persistence and outbox](architecture/agent-persistence.md)
- [Event delivery and idempotency contract](architecture/event-delivery.md)
- [Configuration and collection policy](architecture/policy-configuration.md)
- [Independent health and coverage semantics](architecture/health-and-gap-semantics.md)
- **[Canonical device communication protocol / TLS/ACK review](architecture/device-communication-stack.md)** — implemented reqwest/rustls + Caddy + Spring/PG18, TLS1.2-minimum gap, gRPC/MQTT comparison and release gates.
- **[ADR-0010: RustDesk remote session reuse (proposed)](architecture/adrs/0010-rustdesk-remote-control-integration.md)**, [isolated RD-0—RD-3 plan](architecture/rustdesk-integration-plan.md) and **[RD-2 server-enforced authorization acceptance](architecture/rustdesk-rd2-security-acceptance.md)** — genuine OSS/client synthetic CI has passed rendezvous and relay tests, but **no approved remote-desktop session or production integration**; Pro/OSS/AGPL and fail-closed release gates remain open.
- [REST and event API contract](architecture/api-contract.md)
- [Dashboard WebSocket protocol](architecture/websocket-contract.md)
- [LAN bootstrap and device enrollment](architecture/enrollment-bootstrap.md)
- [Screen recording pipeline](architecture/recording.md)
- [Platform feature matrix](architecture/platform-matrix.md)
- [Deployment and LAN topology](architecture/deployment.md)
- [ADR 0001: Monorepo and modular monolith](architecture/adrs/0001-monorepo.md)
- [ADR 0002: PostgreSQL and SQLite](architecture/adrs/0002-postgres-sqlite.md)
- [ADR 0003: Authorized recording](architecture/adrs/0003-disclosed-capture.md)
- [ADR 0004: Historical direct-main CI policy (L1/L2/L3 supersedes test cadence)](architecture/adrs/0004-rapid-main-development.md)
- [ADR 0005: REST + WebSocket from v0.1](architecture/adrs/0005-rest-plus-websocket.md)
- [ADR 0006: Agent REST vs dashboard WS responsibilities](architecture/adrs/0006-agent-rest-dashboard-ws.md)
- [ADR 0007: Mature ecosystem before custom infrastructure](architecture/adrs/0007-mature-ecosystem-first.md)
- [ADR 0008: Canonical Next.js App Router decision (supersedes old Vite)](architecture/adrs/0008-nextjs-app-router-console.md)
- [ADR 0009: Device protocol stack recommendation (REST/mTLS vs gRPC/MQTT)](architecture/adrs/0009-agent-transport-selection.md)

## Product infographics / 产品彩图
- [Eight coordinated editable SVG posters](visuals/README.md) for non-technical audiences, including overview, flow, stack, features, enrollment, dashboard, recovery and privacy.
- [Asset manifest](visuals/ASSET_MANIFEST.md) · [Visual design rules](visuals/STYLE_GUIDE.md) · [Content consistency matrix](visuals/CONTENT_MATRIX.md). These are **concept images** and not finished UI screenshots.

## Architecture drawings
- [All Mermaid drawings and sources](diagrams/README.md): system context, components, LAN deployment, enrollment, event delivery, WebSocket, video, RBAC, PostgreSQL/Agent SQLite models, outbox, WSS lifecycle, dashboard navigation, health states, trust boundaries and retention.

## Engineering
- **Mandatory reuse-first principle for future work:** [Repository AGENTS.md](../AGENTS.md) · [Reusable-component / dependency policy](development/reuse-first-policy.md) · [ADR-0007](architecture/adrs/0007-mature-ecosystem-first.md)
- [Technology selection](development/technology.md)
- [Spring Boot/Gradle backend stable-version and dependency decisions](development/backend-stack.md)
- [Gradle 9.8.2 Groovy DSL example (documentation only)](development/backend-gradle-reference.md)
- [Pagevela reference: reuse and differences](development/pagevela-reference.md)
- [Repository layout and conventions](development/standards.md)
- [Dashboard UI/UX contract](development/dashboard-ux.md)
- [Dedicated Monitoring Center design](development/monitoring-center.md)
- [Recording & Playback Center design](development/recording-playback-center.md)
- [IntelliJ IDEA import: link the standalone Gradle backend under apps/server](development/intellij-idea.md)
- [Frontend design system and generated REST client](development/frontend-design-system.md)
- [Rust Agent dependency stack and service runtime](development/rust-agent-stack.md) · [AGT-01 source](../apps/agent/README.md) · [Windows/Linux manual service acceptance](testing/agent-lifecycle-manual.md) · [SQLite WAL/outbox manual acceptance](testing/agent-sqlite-manual.md) · [mTLS/heartbeat/batch-ACK manual acceptance](testing/agent-transport-manual.md) · [SRV-03 PostgreSQL event acceptance](testing/server-event-ingestion-manual.md) · [HB-01 heartbeat acceptance](testing/server-heartbeat-manual.md) · [SRV-02 certificate activation acceptance](testing/server-cert-activation-manual.md) · [SRV-02 signed certificate registration acceptance](testing/server-cert-registration-manual.md) · [SRV-02 Caddy Agent ingress identity gate](testing/agent-ingress-identity-manual.md) · [INV-02 latest inventory acceptance](testing/server-inventory-manual.md)
- **[Approved v0.1 batched delivery / three-tier testing plan (2026-11-08)](planning/v0.1-batched-delivery-plan-2026-11-08.md)** — feature-first A/B/C/D/E milestones, path-filtered fast CI, batch integration and milestone acceptance.
- [Batch A first heartbeat source and default-off security boundary](development/batch-a-first-heartbeat-implementation.md) — committed HTTP ingress and Agent typed ACK; full Agent→TLS→PG18 business loop still pending.
- [Development roadmap](planning/roadmap.md)
- [v0.1 implementation sequence](planning/v0.1-implementation-sequence.md)
- [Issue-sized backlog](planning/backlog.md)
- **[L1/L2/L3 active testing and milestone acceptance](testing/strategy.md)**
- [Hands-on real-device manual acceptance](testing/manual-acceptance.md)
- [Real disposable PostgreSQL 18.6 Testcontainers acceptance (historical failures superseded by #41290838250; new Batch A heartbeat route NOT yet E2E)](testing/server-postgres18-manual.md)
- [Cross-platform and WebSocket test matrix](testing/test-matrix.md)
- [Device inventory unit/manual acceptance cases](testing/device-inventory-checklist.md) · [Rust INV-01 on-demand asset collector acceptance](testing/agent-inventory-manual.md)
- [Default-off console browser login/Origin/CSRF manual acceptance (not executed)](testing/console-session-manual.md)
- [Definition of done](planning/definition-of-done.md)

## Security and operations
- [Notice, purpose and privacy](security/privacy-and-consent.md)
- [Device identity and administrative RBAC](security/identity-and-access.md)
- [SRV-02-REV manual device/certificate revocation acceptance — default OFF, synthetic-only](testing/server-device-revocation-manual.md)
- [Role/action permission matrix](security/authorization-matrix.md)
- [Threat model](security/threat-model.md)
- [Retention and deletion](security/data-retention.md)
- [Installation and operations](operations/installation.md)
- [Backup and recovery](operations/backup.md)
- [Incident and monitoring plan](operations/monitoring.md)
- [Troubleshooting runbook](operations/troubleshooting.md)
- [Lightweight release and upgrade plan](operations/release.md)

## Governance
- [D0 technical decision/sign-off package — **approved for V0.1 coding**](governance/d0-decision-package-2026-10-30.md): four selected technical baselines, negative tests and dated Git owner approval record.
- [Final documentation closure review: conceptual closure vs D0 blockers (2026-10-30)](governance/final-closure-review-2026-10-30.md)
- [Cross-document / diagram / SVG consistency review — historical 2026-10-30](governance/documentation-review-2026-10-30.md)
- **[Document consistency/security audit — 2026-11-08](governance/documentation-audit-2026-11-08.md)**
- [Open decisions](governance/open-decisions.md)
- [Docs-before-code gate](governance/documentation-gate.md)
- [Contributing](../CONTRIBUTING.md) · [Security reporting](../SECURITY.md) · [Changelog](../CHANGELOG.md)

**Working rule (2026-11-08):** v0.1 P0 development in batches A–E; path-filtered quick L1 on every relevant source commit, scoped L2 integration at module boundary, complete L3 Chromium/PG18/Windows/Linux on explicit acceptance tag/dispatch. Historical unit-only/direct-main ADRs are not active instructions. Do not enable real employee/device collection or production enrollment without explicit legal/organization approval, approved key custody and actual PKI/OS/RBAC/restore/performance acceptance. v0.2 audit and v0.3 recording remain independently gated.
