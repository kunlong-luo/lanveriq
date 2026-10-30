# Lanveriq server — SRV-01 foundation

D0-approved Java 25 / Gradle 9.8.2 (Groovy DSL) / Spring Boot 4.1.2 / Spring Data JPA/Hibernate / PostgreSQL 18 / Flyway.

**Not a usable monitoring service.** Enrollment, user sessions, Agent mTLS identity, uploads, inventory endpoints and dashboard REST/WSS are not implemented yet. The service rejects business routes by default. No real workplace data is authorized.

## Build and run (Java 25 required)

```sh
cd apps/server
./gradlew test
./gradlew bootJar
```

The wrapper JAR is copied from the official Gradle `v9.8.2` tag and verified against the Gradle 9.8.2 published SHA-256 (`3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`). The distribution URL is pinned with SHA-256 in wrapper properties. Do not run an unverified wrapper JAR.

To start, provision **an isolated PostgreSQL 18 database with a non-superuser**, configure `LANVERIQ_DB_URL`, `LANVERIQ_DB_USER`, `LANVERIQ_DB_PASSWORD`, and run `./gradlew bootRun`. The DB is required; do not disable Flyway or replace PG with H2 to mask startup/migration failures. The app binds to `127.0.0.1:8080` by default and exposes only a basic health endpoint. No default administrator password or user is created.

`src/main/resources/db/migration/V1__device_identity_foundation.sql` is **unexecuted against PostgreSQL** at initial source commit; the real PG18 Flyway migration and JPA database-generated UUID mapping must be verified in an integration environment. CI deliberately executes unit tests only; the classifier unit test is pure and does not need a database.

## Security and implementation boundaries

Do not expose the backend directly on the LAN; the approved future deployment is behind purpose-separated Caddy TLS hosts, with Agent-only mTLS, strict SNI/Host and group-scoped authorization. No real employees/devices/recording until independent policy and security gates are satisfied. See [D0 gate](../../docs/governance/documentation-gate.md), [backend stack](../../docs/development/backend-stack.md) and [implementation sequence](../../docs/planning/v0.1-implementation-sequence.md).
