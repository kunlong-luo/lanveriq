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

The **canonical documentation tree** is deliberately shallow at its active entry points; historical results stay in the relevant topic folder and Git history:

```text
docs/
├── README.md                  # single documentation index
├── CURRENT_STATUS.md          # single live implementation/CI summary
├── architecture/             # normative architecture
│   └── adrs/                 # accepted/proposed decisions
├── security/                  # identity, privacy, FIDO, WORM boundaries
├── development/               # specifications and dated L2B/NXT evidence
├── testing/                   # L1/L2/L3 matrices and original failures
├── planning/                  # one live release-gap register and roadmap
├── governance/               # doc ownership and audit
│   └── archive/              # pinned-Git historical index, no duplicate texts
├── operations/  product/      # authorized runbooks and expected UX
└── diagrams/    visuals/      # Mermaid and conceptual SVG (not UI shots)
```

The [archive index](docs/governance/archive/README.md) links both removed duplicate snapshots to immutable Git commit SHA paths. The [directory keep/delete audit](docs/governance/repository-layout-audit-2026-11-24.md) records what can be safely pruned. Old document snapshots are accessed through **pinned Git links** instead of duplicated active files; unique test evidence and failure history are retained. The documentation CI also checks local links across every `docs/**/*.md`, not just the index pages.

## What can I install today?

**Ordinary endpoint users: not yet.** The planned Tauri one-click installation, signed Agent distribution, fixed-action Polkit broker, real Pop!_OS hardware acceptance and legal approvals are unfinished. In isolated Ubuntu24 OCI only, [actual audited Rust Agent signed-APT transactions passed](docs/development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md), and [an actual malicious local Polkit YES rule was still rejected by the root-side ReviewLab](docs/development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md). **Neither is a user installer; the ReviewLab cannot run APT transactions.** [L2B-27's native 10/10 APT proof](docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) additionally shows that an **older yet validly signed Release Date is accepted** without a trusted independent rollback high-water check; this was not an actual Agent downgrade. Historical instructions to compile Rust, execute `dpkg -i`, edit TOML or enable systemd are **not** a customer installation guide. See [installer product scope](docs/product/linux-desktop-installer-v01.md) and [actual desktop L1 status](docs/development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md).

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


**Historical plan cleanup:** the obsolete v0.1 ticket-sequence transcript was removed from the live `docs/planning/` tree; its original content is linked from the [immutable history index](docs/governance/archive/README.md). Use the [active Linux delivery plan](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md). **v0.1 NOT RELEASE READY.**
