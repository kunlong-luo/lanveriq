# Lanveriq

[**English**](README.md) | [简体中文](README.zh.md)

**LAN-first, self-hosted asset health and audit platform for workplace endpoints with lawful notice and authorization.**

> **Status: V0.1 under development — NOT a production-ready monitoring product.** The Java backend builds and contains default-OFF internal services, Flyway V1–V8 and source-only PostgreSQL integration suites. The Rust Agent builds and runs an **idle lifecycle**; its inventory/SQLite/transport modules are not wired to the running process. **The React dashboard now has a synthetic-only preview in `apps/web`, without live authentication, connected backend APIs or WSS.** Real PostgreSQL 18.6, Caddy/TLS, browser-session and OS-service acceptance gates are still open. No real employee data may be collected or uploaded based on these developer instructions.

![Architecture concept (not a screenshot of the product)](docs/visuals/assets/01-overview.svg)

## Architecture: three application components

The **three product components** are **Web**, **Server**, and **Agent**. PostgreSQL and Caddy are separate **infrastructure dependencies**, not completed applications.

| Component | Path and technology | What runs today? |
|---|---|---|
| Web admin dashboard | `apps/web`: React 19 / Vite 8 / TypeScript 7 / Tailwind 4 / Radix / Lucide | Local synthetic preview with six routes, demo search and detail tabs. NO real login/API/device operations. |
| API and admin server | `apps/server`, Java 25 / Spring Boot 4.1.2 / Gradle / JPA / Flyway | A buildable Spring Boot process with health checks; other business routes default to denial. Real DB/security approval pending. |
| Endpoint Agent | `apps/agent`, Rust 1.94 / Tokio / SQLx SQLite WAL / Reqwest-rustls | Builds and starts an **idle** process; **no automatic monitoring, inventory collection, or uploads**. |
| PostgreSQL (dependency) | PostgreSQL 18.x (18.6 for manual acceptance); Flyway V1–V8 | Reuse dedicated `lanveriq` database in existing `spark-postgres`; full PG18.6 integration acceptance pending. |
| Redis (optional dependency) | Existing `spark-redis` / Redis 8 / Spring Data Redis + Lettuce | Optional **default-OFF** bounded ephemeral refetch-hint bean and Actuator health; not a queue, session, RBAC store or WSS broker. |
| Caddy (dependency) | `deploy/caddy/Caddyfile.reference` | A **reference-only** TLS test config, not a production reverse proxy. |

Intended central topology: **Caddy HTTPS + static React assets → Spring Boot → PostgreSQL**. The Rust Agent runs **natively on explicitly authorized Windows/Linux endpoints**, not as a Docker container on the central host. The future dashboard is a static SPA, not a fourth Node/Next.js production server. See [deployment architecture](docs/architecture/deployment.md).

## Prerequisites

- Git and **JDK 25**; the repository includes the **Gradle 9.8.2 Wrapper**, so you need neither system Gradle nor Maven.
- **Rust 1.94.0** / Cargo from `rust-toolchain.toml`, plus your operating system's native Rust build prerequisites.
- Docker Engine with the existing running `spark-db` PostgreSQL container; a separate short-lived **synthetic-only** PostgreSQL 18.6 container is optional for independent acceptance.
- Bash, curl and OpenSSL for the existing Linux/WSL/macOS local server smoke script.
- **Node.js 24 and pnpm 12** are required for the optional local Web preview. `apps/server/contract-client-check` separately validates generated API types; the Web preview does not call those APIs.

Dependencies and Docker images may require Internet access on first download; offline operation is a **future product runtime** objective, not a promise about development dependency installation.

```bash
git clone https://github.com/kunlong-luo/lanveriq.git
cd lanveriq
```

## Local development and testing

### 1. Reuse your existing `spark-db` PostgreSQL instance

Your local Docker Compose project is named `spark-db`, with an existing `spark-postgres` container, `iot_farm` business database, and (by default) a host-only `127.0.0.1:5432` PostgreSQL port. **Do not start a second persistent PostgreSQL instance.** Lanveriq may **optionally reuse the existing Redis** for best-effort transient hints/health, but needs neither InfluxDB nor PostGIS extensions. Keep the entire existing Compose stack and all of its volumes intact.

The isolation boundary is a **new `lanveriq` database owned by a separate non-superuser `lanveriq` role** in the existing PostgreSQL cluster. Never run Flyway against `iot_farm` and do not run the Lanveriq application as the shared PostgreSQL superuser.

Verify the existing database **without changing or restarting Compose**:

```bash
docker inspect -f '{{.State.Running}}' spark-postgres
docker port spark-postgres 5432
docker exec spark-postgres psql -U root -d postgres -XAtc "SHOW server_version;"
```

Expected: `true`, a host mapping such as `127.0.0.1:5432`, and PostgreSQL **18.x**. The `postgis/postgis:18-3.6` image label does **not** prove that the running patch is 18.6; verify the actual `server_version`. If your Compose variables `POSTGRES_USER`, `BIND_IP` or `POSTGRES_HOST_PORT` differ from the supplied defaults, substitute the actual values. The supplied Redis/PostgreSQL password defaults are weak; rotate any real credentials under a separate maintenance plan. These README instructions never copy or reuse those passwords.

Your existing InfluxDB Explorer maps host **port 18080**, so the local Lanveriq server uses **18081**.

### 2. Create the dedicated Lanveriq database once

Enter PostgreSQL's interactive console through the container's **local Unix socket**:

```bash
docker exec -it spark-postgres psql -U root -d postgres
```

First inspect existing roles/databases with `\du` and `\l`. If either `lanveriq` object already exists, inspect its owner and purpose; **do not recreate or overwrite it**. On a fresh, approved local development setup:

```sql
CREATE ROLE lanveriq LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
```

At the same interactive `psql` prompt, run **`\password lanveriq`**, entering a **unique strong password** via the hidden prompt (do not store it in Git, Compose, terminal history or README). Then:

```sql
CREATE DATABASE lanveriq OWNER lanveriq;
```

Exit using `\q`. Only `lanveriq` will be migrated by the Java application; the existing `iot_farm`, cron jobs, Redis, InfluxDB and Docker volumes remain untouched. The provided Postgres memory limit (256 MiB) is small for a shared workload: measure resource pressure before increasing workload, and do not change existing service limits blindly.

### 3. Start Spring Boot with the existing database (development only)

On the **same host** that publishes Docker's loopback PostgreSQL port, open a trusted Bash terminal:

```bash
cd apps/server
export LANVERIQ_DB_URL='jdbc:postgresql://127.0.0.1:5432/lanveriq'
export LANVERIQ_DB_USER='lanveriq'
read -rsp 'Lanveriq PostgreSQL password: ' LANVERIQ_DB_PASSWORD
echo
export LANVERIQ_DB_PASSWORD
export LANVERIQ_HTTP_BIND='127.0.0.1'
export LANVERIQ_HTTP_PORT='18081'
# Leave all optional LANVERIQ_* business feature flags default OFF.
./gradlew bootRun
```

The hidden password prompt avoids putting credentials into the command line/history; process environments are still sensitive, so this is **for trusted local development only**. On Windows PowerShell, WSL2, remote development hosts, or from inside a container, `127.0.0.1` may refer to a different network namespace; validate connectivity rather than opening PostgreSQL to `0.0.0.0`.

From another terminal:

```bash
curl -fsS http://127.0.0.1:18081/actuator/health
curl -i http://127.0.0.1:18081/api/v1/devices
```

A healthy default-deny development instance is expected to report `{"status":"UP"}` and to reject the unauthenticated business API with **403**. On first start, Flyway runs V1–V8 only against the **new, isolated `lanveriq` database**. This is **not** a working dashboard/login/Agent API or real integration acceptance. HTTP must stay **loopback-only**.

To stop, press `Ctrl+C` in the `bootRun` terminal. **Never stop `spark-postgres`, run `docker compose down`, or run `docker compose down -v`** as part of Lanveriq teardown.

### 4. Optional existing Redis 8 (synthetic-only, no Redis container to create)

Redis is **not required** by the default-deny Java process. To opt in on a
trusted local development host, first check that **your existing** container is
running and still bound to loopback:

```bash
docker inspect -f '{{.State.Running}}' spark-redis
docker port spark-redis 6379
```

From the **same Bash shell as the Spring Boot process**, before `bootRun`:

```bash
export LANVERIQ_REDIS_HOST='127.0.0.1'
export LANVERIQ_REDIS_PORT='6379'
read -rsp 'Existing spark-redis password: ' LANVERIQ_REDIS_PASSWORD
echo
export LANVERIQ_REDIS_PASSWORD
export LANVERIQ_REDIS_ENABLED='true'
./gradlew bootRun
```

Do not run a second Redis container or overwrite the existing Compose
credentials. This opt-in exposes Redis availability through the existing
`/actuator/health` check; Redis unavailable can cause health to be DOWN.
The only code-level Redis feature is a **currently unused internal**
90-second UUID refetch hint; it does not enable monitoring or a new API.
PostgreSQL remains the source of truth. Your Redis uses `allkeys-lru`,
so never store critical session/role/ACK/audit/locking data in it.
Stop only the Java process; **do not stop `spark-redis`, flush keys, run
Compose down, or delete Redis volumes**.

### 5. Optional disposable PostgreSQL 18.6 smoke (separate from spark-db)

The existing `apps/server/scripts/verify-postgres18.sh` still creates its **own short-lived** `postgres:18.6` container. It **does not connect to, stop or delete** `spark-postgres`. To avoid the InfluxDB Explorer port conflict, run this optional isolated script on **18082**, not 18080:

```bash
# Stop any prior smoke process occupying these isolated test ports.
LANVERIQ_SMOKE_HTTP_PORT=18082 \
LANVERIQ_SMOKE_DB_PORT=55432 \
bash apps/server/scripts/verify-postgres18.sh
```

The script creates and removes only its own synthetic database container, secret file and Spring process. It verifies startup/health/migrations/default denial, but **does not** establish real PG18 integration or production TLS acceptance. The 7-class / >=58-case manual Testcontainers suite must remain **disposable and isolated**: never point its destructive synthetic tests at the shared `iot_farm` or the developer `lanveriq` database.

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
- **Frontend:** a synthetic-only `apps/web` React/Vite preview now builds; this is **not** an authenticated, API-connected or deployable management console.
- **Infrastructure:** no deployable Docker Compose file, production-ready Caddy configuration, real certificate provisioning, completed backup/restore drill, or production-ready network policy.

**Do not run an invented `docker compose up -d` command: no Compose stack exists yet.**

### Run a test backend JAR (not a production release)

With the **same environment** already set for the dedicated `127.0.0.1:5432/lanveriq` database and `127.0.0.1:18081` listener, you may build and launch the backend as a foreground JAR instead of using `bootRun`:

```bash
cd apps/server  # Skip if already inside apps/server.
./gradlew bootJar
java -jar build/libs/lanveriq-server-0.1.0-SNAPSHOT.jar
```

Stop with `Ctrl+C`. This starts a **default-deny synthetic development process**, not a reviewed publicly reachable API; it has no production Caddy/TLS/browser release approval. Do not auto-start it or enable collection to work around those gates.

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
| Existing `spark-postgres` | **Do not stop, uninstall or run Compose down** | Preserve `iot_farm`, other databases, Redis/InfluxDB and all volumes |
| Foreground idle Agent | `Ctrl+C`; remove only your copied test TOML | Does not uninstall any OS service |
| Linux experimental systemd Agent | Instructions below | Remove only files you installed and verified |
| Windows test SCM service | No supported installer/uninstaller yet; if manually created, stop/delete the exact known test service using Windows SCM and review its ACLs/data | Never delete an unknown service name |
| Local Web preview | Stop `pnpm dev` with `Ctrl+C` | Remove only generated `apps/web/node_modules` and `apps/web/dist` if needed; do not touch shared Docker volumes |
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

### Optionally delete **only** the dedicated Lanveriq database

Stopping Lanveriq does **not** require deleting `lanveriq`. If (and only if) this is a disposable, authorized test database with no retention obligations, take and verify a restricted backup before any irreversible change:

```bash
umask 077
docker exec spark-postgres pg_dump -U root -d lanveriq -Fc > lanveriq-backup.dump
```

Store the backup securely. After **all Lanveriq applications are stopped**, separately enter the PostgreSQL admin console with `docker exec -it spark-postgres psql -U root -d postgres`; inspect `\l` and `\du` to confirm the exact targets. Only if the `lanveriq` database was expressly created for this project and approved for deletion:

```sql
-- DESTRUCTIVE: execute only against the exact dedicated Lanveriq test DB.
DROP DATABASE lanveriq;
-- Only if the role is unused by any other database or application.
DROP ROLE lanveriq;
```

Do **not** delete `iot_farm`, stop `spark-postgres` or remove its `postgres-data` volume. Your existing pg_dump/Kopia/R2 backup workflow may back up only `iot_farm`; independently confirm whether the new `lanveriq` DB is covered.

## Frontend progress

**The React app now exists as an isolated synthetic UI preview.** It includes overview, Monitoring Center, interactive demonstration device filtering, Radix detail tabs, enrollment explanation and settings. It has **no real login/session, Orval API connection, WSS, live telemetry or usable device commands**. See [Web runbook](apps/web/README.md). PostgreSQL and TLS/session acceptance remain separate gates.

See [frontend design system](docs/development/frontend-design-system.md), [dashboard UX](docs/development/dashboard-ux.md), [roadmap](docs/planning/roadmap.md), and [architecture drawings](docs/diagrams/README.md).

## License and contributing

The public repository **has no final software LICENSE**; visibility is not a grant of use or redistribution rights. See [CONTRIBUTING](CONTRIBUTING.md), [SECURITY](SECURITY.md) and [documentation index](docs/README.md). Never submit real credentials, personal data or recordings in issues.

Copyright (c) 2026 Lanveriq contributors.
