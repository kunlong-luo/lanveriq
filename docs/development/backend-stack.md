# Spring Boot server stack — Gradle / Java 25 stable baseline

**Maintainer decision (2026-10-30): Gradle, not Maven.** D0 is approved and the [SRV-01 source scaffold](../../apps/server/README.md) now exists, including real `build.gradle`, official Gradle Wrapper and CI unit tests. **Actual PostgreSQL 18 migration, mTLS and server runtime are not verified by the scaffold alone.** The user chose Groovy DSL; the entire D0 technical baseline was separately approved, not merely this build tool.

## Verified stable release baseline (2026-10-30)

| Technology | Chosen version | Notes |
|---|---|---|
| Gradle Wrapper | **9.8.2** stable | Official Gradle releases 2026-10-19; Java 25 runtime/toolchain supported |
| JDK | **25 LTS** | Gradle Java toolchain `languageVersion=25`; distribution pinned at first build |
| Spring Boot Gradle plugin | **4.1.2** GA | October 22/23 2026 release; do not use older 4.1.1 example or 4.2 RC |
| Spring dependency-management plugin | **1.1.7** | Integrates Boot's managed dependency BOM |
| Gradle JaCoCo plugin | Built-in `jacoco` | Agent/report **0.8.16** latest released version |
| Spring Framework | 7.x | Boot 4.1.2 BOM owns compatible patch version |
| Spring Security | **7.1.2** | Boot BOM |
| PostgreSQL | **18.x** | Existing server DB major; pin **tested patch image/digest** on deployment |
| Flyway | **12.4.0** | Boot BOM; add `flyway-database-postgresql` |
| PostgreSQL JDBC driver | **42.7.14** | Boot BOM |
| HikariCP | **7.0.2** | Boot BOM, transitively included by Spring Data JPA starter |
| Micrometer | **1.17.2** | Boot BOM via Actuator |
| JUnit Jupiter | **6.0.3** | Boot BOM via test starter; older JUnit 5 reference superseded |
| Mockito | **5.23.0** | Boot BOM via test starter |
| springdoc-openapi | **3.2.0** | explicit dependency; official Boot 4.1.x-compatible release |

Spring Boot's BOM pins its supported transitive libraries and starters. **Don't override all transitive versions just because an individual package has a newer release.** Verify real resolution/test/compile when code is created. Latest stable source references: https://gradle.org/releases/ ; https://docs.gradle.org/current/userguide/compatibility.html ; https://plugins.gradle.org/plugin/org.springframework.boot/4.1.2 ; https://plugins.gradle.org/plugin/io.spring.dependency-management ; https://docs.spring.io/spring-boot/appendix/dependency-versions/coordinates.html ; https://www.jacoco.org/jacoco/index.html ; https://springdoc.org/faq.html .

## Build-tool decision

- **Gradle 9.8.2 + committed Gradle Wrapper (`./gradlew`)**, `build.gradle` **Groovy DSL** matching the maintainer's supplied example. No `pom.xml`, no `mvnw`, no Maven build workflow.
- Plugins: `java`, `jacoco`, `org.springframework.boot:4.1.2`, `io.spring.dependency-management:1.1.7`.
- Boot BOM manages Spring/framework versions automatically. Build scripts use explicit versions only for Gradle/Boot/plugin, JaCoCo and community springdoc.
- **Use Maven Central as the dependency repository** (this is a repository service, **not** choosing the Maven build tool). The Aliyun Maven mirror in the supplied example may be enabled **optionally** for developer networks, after verifying cache provenance/TLS and avoiding silent repository-switching. Gradle Plugin Portal remains required to resolve Gradle plugins.
- One `apps/server/` Gradle **single-module executable jar** initially, one Spring Boot modular monolith, with logical package boundaries not 10 deployables.
- SRV-01 has committed `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.properties`, the official `v9.8.2` Gradle wrapper JAR and actual `settings.gradle`/`build.gradle`. The JAR SHA-256 is checked against Gradle's official published checksum; the distribution ZIP checksum is pinned. Source scaffolding and checksum verification **do not prove dependency resolution or Java/PG integration**.

See the [documentation-only Groovy DSL reference](backend-gradle-reference.md).

## V0.1 backend dependencies — minimal executable core

| Gradle dependency | Why |
|---|---|
| `spring-boot-starter-webmvc` | Typed REST endpoints on Spring MVC + embedded Tomcat |
| `spring-boot-starter-security` | Admin login/session, CSRF, group/scope authorization; device identity mapping |
| `spring-boot-starter-websocket` | Native WSS push to authenticated dashboard; metadata only |
| `spring-boot-starter-validation` | DTO/Bean Validation and input constraints |
| `spring-boot-starter-data-jpa` | **Only direct application persistence starter**: Spring Data repositories + Hibernate ORM + HikariCP (transitive) |
| `spring-boot-starter-flyway` | Versioned PostgreSQL migrations |
| `org.flywaydb:flyway-database-postgresql` | PostgreSQL support in modern Flyway |
| `org.postgresql:postgresql` (runtime) | PG JDBC |
| `spring-boot-starter-actuator` | Restricted health and basic Micrometer metrics |
| `org.springdoc:springdoc-openapi-starter-webmvc-api:3.2.0` | Controlled internal OpenAPI JSON; disable in deployment/prod |
| `spring-boot-starter-test` (test) | JUnit 6, Mockito and assertion libraries |
| `org.springframework.security:spring-security-test` (test) | Targeted authorization unit/slice tests |
| `org.junit.platform:junit-platform-launcher` (test runtime) | Gradle JUnit Platform launcher (BOM) |

**Persistence decision (maintainer): JPA/Hibernate only for application database access.** Spring Data `JpaRepository` handles bounded ordinary CRUD and pagination. For specialized PostgreSQL operations (all-or-nothing event batches, `ON CONFLICT` idempotency, inventory revision compare-and-set, scoped audit queries), use **Spring Data JPA `@Query(nativeQuery=true)` / `@NativeQuery` or JPA `EntityManager.createNativeQuery` within `@Transactional`**, not a parallel `JdbcClient` or `JdbcTemplate` repository layer. Preserve SQL constraints, bound parameters, transactional rollback, explicit flush/clear behavior when mixing ORM writes and native SQL, and profile Hibernate batching/N+1 before tuning. Avoid entity graphs and eager loading for large event histories. Flyway owns every schema migration; Hibernate uses `ddl-auto=validate` and `open-in-view=false`. The PostgreSQL JDBC driver, connection pool and transitive `spring-jdbc` **still exist** under JPA: removing direct JDBC access does not remove the JDBC transport.

## Review of the supplied example dependencies

| User-supplied component | V0.1 choice | Reason |
|---|---|---|
| `jacoco` | **Include**, tool 0.8.16; produce XML + HTML | Measurable Java unit-test coverage, no false pass |
| Caffeine + Cache starter | **Defer** | 1–10 devices and one app instance; prefer bounded simple in-process logic first; cache role decisions only with correct invalidation |
| OAuth2 Resource Server | **Defer** | Admin uses secured same-origin session; Agent uses separately approved device mTLS; no JWT API gateway in V0.1 |
| Spring Data JPA / Hibernate | **Include as the single V0.1 ORM/persistence API** | Ordinary entity CRUD + targeted native PostgreSQL SQL for events/inventory within JPA transaction boundary |
| Redis / Kafka | **Exclude** | No distributed queue/cache; SQLite already provides Agent outbox and WSS is in-process |
| InfluxDB 3 | **Exclude** | PostgreSQL bounded device metrics suffice for 1–10 devices |
| Hibernate Spatial / JTS | **Exclude** | No geospatial tracking or location analytics requirement |
| OpenCSV | **Defer** | Export may be added to V0.2; avoid adding parser/export features before audit data exists |
| GoRules Zen Engine | **Exclude** | No dynamic rules engine planned; versioned typed policies are sufficient |
| Lombok | **Omit** | Java 25 records/standard constructors remove most boilerplate; avoid annotation tooling |
| MapStruct | **Defer** | Add only if DTO mapping becomes complex |
| BouncyCastle provider | **Defer** | Standard Java cryptography supports current P-256/X.509 design; add only after a required crypto algorithm is identified |
| Logstash Logback Encoder | **Defer** | Standard Boot SLF4J/Logback adequate initially; redact security data |
| OpenTelemetry API + Javaagent | **Defer** | No collector/backend to receive spans; Actuator/Micrometer first |
| Testcontainers PG/Kafka | **No early CI** | Tests needing containers are *integration* tests, while early CI executes **only unit tests**; add PG integration tests later, no Kafka |
| ArchUnit | **Optional later** | Useful for package boundary enforcement once multiple Java modules grow |

**Special caution:** `devices.id DEFAULT uuidv7()` is server/database-generated; verify the JPA/Hibernate identifier mapping and insertion/RETURNING behavior against a real PostgreSQL 18 instance before relying on automatic ID generation. Ensure JPA unique constraints and Flyway schema do not drift. No H2 emulation of PostgreSQL 18 behavior for claims about actual transactions/UUIDv7; DB migration correctness requires a real PostgreSQL test environment outside the early unit-only CI.

## Operational/security baseline

Keep Spring behind Caddy/private server network. mTLS Agent access, browser sessions, CSRF/Origin checks, group-scoped REST + WSS, least privilege and stored access audits remain product security requirements. Do not trust client-supplied certificate headers. Restrict Actuator and disable public OpenAPI/Swagger UI endpoints in deployment; no raw employee data in metrics/logs. Spring Boot in-process WSS assumes **one server instance** until explicitly redesigned.

## Coverage and testing policy

JaCoCo reports are attached to unit tests. The supplied **83% coverage limit** is a possible *later reviewed quality target*, **not an unconditional `check` or CI gate** before meaningful tests exist. Add sensible coverage exclusions only with explanation, never fake a passing placeholder. Early GitHub CI invokes real `./gradlew test` when the Java project exists; Gradle `check` and local build may be run by developers; no E2E/Testcontainers integration jobs in early CI.

With Java source now committed, validate plugin/Gradle compatibility, `./gradlew --version`, dependency resolution, `./gradlew test` and `./gradlew bootJar` on JDK 25; also run a real PostgreSQL 18 Flyway/JPA integration exercise outside unit-only CI. **All versions above are documented choices, not verified build artifacts.**
