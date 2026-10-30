# Lanveriq server — SRV-01, SRV-02 and AUTH-01 internal foundations

D0-approved Java 25 / Gradle 9.8.2 (Groovy DSL) / Spring Boot 4.1.2 / Spring Data JPA/Hibernate / PostgreSQL 18 / Flyway.

**Not a usable monitoring service.** SRV-02 now contains internal 256-bit one-use token generation and a group-scoped **JPA issuance** operation, plus V2 users/roles/scopes/audit migration. SRV-02 additionally validates a PKCS#10 CSR with Bouncy Castle 1.86 (ECDSA P-256/SHA-256 proof of possession) and now performs conditional one-time token consumption plus PostgreSQL UUIDv7 pending-device/CSR inserts in a **single Spring JPA transaction**. The CSR's requested subject/SAN is never treated as authoritative identity. **No public enrollment endpoint, admin session, certificate issuance/registration, mTLS activation, uploads or dashboard REST/WSS has been implemented.** The service rejects business routes by default. No real workplace data is authorized.

## Build and run (Java 25 required)

```sh
cd apps/server
./gradlew test
./gradlew bootJar
```

The wrapper JAR is copied from the official Gradle `v9.8.2` tag and verified against the Gradle 9.8.2 published SHA-256 (`3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`). The distribution URL is pinned with SHA-256 in wrapper properties. Do not run an unverified wrapper JAR.

To start, provision **an isolated PostgreSQL 18 database with a non-superuser**, configure `LANVERIQ_DB_URL`, `LANVERIQ_DB_USER`, `LANVERIQ_DB_PASSWORD`, and run `./gradlew bootRun`. The DB is required; do not disable Flyway or replace PG with H2 to mask startup/migration failures. The app binds to `127.0.0.1:8080` by default and exposes only a basic health endpoint. No default administrator password or user is created.

Flyway V1/V2/V3/V4 SQL is **not yet executed against PostgreSQL** in an observed test. [Manual PostgreSQL 18.6 validation](../../docs/testing/server-postgres18-manual.md) provides a disposable, synthetic local smoke check: `bash scripts/verify-postgres18.sh` (requires local Docker, JDK 25, curl and openssl). CI has **passed** Java 25 compilation, the new real CSR/transaction-orchestration JUnit tests, JaCoCo and `bootJar` archive packaging ([run 41066726762](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762)); CI runs only unit tests, with packaging as an additional build task. Migration and JPA-generated UUID behavior still require a real PG18 test environment.

## AUTH-01 internal authentication and authorization foundation

- Spring Security's `DaoAuthenticationProvider` uses a **JPA-backed** `UserDetailsService` to load canonical usernames and stored password hashes; no default account exists.
- The approved password encoder is adaptive, salted **Argon2id** (64 MiB/3 iterations, explicitly versioned `{argon2id}` hashes). `{noop}` and unknown encodings are not accepted. Benchmark these settings on the actual machine before approving use; the real first-admin bootstrap is still pending.
- `AdminScopeService` makes a fresh DB role + device-group check on every operation. V4 adds independent, **unseeded** `user_data_grants` for `internal_operational` IP/MAC reads. Grant revocation and account disable are checked again, not treated as frozen session roles.
- `AuthenticatedEnrollmentTokenIssuer` derives the administrator UUID from the Spring Security principal, not from input JSON; both scope service and token repository independently check active group permissions.
- **HTTP login, CSRF/Origin enforcement with real browser sessions, rate-limited login, initial admin bootstrap, Caddy host separation and WSS authorization are not yet implemented/verified**. The existing security filter denies all business HTTP routes; this is an internal implementation slice, not a usable admin login.
- [Java 25 real authentication + permission tests and `bootJar`](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762) passed. Flyway V4, database role query semantics, login session behavior and credential revocation remain **NOT TESTED against PostgreSQL or real browser/TLS**.

## Security and implementation boundaries

**SRV-02 issuance is internal-only, not a callable API.** Only authenticated group-scoped owner/security admins will be allowed to issue tokens once AUTH-01 session binding/CSRF/rate limiting is implemented. The `EnrollmentTokenIssuer` requires a server-bound principal ID; the SQL re-verifies active user, group role, expiry and creates an access audit in the same transaction. **There is no standalone token-consumption API.** Internally, `PendingEnrollmentService.register` validates the token and CSR before entering the JPA repository's single-transaction consume+pending-device+CSR operation. PostgreSQL V3 enforces SPKI uniqueness, CSR size and digest validity. An invalid, expired or used token yields the same generic denial; failures after the conditional token UPDATE must roll back the whole transaction. **These transactional/locking guarantees remain unverified against PostgreSQL** until real DB tests are executed. Do not seed a default admin, expose a debug endpoint or treat static test secrets as active enrollment. Audit and DB group restrictions have not been integration-tested.


Do not expose the backend directly on the LAN; the approved future deployment is behind purpose-separated Caddy TLS hosts, with Agent-only mTLS, strict SNI/Host and group-scoped authorization. No real employees/devices/recording until independent policy and security gates are satisfied. See [D0 gate](../../docs/governance/documentation-gate.md), [backend stack](../../docs/development/backend-stack.md) and [implementation sequence](../../docs/planning/v0.1-implementation-sequence.md).
