# Changelog

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
