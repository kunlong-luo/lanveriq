# Lanveriq

**English** · [简体中文](README.zh.md) · [Documentation home](docs/README.md)

Lanveriq is a Linux-first, self-hosted endpoint inventory, health-state and audit platform for **disclosed, lawfully authorized** workplace devices. First target: **Pop!_OS 24.04 / COSMIC**.

> **v0.1 NOT RELEASE READY.** No approved signed one-click Agent installer, privileged Polkit transaction broker, physical Pop!_OS L3, production CA/MFA, independent WORM or legal/SRE sign-off exists. Production networking, automatic collection/recovery, remote control and certificate issuance remain **OFF**. Synthetic CI PASS is not production authorization.

[Single current status](docs/CURRENT_STATUS.md) · [Release gates G-01–G-10](docs/planning/release-gap-register.md) · [Active Linux delivery plan](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [Keep/delete audit](docs/governance/repository-layout-audit-2026-11-24.md)

## Source directories — what stays

| Path | Responsibility | Decision |
| --- | --- | --- |
| [`apps/web/`](apps/web/README.md) | **Only Web console** — Next.js 16 / React 19 / Tailwind 4 / Base UI; public Pages is disconnected | **KEEP** |
| [`apps/server/`](apps/server/README.md) | Java 25 / Spring Boot 4.2 / **Gradle** / JPA-Hibernate / Flyway / PostgreSQL 18; mTLS, domain APIs and transactions | **KEEP** |
| [`apps/agent/`](apps/agent/README.md) | Rust / Tokio / SQLx SQLite / reqwest-rustls; isolated read-only Linux probe, durable outbox and strict ACK; production automatic collection OFF | **KEEP** |
| [`apps/desktop/`](apps/desktop/README.md) | Tauri 2 / React **read-only** installation-readiness GUI; Vite builds the Tauri window, not another Web console | **KEEP** |
| [`deploy/caddy/`](deploy/caddy/Caddyfile.reference) | TLS 1.3 / double mTLS references and isolated CI; **not** a production Internet-facing config | **KEEP** |
| [`deploy/network/`](deploy/network/verify_dual_access.py) | LAN/WAN boundary refusal tests | **KEEP** |
| [`deploy/rustdesk/`](deploy/rustdesk/client-release-lock.json), [`deploy/rustdesk-synthetic/`](deploy/rustdesk-synthetic/verify_rd2_release_boundary.py) | Candidate RustDesk protocol, license and RD-2 release-denial tests; remote control OFF | **KEEP (lab only)** |
| [`docs/`](docs/README.md) | Architecture, specifications, current status/gates, unique dated security/CI evidence and immutable historical pointers | **KEEP and deduplicate** |
| [`.github/workflows/`](.github/workflows/docs-governance.yml) | Focused L1, disposable L2, RD-2 and documentation/release-evidence tests; not physical L3 approval | **KEEP** |

Root [`AGENTS.md`](AGENTS.md), [`CONTRIBUTING.md`](CONTRIBUTING.md), [`SECURITY.md`](SECURITY.md) and [`CHANGELOG.md`](CHANGELOG.md) each have a distinct purpose. The repository root is **neither a Gradle nor Maven application**. Do not recreate the removed `apps/web-next/`, legacy Vite Web, secondary Maven backend or independent Agent control plane.

## Documentation ownership

```text
docs/
├── README.md             # single documentation index
├── CURRENT_STATUS.md     # single current implementation / CI fact ledger
├── architecture/adrs/    # architecture, protocols and ADRs
├── security/             # identity, FIDO, WORM, privacy and unique threat evidence
├── development/          # standards, frontend design and dated implementation reports
├── testing/              # L1/L2/L3 validation, failures and hostile cases
├── planning/             # active Linux plan, single gate register, DoD/scorecard, historical acceptance
├── governance/archive/   # ownership, layout audit and pinned-Git history index
├── operations/           # development, backup, recovery and release controls
├── product/              # product requirements and expected UX
├── diagrams/             # Mermaid source diagrams
└── visuals/              # conceptual SVG, not screenshots of running UI
```

Obsolete `docs/planning/backlog.md`, `roadmap.md`, `v0.1-batched-delivery-plan-2026-11-08.md` and the former implementation-sequence transcript have been **removed from the working tree**. Their full original text remains accessible through the [pinned Git history index](docs/governance/archive/README.md). Keep independent failures, signed-package attack evidence and original ADRs. For current work use only the [Linux delivery plan](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md), [CURRENT_STATUS](docs/CURRENT_STATUS.md) and [release-gap register](docs/planning/release-gap-register.md). Documentation CI checks links across all `docs/**/*.md` and denies resurrected paths.

## Developer preview — not endpoint installation

From an authorized isolated development machine:

```bash
cd apps/web
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm dev
# http://127.0.0.1:3100 — disconnected preview
```

Other developer tests: `cd apps/server && ./gradlew test`, `cd apps/agent && cargo test --workspace --locked`, `cd apps/desktop && pnpm test`. See [development-only PostgreSQL/loopback setup](docs/operations/installation.md). End users should **not** compile Rust, invoke `dpkg -i` or enable systemd services as a substitute for an approved installer.

The next isolated business-loop milestone is [durable Linux inventory recovery after a real Caddy outage, exact mTLS ACK, PG18 revision advance and operator-triggered Chromium refresh](docs/development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md). **Its CI status is PENDING, not a product release approval.**

## Release boundary and next work

An isolated [Agent→mTLS→Spring/PostgreSQL→Next.js synthetic chain](docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) has passed. Native [APT accepted a valid older signed Release](docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md); the [L2B-28 source-only approval-denial contract](docs/development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) does **not** implement an independent trusted anti-rollback high-water, release publisher or installation authorization.

Before any production deployment: independently approved publisher root, secure monotonic high-water and trusted time, least-privilege Polkit authorization, consented Pop!_OS COSMIC L3, administrator MFA/CA, externally administered WORM, legal and SRE disaster-recovery sign-offs. See [AGENTS.md](AGENTS.md), [security policy](SECURITY.md) and [release runbook](docs/operations/release.md). **v0.1 NOT RELEASE READY.**
