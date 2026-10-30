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
- [Agent local persistence and outbox](architecture/agent-persistence.md)
- [REST and event API contract](architecture/api-contract.md)
- [Screen recording pipeline](architecture/recording.md)
- [Platform feature matrix](architecture/platform-matrix.md)
- [Deployment and LAN topology](architecture/deployment.md)
- [ADR 0001: Monorepo and modular monolith](architecture/adrs/0001-monorepo.md)
- [ADR 0002: PostgreSQL and SQLite](architecture/adrs/0002-postgres-sqlite.md)
- [ADR 0003: Authorized recording](architecture/adrs/0003-disclosed-capture.md)
- [ADR 0004: Direct main, minimal CI, delayed E2E](architecture/adrs/0004-rapid-main-development.md)

## Engineering
- [Technology selection](development/technology.md)
- [Pagevela reference: reuse and differences](development/pagevela-reference.md)
- [Repository layout and conventions](development/standards.md)
- [Development roadmap](planning/roadmap.md)
- [Issue-sized backlog](planning/backlog.md)
- [Unit-only CI and manual checks](testing/strategy.md)
- [Definition of done](planning/definition-of-done.md)

## Security and operations
- [Notice, purpose and privacy](security/privacy-and-consent.md)
- [Device identity and administrative RBAC](security/identity-and-access.md)
- [Threat model](security/threat-model.md)
- [Retention and deletion](security/data-retention.md)
- [Installation and operations](operations/installation.md)
- [Backup and recovery](operations/backup.md)
- [Incident and monitoring plan](operations/monitoring.md)

## Governance
- [Open decisions](governance/open-decisions.md)
- [Docs-before-code gate](governance/documentation-gate.md)
- [Contributing](../CONTRIBUTING.md) · [Security reporting](../SECURITY.md) · [Changelog](../CHANGELOG.md)

**Working rule:** MVP changes may commit directly to main, no PR requirement. Pre-release CI executes unit tests only; no E2E suite yet. Code starts only after the docs gate has been reviewed. Privacy/security controls cannot be deferred merely for speed.
