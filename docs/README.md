# Lanveriq — documentation index

**Baseline:** v0.1 draft (2026-10-30). The repo is **documentation-only**. Planned architecture is not implemented or tested. English technical documents are canonical; see [中文介绍](../README.zh.md).

## Product
- [Requirements and scope](product/requirements.md)
- [User stories and flows](product/user-stories.md)
- [Quality requirements](product/nonfunctional.md)

## Architecture
- [System overview and trust boundaries](architecture/system-overview.md)
- [Component decomposition](architecture/components.md)
- [Storage and event models](architecture/data-model.md)
- [Hardware/OS/network inventory and sampling](architecture/device-inventory.md)
- [Canonical UUIDv7 device identity](architecture/device-identity.md)
- [Agent local persistence and outbox](architecture/agent-persistence.md)
- [Event delivery and idempotency contract](architecture/event-delivery.md)
- [Configuration and collection policy](architecture/policy-configuration.md)
- [Independent health and coverage semantics](architecture/health-and-gap-semantics.md)
- [REST and event API contract](architecture/api-contract.md)
- [Dashboard WebSocket protocol](architecture/websocket-contract.md)
- [LAN bootstrap and device enrollment](architecture/enrollment-bootstrap.md)
- [Screen recording pipeline](architecture/recording.md)
- [Platform feature matrix](architecture/platform-matrix.md)
- [Deployment and LAN topology](architecture/deployment.md)
- [ADR 0001: Monorepo and modular monolith](architecture/adrs/0001-monorepo.md)
- [ADR 0002: PostgreSQL and SQLite](architecture/adrs/0002-postgres-sqlite.md)
- [ADR 0003: Authorized recording](architecture/adrs/0003-disclosed-capture.md)
- [ADR 0004: Direct main, minimal CI, delayed E2E](architecture/adrs/0004-rapid-main-development.md)
- [ADR 0005: REST + WebSocket from v0.1](architecture/adrs/0005-rest-plus-websocket.md)
- [ADR 0006: Agent REST vs dashboard WS responsibilities](architecture/adrs/0006-agent-rest-dashboard-ws.md)

## Product infographics / 产品彩图
- [Eight coordinated editable SVG posters](visuals/README.md) for non-technical audiences, including overview, flow, stack, features, enrollment, dashboard, recovery and privacy.
- [Asset manifest](visuals/ASSET_MANIFEST.md) · [Visual design rules](visuals/STYLE_GUIDE.md) · [Content consistency matrix](visuals/CONTENT_MATRIX.md). These are **concept images** and not finished UI screenshots.

## Architecture drawings
- [All Mermaid drawings and sources](diagrams/README.md): system context, components, LAN deployment, enrollment, event delivery, WebSocket, video, RBAC, PostgreSQL/Agent SQLite models, outbox, WSS lifecycle, dashboard navigation, health states, trust boundaries and retention.

## Engineering
- [Technology selection](development/technology.md)
- [Pagevela reference: reuse and differences](development/pagevela-reference.md)
- [Repository layout and conventions](development/standards.md)
- [Dashboard UI/UX contract](development/dashboard-ux.md)
- [Development roadmap](planning/roadmap.md)
- [v0.1 implementation sequence](planning/v0.1-implementation-sequence.md)
- [Issue-sized backlog](planning/backlog.md)
- [Unit-only CI and manual checks](testing/strategy.md)
- [Hands-on acceptance templates (no E2E CI)](testing/manual-acceptance.md)
- [Cross-platform and WebSocket test matrix](testing/test-matrix.md)
- [Device inventory unit/manual acceptance cases](testing/device-inventory-checklist.md)
- [Definition of done](planning/definition-of-done.md)

## Security and operations
- [Notice, purpose and privacy](security/privacy-and-consent.md)
- [Device identity and administrative RBAC](security/identity-and-access.md)
- [Role/action permission matrix](security/authorization-matrix.md)
- [Threat model](security/threat-model.md)
- [Retention and deletion](security/data-retention.md)
- [Installation and operations](operations/installation.md)
- [Backup and recovery](operations/backup.md)
- [Incident and monitoring plan](operations/monitoring.md)
- [Troubleshooting runbook](operations/troubleshooting.md)
- [Lightweight release and upgrade plan](operations/release.md)

## Governance
- [Open decisions](governance/open-decisions.md)
- [Docs-before-code gate](governance/documentation-gate.md)
- [Contributing](../CONTRIBUTING.md) · [Security reporting](../SECURITY.md) · [Changelog](../CHANGELOG.md)

**Working rule:** MVP changes may commit directly to main, no PR requirement. Pre-release CI executes unit tests only; no E2E suite yet. Code starts only after the docs gate has been reviewed. Privacy/security controls cannot be deferred merely for speed.
