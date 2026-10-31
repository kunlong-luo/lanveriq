# Lanveriq server — SRV-01, SRV-02 and AUTH-01 internal foundations

D0-approved Java 25 / Gradle 9.8.2 (Groovy DSL) / Spring Boot 4.1.2 / Spring Data JPA/Hibernate / PostgreSQL 18 / Flyway.

**Not a usable monitoring service.** SRV-02 now contains internal 256-bit one-use token generation and a group-scoped **JPA issuance** operation, plus V2 users/roles/scopes/audit migration. SRV-02 additionally validates a PKCS#10 CSR with Bouncy Castle 1.86 (ECDSA P-256/SHA-256 proof of possession) and now performs conditional one-time token consumption plus PostgreSQL UUIDv7 pending-device/CSR inserts in a **single Spring JPA transaction**. The CSR's requested subject/SAN is never treated as authoritative identity. **No public enrollment endpoint, enabled browser login, certificate issuance/registration, network-reachable mTLS activation, uploads or dashboard REST/WSS has been implemented. An internal activation transaction is now staged but unverified on PostgreSQL.** A conditional console login/CSRF/logout slice now exists in code but is OFF by default, NOT an approved deployment feature. The service rejects business routes by default. No real workplace data is authorized.

## Build and run (Java 25 required)

```sh
cd apps/server
./gradlew test
./gradlew bootJar
# Opt-in ONLY: requires an available Docker daemon; starts synthetic PostgreSQL 18.6.
./gradlew integrationTest
```

The wrapper JAR is copied from the official Gradle `v9.8.2` tag and verified against the Gradle 9.8.2 published SHA-256 (`3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98`). The distribution URL is pinned with SHA-256 in wrapper properties. Do not run an unverified wrapper JAR.

To start, provision **an isolated PostgreSQL 18 database with a non-superuser**, configure `LANVERIQ_DB_URL`, `LANVERIQ_DB_USER`, `LANVERIQ_DB_PASSWORD`, and run `./gradlew bootRun`. The DB is required; do not disable Flyway or replace PG with H2 to mask startup/migration failures. The app binds to `127.0.0.1:8080` by default and exposes only a basic health endpoint. No default administrator password or user is created.

Flyway V1/V2/V3/V4/V5/V6/V7 SQL is **not yet executed against PostgreSQL** in an observed test. A dedicated Testcontainers PostgreSQL 18.6 integration suite now exists under `src/integrationTest`, using Spring Boot `@ServiceConnection` and JPA/Hibernate. It is compiled in automatic unit CI but executed only via the explicit `integrationTest` Gradle task or the manual-only [GitHub Action](../../.github/workflows/server-postgres18-integration.yml). This is a test harness, not yet a successful database run. [Manual PostgreSQL 18.6 validation](../../docs/testing/server-postgres18-manual.md) provides a disposable, synthetic local smoke check: `bash scripts/verify-postgres18.sh` (requires local Docker, JDK 25, curl and openssl). CI has **passed** Java 25 compilation, the new real CSR/transaction-orchestration JUnit tests, JaCoCo and `bootJar` archive packaging ([run 41066726762](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762)); CI runs only unit tests, with packaging as an additional build task. Migration and JPA-generated UUID behavior still require a real PG18 test environment.

## SRV-02 trusted Agent ingress boundary — REFERENCE ONLY

`AgentIngressAssertionPolicy` stages a **default-OFF, package-internal** parser for a future verified Caddy Agent identity assertion. It checks an exact loopback transport peer, canonical Agent Host and POST path, one lowercase SHA-256 leaf fingerprint, and rejects missing, duplicate, underscored/lookalike and forwarded certificate headers. It rejects the known SHA-256 of an empty client certificate. The bean is created only with `lanveriq.agent.ingress.assertions-enabled=true` **and** an explicit canonical `expected-host`; there is **no business HTTP route** using it.

[Reference-only Caddyfile](../../deploy/caddy/Caddyfile.reference) isolates console, enroll and Agent SNI, uses `require_and_verify` with a dedicated public client CA, `strict_sni_host`, h1/h2 only, and edge header stripping/overwriting; console/enroll return 503 and Spring still denies protected routes. **This is not a runnable/validated production configuration**. Neither a forwarded fingerprint nor a loopback remote address proves a verified mTLS handshake, because local processes in the same namespace could forge a proxy header. The reference now uses **TLS client authentication on Caddy→Spring as well**: Caddy presents its dedicated upstream client certificate; Tomcat requires a trusted TLS client leaf and the internal parser pins its DER SHA-256 digest before accepting a forwarded Agent fingerprint. Opt-in profile `application-agent-upstream-mtls-acceptance.yml` binds HTTPS to loopback 8443 with a separate backend server key and trust store. Real mutual-TLS proof, Caddy adapt/validate, TLS/Host/HTTP2 negative probes, direct-backend isolation and live PostgreSQL credential checks are still mandatory before any controller is exposed. [Manual SRV-02-EDGE gate](../../docs/testing/agent-ingress-identity-manual.md).

## SRV-02 signed certificate validation + scoped registration — DEFAULT OFF

The internal `ClientCertificateProfileValidator` now checks a **genuine signed** P-256 clientAuth leaf with JDK PKIX/Bouncy Castle against an **independently pinned dedicated client CA root** (not the request/OS trust store), exact server-assigned `urn:uuid:<device_id>` URI SAN, CSR SPKI digest, digitalSignature-only KeyUsage, exclusive clientAuth EKU, CA=false, and a maximum 90-day lifetime. Only a validated SHA-256 public leaf fingerprint/issuer/serial and validity metadata leave this validator. No CA private keys or custom issuance mechanisms exist.

A `ReviewedDeviceCertificateRegistrationService` is opt-in under `lanveriq.client-cert-registration.enabled=true` and requires an **absolute local** `lanveriq.client-cert-registration.trusted-ca-file` with the independently reviewed public client CA certificate. This feature is **DEFAULT OFF**, has **NO HTTP ENTRYPOINT**, and must not be enabled outside isolated synthetic development. The registrar requires a real server-session `AdminPrincipal`, device-group `owner_security_admin` recheck under a device row lock, a pending unexpired CSR with the identical public key, and zero already registered credentials. It atomically records the reviewer, a **pending** public credential fingerprint, and the `device_certificate.register` access audit. The device stays `pending_certificate` until separately verified mTLS activation.

The JDK PKIX checker in this staging slice has **no CRL/OCSP revocation evidence**, and there is no real signed-leaf operator workflow, console controller/CSRF/Origin security acceptance, CA provisioning, trusted proxy bridge or PostgreSQL 18 runtime acceptance. No real-device registration is authorized. See [SRV-02-REG synthetic/manual acceptance](../../docs/testing/server-cert-registration-manual.md).

## SRV-02 internal credential activation — no HTTP route

`DeviceActivationService` and its JPA repository stage the D0-approved **operator-reviewed pending credential → active** transaction. Before any update the caller must supply an internally constructed `VerifiedDeviceCertificate` (never a JSON/header fingerprint), and schema/device/path identity must match. PostgreSQL locks the canonical device, requires an approved unexpired pending CSR with the matching SPKI digest, a current unrevoked pending credential with the exact fingerprint, and conditionally changes both credential and device state **within one Spring transaction**. A valid same-certificate retry in the already-active state is idempotent. Unit/Spring AOP and opt-in PostgreSQL 18.6 synthetic tests are staged.

The synthetic integration fixture **directly inserts a reviewed-CSR and credential row**; it does NOT test a signed certificate, issuer verification, operator scope/CSRF, certificate custody, actual Caddy client authentication or request-provenance. Offline leaf signing, independently reviewed certificate registration and verified Caddy→Spring identity forwarding remain **unimplemented**. The activation service is **internal only** and must not be exposed until these boundaries pass real security tests. [SRV-02 activation test plan](../../docs/testing/server-cert-activation-manual.md).

## HB-01 internal latest heartbeat state — V7, DEFAULT OFF

Flyway V7 introduces the **latest-only** `device_heartbeat_latest` operational state per device (queue, errors as bounded machine codes, capability status, version and timestamps). The `DeviceHeartbeatService` accepts the V0.1 typed heartbeat internally, rejects unbound or invalid identities/payloads, rechecks an **active, unrevoked, unexpired fingerprint** under a PostgreSQL device row lock, and atomically updates `devices.last_seen_at` plus the latest heartbeat row. PostgreSQL supplies `received_at`; an Agent-supplied `sent_at` is not authoritative for connectivity or source coverage. No raw network, file paths, process names, activity or hardware serials are accepted.

Java 25 JUnit policy/JPA/Spring proxy tests and opt-in PostgreSQL 18.6 integration assertions are present. **This is not an HTTP endpoint**. Caddy authenticated identity forwarding, Agent service scheduling, actual TLS, device activation, and real database execution remain pending. Never expose direct `/heartbeats` without a verified mTLS bridge. [HB-01 synthetic-only acceptance](../../docs/testing/server-heartbeat-manual.md).

## AUTH-01 staged browser session — DEFAULT OFF

Spring Security's maintained form login/logout, HttpSession-based CSRF tokens and session fixation protection have been staged for the **console-only** `/api/v1/auth/**` paths; no business APIs become accessible. Security requires an explicitly canonical HTTPS console origin and verifies exact Host/Origin/fetch-metadata **before** CSRF/logout/login filters. Browser sessions use a 15-minute host-only `__Host-lanveriq-session` cookie with `Secure`, `HttpOnly`, `SameSite=Strict`; forwarding headers are not automatically trusted.

**Config remains `LANVERIQ_CONSOLE_AUTH_ENABLED=false`**. Source now includes Bucket4j/Caffeine login throttling, session-version revalidation/revocation and a minimal authentication audit, but **actual** PostgreSQL 18, Caddy trusted TLS/DNS/strict SNI/Host, real browser/session negative tests, abuse controls, and audit retention verification remain missing. An operator must not enable it for a real pilot. See [console session acceptance matrix](../../docs/testing/console-session-manual.md). The code has Java 25 unit coverage for Host/Origin/fetch-site and CSRF response; neither real HTTPS login nor the complete Spring filter chain has been manually exercised.

## SRV-03 internal event ingestion — Flyway V6, no HTTP route

[SRV-03 service](src/main/java/com/lanveriq/server/events/DeviceEventIngestionService.java) validates the entire V0.1 operational event batch before opening a database write. Flyway V6 provides immutable `endpoint_events`, native UUIDv7, JSONB and two device-scoped unique identifiers. One Spring JPA/Hibernate transaction locks the active device, revalidates its active/expiry/revocation credential fingerprint, inserts new events through PostgreSQL JSONB `ON CONFLICT DO NOTHING` and compares every accepted sequence+ID+canonical SHA-256 fingerprint. A conflict fails and rolls back every new row; Spring AOP commits before ACK is returned.

Production service accepts only bounded `agent_transport/collection_gap` events; `synthetic_test/protocol_fixture` is test-policy-only. **No actual HTTP ingestion controller, Caddy verified-client identity bridge, credential activation or real PostgreSQL 18 test has run.** Business endpoints remain denyAll. Real PostgreSQL Testcontainers tests are implemented (but execution has not yet been observed). [SRV-03 acceptance matrix](../../docs/testing/server-event-ingestion-manual.md).

## AUTH-01 V5 credential/session hardening (unit verified; deployment NOT approved)

- **Limit attempts using maintained libraries:** `bucket4j_jdk17-core:8.21.0` and `caffeine:3.3.1` implement 5 attempts per canonical lowercased username per 10 minutes and 30 attempts per transport peer per minute, with 10,000/2,000 cache-entry bounds. Invalid whitespace-padded names cannot consume a valid account's individual quota. A JDK semaphore additionally caps concurrent Argon2id password checks at **two** to prevent burst-induced memory exhaustion. The login filter only trusts `getRemoteAddr()`, never `X-Forwarded-For`; behind Caddy this may be a shared peer and must be tested/tuned. It rejects oversized/unknown-length payloads, records throttle events, and sends 429. Single-instance only, not a cluster-wide guarantee.
- **Session revocation:** Flyway V5 adds `users.session_version` (starts at zero); disable/recovery/password-reset paths **must increment it** and session revalidation checks the stored version and active flag on each console-auth request. `AdminSessionRevocationService` is internal-only, uses server-authenticated owner UUID, atomically checks issuer/target group scope and rotates the epoch with an allowed access audit. No HTTP session-rotation endpoint or WSS authorization exists yet.
- **Minimal authentication audit:** `admin_auth_log` stores event type, outcome, timestamp and optional *verified* admin ID; it intentionally never stores raw usernames on failed logins, IP addresses, passwords or session tokens. Audit failure on successful login invalidates the new session rather than reporting success. Audit retention and unknown-user abuse management require separate review.
- Java 25 JUnit coverage exercises bucket limits, concurrent Argon2id budget/permit release, malformed payloads, disabled or rotated sessions, authenticated-principal-only revocation, authorization SQL structure and audit fail-closed paths. **Real PostgreSQL V5 migrations, concurrent epoch updates, Caddy peer identity, filter chain behavior and browser login have not been observed in a runtime environment.**

## AUTH-01 internal authentication and authorization foundation

- Spring Security's `DaoAuthenticationProvider` uses a **JPA-backed** `UserDetailsService` to load canonical usernames and stored password hashes; no default account exists.
- The approved password encoder is adaptive, salted **Argon2id** (64 MiB/3 iterations, explicitly versioned `{argon2id}` hashes). `{noop}` and unknown encodings are not accepted. Benchmark these settings on the actual machine before approving use; the real first-admin bootstrap is still pending.
- `AdminScopeService` makes a fresh DB role + device-group check on every operation. V4 adds independent, **unseeded** `user_data_grants` for `internal_operational` IP/MAC reads. Grant revocation and account disable are checked again, not treated as frozen session roles.
- `AuthenticatedEnrollmentTokenIssuer` derives the administrator UUID from the Spring Security principal, not from input JSON; both scope service and token repository independently check active group permissions.
- **HTTP login, CSRF/Origin enforcement with real browser sessions, rate-limited login, Caddy host separation and WSS authorization are not yet implemented/verified**. The existing security filter denies all business HTTP routes; this is an internal implementation slice, not a usable admin login.
- [Java 25 real authentication + permission tests and `bootJar`](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762) passed. [Spring AOP transaction-proxy commit/rollback regression tests](https://github.com/kunlong-luo/lanveriq/actions/runs/41068354128) also passed; actual PostgreSQL concurrency is unverified. Flyway V4, database role query semantics, login session behavior and credential revocation remain **NOT TESTED against PostgreSQL or real browser/TLS**.

## Offline first administrator bootstrap (AUTH-01; synthetic environment only)

An explicitly opted-in **offline Java terminal** can provision exactly one first owner with one new assigned device group and an access audit entry. No default username, password, group or sensitive-data grants are seeded. The command refuses to run in a web application context, reads and confirms the **administrator password using `System.console().readPassword()`**, requires 16–128 characters, and clears its terminal char arrays after use. Spring Security stores only a salted, versioned Argon2id hash.

**Requires a dedicated, isolated PostgreSQL 18 instance with Flyway V1–V6 already applied and trusted database credentials.** The following is a manual procedure, **NOT YET EXECUTED against PostgreSQL**. Use a local interactive terminal, not an IDE or CI shell without a console:

```sh
cd apps/server
./gradlew bootJar
java -jar build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar \
  --spring.main.web-application-type=none \
  --lanveriq.bootstrap.enabled=true \
  --lanveriq.bootstrap.login=sec.owner \
  --lanveriq.bootstrap.group=Engineering
```

Supply `LANVERIQ_DB_URL`, `LANVERIQ_DB_USER` and `LANVERIQ_DB_PASSWORD` through the existing DB configuration. **Never place the administrator password in shell arguments, scripts, environment variables, Git or logs.** Interactive confirmation must match. If a user (even disabled) already exists, bootstrap fails and does not issue a replacement/recovery credential. Concurrent bootstraps are serialized by locking the existing `owner_security_admin` role row in the database transaction. No `internal_operational` grant is created. For evidence, follow the [manual PostgreSQL/first-admin runbook](../../docs/testing/server-postgres18-manual.md). This does **not** enable login: the HTTP security filter continues to deny business routes.

## Security and implementation boundaries

**SRV-02 issuance is internal-only, not a callable API.** Only authenticated group-scoped owner/security admins will be allowed to issue tokens once AUTH-01 session binding/CSRF/rate limiting is implemented. The `EnrollmentTokenIssuer` requires a server-bound principal ID; the SQL re-verifies active user, group role, expiry and creates an access audit in the same transaction. **There is no standalone token-consumption API.** Internally, `PendingEnrollmentService.register` validates the token and CSR before entering the JPA repository's single-transaction consume+pending-device+CSR operation. PostgreSQL V3 enforces SPKI uniqueness, CSR size and digest validity. An invalid, expired or used token yields the same generic denial; failures after the conditional token UPDATE must roll back the whole transaction. **These transactional/locking guarantees remain unverified against PostgreSQL** until real DB tests are executed. Do not seed a default admin, expose a debug endpoint or treat static test secrets as active enrollment. Audit and DB group restrictions have not been integration-tested.


Do not expose the backend directly on the LAN; the approved future deployment is behind purpose-separated Caddy TLS hosts, with Agent-only mTLS, strict SNI/Host and group-scoped authorization. No real employees/devices/recording until independent policy and security gates are satisfied. See [D0 gate](../../docs/governance/documentation-gate.md), [backend stack](../../docs/development/backend-stack.md) and [implementation sequence](../../docs/planning/v0.1-implementation-sequence.md).
