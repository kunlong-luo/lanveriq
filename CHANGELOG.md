# Changelog

## 2026-10-31 — AGT-01 Rust idle lifecycle and OS service adapters
- Created Rust 1.94 workspace, committed Cargo.lock, Tokio 1.54.1 idle lifecycle with cooperative stop, strict Serde/TOML versioned config, Clap CLI, tracing, bounded 64 KiB config and negative tests. No network, enrollment, inventory, SQLite or screen capture.
- Added experimental Linux systemd sandboxed unit with SIGTERM handling, Windows SCM dispatcher/Stop status through the maintained `windows-service` 0.8.1 crate and separate Linux/Windows unit build workflow.
- Actual real Pop!_OS/systemd and Windows 11 SCM service install/stop, credential custody, OS privileges, linked SQLite version, endpoint TLS and any monitoring are **NOT TESTED/IMPLEMENTED**. [Manual AGT-01 acceptance](docs/testing/agent-lifecycle-manual.md).


## 2026-10-31 — AUTH-01 login abuse limits, revocable sessions and authentication audit
- Added Bucket4j 8.21.0 + Caffeine 3.3.1 with bounded local rate-limit state for canonical login and transport peer, a JDK two-permit concurrent Argon2id budget, plus request-size guard and generic 429 responses; never trust user-supplied forwarded IP headers.
- Added Flyway V5 `users.session_version` and separate append-only `admin_auth_log`; browser auth session checks live disabled/version state, internal group-scoped owner-only revocation transaction increments the epoch and audits allowed action. Failed/unknown login audit stores no submitted user identifier.
- Login/out audit handlers fail closed for unavailable audit DB (including session invalidation on a failed successful-login audit). Source unit tests cover rate limits, padded-name quota separation, concurrency permit cleanup, epoch changes, group revocation, audit and malformed traffic.
- **Evidence boundary:** Gradle unit tests/bootJar can verify source behavior, not actual PG18 V1–V5 migration or concurrent DB changes, live Caddy/TLS/CSRF/browser behavior, device sessions, audit retention, or real monitored endpoints. Console login stays OFF by default.



## 2026-10-30 — AUTH-01 staged console browser sessions (disabled by default)
- Added conditional Spring Security native console-only form login/logout, HttpSession CSRF JSON bootstrap and session fixation rotation, strict exact HTTPS Host/Origin/Fetch-Site checks ordered BEFORE CSRF/logout/login, and Secure/HttpOnly/SameSite=Strict `__Host-` cookie configuration (15-minute inactivity timeout).
- Feature flag `LANVERIQ_CONSOLE_AUTH_ENABLED=false` stays off; no device, monitoring or WSS routes are opened. Unit tests cover malformed origins, cross-site and duplicate headers, missing CSRF bootstrap and no-store response. See [manual acceptance plan](docs/testing/console-session-manual.md).
- Real PG18, Caddy hostname/SNI/mTLS, runtime browser sessions, login throttling, security audit, disabled-session invalidation and rate-limited login **have not been tested or completed**.


## 2026-10-30 — AUTH-01 offline first-admin bootstrap
- Added explicit opt-in non-web Spring Boot CLI that uses an interactive terminal password prompt (not shell/env), Argon2id through Spring Security, a PostgreSQL owner-role row lock and a single JPA transaction for the first administrator, group, assigned owner scope and audit log. There is no default admin and no automatic `internal_operational` data grant.
- Added Java 25 unit tests for canonical input/secret redaction, missing confirmation, non-web-only restrictions, first-admin existence guard, locking order and audit. [Build + unit CI passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41067769799); a separate subsequent fix prevents HTTP filter bean construction in offline mode.
- Removed `final` from Spring `@Transactional` first-admin and group-scope services to permit class-based transaction proxying. [Java 25 AOP commit/rollback tests passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41068354128) alongside unit tests and `bootJar`; actual database behavior remains untested.
- PostgreSQL 18 migration/transaction concurrency, CLI process execution, real browser sessions, Caddy/TLS and WSS are **not yet verified**. The PG18 manual runbook now includes a separate synthetic first-admin acceptance checklist.


## 2026-10-30 — AUTH-01 internal administrator identity and group scope
- Added Spring Data JPA read-only user entity/repository, Spring Security `DaoAuthenticationProvider` and Argon2id password hashes (64 MiB/3 iterations); no default credentials, plaintext fallback or publicly enabled login.
- Added device-group-scoped `AdminScopeService` that queries live active user/role/group state, with separate `internal_operational` grants (Flyway V4; none seeded) for raw IP/MAC and other sensitive network detail. Added authenticated-principal-only internal token issuance facade.
- [Java 25 real unit CI and Boot JAR compilation passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762), including DAO provider, invalid credentials, disabled users, role changes and forged principal negative checks. PostgreSQL 18 V1–V4 migrations and live admin login/TLS/CSRF/WSS remain **NOT TESTED**.


## 2026-10-30 — SRV-02 pending enrollment and CSR validation
- Added maintained Bouncy Castle 1.86 PKCS#10 parsing and P-256/SHA-256 CSR signature validation; malformed, wrong-key and unsupported-curve requests are rejected in JUnit tests.
- Added one-transaction JPA PostgreSQL token consume (conditional UPDATE), pending server-side UUIDv7 device insert, public CSR digest/SPKI insert and Flyway V3 uniqueness/size checks. No raw JDBC, homegrown CSR parser, live TLS endpoint or certificate issuer.
- [Java 25 CI compilation, JUnit and bootJar passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41065309965). PostgreSQL migrations, concurrency and failed-transaction rollback are **NOT TESTED**. The manual PG18.6 script now checks V1/V2/V3, but still has **not been executed**.

## 2026-10-30 — SRV-02 internal identity and issuance slice
- Added V2 PostgreSQL admin users, roles, group scope and admin access audit schema with **no default administrator**; Flyway V1/V2 have **not** been run against a real PG18 instance.
- Added secure 256-bit one-use pairing token issuance through a group-authorized JPA native SQL operation, with hashed verifier storage, 1–15-minute expiry, audit and fail-closed handling. The token is **not** an Agent credential and no HTTP enrollment/CSR/mTLS activation or consumer endpoint exists yet.
- [Java 25 JUnit + Boot JAR packaging CI passed](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441). Added a separately run [synthetic manual PG18.6 smoke check](docs/testing/server-postgres18-manual.md); its startup, SQL and authorization results are **NOT TESTED** until observed on an actual Docker/JDK25 host.


## 2026-10-30 — V0.1 implementation begins (SRV-01)
- D0 technical baseline approved for V0.1 coding only; real employee-data collection and live pilots still require separate authorization and security/platform evidence.
- Added `apps/server` Java 25 + Gradle 9.8.2 Wrapper (official JAR checksum verified), Spring Boot 4.1.2, Spring Data JPA/Hibernate, Flyway/PostgreSQL 18 foundation and deny-by-default Spring Security configuration.
- Added V0.1 server-received heartbeat connectivity classifier with meaningful JUnit unit tests and a unit-only GitHub Actions workflow. **JDK 25 unit CI passed** ([run 41058974204](https://github.com/kunlong-luo/lanveriq/actions/runs/41058974204)), including Java/test compilation and JaCoCo. Actual PostgreSQL 18 migration/`bootJar` and mTLS enrollment are **not yet verified**; endpoint telemetry and dashboard are not implemented.


## 2026-10-30 — final documentation closure audit
- Audited 90 existing Markdown files (446 local links), 20 Mermaid source pages and 8 SVG concept images structurally; [final closure report](docs/governance/final-closure-review-2026-10-30.md). No real browser/OS/build test claims.
- Reconciled event `schema_version`/UUIDv7 fixtures, SQLx SQLite migration state, health-versus-IP/MAC permissions, silently lost WSS hints and timed REST reconciliation, V0.3 UTC media frame authorization, and separated enrollment/browser/mTLS PKI design **pending D-07 sign-off**.


## 2026-10-30 — Ecosystem reuse decision
- [ADR-0007](docs/architecture/adrs/0007-mature-ecosystem-first.md) selects mature third-party libraries over custom SDKs/async SQLite workers. Rust Agent chooses **SQLx 0.9 SQLite** (single-connection write pool and built-in migrations); React uses **Orval-generated OpenAPI types/Query hooks**. Reconciled architecture and plans; no application code has been compiled.

## 2026-10-30 — JPA-only application persistence (maintainer direction)
- Selected Spring Data JPA / Hibernate ORM as the **only direct application DB access layer**, with PostgreSQL-specific native SQL via JPA `EntityManager` or repository `@NativeQuery` for transactional event ingest/inventory revisions; removed the **direct** `spring-boot-starter-jdbc` choice. PostgreSQL JDBC driver/HikariCP still underpin Hibernate transitively; Flyway continues schema migrations. This is a documentation decision, not compiled code.

## 2026-10-30 — Gradle backend selection (maintainer direction)
- **Gradle Groovy DSL replaces Maven** for the Java backend. Version baseline: Gradle 9.8.2, Java 25 LTS, Spring Boot 4.1.2, dependency-management 1.1.7, JaCoCo 0.8.16.
- Added [backend Gradle reference](docs/development/backend-gradle-reference.md), removed Maven/POM reference, selected minimal JDBC/REST/WSS/Flyway/Actuator/security dependencies rather than copying Kafka/Redis/InfluxDB/JPA/geo/OTel from an unrelated IoT build; **no code or built dependencies yet**.

## 2026-10-30 — document consistency audit
- Reconciled V0.1 base RBAC, Monitoring Center, recording review-case APIs, agent/DB authorization arrows, capture and source-gap milestones, revised segment trial and inventory flow.
- Statically reviewed the 83 existing Markdown docs, 20 Mermaid diagrams and 8 SVG assets; recorded remaining non-rendered/manual and organizational decisions in [audit report](docs/governance/documentation-review-2026-10-30.md).


All notable project changes will be documented here. No product binaries or running services exist yet.

## [Unreleased] — documentation planning baseline, 2026-10-30

### Added
- English README and Chinese project introduction.
- Product requirements, user journeys, nonfunctional targets and acceptance criteria.
- LAN system architecture, component boundaries, PostgreSQL schema and SQLite/WAL agent outbox design.
- Device enrollment and event API contracts, platform compatibility matrix, later recording design.
- Security/privacy, device authorization, threat model, retention and operational runbooks.
- Development roadmap, issue-sized backlog, unit test strategy and docs-first gate.
- Architecture decisions for monorepo, databases, disclosed recording and rapid main-first development.
- Pagevela architecture reference and selective transfer matrix.
- Detailed atomic event delivery and reinstall identity rules, organizational policy lifecycle, permission matrix, health/gap semantics, v0.1 manual acceptance and implementation sequence.
- Initial polling-first draft was **superseded** by v0.1 REST + authenticated WebSocket dashboard notifications, retaining REST for Agent durability; new WS contract and ADR-0005/0006.
- Eight coordinated self-contained 16:9 SVG stakeholder infographics (overview, data flow, tech stack, capabilities, enrollment, sample dashboard, offline recovery, privacy) with visual design guide, asset inventory and terminology matrix.
- Device inventory V0.1 design for OS/CPU/RAM/disks, multi-NIC MAC and IPv4/IPv6, device identity separation, metric sampling, restricted access and retention; inventory ER/data-flow diagrams and acceptance checklist.
- 16 GitHub Mermaid diagram pages with linked index: system/component/deployment, registration, durable event delivery, WS flow/lifecycle, PostgreSQL/SQLite models, RBAC, capture, coverage, trust, retention and dashboard navigation.
- Cross-platform/WS test matrix, troubleshooting and release runbooks.
- Documentation-only internal link and Mermaid fence checks; no actual diagram rendering, runtime testing or product code.

### Policy
Direct main commits during pre-release; CI only runs unit tests once code exists; no E2E until a later quality stage. **No functional implementation has begun.**
