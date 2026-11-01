# Lanveriq

[**English**](README.md) | [简体中文](README.zh.md)

**LAN-first, self-hosted asset health and audit platform for workplace endpoints with lawful notice and authorization.**

> **Status: V0.1 under development — NOT a production-ready monitoring product.** The Java backend builds and contains default-OFF internal services, Flyway V1–V8 and source-only PostgreSQL integration suites. The Rust Agent builds and runs an **idle lifecycle**; its inventory/SQLite/transport modules are not wired to the running process. **The React dashboard has not been created.** Real PostgreSQL 18.6, Caddy/TLS, browser-session and OS-service acceptance gates are still open. No real employee data may be collected or uploaded based on these developer instructions.

![Architecture concept (not a screenshot of the product)](docs/visuals/assets/01-overview.svg)

## Architecture: three application components

The **three product components** are **Web**, **Server**, and **Agent**. PostgreSQL and Caddy are separate **infrastructure dependencies**, not completed applications.

| Component | Path and technology | What runs today? |
|---|---|---|
| Web admin dashboard | Planned `apps/web`, React 19 / Vite / TypeScript / Tailwind / TanStack Query / Orval | **NOT IMPLEMENTED**. No React pages, Vite dev server, or frontend build exists. |
| API and admin server | `apps/server`, Java 25 / Spring Boot 4.1.2 / Gradle / JPA / Flyway | A buildable Spring Boot process with health checks; other business routes default to denial. Real DB/security approval pending. |
| Endpoint Agent | `apps/agent`, Rust 1.94 / Tokio / SQLx SQLite WAL / Reqwest-rustls | Builds and starts an **idle** process; **no automatic monitoring, inventory collection, or uploads**. |
| PostgreSQL (dependency) | PostgreSQL 18.6; server Flyway V1–V8 | Run an isolated local synthetic database; full real-PG integration acceptance not yet executed. |
| Caddy (dependency) | `deploy/caddy/Caddyfile.reference` | A **reference-only** TLS test config, not a production reverse proxy. |

Intended central topology: **Caddy HTTPS + static React assets → Spring Boot → PostgreSQL**. The Rust Agent runs **natively on explicitly authorized Windows/Linux endpoints**, not as a Docker container on the central host. The future dashboard is a static SPA, not a fourth Node/Next.js production server. See [deployment architecture](docs/architecture/deployment.md).

## Prerequisites

- Git and **JDK 25**; the repository includes the **Gradle 9.8.2 Wrapper**, so you need neither system Gradle nor Maven.
- **Rust 1.94.0** / Cargo from `rust-toolchain.toml`, plus your operating system's native Rust build prerequisites.
- Docker Engine with a working daemon to create an isolated, **synthetic-only** PostgreSQL 18.6 instance.
- Bash, curl and OpenSSL for the existing Linux/WSL/macOS local server smoke script.
- Node/pnpm are **not required to run a frontend today**, because there is no `apps/web`. The directory `apps/server/contract-client-check` tests **generated API types**, not React pages.

Dependencies and Docker images may require Internet access on first download; offline operation is a **future product runtime** objective, not a promise about development dependency installation.

```bash
git clone https://github.com/kunlong-luo/lanveriq.git
cd lanveriq
```

## Local development and testing

### 1. One-shot synthetic PostgreSQL + backend smoke test (recommended)

On an **authorized development machine**, run:

```bash
bash apps/server/scripts/verify-postgres18.sh
```

This existing script generates an ephemeral PostgreSQL password, starts a `postgres:18.6` container published only on `127.0.0.1:55432`, runs Spring Boot on `127.0.0.1:18080`, checks health, Flyway V1–V8, PostgreSQL UUIDv7, absence of seeded users/events/grants and business-route default denial. On exit it cleans up its container, temporary password and app process. **It is a development check, not production deployment or acceptance evidence until actually executed.** Both ports must be available.

### 2. Keep the backend running against a disposable local database

From the repository root, in a Bash terminal:

```bash
docker run -d --rm --name lanveriq-pg-dev \
  -p 127.0.0.1:55432:5432 \
  -e POSTGRES_DB=lanveriq \
  -e POSTGRES_USER=lanveriq \
  -e POSTGRES_PASSWORD=synthetic-local-only \
  postgres:18.6
docker exec lanveriq-pg-dev pg_isready -U lanveriq -d lanveriq
```

The literal password above is **only for a disposable localhost synthetic fixture**; never reuse it for employee data, remotely accessible databases or production. Wait until `pg_isready` succeeds, then open a separate terminal:

```bash
cd apps/server
export LANVERIQ_DB_URL='jdbc:postgresql://127.0.0.1:55432/lanveriq'
export LANVERIQ_DB_USER='lanveriq'
export LANVERIQ_DB_PASSWORD='synthetic-local-only'
export LANVERIQ_HTTP_BIND='127.0.0.1'
export LANVERIQ_HTTP_PORT='18080'
# Keep every LANVERIQ_* feature enablement at its default false.
./gradlew bootRun
```

In a third terminal:

```bash
curl -fsS http://127.0.0.1:18080/actuator/health
curl -i http://127.0.0.1:18080/api/v1/devices
```

Expected: health `{"status":"UP"}`, with an unauthenticated business request denied (**403**). This HTTP mode is strictly **loopback-only for synthetic development**. Never expose the Spring connector on the LAN or disable TLS verification for a production path.

To stop: press `Ctrl+C` where `bootRun` is running, then `docker stop lanveriq-pg-dev`. The `--rm` synthetic container has **no persistent volume** and is removed on stop. Do not use these teardown commands against any existing real database.

### 3. Run the idle Rust Agent

From the repository root:

```bash
cargo test --workspace --locked
cargo build --release --locked -p lanveriq-agent
cp apps/agent/config/agent.example.toml /tmp/lanveriq-agent-demo.toml
cargo run --locked -p lanveriq-agent -- \
  --config /tmp/lanveriq-agent-demo.toml
```

The example config is non-secret; the Agent demands an **absolute path**. Press `Ctrl+C` to exit and optionally remove the copied `/tmp/lanveriq-agent-demo.toml`. The executable is currently idle: **no device inventory, network request, telemetry or recording takes place**.

For a Windows 11 **foreground** test in PowerShell:

```powershell
Copy-Item .\apps\agent\config\agent.example.toml "$env:TEMP\lanveriq-agent-demo.toml"
cargo run --locked -p lanveriq-agent -- --config "$env:TEMP\lanveriq-agent-demo.toml"
```

This is not an installed Windows SCM service.

### 4. Local unit tests and builds

```bash
# Repository root — Rust tests and checks
cargo test --workspace --locked
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings

# Backend — Java JUnit, JAR and *compilation only* of optional DB test sources
cd apps/server
./gradlew test bootJar compileIntegrationTestJava
```

Real PostgreSQL **manual** integration execution is a **separate security gate** (7 classes, at least 58 passed cases, zero failed/skipped), not part of standard CI:

```bash
cd apps/server
./gradlew --no-build-cache --rerun-tasks integrationTest
python3 scripts/assert-postgres18-junit.py build/test-results/integrationTest
```

Run that only against disposable, explicitly authorized Docker-backed synthetic fixtures. The repository has **no observed successful PostgreSQL 18.6 manual run**. See [manual acceptance instructions](docs/testing/server-postgres18-manual.md) or the [manual GitHub Actions workflow](https://github.com/kunlong-luo/lanveriq/actions/workflows/server-postgres18-integration.yml).

## Deployment and installation

### What is actually deployable now?

- **Backend:** a local development Spring process and buildable Boot JAR — **not** a reviewed production application server.
- **Agent:** a compiled **idle** binary, plus an **experimental** Linux systemd unit. No approved automatic installation, device collection, upload or Windows installer.
- **Frontend:** **no `apps/web`, `pnpm dev`, build, deployable SPA or UI pages**.
- **Infrastructure:** no deployable Docker Compose file, production-ready Caddy configuration, real certificate provisioning, completed backup/restore drill, or production-ready network policy.

**Do not run an invented `docker compose up -d` command: no Compose stack exists yet.**

### Optional Linux-only idle Agent service trial

**Only on an explicitly authorized isolated test VM**, after inspecting the [systemd unit](deploy/systemd/lanveriq-agent.service):

```bash
cargo build --release --locked -p lanveriq-agent
sudo install -m 0755 target/release/lanveriq-agent /usr/local/bin/lanveriq-agent
sudo install -d -m 0755 /etc/lanveriq-agent
# This exact example config has no secrets; never use mode 0644 for future keys.
sudo install -m 0644 apps/agent/config/agent.example.toml /etc/lanveriq-agent/agent.toml
sudo install -m 0644 deploy/systemd/lanveriq-agent.service /etc/systemd/system/lanveriq-agent.service
sudo systemd-analyze verify /etc/systemd/system/lanveriq-agent.service
sudo systemctl daemon-reload
sudo systemctl start lanveriq-agent
sudo systemctl --no-pager status lanveriq-agent
```

This example intentionally does **not** `systemctl enable` the unit at boot. Linux systemd permission and OS lifecycle tests are pending. Windows has an SCM dispatcher in source, but **no tested service installer/uninstaller or MSI**; review the [Agent lifecycle test plan](docs/testing/agent-lifecycle-manual.md).

### Production installation: blocked

Planned central deployment requires a reviewed **Caddy / Spring Boot / PostgreSQL** stack with built static React assets and native Agent installation, but cannot be completed today. Gates include: implementing the dashboard; packaging/Compose; real PG18.6 transaction/rollback tests; Caddy TLS/mTLS, Host/Origin/CSRF and browser cookies; per-device certificates and OS credential custody; backups/restore; Windows/Linux installation tests; and organizational privacy notice/approval.

No real device enrollment, user-activity collection, automated uploads, monitoring or screen recording may be enabled simply by changing development flags. See [installation runbook](docs/operations/installation.md), [privacy and notice](docs/security/privacy-and-consent.md), and [D0 gate](docs/governance/documentation-gate.md).

## Stop and uninstall

| Component | Safe development teardown | Data warning |
|---|---|---|
| Spring Boot `bootRun` | `Ctrl+C` in its terminal | Does not delete database files |
| Ephemeral PostgreSQL container above | `docker stop lanveriq-pg-dev` | **Only for that named `--rm` synthetic fixture**; removes its temporary DB |
| Foreground idle Agent | `Ctrl+C`; remove only your copied test TOML | Does not uninstall any OS service |
| Linux experimental systemd Agent | Instructions below | Remove only files you installed and verified |
| Windows test SCM service | No supported installer/uninstaller yet; if manually created, stop/delete the exact known test service using Windows SCM and review its ACLs/data | Never delete an unknown service name |
| Web frontend | No installed component | Nothing to uninstall |
| Production backend/DB/Caddy | **No approved one-command uninstall** | Backup, retention, key revocation and audit controls required |

For a Linux Agent **installed using exactly the isolated-VM commands above**, confirm file ownership and paths first, then:

```bash
sudo systemctl stop lanveriq-agent
sudo systemctl disable lanveriq-agent  # Only if someone explicitly enabled it
sudo rm /etc/systemd/system/lanveriq-agent.service
sudo systemctl daemon-reload
sudo rm /usr/local/bin/lanveriq-agent
sudo rm /etc/lanveriq-agent/agent.toml
sudo rmdir /etc/lanveriq-agent
```

This intentionally does **not** delete `/var/lib` state, SQLite/WAL, database volumes, certificates, audit history or backups. Destructive real-data removal requires its own authorization, backup and retention review.

## Frontend progress

**The admin frontend is not finished and has not started as a React app.** `apps/server/contract-client-check` only verifies Springdoc → Orval → generated TypeScript/TanStack Query hooks. The next useful frontend work is a **synthetic-only** React app shell with login/session UX, scoped device list and health overview; real integration depends on independent PostgreSQL and TLS/session acceptance.

See [frontend design system](docs/development/frontend-design-system.md), [dashboard UX](docs/development/dashboard-ux.md), [roadmap](docs/planning/roadmap.md), and [architecture drawings](docs/diagrams/README.md).

## License and contributing

The public repository **has no final software LICENSE**; visibility is not a grant of use or redistribution rights. See [CONTRIBUTING](CONTRIBUTING.md), [SECURITY](SECURITY.md) and [documentation index](docs/README.md). Never submit real credentials, personal data or recordings in issues.

Copyright (c) 2026 Lanveriq contributors.
