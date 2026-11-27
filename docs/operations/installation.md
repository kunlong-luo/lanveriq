# Lanveriq 本地开发环境与未来安装边界 / Local development runbook

**ACTIVE · 2026-11-24 · development-only · v0.1 NOT RELEASE READY。** 本文件保留**实际需要的本地 PostgreSQL/Spring/Agent 隔离开发步骤**，不是可让普通 Pop!_OS 用户复制执行的安装流程，也不是生产授权。请先阅读 [中英根 README](../../README.zh.md)、[当前状态](../CURRENT_STATUS.md)、[发布 Gate](../planning/release-gap-register.md)。

**正式用户的预期体验**是受签名认证、逐次管理员授权的 Tauri2 GUI，独立 Rust Agent 将来在**另行批准**后通过 systemd 后台运行、无持久托盘图标但可从应用菜单管理；不需要用户安装 Rust/Node 或手动输入命令。[ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [用户流程](../product/linux-desktop-installer-v01.md) · [真实 L3 验收](../testing/linux-desktop-installer-acceptance.md)。

**当前工程边界：** [L2B-23 Ubuntu24 OCI APT 真包 install/reinstall/remove](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)和 [L2B-27 10/10 原生 APT 签名旧 Release Date 接受](../development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md)均仅在**临时无网络 CI**；实际发行者签名、独立高水位、root broker、具名 Pop!_OS24 COSMIC L3 均未批准，不能用于真实终端。生产联网、采集、远控、自动恢复、CA OFF。

**Status: development-only.** The repository now contains a runnable default-deny Java Spring Boot process and an idle Rust Agent binary, but **only a disconnected Next.js 16 App Router preview at `apps/web` (no live login/API), no production-ready Compose stack, no signed MSI/DEB installers, and no production Caddy configuration**. This is **not** a supported production monitoring deployment. Legal/privacy notice and real PostgreSQL/TLS/OS acceptance gates remain open.

For copyable development and synthetic-only commands, begin with the [Chinese README](../../README.zh.md) or [English README](../../README.md). They cover:

1. Reuse the **already running** `spark-db` / `spark-postgres` on `127.0.0.1:5432` only after creating a dedicated database and role. The reviewed one-time, **password-free** [bootstrap SQL](../../apps/server/scripts/provision-lanveriq-database.sql) must be run **manually**, against maintenance database `postgres`, only if both `lanveriq` objects do not already exist. It creates a `NOLOGIN` non-superuser owner and independent database, revokes `PUBLIC` database privileges, then requires a separate interactive `psql` `\password` step and explicit `ALTER ROLE lanveriq LOGIN`. The original `iot_farm` database is never migrated or removed.
2. Run `apps/server/./gradlew bootRun` with `LANVERIQ_DB_URL=jdbc:postgresql://127.0.0.1:5432/lanveriq`, `LANVERIQ_DB_USER=lanveriq` and an independently chosen secret, binding local Spring HTTP to `127.0.0.1:18081` (host port `18080` is used by the supplied InfluxDB Explorer).
3. An **optional, independent, temporary** PostgreSQL 18.6 + Boot smoke script: `LANVERIQ_SMOKE_HTTP_PORT=18082 bash apps/server/scripts/verify-postgres18.sh`. It cleans up **only its own** synthetic PostgreSQL container; it does not touch `spark-postgres` or `iot_farm`.
4. Run a default-deny Java JAR built by Gradle, or an idle Rust Agent using an absolute non-secret TOML config; neither path is a production rollout.
5. On an **isolated test VM only**, inspect and install the optional Linux Agent systemd unit without enabling it at boot. Stop/uninstall only the reviewed Lanveriq-owned files, and separately approve any deletion of the `lanveriq` database.

**DBeaver script settings:** In Preferences → Editors → SQL Editor → SQL Processing → Scripts, choose **Commit type: After each query (autocommit)**, not **At script end** (which causes SQLSTATE 25001 for `CREATE DATABASE`). Enable error-stop and execute sequentially (Alt+X). The general toolbar's Auto-commit setting is not a substitute for this script commit setting. Inspect any partial provisioning state on a fresh database session before retrying.

**JDBC/IDE-compatible SQL bootstrap:** `apps/server/scripts/provision-lanveriq-database.sql` contains only PostgreSQL SQL, no `psql` directives. Open it against the **`postgres`** maintenance database as the authorized administrator, set **Auto-commit** and **Stop on error**, and execute statements in sequence. **Do not** wrap `CREATE DATABASE` in a transaction. If using psql, pass `-v ON_ERROR_STOP=1` on the invocation rather than adding a backslash directive to the SQL file. Passwords are not in source: configure via a GUI credential editor or the interactive psql `\password` command, then separately enable LOGIN. Existence checks are deliberate; inspect partial states rather than rerunning.

## Linux 用户目录约定

| XDG 根（未设置时的默认值） | Lanveriq 用途 |
| --- | --- |
| `$XDG_CONFIG_HOME`（`$HOME/.config`） | `lanveriq/` 用户配置 |
| `$XDG_DATA_HOME`（`$HOME/.local/share`） | `lanveriq/pki/` 本机测试 CA、`enrollment/` 注册材料、`agent/` 持久化 SQLite |
| `$XDG_STATE_HOME`（`$HOME/.local/state`） | `lanveriq/` 日志和运行状态 |
| `$XDG_CACHE_HOME`（`$HOME/.cache`） | `lanveriq/` 可清理缓存 |

只接受绝对 XDG 根；按职责命名，不再新建阶段编号目录或 `~/lanveriq-dev`。测试 PKI 目录 0700、私钥 0600，系统运行配置仍沿用现有 `/etc/lanveriq`、`/var/lib/lanveriq` 和代理部署路径。已有 `pki` 内部结构保留，勿擅自重排。迁移前检查引用、停止 SQLite 写入者、保留 WAL/SHM 或使用 SQLite 一致性备份、验证内容和权限后才退役旧路径；禁止直接覆盖或删除待发送数据。此为路径规范，不代表历史脚本、安装器或实机数据均已迁移。

## 本机目录与命名规范（2026-11-27，维护者确认）

下表是维护者的 Pop!_OS 开发环境目录映射，使用 `$HOME` 表示当前用户主目录，不硬编码个人用户名。其他开发机按实际 checkout 位置调整；系统级目录是长期命名约定，不代表已批准生产安装。

| 用途 | 目录或文件 | 状态 |
| --- | --- | --- |
| Lanveriq 仓库根目录 | `$HOME/Projects/github/lanveriq/` | 本机已确认 |
| Spring Boot / Gradle | `$HOME/Projects/github/lanveriq/apps/server/` | 源码目录 |
| Rust Agent | `$HOME/Projects/github/lanveriq/apps/agent/` | 源码目录 |
| Next.js Web | `$HOME/Projects/github/lanveriq/apps/web/` | 源码目录 |
| 共享 Spark 基础设施 | `$HOME/Projects/IOT/spark-iot-farm/spark-iot-infra/` | 外部项目，不属于本仓库 |
| 统一代理配置 | 上述基础设施的 `docker/0-proxy/` | `docker-compose.yml`、`Caddyfile`、`nginx.conf`、`.env`、`.env.example` |
| 统一启停脚本 | 上述基础设施的 `scripts/` | `spark-compose.sh`、`deploy-all.sh`、`deploy-app.sh` |
| Caddy 外部 TLS 配置 | `/etc/spark/caddy/tls/` | 项目分目录、只挂载所需运行材料 |
| Caddy 公共根证书 | `/etc/spark/caddy/caddy-root-ca.crt` | 本机开发公共 CA，非正式信任根 |
| Spring TLS | `/etc/lanveriq/server/tls/` | 已创建本机测试 PKCS12；运行用户读取授权仍需验证 |
| Agent TLS | `/etc/lanveriq/agent/tls/` | 本机测试身份材料；负例证书不得用于正式安装 |
| Spring 开发 JAR | `$HOME/.local/share/lanveriq/server/lanveriq-server.jar` | 计划的非 root 开发构建输出，尚未验证 |
| Agent 系统服务持久化 | `/var/lib/lanveriq/` | 长期目标；已有 SQLite pending 不迁移、不覆盖，直到单独验证 |

- 构建从 Lanveriq 根目录进入 `apps/server` 使用 Gradle；代理配置检查在 Spark 的 `docker/0-proxy` 执行；共享代理启停在 Spark 的 `scripts` 执行。Lanveriq 自身的启停不得停止共享代理、PostgreSQL 或其他 Spark 服务。
- 通用 Caddy 使用 `spark-caddy` 容器，复用既有 `spark-iot-net` 与代理 Compose 分组；本机 HTTPS 仅发布回环 TCP 443，不通过 Cloudflare Tunnel 暴露 Agent。Caddy 自动证书和配置数据由 `caddy-data` / `caddy-config` Docker 卷持久化；不得通过删除卷重置信任根。
- 使用官方组件名称、kebab-case 目录/容器名称及 UPPER_SNAKE_CASE 环境变量。长期目录不含 MVP 编号、版本号、临时阶段名。外部证书目录通过 `CADDY_TLS_DIR` 指向系统级 TLS 目录。秘密及个人路径不得写入 Git、CI 或日志；私钥/密码文件应为 owner-only，运行用户权限按最小授权配置，不以 root 启动 Java。
- Caddy 内部 CA 只用于本机 HTTPS 服务端；Agent 客户端和 Caddy 至 Spring 上游客户端信任域保持独立。共享 Bridge 网络不是独立安全域；实际后端连接、证书绑定及负例仍须按 [MVP-03 任务卡](../planning/local-mvp-tasks.md) 验收。不得把本目录约定当成生产配置或发行许可。

## Recommended manual PostgreSQL setup on the Linux development machine

From the repository root, use `bash apps/server/scripts/setup-local-postgres.sh` rather than executing `CREATE DATABASE` through a JDBC SQL editor. The helper is manually invoked, requires an interactive terminal, a running existing `spark-postgres`, authorized `root` superuser access and PostgreSQL 18 SCRAM settings. It checks for pre-existing or interrupted `lanveriq` objects, requires exact confirmations, delegates separate statements to the container's `psql`, and uses hidden interactive password prompts. It never creates a new container, changes `iot_farm`, enables monitoring, or runs as part of Spring Boot/Flyway/CI. No password is stored in source control. The standalone bootstrap SQL remains reviewable at [`apps/server/scripts/provision-lanveriq-database.sql`](../../apps/server/scripts/provision-lanveriq-database.sql).

## Shared PostgreSQL safety boundary for `spark-db`

The supplied local Compose stack already serves several unrelated applications.
Do **not** fork a second long-lived PostgreSQL container, attach Lanveriq to
`iot_farm`, or create a second Redis/PostgreSQL container. Redis is an **optional** non-authoritative Spring Data Redis/Lettuce transient hint dependency and can reuse the existing `spark-redis` on `127.0.0.1:6379`. No InfluxDB/PostGIS extensions are required.
Use a new database owner and **check actual** `SHOW server_version`: the
`postgis/postgis:18-3.6` tag identifies a PostgreSQL major version but does
not prove the tested `postgres:18.6` patch level. Dedicated manual
Testcontainers PostgreSQL 18.6 acceptance remains independent and must never
run destructive fixture SQL against the shared service.

The existing `postgres-data`, `redis-data`, `influxdb-data` and other
volumes, and the `spark-iot-net`/`spark-iot-internal-net` networks, belong
to the user's existing stack and must **not** be created, removed or renamed
by Lanveriq installers/uninstallers. Uninstalling Lanveriq must not run
`docker compose down`, `docker compose down -v`,
`docker stop spark-postgres` or `docker stop spark-redis`. Never run Redis `FLUSHALL` / `FLUSHDB` on this shared instance.

The provided example uses a local 256 MiB PostgreSQL container memory limit;
review load, error logs and capacity separately before moving past synthetic
development. The existing Kopia/R2 backup plan may dump **only `iot_farm`**;
a newly created `lanveriq` database requires separately verified backup
coverage or its own `pg_dump` and restore drill. Never post database passwords
in the repository or CI.

## Optional shared Redis boundary (default OFF)

Spring Boot 4.1.2 manages the Spring Data Redis/Lettuce dependency. The
`LANVERIQ_REDIS_ENABLED` flag (default `false`) controls a small
`RedisDeviceRefreshHints` component and Redis health contribution. No
business request, Agent event, user/session/role grant, audit, outbox, device
credential or canonical device status is persisted in Redis. Even when enabled,
the hint bean currently has **no production caller**, so opting in does not
activate any monitoring, background uploads, or device management.

For an explicitly authorized isolated development session, first check
`docker inspect -f '{{.State.Running}}' spark-redis` and
`docker port spark-redis 6379`. The default endpoint is
`127.0.0.1:6379` (the existing Compose mapping). From the same trusted
Bash shell used to start Java, read the configured Redis password into
`LANVERIQ_REDIS_PASSWORD` using `read -rsp` and export it, then set
`LANVERIQ_REDIS_ENABLED=true` before `./gradlew bootRun`. No Redis password
must be committed, echoed in logs, or copied from public examples.

The shared Redis image is configured with `allkeys-lru`. Cache misses and
loss of hints must always trigger authorized PostgreSQL/REST reconciliation;
Redis is **not** a trustworthy status source, a distributed lock or an
authorization engine. The deployed Redis instance's password, memory budget
(64 MiB in the supplied Compose environment), reachability and restore policy
need separate operational acceptance. With Redis enabled, Actuator health may
report DOWN if it is unavailable; with Redis disabled, it is not required for
the Java health check.

## Components and deployment dependency boundary

| Product component | Planned execution site | State |
|---|---|---|
| Next.js App Router admin Web (primary) | `apps/web`, Next.js 16.4.5 standalone localhost preview; separate public disconnected GitHub Pages static export | Main UI builds and Pages preview pass CI; live API, session, WSS and real device access remain OFF. The old Vite app was removed in NXT-23; the current `apps/web` is the relocated Next.js project, not a Vite proxy |
| Java Spring Boot server | Server host, behind accepted HTTPS/TLS ingress | Buildable local development process; privileged business endpoints default OFF |
| Rust endpoint Agent | Native managed Windows/Linux endpoint | Idle binary, standalone synthetic libraries and OS test-only service unit; no live enrollment/upload |
| PostgreSQL 18 | Private server network / synthetic Docker on loopback | Database migration source written; manual integration acceptance pending |
| Caddy | LAN HTTPS entrypoint with distinct console/enroll/agent hosts | Synthetic acceptance reference only; **NOT deployable Caddy production config** |

The three **business components** are Web, Server and Agent; PostgreSQL and Caddy are additional required infrastructure. The Agent should **not** be placed in the central Compose deployment. The selected future **Next.js SSR/RSC** topology requires a **private Node.js runtime behind Caddy** with strict internal-origin isolation and Spring session checks for protected HTML/RSC. GitHub Pages can host only the separately gated **public disconnected static UI**, not the production console. Production deployment is still not approved. See [topology and trust boundaries](../architecture/deployment.md).

## Requirements for a later production deployment

- Dedicated supported Linux server, sized volumes/backups, Docker Engine/Compose and restricted admin SSH.
- Approved internal CA and client CA; correct console/enroll/Agent hostnames, Caddy SNI/Host checks, mutually authenticated Agent TLS and independent pinned Caddy-to-Spring TLS.
- PostgreSQL on a private network with pinned/reviewed image, persistent volume, Flyway versioning, `pg_dump`/restore drill, secret custody and retention controls.
- Completed React app, exact same-origin session/cookie/CSRF/Origin browser acceptance, current group-scoped authorization and audited admin controls.
- Real PostgreSQL 18.6 **seven-suite / >=58 passing test** evidence from the [manual integration workflow](../../.github/workflows/server-postgres18-integration.yml), not just source compilation.
- For Linux-only v0.1, **actual authorized Pop!_OS 24.04** systemd service lifecycle, device identity, non-root certificate custody and rollback testing. Windows native SCM/CNG and installer L3 are required separately before declaring Windows support.
- Prior organization/legal authorization, clear employee notice and explicit allowed fields/collection schedules. V0.2 app/audit and V0.3 recording have separate permission gates.

**No operator should execute `docker compose up -d` against this repository today:** there is no Compose definition in the current tree. Do not substitute the reference `deploy/caddy/Caddyfile.reference` for a production config or open the dev HTTP bind beyond loopback.

## Development stop/uninstall vs. production data deletion

The README's development teardown stops only the foreground Java/Agent processes, and (if explicitly used) the smoke script automatically removes its **own** temporary `--rm` PostgreSQL container. The shared `spark-postgres` is never stopped by Lanveriq teardown. Its Linux systemd snippet removes only the reviewed example unit and binary on an **isolated test VM**. No automatic deletion of `/var/lib`, SQLite WAL, persistent PostgreSQL data, keys, certificates, recordings or audit logs is provided or approved.

Production uninstall must eventually be a reviewed process with explicit organization authorization, backup/restore evidence, record-retention decision, certificate/session revocation, Agent OS service disablement, and audit of what was removed. A generic `docker compose down -v` or recursive filesystem deletion is **not a safe uninstall policy**.

For operational requirements, see [backup/restore](backup.md), [security and notice](../security/privacy-and-consent.md), [manual verification](../testing/server-postgres18-manual.md), and [release readiness](release.md).
