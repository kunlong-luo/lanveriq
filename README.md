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

## v0.1.0-dev.1 source-only preview candidate (NOT PUBLISHED)

[Candidate release notes](docs/operations/source-preview-v0.1.0-dev.1.md) · [Offline preflight source](tools/release/verify_source_preview.py) · [L1 #45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790). This is a **source-only development review candidate**, with no Git tag or GitHub Release, no downloadable Agent installer/GUI installation asset and **no approval to deploy**. The verifier rejects fake approved publisher/Polkit/anti-rollback claims, enabled production switches and added assets. The CI identity is pinned to the exact historical synthetic SHA and run IDs; this is **not independent release provenance**.

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

The next isolated business-loop milestone is [durable Linux inventory recovery after a real Caddy outage, exact mTLS ACK, PG18 revision advance and operator-triggered Chromium refresh](docs/development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md). **Verified at functional SHA [`db570181`](https://github.com/kunlong-luo/lanveriq/commit/db570181d603a7f069a85a23c009658e9a8bed4c): [Linux L2 #45554279736 — both jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736).** This is not a product installer, automatic Agent recovery or production approval.

**G-06 incremental L2 PASS:** [Post-reconnect duplicate/stale/conflict PG18 invariants](docs/development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md), functional SHA [`9ba85399`](https://github.com/kunlong-luo/lanveriq/commit/9ba853991245765eaf313d3b99d9707c01373942), [Linux #45571463449 both jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449). Synthetic-only; G-06 OPEN.

**G-06 isolated L2 PASS:** [Original SQLite replay after PostgreSQL commit with ACK withheld from Rust](docs/development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md), functional SHA f83aeab6, [Linux #45581861955 both jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955). **New L2 PASS:** [Real PostgreSQL18 SIGKILL/WAL restart before exact Rust outbox replay](docs/development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md), functional SHA 3e5b7009, [Linux #45583968475 both jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583968475). Neither validates true on-wire ACK loss or production disaster recovery.

**G-08 isolated backup:** [PG18 restore #45586283694 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283694); [corrupt archive #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849). [Same-admin backup evidence CLI](docs/operations/backup.md) `06897384` [L1 #45588705792 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588705792), L2 NOT RUN; never production DR approval.

## Native APT: cold-cache signed repository and witness double-replay (L2B-33)

[L2B-33 native Ubuntu OCI #45569177575 — 20/20 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575) keeps the previous 17 genuine signed-APT cases and adds cold-cache validation of **both an older signed `InRelease`/different `Packages` index and an authentic, same-sequence old witness**. Native APT actually adopted the older index; a *separately read-only mounted CI-only approved witness digest* refused the joint replay. Approved evidence passed; missing anchor failed closed. [L2B-33 report (including first CI failures)](docs/development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md). **This is not externally administered anti-rollback custody, an approved release signer, a user installer or Pop!_OS L3. G-05 remains OPEN.**

## Release boundary and next work

An isolated [Agent→mTLS→Spring/PostgreSQL→Next.js synthetic chain](docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) has passed. Native [APT accepted a valid older signed Release](docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md); the [L2B-28 source-only approval-denial contract](docs/development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) does **not** implement an independent trusted anti-rollback high-water, release publisher or installation authorization.

Before any production deployment: independently approved publisher root, secure monotonic high-water and trusted time, least-privilege Polkit authorization, consented Pop!_OS COSMIC L3, administrator MFA/CA, externally administered WORM, legal and SRE disaster-recovery sign-offs. See [AGENTS.md](AGENTS.md), [security policy](SECURITY.md) and [release runbook](docs/operations/release.md). **v0.1 NOT RELEASE READY.**
