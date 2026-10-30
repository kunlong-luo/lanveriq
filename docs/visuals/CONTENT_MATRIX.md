# Terminology and content consistency matrix

The visuals should explain the product **faithfully**, not expand the monitoring scope or imply unfinished features exist.

| Theme / posters | Allowed statement | Not allowed / misleading | Technical source |
|---|---|---|---|
| Endpoint (01,03) | Native Rust Agent, Windows 11 + selected Linux desktop; local SQLite WAL | All Linux desktop environments fully supported | [Platform matrix](../architecture/platform-matrix.md) |
| Transport (01,02,03,07) | Agent HTTPS REST; dashboard REST + WSS | Agent WebSocket for durable event ACK or WSS as source of truth | [ADR-0006](../architecture/adrs/0006-agent-rest-dashboard-ws.md) |
| Device pairing (02,05) | Unique credential and single-use pairing; verify internal CA | Device is trusted merely because it is on LAN | [Enrollment](../architecture/enrollment-bootstrap.md) |
| Audit events (01,02,04,07) | Source allowlist, v0.2 scoped activity and OS audit events | Continuous total surveillance, full 24-hour completeness | [PRD](../product/requirements.md) |
| Offline queue (02,07) | SQLite stores bounded durable pending events, PostgreSQL acknowledges after commit | Unlimited outage retention, absolute zero event loss | [Delivery](../architecture/event-delivery.md) |
| Dashboard (01,03,06) | React + Vite + TypeScript, scoped REST and WSS notifications | Next.js SSR or an already implemented UI | [Dashboard UX](../development/dashboard-ux.md) |
| Tech stack (03) | Java 25, Spring Boot 4, PostgreSQL 18, Caddy, pnpm for JS | pnpm is a framework, PostgreSQL runs on every Agent | [Technology](../development/technology.md) |
| Privilege (06,08) | Admin session, RBAC, device-group scope, sensitive-read audit | All administrators can view every employee's recordings | [Authorization](../security/authorization-matrix.md) |
| Recording (04,08) | Optional V0.3, notice, OS permission, review approval | Hidden capture, Wayland permission bypass, guaranteed locked-screen recording | [Recording](../architecture/recording.md) |
| Status (01,04,06) | Online, stale, offline and source gaps separately | Missing logs imply idle employee or wrongdoing | [Health states](../architecture/health-and-gap-semantics.md) |
| Product release (ALL) | Plans/concepts only; no running app yet | Screenshot of actual production deployment or measured KPIs | [D0 gate](../governance/documentation-gate.md) |

## Locale consistency
- English source: technology names, README, technical specs.
- Chinese illustrations: short user-facing explanatory text. Preserve exact version numbers and transport terms.
- Use `终端设备` (Endpoint), `局域网服务器` (LAN Server), `管理后台` (Dashboard), `审计事件` (audit events), `设备状态` (device health), `采集缺口` (collection gap).
- `设备在线` never means `所有采集源健康`.
- Privacy notes must clearly distinguish **routine disclosed audit** from **separate user-session OS-authorized recording**.

## Asset checks (before release)
- SVG renders at full size and at approximately half-size with no clipped Chinese glyphs.
- Relative asset and diagram links resolve in GitHub.
- All eight have same header/footer, palette, numbered badge and conceptual status.
- Diagram and PRD milestone match; recording shown as optional later feature.
- Any fake counts/graphs are explicitly sample values.
- No real employee data, credential, hostname, private CA key or IP address.
