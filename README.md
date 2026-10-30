# Lanveriq

**LAN-first, self-hosted endpoint activity and audit platform for disclosed, authorized workplace devices.**

> **Status: documentation/design phase only. No functional agent, server, dashboard, recording or installer has been implemented. Do not deploy this repository as a monitoring product yet.**

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
Rust endpoint agent; optional Tauri 2 desktop companion; Java 25 / Spring Boot 4 modular-monolith API; React + Vite dashboard; PostgreSQL; local disk/NAS video storage; HTTPS/WebSocket; Docker Compose + Caddy.

## Documentation
See [Documentation Index](docs/README.md), [Product Requirements](docs/product/requirements.md), [System Architecture](docs/architecture/system-overview.md), [Security and Privacy](docs/security/privacy-and-consent.md), [Development Roadmap](docs/planning/roadmap.md), and [中文说明](README.zh.md).

**Documentation-first gate:** No product code is to be started until the design baseline, privacy scope, API and data contracts, risks, acceptance criteria and open decisions have been reviewed. See [Documentation Gate](docs/governance/documentation-gate.md).

## Contributions and licensing
Public visibility does **not** grant permission to use or redistribute the software. A software license must be selected and committed after a dependency/license review; until then, rights are reserved. See [Contributing](CONTRIBUTING.md) and [Security](SECURITY.md). Avoid posting actual recordings, personal data or credentials in issues.

Copyright (c) 2026 Lanveriq contributors.
