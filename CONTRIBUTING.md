# Contributing to Lanveriq

**Active 2026-11-24 · v0.1 NOT RELEASE READY.** Java25/Gradle/Spring Boot4.2/PostgreSQL18.7, Rust1.100/SQLx SQLite, Next.js16.4.5 in `apps/web`, and Tauri2 read-only GUI in `apps/desktop` are real source projects with **real synthetic CI**, but are not approved for production or actual employee data. Earlier claims that there was no code and only unit tests would be added later are superseded.

Read [AGENTS](AGENTS.md), [CURRENT_STATUS](docs/CURRENT_STATUS.md), [engineering playbook](docs/governance/engineering-playbook.md), [DoD](docs/planning/definition-of-done.md), [documentation lifecycle](docs/governance/document-lifecycle.md), [release gaps](docs/planning/release-gap-register.md) before editing.

## Contribution procedure
- The current single-maintainer strategy is **direct changes on `main`**, small reversible commits with checked expected remote SHA; no force pushes or deletion of failing evidence. Multi-contributor/production branch protection requires future approval.
- Prefer maintained mature libraries and stable platform/framework features over custom crypto, networking, UUIDs, migrations, authorization or UI frameworks. Check compatibility and license. [Reuse-first](docs/development/reuse-first-policy.md) · [ADRs](docs/architecture/adrs/README.md).
- Backend is Java25/Gradle/JPA-Hibernate/Flyway/PG18; Agent Rust/SQLx/SQLite/reqwest; Web Next.js App Router/React/Tailwind4/Base UI. Do not recreate Maven, Vite, `apps/web-next` or parallel UI.
- Security and database changes require actual negative tests and appropriate L1/L2 evidence on the **same functional SHA**. Cite workflow run ID/link and exact PASS/FAIL/PENDING/NOT RUN. A path-filtered test is NOT RUN; docs-only CI is not a substitute. Follow [test strategy](docs/testing/strategy.md).
- Update subsystem SPEC, [CURRENT_STATUS](docs/CURRENT_STATUS.md) and any affected release Gate. Docs use [documentation verification](.github/workflows/docs-governance.yml). Preserve historical CI FAIL and archived links when deleting obsolete docs; use the [keep/delete directory audit](docs/governance/repository-layout-audit-2026-11-24.md). Keep root and subproject READMEs concise.

## Safety, reporting and licensing
No employee data, secrets, real cert signer keys, covert monitoring, live endpoints or recordings in tests/logs. Production Agent networking, collection, auto-recovery, RustDesk RD-2, CA issuance, FIDO trusted source and real WebAuthn MFA remain OFF. [Private vulnerability reporting](SECURITY.md).

**Maintainer-selected direction (2026-11-28): proprietary software**, not an open-source license grant for Lanveriq-owned code. Actual copyright holder, contribution title, copied/imported code, intended customer terms and distribution approval remain unverified. Public repository visibility is not a license; do not promise confidentiality or change visibility without explicit approval. Before accepting external contributions, obtain an approved rights/permission arrangement; do not treat a commit or DCO as copyright assignment. Preserve third-party licenses, notices and applicable source obligations; do not relicense dependencies. See the [ownership and third-party review card](docs/operations/gpui-distribution-review-card-2026-11-28.md). RustDesk remains OFF; no distribution authorization or SLA is granted.
