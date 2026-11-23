# STACK-PG18.7 — PostgreSQL 18 maintenance version upgrade

**2026-11-23 · SOURCE CANDIDATE · exact-SHA 7/7 CI SUCCESS · NOT production DB migration · v0.1 NOT RELEASE READY**

## Scope

Official upstream: https://www.postgresql.org/docs/release/18.7/ . PostgreSQL 18.7 was released 2026-11-12 as a PostgreSQL 18 maintenance/security release. This batch changes the existing **synthetic test baseline** from 18.6 to 18.7 on the same major version. No actual production PostgreSQL database or filesystem is modified. No PostgreSQL 19 migration, Flyway history rewrite, JPA SQL rewrite, new network port or device activation.

- All **11** Java integrationTest Testcontainers PostgreSQLContainer classes select postgres:18.7. Test methods and immutable DB constraints are otherwise unchanged.
- PostgreSqlEventIngestionIT checks actual SQL server_version_num **180007** (previously 180006), so 18.6 cannot silently satisfy the new accepted version.
- Real isolated Caddy → Spring/Tomcat TLS1.3 → PostgreSQL script verify-agent-upstream-tls.sh selects postgres:18.7 and enforces version prefix 18.7.
- Both server-postgres18-integration.yml and linux-batch-a-caddy-pg18-acceptance.yml record actual PostgreSQL 18.7 image provenance. Path filters trigger both workflows from changed source; output must be observed before PASS.
- Other unrelated source, administrator security and production feature flags remain unchanged. New vendor/cloud SDKs are not introduced.

## Acceptance and failure discipline

1. **Same functional source SHA:** server-postgres18-integration must run real PostgreSQL 18.7, Flyway and all 11 security/transaction Testcontainers classes with mandated JUnit XML and strict server version assertion. Skipped/dry-run/compile-only do not count.
2. **Same functional source SHA:** Linux Batch A disposable Ubuntu24 real Rust synthetic → Caddy TLS1.3 → real Spring/Tomcat → real PostgreSQL 18.7 plus dual network and replay/ACK negative tests. Partial jobs are identified precisely.
3. Preserve sources and CI run URLs and any FAIL, including missing Docker image, timeouts or Java failures. If a rollout fails, revert source and CI pins with a new main commit, without touching any real database and without rewriting CI history.
4. This is an upgrade of *disposable test images*, not physical Pop!_OS24 L3 nor data migration in a nonempty external cluster. Backup/restore, WORM, CA, official FIDO, physical host, retention, consent and production runbook gates remain OPEN.

**G-02/G-05/G-08 remain OPEN; v0.1 NOT RELEASE READY; production networking, collection, automatic recovery, remote control and certificate signing OFF.**

## Actual accepted evidence

Functional SHA [544494476414a59b151aed5413617b42a100299b](https://github.com/kunlong-luo/lanveriq/commit/544494476414a59b151aed5413617b42a100299b), **7/7 completed SUCCESS**, including [PG18.7 real Testcontainers 11 classes #45136329326](https://github.com/kunlong-luo/lanveriq/actions/runs/45136329326) and [real Rust/Caddy/Spring/Tomcat TLS1.3/PG18.7 synthetic ACK/replay #45136329403](https://github.com/kunlong-luo/lanveriq/actions/runs/45136329403). No external production PostgreSQL 18 cluster was upgraded.
