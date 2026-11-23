# STACK-BOOT4.2 — Spring Boot 4.2.0 + Springdoc 3.2.1 compatibility upgrade

**2026-11-23 · MAIN SOURCE CANDIDATE · same SHA CI PENDING · v0.1 NOT RELEASE READY**

## Rationale and official sources

Spring Boot 4.2.0 was released 2026-11-19: https://spring.io/blog/2026/11/19/spring-boot-4-2-0-available-now/ .
Official migration/release notes: https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.2.0-Release-Notes .
Springdoc OpenAPI starter API 3.2.1: https://springdoc.org/modules.html .

Code changes are limited to the existing apps/server/build.gradle Gradle plugin spring-boot **4.1.2→4.2.0** and OpenAPI API starter **3.2.0→3.2.1**. Java 25 LTS, Gradle 9.9.2 distribution, JPA/Hibernate + Flyway, PostgreSQL18.7, Caddy/Rust TLS1.3 and cloud-neutral witness remain as previously approved source design. No new framework, vendor SDK, automatic device collection, production service, certificate issuer, login or FIDO trust is enabled by upgrading dependencies.

## Breakage risks and required exact-SHA checks

- **Security/routing:** Servlet filter chain and RBAC/Origin/session rejection, real Springdoc DTO schema, CSRF and trusted forwarded headers may be affected by Spring Framework/Tomcat BOM upgrades. Run actual Java25 unit tests and do not accept a skipped security test.
- **Database:** Re-execute all 11 real Testcontainers PostgreSQL18.7 classes (Flyway, row immutability, rollback, credential/revocation); no H2 stand-in or compilation-only acceptance. Assert server_version_num 180007.
- **TLS:** Re-execute disposable Ubuntu24 real Rust Agent → Caddy TLS1.3 → Spring/Tomcat TLS1.3 → PostgreSQL18.7 ACK/replay and negative privilege tests. The workflow now watches apps/server/build.gradle.
- **Artifact:** Build actual bootJar in the Java unit workflow. Milestone-only Orval client regeneration/Chromium authenticated real API remains separate and cannot be claimed from an untriggered workflow.
- **Operational:** Redis is still optional, not a permission/ACK store; TLS trust, admin MFA and external WORM remain NOT AUTHORIZED. Evaluate known Spring Boot 4.2 changes to forwarded headers/SSL bundle options before any later real host service acceptance.

## CI reporting and rollback

Record functional source SHA and completed run URLs only when actual. On a release regression, revert the version pins in a **new main commit**, retain failed CI, and do not flip any feature flag. **G-02/G-05/G-08 and all production release gates remain OPEN. v0.1 NOT RELEASE READY.**
