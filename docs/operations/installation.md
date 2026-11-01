# Lanveriq installation, local development, and deployment runbook

**Status: development-only.** The repository now contains a runnable default-deny Java Spring Boot process and an idle Rust Agent binary, but **no `apps/web` React application, no production-ready Compose stack, no signed MSI/DEB installers, and no production Caddy configuration**. This is **not** a supported production monitoring deployment. Legal/privacy notice and real PostgreSQL/TLS/OS acceptance gates remain open.

For copyable development and synthetic-only commands, begin with the [Chinese README](../../README.zh.md) or [English README](../../README.md). They cover:

1. An existing disposable PostgreSQL 18.6 + Boot smoke script: `bash apps/server/scripts/verify-postgres18.sh`. The script cleans up its temporary test DB and app process.
2. How to run a manually started **127.0.0.1-only** PostgreSQL container and `apps/server/./gradlew bootRun` with default-deny Spring Security.
3. The idle Rust Agent console run with an **absolute, non-secret** TOML configuration; it collects/uploads nothing.
4. An **isolated test VM only** Linux systemd installation using `deploy/systemd/lanveriq-agent.service`, without enabling automatic startup.
5. Matching stop/uninstall instructions limited to the named disposable development resources. Real employee/audit/PKI data must never be removed without written approval and retention review.

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

The README's development teardown stops the foreground Java/Agent processes and the uniquely named `--rm` disposable test PostgreSQL container. Its Linux systemd snippet removes only the reviewed example unit and binary on an **isolated test VM**. No automatic deletion of `/var/lib`, SQLite WAL, persistent PostgreSQL data, keys, certificates, recordings or audit logs is provided or approved.

Production uninstall must eventually be a reviewed process with explicit organization authorization, backup/restore evidence, record-retention decision, certificate/session revocation, Agent OS service disablement, and audit of what was removed. A generic `docker compose down -v` or recursive filesystem deletion is **not a safe uninstall policy**.

For operational requirements, see [backup/restore](backup.md), [security and notice](../security/privacy-and-consent.md), [manual verification](../testing/server-postgres18-manual.md), and [release readiness](release.md).
