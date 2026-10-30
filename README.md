# Lanveriq

**LAN-first, self-hosted endpoint activity and audit platform for disclosed, authorized workplace devices.**

> **Status: documentation/design phase only. No functional agent, server, dashboard, recording or installer has been implemented. Do not deploy this repository as a monitoring product yet.**

![Lanveriq overall architecture concept](docs/visuals/assets/01-overview.svg)

[Browse all eight color infographics](docs/visuals/README.md) · [Editable Mermaid engineering diagrams](docs/diagrams/README.md)

Lanveriq is planned as a cross-platform (Windows 11 and selected Linux desktops) endpoint-management and audit system for small offices (initial target: 1–10 endpoints). It will run on a private LAN, remain usable without Internet access, and avoid external cloud dependencies and AI.

## Planned capabilities
- Device enrollment, heartbeat, inventory and health.
- Scoped application-activity timeline and audited system events.
- Optional, **clearly disclosed and OS-authorized** screen recording, segment integrity, retention and playback.
- Browser administration with role-based access and viewing/export audit logs.
- Offline buffering with explicit collection gaps, not claims of perfect visibility.

## Non-goals
- Covert surveillance, hidden installation, keylogging, password capture, bypassing OS capture permissions or persistent invisible screen viewing.
- Recording microphones/cameras, scraping private messages or bypassing TLS.
- AI analytics, remote arbitrary command execution, or Internet SaaS.

## Technology direction (subject to architecture approval)
Rust endpoint agent; optional Tauri 2 desktop companion; Java 25 / Spring Boot 4 modular-monolith API; React + Vite dashboard; PostgreSQL 18 server + SQLite WAL on endpoints; local disk/NAS video storage; HTTPS REST for Rust Agent and REST + authenticated WSS for the v0.1 dashboard; Docker Compose + Caddy.

## Documentation
See [Architecture Drawings](docs/diagrams/README.md), [WebSocket Contract](docs/architecture/websocket-contract.md), [Documentation Index](docs/README.md), [Product Requirements](docs/product/requirements.md), [System Architecture](docs/architecture/system-overview.md), [Security and Privacy](docs/security/privacy-and-consent.md), [Development Roadmap](docs/planning/roadmap.md), and [中文说明](README.zh.md).

**Documentation-first gate:** No product code is to be started until the D0 technical baseline and blocking security/protocol decisions have been **explicitly approved by the maintainer**, with unresolved risks recorded. Reviewing documents or selecting Gradle alone does not constitute approval. See [Documentation Gate](docs/governance/documentation-gate.md).

## Early development workflow

Development is **directly on main** during pre-release; no mandatory feature branches or pull requests. CI will run **unit tests only** once implementation modules and real tests exist (Rust Cargo tests, Java JUnit, React Vitest). E2E, cross-platform automation and comprehensive CI are deliberately postponed until a dedicated hardening milestone. Sensitive authentication, scoped collection, event recovery and OS screen-capture permissions still require targeted manual verification before any real-world pilot.

Pagevela's React/Vite design, feature modules, UI consistency and ordered persistence lessons are selectively adopted: [Pagevela architecture review](docs/development/pagevela-reference.md). Server database is PostgreSQL; Rust Agent local store is SQLite WAL. See [development roadmap](docs/planning/roadmap.md) and [ADR-0004](docs/architecture/adrs/0004-rapid-main-development.md).

## Contributions and licensing
Public visibility does **not** grant permission to use or redistribute the software. A software license must be selected and committed after a dependency/license review; until then, rights are reserved. See [Contributing](CONTRIBUTING.md) and [Security](SECURITY.md). Avoid posting actual recordings, personal data or credentials in issues.

Copyright (c) 2026 Lanveriq contributors.
