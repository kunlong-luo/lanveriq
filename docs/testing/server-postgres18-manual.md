# Manual PostgreSQL 18 and Spring Boot smoke verification

**State: scripted but NOT EXECUTED in the repository authoring environment.**
This is a strictly **manual, synthetic-only local check** for SRV-01/SRV-02/SRV-03 schema checks, including V7 latest heartbeat state and V8 current inventory schema. The automatic GitHub Actions workflow runs unit tests, compiles opt-in database integration-test sources and packages the Boot JAR, but does **not execute** PostgreSQL containers; no privileged employee data is used. A separate manual Testcontainers workflow can execute PostgreSQL 18 integration tests.

Requirements: JDK 25, Docker engine with a daemon you control, a free host port 55432 and 18080, curl, openssl, Bash and permission to pull the official PostgreSQL `postgres:18.6` Docker tag. The official current supported 18.x minor is 18.6 as of 2026-10-30; a production image must be independently reviewed and **pinned by digest** before deployment.

**New staged SRV-02-REV suite:** `PostgreSqlDeviceRevocationIT` now additionally exercises the **default-OFF** group-scoped JPA device/credential revoke and allowed audit atomically, disabled/wrong-group denial, idempotency and deliberate PostgreSQL audit-trigger failure rollback. It is part of the **manually dispatched** `integrationTest` source, **not actual executed PostgreSQL evidence**, and does not add an HTTP revocation path. See [manual device revocation gate](server-device-revocation-manual.md).

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
