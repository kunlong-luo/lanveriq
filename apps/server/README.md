# Lanveriq server — SRV-01 build foundation + SRV-02 internal enrollment token slice

D0-approved Java 25 / Gradle 9.8.2 (Groovy DSL) / Spring Boot 4.1.2 / Spring Data JPA/Hibernate / PostgreSQL 18 / Flyway.

**Not a usable monitoring service.** SRV-02 now contains internal 256-bit one-use token generation and a group-scoped **JPA issuance** operation, plus V2 users/roles/scopes/audit migration. No public enrollment endpoint, admin sessions, CSR acceptance, pending-device atomic enrollment, mTLS activation, uploads, inventory APIs or dashboard REST/WSS have been implemented. The service rejects business routes by default. No real workplace data is authorized.

## Build and run (Java 25 required)

```sh
cd apps/server
./gradlew test
./gradlew bootJar
```

The wrapper JAR is copied from the official Gradle `v9.8.2` tag and verified against the Gradle 9.8.2 published SHA-256 (`3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`). The distribution URL is pinned with SHA-256 in wrapper properties. Do not run an unverified wrapper JAR.

To start, provision **an isolated PostgreSQL 18 database with a non-superuser**, configure `LANVERIQ_DB_URL`, `LANVERIQ_DB_USER`, `LANVERIQ_DB_PASSWORD`, and run `./gradlew bootRun`. The DB is required; do not disable Flyway or replace PG with H2 to mask startup/migration failures. The app binds to `127.0.0.1:8080` by default and exposes only a basic health endpoint. No default administrator password or user is created.

Flyway V1/V2 SQL is **not yet executed against PostgreSQL** in an observed test. [Manual PostgreSQL 18.6 validation](../../docs/testing/server-postgres18-manual.md) provides a disposable, synthetic local smoke check: `bash scripts/verify-postgres18.sh` (requires local Docker, JDK 25, curl and openssl). CI has **passed** Java 25 compilation, JUnit tests, JaCoCo and `bootJar` archive packaging ([run 41061058441](https://github.com/kunlong-luo/lanveriq/actions/runs/41061058441)); CI runs only unit tests, with packaging as an additional build task. Migration and JPA-generated UUID behavior still require a real PG18 test environment.

## Security and implementation boundaries

**SRV-02 issuance is internal-only, not a callable API.** Only authenticated group-scoped owner/security admins will be allowed to issue tokens once AUTH-01 session binding/CSRF/rate limiting is implemented. The `EnrollmentTokenIssuer` requires a server-bound principal ID; the SQL re-verifies active user, group role, expiry and creates an access audit in the same transaction. **There is no standalone token-consumption API**: consumption and validated CSR/pending UUIDv7 device creation must commit atomically in the later stage. Do not seed a default admin, expose a debug endpoint or treat static test secrets as active enrollment. Audit and DB group restrictions have not been integration-tested.


Do not expose the backend directly on the LAN; the approved future deployment is behind purpose-separated Caddy TLS hosts, with Agent-only mTLS, strict SNI/Host and group-scoped authorization. No real employees/devices/recording until independent policy and security gates are satisfied. See [D0 gate](../../docs/governance/documentation-gate.md), [backend stack](../../docs/development/backend-stack.md) and [implementation sequence](../../docs/planning/v0.1-implementation-sequence.md).
