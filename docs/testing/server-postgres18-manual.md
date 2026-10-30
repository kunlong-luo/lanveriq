# Manual PostgreSQL 18 and Spring Boot smoke verification

**State: scripted but NOT EXECUTED in the repository authoring environment.**
This is a strictly **manual, synthetic-only local check** for SRV-01/SRV-02. The automatic GitHub Actions workflow continues to run unit tests and archive packaging only; no integration/E2E job or privileged employee data is used.

Requirements: JDK 25, Docker engine with a daemon you control, a free host port 55432 and 18080, curl, openssl, Bash and permission to pull the official PostgreSQL `postgres:18.6` Docker tag. The official current supported 18.x minor is 18.6 as of 2026-10-30; a production image must be independently reviewed and **pinned by digest** before deployment.

From the project root, on a local workstation with these tools:

```bash
bash apps/server/scripts/verify-postgres18.sh
```

The script:
1. Generates a temporary synthetic PostgreSQL password under a private temporary directory; does not commit the secret.
2. Starts a disposable PostgreSQL 18.6 container reachable **only at host 127.0.0.1:55432**, not the LAN.
3. Builds the real executable Boot JAR via `./gradlew bootJar` and starts Spring via `./gradlew bootRun` with Flyway and `ddl-auto: validate`.
4. Waits for **health UP**, checks successful Flyway versions V1/V2 and DB-generated UUIDv7, verifies **no seeded administrator account**, and asserts an anonymous business GET receives HTTP 403.
5. Removes the container, app process and temporary files on exit. Its test SQL rolls back the synthetic device-group insertion.

Retain dated local execution evidence and the tested Docker image digest in the [documentation gate](../governance/documentation-gate.md). A successful run validates startup/migrations/guard rails but **not** JPA enrollment SQL under concurrency, client certificate issuance, group-scoped admin sessions, actual endpoint telemetry or operating-system compatibility. Those need later independent acceptance. If the script fails, capture the redacted error and leave the corresponding gate unchecked; do not disable Flyway, CSRF or security to make the smoke pass.

No real employee information or real certificate secrets belong in test fixtures, CI logs or public GitHub issues.
