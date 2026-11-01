# Lanveriq installation, local development, and deployment runbook

**Status: development-only.** The repository now contains a runnable default-deny Java Spring Boot process and an idle Rust Agent binary, but **no `apps/web` React application, no production-ready Compose stack, no signed MSI/DEB installers, and no production Caddy configuration**. This is **not** a supported production monitoring deployment. Legal/privacy notice and real PostgreSQL/TLS/OS acceptance gates remain open.

For copyable development and synthetic-only commands, begin with the [Chinese README](../../README.zh.md) or [English README](../../README.md). They cover:

1. Reuse an **already running** local `spark-db` Compose PostgreSQL container `spark-postgres` at `127.0.0.1:5432` after **creating a separate `lanveriq` database and non-superuser**. The original `iot_farm` database is never migrated or removed.
2. Run `apps/server/./gradlew bootRun` with `LANVERIQ_DB_URL=jdbc:postgresql://127.0.0.1:5432/lanveriq`, `LANVERIQ_DB_USER=lanveriq` and an independently chosen secret, binding local Spring HTTP to `127.0.0.1:18081` (host port `18080` is used by the supplied InfluxDB Explorer).
3. An **optional, independent, temporary** PostgreSQL 18.6 + Boot smoke script: `LANVERIQ_SMOKE_HTTP_PORT=18082 bash apps/server/scripts/verify-postgres18.sh`. It cleans up **only its own** synthetic PostgreSQL container; it does not touch `spark-postgres` or `iot_farm`.
4. Run a default-deny Java JAR built by Gradle, or an idle Rust Agent using an absolute non-secret TOML config; neither path is a production rollout.
5. On an **isolated test VM only**, inspect and install the optional Linux Agent systemd unit without enabling it at boot. Stop/uninstall only the reviewed Lanveriq-owned files, and separately approve any deletion of the `lanveriq` database.

## Shared PostgreSQL safety boundary for `spark-db`

The supplied local Compose stack already serves several unrelated applications.
Do **not** fork a second long-lived PostgreSQL container, attach Lanveriq to
`iot_farm`, or include Redis/InfluxDB/PostGIS in the Lanveriq runtime stack.
Use a new database owner and **check actual** `SHOW server_version`: the
`postgis/postgis:18-3.6` tag identifies a PostgreSQL major version but does
not prove the tested `postgres:18.6` patch level. Dedicated manual
Testcontainers PostgreSQL 18.6 acceptance remains independent and must never
run destructive fixture SQL against the shared service.

The existing `postgres-data`, `redis-data`, `influxdb-data` and other
volumes, and the `spark-iot-net`/`spark-iot-internal-net` networks, belong
to the user's existing stack and must **not** be created, removed or renamed
by Lanveriq installers/uninstallers. Uninstalling Lanveriq must not run
`docker compose down`, `docker compose down -v`, or
`docker stop spark-postgres`.

The provided example uses a local 256 MiB PostgreSQL container memory limit;
review load, error logs and capacity separately before moving past synthetic
development. The existing Kopia/R2 backup plan may dump **only `iot_farm`**;
a newly created `lanveriq` database requires separately verified backup
coverage or its own `pg_dump` and restore drill. Never post database passwords
in the repository or CI.

## Components and deployment dependency boundary

| Product component | Planned execution site | State |
|---|---|---|
| React/Vite admin Web | Future static assets behind same-origin Caddy | **NOT IMPLEMENTED**; `apps/web` absent |
| Java Spring Boot server | Server host, behind accepted HTTPS/TLS ingress | Buildable local development process; privileged business endpoints default OFF |
| Rust endpoint Agent | Native managed Windows/Linux endpoint | Idle binary, standalone synthetic libraries and OS test-only service unit; no live enrollment/upload |
| PostgreSQL 18 | Private server network / synthetic Docker on loopback | Database migration source written; manual integration acceptance pending |
| Caddy | LAN HTTPS entrypoint with distinct console/enroll/agent hosts | Synthetic acceptance reference only; **NOT deployable Caddy production config** |

The three **business components** are Web, Server and Agent; PostgreSQL and Caddy are additional required infrastructure. The Agent should **not** be placed in the central Compose deployment. A future production build serves the React SPA as static assets; there is no requirement for a separate Node/Next.js production server. See [topology and trust boundaries](../architecture/deployment.md).

## Requirements for a later production deployment

- Dedicated supported Linux server, sized volumes/backups, Docker Engine/Compose and restricted admin SSH.
- Approved internal CA and client CA; correct console/enroll/Agent hostnames, Caddy SNI/Host checks, mutually authenticated Agent TLS and independent pinned Caddy-to-Spring TLS.
- PostgreSQL on a private network with pinned/reviewed image, persistent volume, Flyway versioning, `pg_dump`/restore drill, secret custody and retention controls.
- Completed React app, exact same-origin session/cookie/CSRF/Origin browser acceptance, current group-scoped authorization and audited admin controls.
- Real PostgreSQL 18.6 **seven-suite / >=58 passing test** evidence from the [manual integration workflow](../../.github/workflows/server-postgres18-integration.yml), not just source compilation.
- Accepted Windows 11 and Linux system service lifecycle, device identity and certificate custody tests.
- Prior organization/legal authorization, clear employee notice and explicit allowed fields/collection schedules. V0.2 app/audit and V0.3 recording have separate permission gates.

**No operator should execute `docker compose up -d` against this repository today:** there is no Compose definition in the current tree. Do not substitute the reference `deploy/caddy/Caddyfile.reference` for a production config or open the dev HTTP bind beyond loopback.

## Development stop/uninstall vs. production data deletion

The README's development teardown stops only the foreground Java/Agent processes, and (if explicitly used) the smoke script automatically removes its **own** temporary `--rm` PostgreSQL container. The shared `spark-postgres` is never stopped by Lanveriq teardown. Its Linux systemd snippet removes only the reviewed example unit and binary on an **isolated test VM**. No automatic deletion of `/var/lib`, SQLite WAL, persistent PostgreSQL data, keys, certificates, recordings or audit logs is provided or approved.

Production uninstall must eventually be a reviewed process with explicit organization authorization, backup/restore evidence, record-retention decision, certificate/session revocation, Agent OS service disablement, and audit of what was removed. A generic `docker compose down -v` or recursive filesystem deletion is **not a safe uninstall policy**.

For operational requirements, see [backup/restore](backup.md), [security and notice](../security/privacy-and-consent.md), [manual verification](../testing/server-postgres18-manual.md), and [release readiness](release.md).
