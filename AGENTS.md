# Lanveriq — AI agent rules (CURRENT)

**ACTIVE · 2026-11-22 · v0.1 NOT RELEASE READY · `main` only.** All former milestone notes are preserved in [historical AGENTS](docs/governance/archive/AGENTS-pre-consolidation-2026-11-22.md). Do not prepend CI logs to this file.

## Source authority
Read [CURRENT_STATUS](docs/CURRENT_STATUS.md), [engineering playbook](docs/governance/engineering-playbook.md), [document lifecycle](docs/governance/document-lifecycle.md), [DoD](docs/planning/definition-of-done.md), [release gaps](docs/planning/release-gap-register.md), [technical standards](docs/development/standards.md) and [ADR index](docs/architecture/adrs/README.md). Code, locks and DB migrations determine implemented behavior; verified CI at exact functional SHA determines test claims. Historical D0 approvals do not authorize production.

## Safety: hard stops
- **Production networking, device/employee collection, automatic recovery, remote control RD-2, CA/issuer, official FIDO trust and live administrator WebAuthn MFA remain default-OFF and NOT APPROVED.** No real employee data, real PKI keys, covert recording or new public network exposure.
- D4-C9–C16 are **untrusted synthetic** FIDO staging. Public GlobalSign R46 identity ≠ approved root; Ed25519 proof ≠ real reviewer key custody; asserted CRL/TLS ≠ authenticated retrieval; DB-owned digest ≠ external WORM/rollback protection. Never promote untrusted objects to Spring authentication or issuer trust.
- Maintain mTLS/TLS1.3, exact ACK, RBAC/Origin/session, PKIX/revocation, immutable database audit and rollback rejection. Any security-sensitive change needs negative tests.

## One real technology stack
- Web: only `apps/web` Next.js16 App Router / React19 / Tailwind4 / Base UI; no `apps/web-next` or Vite. Reuse [current UI tokens](docs/development/frontend-design-system.md), disabled-state semantics and keyboard/scroll accessibility. Pages preview is disconnected.
- Backend: Java25 / **Gradle** / Spring Boot4.1.2 / **JPA-Hibernate** / Flyway / PostgreSQL18; no Maven, handwritten JDBC DAO or parallel DB authority.
- Agent: Rust/Tokio / SQLx SQLite / reqwest-rustls TLS1.3 / mTLS, durable outbox and strict ACK. Reuse mature libraries instead of homemade crypto, UUID, database or remote desktop stacks.
- Linux-first **Pop!_OS 24.04**; separate Windows L3. RustDesk candidate is OFF until explicit license/security signoff.

## Every implementation cycle
1. Check the current `main` source SHA, code, ADRs and open release Gate before changes. Work **only on main** (current maintainer policy), small reversible commits; never force-push or erase failures.
2. Define exact scope and what remains unauthorized. Add targeted fail-closed negative tests immediately. L1 source/unit, L2 real disposable PG18/TLS/Agent/Chromium integration as relevant, L3 real Pop!_OS physical and independent approvals are separate.
3. Record **functional SHA, run ID/URL, test scope, PASS/FAIL/PENDING/NOT RUN**. Path-filtered ≠ executed. Never use docs-only CI as feature acceptance or claim a queued check passed.
4. Update feature SPEC and [CURRENT_STATUS](docs/CURRENT_STATUS.md), plus [release-gap register](docs/planning/release-gap-register.md) when a gate changes. Follow [documentation authority](docs/governance/document-lifecycle.md); keep detailed logs in dated reports and CHANGELOG, not AGENTS or README.
5. End with actual implementation, verified results, failures, unresolved blockers and next acceptance criterion. **v0.1 NOT RELEASE READY** until independent release approval.

[Security](SECURITY.md) · [Contributing](CONTRIBUTING.md) · [Historical archive](docs/governance/archive/AGENTS-pre-consolidation-2026-11-22.md).
