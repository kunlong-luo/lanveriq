# Lanveriq

**English** · [简体中文](README.zh.md) · [Documentation index](docs/README.md)

Lanveriq is a Linux-first, self-hosted endpoint inventory, health-state and audit project for **lawfully disclosed, explicitly authorized** workplace devices. The first target is **Pop!_OS 24.04 / COSMIC**.

> **Release: v0.1 NOT RELEASE READY.** There is no signed, independently approved, one-click Agent installer or authorized production deployment. All real-device networking, automatic inventory collection, remote access, automatic recovery and certificate issuance remain **default OFF**. CI successes demonstrate isolated tests, not consent or deployment authority.

[Current verified status](docs/CURRENT_STATUS.md) · [Open release gates G-01–G-10](docs/planning/release-gap-register.md) · [Engineering scorecard](docs/planning/project-scorecard.md) · [Keep/delete repository audit](docs/governance/repository-layout-audit-2026-11-24.md)

## Which directories are real and why do they exist?

| Path | Purpose and stack | Current boundary | Decision |
| --- | --- | --- | --- |
| [`apps/web/`](apps/web/README.md) | The **only Web admin console**, Next.js 16.4.5 / React 19 / Tailwind 4 / Base UI | Public [Pages preview](https://kunlong-luo.github.io/lanveriq/) is disconnected; real business data is only proven in disposable CI | **KEEP** |
| [`apps/server/`](apps/server/README.md) | Java 25 / Spring Boot 4.2.0 / Gradle / JPA-Hibernate / Flyway / PostgreSQL 18 | Deny-by-default business APIs; real PG18 and TLS synthetic integration tested | **KEEP** |
| [`apps/agent/`](apps/agent/README.md) | Rust 1.100 / Tokio / SQLx SQLite / reqwest-rustls Agent | Durable outbox and read-only host sampling tested in isolated CI; no authorized live collection | **KEEP** |
| [`apps/desktop/`](apps/desktop/README.md) | Tauri 2 + React local install-readiness and status GUI | **Read-only**; no trusted installer/Polkit broker, service activation or production entitlement | **KEEP** |
| [`deploy/caddy/`](deploy/caddy/Caddyfile.reference) | TLS1.3 / double-mTLS reference and disposable testing | Not a production Internet-facing configuration | **KEEP** |
| [`deploy/network/`](deploy/network/verify_dual_access.py) | LAN/WAN boundary tests | Isolated security validation only | **KEEP** |
| [`deploy/rustdesk/`](deploy/rustdesk/client-release-lock.json) and [`deploy/rustdesk-synthetic/`](deploy/rustdesk-synthetic/verify_rd2_release_boundary.py) | Optional RustDesk protocol/pilot and RD-2 release-denial tests | Remote control is **OFF**; licensing and security approval absent | **KEEP (isolated)** |
| [`docs/`](docs/README.md) | Architecture, current status, tests, release gaps, dated audit evidence | [Lifecycle rules](docs/governance/document-lifecycle.md) distinguish authoritative from historical material | **KEEP** |
| [`.github/workflows/`](.github/workflows/docs-governance.yml) | Test/build/security/documentation CI | CI is not production sign-off | **KEEP** |

The old `apps/web-next/` and the previous React/Vite **Web console** are no longer present. **Do not recreate a second Web frontend, Maven build, or independent agent control plane.** The Vite build under `apps/desktop` is only for the Tauri window, not a second Web console. This repository root is intentionally **not** a Gradle project.

Inside `docs/`, keep `architecture/` for ADRs and technical design, `security/` for security boundaries, `testing/` for exact test results, `development/` for technical specs and dated implementation evidence, `governance/` for document authority, `planning/` for release gaps and priorities, `operations/` for carefully scoped runbooks, `product/` for expected user behavior, `diagrams/` for editable Mermaid, and `visuals/` for conceptual SVGs (not screenshots). The [repository audit](docs/governance/repository-layout-audit-2026-11-24.md) explains what has been deleted and why other directories remain.

## What can I install today?

**Ordinary endpoint users: not yet.** The planned Tauri one-click installation, signed Agent distribution, fixed-action Polkit broker, real Pop!_OS hardware acceptance and legal approvals are unfinished. Historical instructions to compile Rust, execute `dpkg -i`, edit TOML or enable systemd are **not** a customer installation guide. See [installer product scope](docs/product/linux-desktop-installer-v01.md) and [actual desktop L1 status](docs/development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md).

**Developers only**, from a trusted, isolated machine:

```bash
# Single Next.js management preview; no real login or device data
cd apps/web
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm dev
# http://127.0.0.1:3100
```

For other components, `cd apps/server && ./gradlew test`, `cd apps/agent && cargo test --workspace --locked`, or `cd apps/desktop && pnpm test`. These are **development tests, not endpoint installation or activation**. To configure a separately isolated backend database, follow [local development runbook](docs/operations/installation.md), not an obsolete README transcript.

## Release and contribution rules

Releasing needs independent approval of signed APT/Agent packages and a narrowly scoped Polkit broker, real consented Pop!_OS 24.04 COSMIC L3, CA/admin MFA, independently administered WORM, privacy/licensing and operational rollback. None can be bypassed by a UI checkbox or a synthetic CI result.

Read [AGENTS.md](AGENTS.md), [contributing](CONTRIBUTING.md), [engineering playbook](docs/governance/engineering-playbook.md), [development plan](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md), [security policy](SECURITY.md), and [change history](CHANGELOG.md). Development score is **not** a release certificate. **v0.1 NOT RELEASE READY.**
