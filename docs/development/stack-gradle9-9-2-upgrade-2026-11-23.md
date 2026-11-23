# STACK-GRADLE9.9.2 — Java 25 Gradle distribution update

**2026-11-23 · main source candidate · exact-source CI PENDING · no production deployment**

## What changed and what did not

Official Gradle 9.9.2 release notes: https://docs.gradle.org/9.9.2/release-notes.html . Official release checksums: https://gradle.org/release-checksums/ .

The checked-in apps/server/gradle/wrapper/gradle-wrapper.properties downloads **gradle-9.9.2-bin.zip** with published SHA-256:

    971fcd7150ca4ba04852c0b2ca0d2d0ab9468e14b03473eab19528809914c76b

The existing gradle-wrapper.jar **remains the verified official Gradle 9.8.2 bootstrap JAR** (SHA-256 3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98). This is intentionally **a distribution-only upgrade**, not a claim that the wrapper JAR was also upgraded. Replacing a security-critical executable JAR with unverified bytes is forbidden. The official **9.9.2 wrapper JAR** digest is 621008afc4f5d59357fcf06ffa644dd5aade5e091bdc363742710445f73cdaf8; its controlled binary update and matching all CI checksum references remain separate follow-up work.

- Java 25 LTS, Spring Boot 4.1.2, JPA/Hibernate/Flyway, PostgreSQL 18.7 and production restrictions remain unchanged by this batch.
- Server unit and integrationTest source compile, full PostgreSQL18.7 Testcontainers, and Linux Rust→Caddy/Tomcat→PostgreSQL18.7 CI each run ./gradlew --version and require the actual 9.9.2 distribution. Existing wrapper JAR signature checksum guards still remain.
- These exact Gradle-wrapper properties are now path-scoped triggers for both heavyweight isolated PG18/Ubuntu Linux flows, preventing silent test skips on future distribution changes.

## Acceptance

Only mark Gradle 9.9.2 distribution as PASS once source SHA has successful **actual** server Java unit, Java integrationTest compile, PostgreSQL18.7 11 JUnit classes and both Linux acceptance CI jobs. Any failure requires analysis or a new reverting main commit. The version assertion must not be removed to convert failures into success.

A launcher-JAR upgrade to the official 9.9.2 SHA-256 must use actual trusted binary bytes and re-run all checks; until then report the launcher as 9.8.2. Neither this release nor previous PG18.7 CI means Pop!_OS physical G-05, production database backup/restore, G-02 independent WORM or FIDO CA validation. v0.1 NOT RELEASE READY, production network, collection, auto-recovery, remote and signing OFF.

## 2026-11-23 actual source-SHA acceptance

**Function SHA [d6b691a0dccb0af45ccfc9f7fb89b3142dc1043f](https://github.com/kunlong-luo/lanveriq/commit/d6b691a0dccb0af45ccfc9f7fb89b3142dc1043f): 7/7 workflows completed SUCCESS.** Actual Gradle version assertions passed at source SHA in [Java unit #45137409111](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409111), [integration source compile #45137409033](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409033), [real PG18.7 Testcontainers #45137409087](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409087), and [Linux Agent/Caddy/Tomcat/PG18.7 synthetic TLS #45137409080](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409080). [Next.js #45137409151](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409151), [docs #45137409082](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409082), [RD-2 default-OFF #45137409029](https://github.com/kunlong-luo/lanveriq/actions/runs/45137409029) also SUCCESS.

The **distribution is accepted**, but the 9.9.2 wrapper JAR binary replacement is still NOT DONE. G-02/G-05/G-08 OPEN, v0.1 NOT RELEASE READY.
