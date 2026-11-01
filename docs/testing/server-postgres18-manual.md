# Manual PostgreSQL 18 and Spring Boot smoke verification

**State: scripted but NOT EXECUTED in the repository authoring environment.**
This is a strictly **manual, synthetic-only local check** for SRV-01/SRV-02/SRV-03 schema checks, including V7 latest heartbeat state and V8 current inventory schema. The automatic GitHub Actions workflow runs unit tests, compiles opt-in database integration-test sources and packages the Boot JAR, but does **not execute** PostgreSQL containers; no privileged employee data is used. A separate manual Testcontainers workflow can execute PostgreSQL 18 integration tests.

Requirements: JDK 25, Docker engine with a daemon you control, two available loopback host ports (default 55432 for the temporary database and 18080 for Spring), curl, openssl, Bash and permission to pull the official PostgreSQL `postgres:18.6` Docker tag. Set `LANVERIQ_SMOKE_DB_PORT` and `LANVERIQ_SMOKE_HTTP_PORT` to override the smoke ports; for an existing `spark-db` with InfluxDB Explorer on 18080 use `LANVERIQ_SMOKE_HTTP_PORT=18082 bash apps/server/scripts/verify-postgres18.sh` from the repository root. The smoke script **creates and destroys only its own temporary PostgreSQL container** and never connects to the shared `spark-postgres` or `iot_farm` database. The official current supported 18.x minor is 18.6 as of 2026-10-30; a production image must be independently reviewed and **pinned by digest** before deployment.

**New staged SRV-02-REV suite:** `PostgreSqlDeviceRevocationIT` now additionally exercises the **default-OFF** group-scoped JPA device/credential revoke and allowed audit atomically, disabled/wrong-group denial, idempotency and deliberate PostgreSQL audit-trigger failure rollback. It is part of the **manually dispatched** `integrationTest` source, **not actual executed PostgreSQL evidence**, and does not add an HTTP revocation path. See [manual device revocation gate](server-device-revocation-manual.md).

**Preflight fixture correction (2026-10-31, source-only):** The activation and certificate registration Testcontainers classes previously recreated a fixed `csr_spki_digest` on every test method even though Flyway V3 enforces `UNIQUE (csr_spki_digest)` across all pending device CSRs. Their synthetic `@BeforeEach` data now uses fresh, same-device-consistent 64-hex SPKI digests. Certificate-registration fixtures also use relative validity times. This removes a predictable setup failure on the second method while preserving real PostgreSQL uniqueness enforcement. **No PostgreSQL run has yet verified the fix.**

**WEB-01-READ opt-in scope/projection suite (source-only):** `PostgreSqlDeviceDirectoryIT` adds synthetic group-isolation, stale disabled-admin, multi-role non-duplication, keyset pagination and null-safe health evidence. The manual JUnit evidence gate now requires this **sixth** class and at least **47 passing cases**; normal CI only compiles the database tests. No live HTTP directory query is enabled.

**Opt-in REST contract status (2026-10-31):** Source now contains read-only, triple-flag-conditional console device-list/detail controllers and a distinct Spring Security chain plus Boot 4 sliced MVC tests. These do **not** exercise actual HTTPS, Caddy, a persistent browser cookie or PostgreSQL; all corresponding manual gates remain open. API docs/Swagger remain disabled by default. No live data collection or frontend is enabled.

**MON-01-OVERVIEW addition:** `PostgreSqlDeviceDirectoryIT` now contains opt-in tests for real PostgreSQL count isolation, multiple-role de-duplication, online/stale/offline vs pending/revoked/retired, and immediate role-grant deletion effects. The manual JUnit gate demands four passing cases in that class (at least 49 across six classes). These are **new test sources only**, not an observed database execution. Current automatic Java CI compiles this suite without launching Docker.

**SRV-02-REG-ATOMIC source-only hardening:** The certificate registration repository now locks the pending device and *then* obtains a fresh current administrator/group scope snapshot with PostgreSQL `FOR SHARE OF u,s`. Certificate review, credential and allowed audit remain one transaction and registration rejects a suppressed 0-row audit. `PostgreSqlCertificateRegistrationIT` has two new synthetic cases for an observed device-row lock wait plus admin disable, and a deliberately suppressed audit trigger with rollback/retry. The manual evidence checker now demands all **six** Testcontainers classes, five certificate-registration cases and **>=51 passing JUnit cases total**. Actual PostgreSQL 18 execution is still **NOT OBSERVED**.

## AUTH-01 administrator mutation audit atomicity — manual PG acceptance PENDING

The internal `JpaAdminSessionRevocationRepository` now requires exactly one
`admin.sessions.revoke` audit row after incrementing `users.session_version`.
The offline `JpaFirstAdminBootstrapRepository` similarly requires exactly
one group-scoped owner grant and exactly one `administrator.bootstrap`
audit row. A PostgreSQL `BEFORE INSERT` trigger can silently suppress a
row by returning `NULL`; each zero-row outcome must throw within the
calling Spring `@Transactional` boundary so no mutation can commit
unaccounted for. Mockito unit negatives cover the zero-row branches, but
**these are not evidence of actual PostgreSQL rollback**.

The opt-in `PostgreSqlAdminAuditAtomicityIT` now stages this verification
as a **seventh** Testcontainers class. Its source has been added, but no real
PostgreSQL container run is yet observed. The tests must demonstrate:

1. On a disposable empty V1–V8 database, suppress the first administrator
   `user_role_scopes` insert and confirm the bootstrap transaction rolls
   back the newly inserted user and device group, with no allowed audit.
2. Independently suppress the `administrator.bootstrap` audit row; verify
   that the administrator, role scope and group all roll back together.
3. With a synthetic scoped owner and target account, suppress
   `admin.sessions.revoke` in `admin_access_log`; verify that the target's
   `session_version` remains unchanged and no allowed audit is committed.
4. Remove each synthetic trigger and retry on a fresh disposable fixture;
   verify exactly one grant/audit and one monotonic session version increment
   on a successful operation. Also verify wrong-group/disabled issuer denial.
5. Reject a second first-admin bootstrap after a successful initial creation,
   and verify no implicit `internal_operational` grant is inserted.
6. Keep all these as **independent pending gates** until executed against
   real PostgreSQL 18.6 and bound to a tested Git SHA/JUnit evidence.

No real administrator password, organization database, LAN endpoint, or
real employee data may be used for these checks.

**AUTH-01 session-revocation race hardening (source-only):** To prevent an
administrator's revoked grant from authorizing a session reset after a user-row
lock wait, the repository now locks issuer and target users in stable UUID
order, then makes a fresh PostgreSQL READ COMMITTED statement to check both
current group memberships, retaining `FOR SHARE` on the exact grant rows.
A new `PostgreSqlAdminAuditAtomicityIT` negative case observes an actual
`pg_stat_activity` user-lock wait, removes the issuer's owner grant before
release, and requires denial with **no session version increment or audit**.
The manual evidence gate now requires **seven Testcontainers classes and at
least 58 passing cases**. The new concurrency case has not been run against
PostgreSQL; unit tests and integration source compilation alone cannot
validate its lock semantics.

**Source compilation checkpoint (2026-11-01):** [Server unit CI #41268054649](https://github.com/kunlong-luo/lanveriq/actions/runs/41268054649)
completed successfully on commit `234fe37b1306c093ec5324d419d5523e23287dc8`.
It includes Java unit tests, compilation of **all seven** opt-in PostgreSQL
integration suites, the synthetic-XML JUnit evidence checker tests, Boot JAR
packaging, and Orval/TypeScript validation. It starts **no PostgreSQL
container**, so neither the 57-case database gate nor administrator
transaction rollback acceptance has passed. Manual workflow dispatch and
its real JUnit results remain required.

## Manual JUnit evidence gate — mandatory, not a database substitute

The [manually dispatched workflow](../../.github/workflows/server-postgres18-integration.yml) now runs a **fail-closed post-test evidence check** using `apps/server/scripts/assert-postgres18-junit.py`. It reads Gradle's generated `build/test-results/integrationTest/TEST-*.xml`, and **fails the manual run** unless all seven Testcontainers suites (event/heartbeat, activation, inventory, certificate registration, revocation, scoped device directory/monitoring, and administrator audit atomicity) report **at least 58 successful tests in total**, with explicit mandatory checks for PostgreSQL 18.6/Flyway V1–V8, two-key event conflict rollback, immutable event trigger, cross-device identity isolation, certificate revocation/race, audited registration/activation, inventory child-table rollback and post-lock admin revocation, suppressed first-admin role/audit inserts and suppressed session-revocation audit, and observed post-lock session-grant denial. Every failed, errored or skipped JUnit case blocks acceptance; absent, corrupted and duplicate reports are rejected. The Git commit, image ID/digests and JUnit artifacts continue to be captured by the existing manual workflow.

**Strict XML provenance check (source-only, 2026-11-01):** The manual
gate now requires each Gradle `TEST-*.xml` filename, JUnit suite name
and testcase `classname` to match the exact package of one of the seven
reviewed Java integration test classes. Unknown reports, mismatched
testcase identities and inconsistent/nonzero aggregate failure, error or
skip metadata fail closed. The standard counts and mandatory methods
remain **seven classes / 58 executed passing cases**. A passing synthetic
XML checker in the normal CI is not proof that Docker or PostgreSQL was run.

The evidence checker itself is unit-tested using **invented XML only** in automatic Java CI; it runs **no Docker container or database** there. To verify an explicitly run local integration suite, use:

```sh
cd apps/server
./gradlew --no-build-cache --rerun-tasks integrationTest
python3 scripts/assert-postgres18-junit.py build/test-results/integrationTest
```

The GitHub manual workflow also uses `--no-build-cache --rerun-tasks`
so its JUnit report cannot be satisfied by cached Gradle test outputs;
the Docker-backed suite must execute in that specific Actions run.

**No manual run has been observed.** A passing XML evidence checker on invented fixtures proves only that the gate logic is functional; actual PostgreSQL test acceptance requires a successful explicitly dispatched manual run on the reviewed SHA.

## Preferred real SQL and transaction verification (separate from the smoke script)

The V8 `PostgreSqlDeviceInventoryIT` suite now additionally injects PostgreSQL trigger failures separately in NIC, address and volume inserts, asserting all current/child rows roll back to the previous committed snapshot before a normal retry, and races higher/lower revisions to prove the higher snapshot wins. These cases are **source-only until the Docker-backed suite actually executes**. The [SRV-03 Testcontainers suite](server-event-ingestion-manual.md), [HB-01 heartbeat suite](server-heartbeat-manual.md), [SRV-02 activation suite](server-cert-activation-manual.md) and [INV-02 latest inventory suite](server-inventory-manual.md) are staged to exercise PostgreSQL 18 with Flyway/JPA, including event dedup, latest heartbeat, certificate activation and monotonic multi-NIC inventory replacement. It is **not** part of the default `test` task. Run `cd apps/server && ./gradlew integrationTest` with JDK 25 and Docker, or manually dispatch **Actions → PostgreSQL 18 integration (manual, synthetic-only)** on `main`. Keep the gate open until an actual successful run is recorded, regardless of successful source compilation.

From the project root, on a local workstation with these tools:

```bash
bash apps/server/scripts/verify-postgres18.sh
```

The script:
1. Generates a temporary synthetic PostgreSQL password under a private temporary directory; does not commit the secret.
2. Starts a disposable PostgreSQL 18.6 container reachable **only at host 127.0.0.1:55432**, not the LAN.
3. Builds the real executable Boot JAR via `./gradlew bootJar` and starts Spring via `./gradlew bootRun` with Flyway and `ddl-auto: validate`.
4. Waits for **health UP**, checks successful Flyway versions V1/V2/V3/V4/V5/V6/V7/V8 and DB-generated UUIDv7, verifies **no seeded administrator account, authentication-audit row, endpoint event or raw-network-access grant**, plus both canonical-event uniqueness constraints and immutability trigger, and asserts an anonymous business GET receives HTTP 403.
5. Removes the container, app process and temporary files on exit. Its test SQL rolls back the synthetic device-group insertion.

## Isolated Spring/Tomcat mTLS smoke (manual and not run)

The new [Spring Tomcat upstream-mTLS workflow](../../.github/workflows/spring-upstream-mtls-synthetic.yml) / `bash apps/server/scripts/verify-agent-upstream-tls.sh` starts a **separate disposable PG18.6 Docker container**, real Spring Boot JAR, and one-time synthetic server/client trust stores. It probes actual Java TLSv1.3 client-certificate enforcement and confirms a valid upstream proxy identity still cannot access disabled Agent business APIs. This is not a substitute for `./gradlew integrationTest`: it exercises real Flyway/startup and TLS handshake, but does **not run** all JPA concurrency, activation, inventory, or event integration cases. Nor does it run actual Caddy; both manual gates remain independent.

## Combined Caddy-to-real-Tomcat upstream bridge (manual, not run)

The [separately dispatched combined bridge workflow](../../.github/workflows/caddy-spring-postgres18-bridge.yml) adds live Caddy 2.11.7 in front of the **real Spring/Tomcat** TLSv1.3 connector and disposable PostgreSQL 18.6 via `LANVERIQ_SYNTHETIC_CADDY_BRIDGE=1 bash apps/server/scripts/verify-agent-upstream-tls.sh`. Unlike the standalone Python backend probe, it checks the real proxy-to-Tomcat handshake with both trust sides, wrong-CA upstream client rejection and wrong backend CA rejection. A successful authorized transport receives HTTP 403 due to intentionally disabled Agent routes. This is **not a functional Agent HTTP test**, nor evidence that the Testcontainers JPA integration suite has run; all these gates remain separately open without an observed manual success.

## Separate manual first-admin bootstrap check (NOT RUN)

The basic smoke script above **deletes its disposable PostgreSQL container at exit**. For the following additional acceptance steps, prepare a **separate isolated, persistent-for-test-only PostgreSQL 18** database with Flyway V1–V8. Do not bootstrap an organization's real database until security and organizational authorization are approved.

1. Verify `SELECT count(*) FROM users` is zero and `SELECT count(*) FROM user_data_grants` is zero. Keep this test DB loopback-only.
2. With the same DB connection variables configured, launch from a genuine **local interactive terminal**, outside an IDE/CI pipe (a Java Console must exist):

   ```sh
   cd apps/server
   ./gradlew bootJar
   java -jar build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar \
     --spring.main.web-application-type=none \
     --lanveriq.bootstrap.enabled=true \
     --lanveriq.bootstrap.login=sec.owner \
     --lanveriq.bootstrap.group=Engineering
   ```

   Type a newly chosen 16–128-character synthetic passphrase **only into the hidden terminal prompts**. Do not use `--password`, a password environment variable or stdin redirection; `System.console()` deliberately refuses non-interactive launch. No HTTP service must listen during the bootstrap process.
3. Check exactly one user, one correctly scoped owner/security-admin role, one `administrator.bootstrap` allowed audit row, and **zero** `internal_operational` grants. The original test DB must retain no plaintext password.
4. Try an identical second interactive bootstrap (with any valid password) against the same test DB; it **must fail without adding any rows**. Concurrent fresh-database attempts must also leave precisely one administrator and one group; verify separately with real PostgreSQL and a controlled two-terminal exercise.
5. Verify the normal server startup without the opt-in flags still has **no public login endpoint**, and unauthenticated business routes remain denied. Restore only the synthetic test DB snapshot; do not export passwords or live credentials.

Additional acceptance for V5 (not yet run): verify `users.session_version` starts at zero, that `admin_auth_log` has no seed rows and does not contain raw usernames, passwords, cookies, CSRF tokens or IP addresses. Rotate a **synthetic** account with the internal `AdminSessionRevocationService` (future verified console entrypoint) and confirm the target's `session_version` increments once and only when both issuer and target share the specified group and the issuer has an active `owner_security_admin` grant. Wrong-group and disabled issuer must not change the version or produce an allowed audit row. Verify audit `admin.sessions.revoke` commits in the same transaction. Test two concurrent revocations, error rollback and account-disable session denial separately in the actual PG18 environment.

SRV-03 real-PostgreSQL acceptance remains open: with synthetic activated devices and distinct reviewed certificate fingerprints, verify actual JSONB insertion, `FOR UPDATE` concurrency, identical retries, two-key conflict rollback, revoked-device denial and complete post-commit ACK. [SRV-03 runbook](server-event-ingestion-manual.md). The smoke script never invokes event ingestion.

The Java 25 unit tests cover first-run validation, SQL operation order, existence denial and secret-array wiping; **they do not prove real database locks, Flyway compatibility, Argon2 resource sizing on the target machine or live session behavior**. Record dated output, PostgreSQL version and image digest if manually exercised.

Retain dated local execution evidence and the tested Docker image digest in the [documentation gate](../governance/documentation-gate.md). A successful run validates startup/migrations/guard rails but **not** JPA token issuance, conditional token consume or pending-device/CSR INSERT under concurrency, JPA administrator authentication, group-scoped grants, client certificate issuance, admin sessions, actual endpoint telemetry or operating-system compatibility. Those need later independent acceptance. If the script fails, capture the redacted error and leave the corresponding gate unchecked; do not disable Flyway, CSRF or security to make the smoke pass.

No real employee information or real certificate secrets belong in test fixtures, CI logs or public GitHub issues.
