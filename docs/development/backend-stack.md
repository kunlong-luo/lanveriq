# Lanveriq Spring Boot backend — stable dependency baseline

**Decision date: 2026-10-30. Status: selected for maintainer D0 technical review; no dependencies installed or tested.** This is the **canonical Java backend stack** and overrides older generic `Spring Boot 4` and `JUnit 5` labels. These are the latest **verified stable toolchain releases** as of the decision date, **not an instruction to blindly override every transitive dependency to its own newest release**.

## 1. Version freeze

| Dependency / tool | Pinned selection or managed version | Mechanism |
|---|---|---|
| Java language/runtime | **JDK 25 LTS**, compiler release 25 | LTS preference over newer short-lived feature JDK; pin approved distribution on actual implementation |
| Spring Boot | **4.1.2** GA | `spring-boot-starter-parent:4.1.2` |
| Spring Framework | 7.x | Spring Boot 4.1.2 BOM (no direct override) |
| Spring Security | **7.1.2** | BOM |
| Maven CLI | **3.10.0** GA | **Maven Wrapper** on code start; no Gradle |
| PostgreSQL server | **18.x** existing Lanveriq DB baseline | Separate deployment image with **explicit tested patch tag/digest** at implementation; don't silently change to another major |
| PG JDBC driver | **42.7.14** | BOM |
| Flyway | **12.4.0** | BOM; PostgreSQL-specific module is required |
| HikariCP | **7.0.2** | BOM via Spring JDBC |
| Micrometer | **1.17.2** | BOM via Actuator |
| JUnit Jupiter | **6.0.3** | BOM through `spring-boot-starter-test` |
| Mockito | **5.23.0** | BOM through test starter |
| springdoc-openapi | **3.2.0** | Explicit, community-maintained Spring Boot 4.1-compatible version |
| Servlet runtime | embedded Tomcat 11.0.x | BOM, **not** a separate external servlet installation |
| JSON/validation/logging | Spring Boot-managed Jackson, Jakarta Validation/Hibernate Validator, SLF4J/Logback | no manual versions |

**Do not use pre-release Spring Boot 4.2.0-RC1 or Maven 4.0.0-rc; do not claim Gradle is needed.** Java 25 is the selected **LTS** target, not the numerically newest Java release. PostgreSQL 18 is a **chosen supported major**, not a claim that it is the newest PostgreSQL series.

Official verification links (read on 2026-10-30):
- Spring Boot stable/release: https://spring.io/blog/2026/10/23/spring-boot-4-1-2-available-now/
- Spring Boot system requirements: https://docs.spring.io/spring-boot/system-requirements.html
- Boot 4.1.2 managed dependency table: https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html
- Apache Maven releases/downloads: https://maven.apache.org/download.cgi
- Spring Boot 4 modularized starters: https://spring.io/blog/2025/10/28/modularizing-spring-boot/
- springdoc 3.2.0/Boot compatibility: https://springdoc.org/
- Flyway PostgreSQL module: https://docs.spring.io/spring-boot/how-to/data-initialization.html

## 2. Exact V0.1 backend dependency set

| Maven coordinate, `org.springframework.boot:` prefix unless noted | Role | Required? |
|---|---|---|
| `spring-boot-starter-webmvc` | Spring MVC REST, validation endpoints, embedded Tomcat | Yes |
| `spring-boot-starter-security` | Admin session/CSRF, role + device-group permissions and mTLS identity mapping | Yes |
| `spring-boot-starter-websocket` | Native WSS /ws handler, server-side scope and best-effort notifications | Yes |
| `spring-boot-starter-validation` | Bean Validation for typed request DTOs and configuration | Yes |
| `spring-boot-starter-jdbc` | Spring `JdbcClient`, `NamedParameterJdbcTemplate`, HikariCP and PostgreSQL transactions | Yes |
| `spring-boot-starter-flyway` | Explicit SQL migrations and schema versioning | Yes |
| `org.flywaydb:flyway-database-postgresql` | Flyway 12 PostgreSQL support | Yes |
| `org.postgresql:postgresql` (runtime) | JDBC PostgreSQL connector | Yes |
| `spring-boot-starter-actuator` | Restricted health, readiness/liveness, Micrometer metrics | Yes |
| `org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0` | Generate OpenAPI JSON for local controlled developer review | Yes in dev profile; **disable docs endpoint in production** |
| `spring-boot-starter-test` (test) | JUnit 6, Mockito, AssertJ | Yes, CI runs only real unit tests |
| `spring-boot-starter-security-test` (test) | Security authorization test utilities compatible with Spring Boot 4 | Yes, only unit/slice tests in early CI |

**Do not use `spring-boot-starter-web` as the canonical new dependency**: Boot 4's dedicated MVC starter is `spring-boot-starter-webmvc`. Spring Boot BOM manages all the above versions **except** springdoc 3.2.0. Avoid direct `spring-security-*`, `flyway-core`, `jackson-*`, `logback-*`, `junit-*`, `micrometer-*` version overrides just to chase patch numbers.

## 3. PostgreSQL data access decision

Choose **Spring JDBC / JdbcClient** and explicit SQL with `@Transactional`. This is intentionally **not JPA/Hibernate, jOOQ, R2DBC or Spring Data JDBC in V0.1**:
- Endpoint event ingestion requires all-or-nothing batch validation, PostgreSQL `UNIQUE(device_id,device_sequence)`, strict duplicate comparison and explicit ACKs; SQL and transactions must be directly visible.
- Inventory snapshots atomically replace bounded child NIC/address/volume rows only if `inventory_revision` is newer.
- Cursor paging for device events and role-group scope is explicit SQL; create indexes after measuring.
- Keep repositories/interfaces separated from services; use parameter binding (never dynamic SQL concatenating client input) and purpose-specific query methods.
- The current conceptual data model remains PostgreSQL/Flyway; revise the technology summary from earlier `Spring Data JDBC` to `Spring JDBC` to avoid implying two competing approaches.

Spring Data JDBC, jOOQ and Hibernate are **not inherently wrong** and can be reviewed with an ADR if complexity later warrants them.

## 4. Server modules (logical Java packages, not separate deployables)

`bootstrap`, `identity`, `enrollment`, `devices`, `inventory`, `health`, `events`, `policy`, `websocket`, `retention`, and later `audit` / `recording` / `reviewcases`. One Maven `jar`, one Spring process, one PostgreSQL database.

Controller (typed DTO/validation) → application service (auth checks/transaction) → repository (bound SQL) → PostgreSQL. WebSocket dispatcher publishes **after commit** and never acts as durable ACK. Browser REST session and Agent device-credential trust must be distinct; LAN/hostnames do not confer permissions.

## 5. Security/operational defaults

- Spring Security HTTP session in a Secure/HttpOnly/SameSite cookie for browser. Validate CSRF on mutations, exact WS Origin on upgrade, role + assigned group on **every** REST call/WS dispatch. Secure password hash via Spring Security's supported PasswordEncoder; no JWT library needed initially.
- Agent requests must be bound to an active per-device credential (P0 device mTLS design requires a separately reviewed Caddy/proxy boundary; do not blindly trust a user-supplied forwarded certificate header). Device_id UUID alone is **not** authentication.
- Explicit JSON schema/version checks, payload caps, pagination, time bounds and uniform structured errors; sensitive fields redacted from logs.
- Actuator `health` and minimum diagnostics are allowed only through internal/admin-restricted paths; **never publish** env, configprops, heapdump, mappings, loggers, metrics or OpenAPI JSON to unauthenticated LAN clients. Prometheus server and export registry can be added **only when needed**, not default.
- Use HikariCP pool caps suitable for one app instance, transaction timeout, bound query and connection timeouts. Do not block network ingress waiting on unbounded WS outbound buffers.
- Structured JSON logging can be added after actual operational need; SLF4J + Boot-managed Logback is sufficient for startup. Administrative/security access logs live in PostgreSQL and have their own retention.

## 6. Unit tests, versions and upgrade policy

Unit-only GitHub CI for V0.x, with real JUnit 6/Mockito tests. Include deterministic pure-domain tests for role scope, enrollment token expiry/one-use, immutable event fingerprints, error classification, WSS subscription scope and clock boundaries. **PostgreSQL migrations, commit behavior, proxy mTLS and real WSS behavior require manual/integration checks outside early unit CI**; tests are not considered complete solely by unit success.

At V0.1 implementation:
1. Adopt [reference Maven POM](backend-maven-reference.md) and Maven Wrapper with Maven 3.10.0, JDK 25.
2. Confirm dependency tree resolves, compile/package locally, run actual unit tests, audit transitive licenses/vulnerabilities and pin exact server container tag.
3. Maintain one `spring-boot-starter-parent` version; use BOM updates as a **reviewed unit** after compatible stable patch release, not automatic major upgrades.
4. Record confirmed versions from actual lock/build artifacts. This document is a **planning version snapshot**, not evidence of a successful build.
