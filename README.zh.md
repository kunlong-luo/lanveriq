# Lanveriq（澜维巡）

[English](README.md) | **中文**

**基于局域网、自托管、面向已告知且经过授权的企业终端的资产健康与审计平台。**

> **当前状态：V0.1 开发中，不是可正式部署的监控产品。** Java 后端已有可构建的 Spring Boot 应用、Flyway V1–V8 和默认关闭的部分内部能力；Rust Agent 已有可运行的**空闲进程**、SQLite/传输/库存模块，但尚未接入实际服务循环；**React 管理前端尚未创建**。PostgreSQL 18.6 真实数据库、Caddy/TLS、管理员浏览器会话及 Windows/Linux 实机验收尚未全部完成。严禁把本地演示当成真实员工数据采集授权。

![Lanveriq 架构概念图](docs/visuals/assets/01-overview.svg)

## 一、项目包含哪些服务？

**三个业务组件**是前端、后端和终端 Agent。数据库与代理是它们的**部署依赖**，不是已经完工的三个业务应用。

| 组件 | 仓库位置 / 技术 | 当前状态 | 运行方式 |
|---|---|---|---|
| 管理前端 Web | 计划 `apps/web`：React 19、Vite、TypeScript、pnpm、Tailwind、TanStack Query、Orval | **未实现**：无 React 页面、前端开发服务器或打包产物 | **目前没有** `pnpm dev`、`pnpm build` 可用 |
| 管理后端 Server | `apps/server`：Java 25、Spring Boot 4.1.2、Gradle、JPA/Hibernate、Flyway | 可单独构建并连接**合成测试数据库**；业务接口默认拒绝，部分功能仅源码/测试状态 | `./gradlew bootRun` |
| 终端 Agent | `apps/agent`：Rust 1.94、Tokio、SQLx/SQLite WAL、Reqwest/rustls | 可编译、启动、停止；运行时**不采集、不上传、不监控** | `cargo run --locked -p lanveriq-agent -- --config <绝对路径>` |
| 数据库（基础设施） | PostgreSQL 18.6 | Flyway V1–V8 已编写；真实 PostgreSQL 全量集成验收待执行 | 本地 Docker 或独立 PostgreSQL |
| HTTPS 代理（基础设施） | Caddy | `deploy/caddy/Caddyfile.reference` **仅为安全测试参考配置**，不能直接用于生产 | 正式部署方案待验收 |

设计目标：在服务器使用 **Caddy（HTTPS/静态前端）→ Spring Boot → PostgreSQL**，在已授权的 Windows/Linux 终端原生运行 Rust Agent。**Agent 不应部署在服务器 Docker Compose 中。** 前端静态资源最终可由 Caddy 托管，不需要额外的 Next.js 服务。详细设计见 [部署架构](docs/architecture/deployment.md)。

## 二、准备环境

- Git；**JDK 25**；仓库自带 **Gradle 9.8.2 Wrapper**（无需 Maven，也无需单独安装 Gradle）。
- Rust **1.94.0**（见 `rust-toolchain.toml`）、Cargo；按目标系统安装相应的 Rust 原生构建工具。
- Docker Engine 和可用的 Docker daemon，用于**合成数据的 PostgreSQL 18.6 本地测试**。首次安装工具链与拉取依赖/镜像可能需要联网；产品的未来运行目标是离线局域网。
- Bash、curl、OpenSSL，用于现有 Linux/WSL/macOS 开发环境下的服务器自检脚本。生产服务器及 Agent OS 服务还需要单独验收。
- **当前不需要 Node/pnpm 来启动管理页面，因为前端项目尚不存在。** Orval 契约测试使用独立的 `apps/server/contract-client-check`，不能替代 UI。

克隆仓库：

```bash
git clone https://github.com/kunlong-luo/lanveriq.git
cd lanveriq
```

## 三、本地运行：推荐先使用自动清理的合成数据自检

最安全的入门方式是在**授权的开发机**上运行仓库现有的一次性脚本：

```bash
bash apps/server/scripts/verify-postgres18.sh
```

它会在 **127.0.0.1:55432** 启动一次性 PostgreSQL 18.6，生成临时数据库密码，编译并启动 Spring Boot（**127.0.0.1:18080**），验证 Flyway V1–V8、数据库 UUIDv7、无预置管理员/采集数据、业务 API 默认拒绝；退出时停止进程、删除临时容器和密码文件。**这是开发验证，不是生产部署，也不会启用 Agent/前端。** 运行所需端口不得被其他程序占用。该脚本在开发仓库中已有，但不代表此处已执行成功。

### 长时间本地启动后端（仅合成数据）

在 Linux/WSL/macOS Bash 开发终端，先启动一个**无持久化数据卷、仅绑定 loopback** 的测试数据库：

```bash
docker run -d --rm --name lanveriq-pg-dev \
  -p 127.0.0.1:55432:5432 \
  -e POSTGRES_DB=lanveriq \
  -e POSTGRES_USER=lanveriq \
  -e POSTGRES_PASSWORD=synthetic-local-only \
  postgres:18.6
```

此密码只允许用于一次性本地合成数据容器，**不要用于任何真实数据库**。等待 `docker exec lanveriq-pg-dev pg_isready -U lanveriq -d lanveriq` 返回成功后，在另一个终端：

```bash
cd apps/server
export LANVERIQ_DB_URL='jdbc:postgresql://127.0.0.1:55432/lanveriq'
export LANVERIQ_DB_USER='lanveriq'
export LANVERIQ_DB_PASSWORD='synthetic-local-only'
export LANVERIQ_HTTP_BIND='127.0.0.1'
export LANVERIQ_HTTP_PORT='18080'
# 所有功能开关保留默认 false，不启用真实接入。
./gradlew bootRun
```

新开终端验证：

```bash
curl -fsS http://127.0.0.1:18080/actuator/health
curl -i http://127.0.0.1:18080/api/v1/devices
```

健康检查预期是 `{"status":"UP"}`，未授权业务 API 默认应返回 **403**。若启动失败，检查 JDK 25、数据库端口、Flyway、连接信息和后端日志。这里的 HTTP **仅限 loopback 合成数据调试**；正式环境必须使用通过验收的 HTTPS/Caddy，**绝不能把端口直接开放给局域网**。

停止：后端终端按 `Ctrl+C`，然后 `docker stop lanveriq-pg-dev`。由于使用 `--rm` 且没有数据卷，该测试数据库会被删除。不要对实际生产数据库执行本节删除操作。

### 本地运行 Rust Agent（只启动空闲循环）

在仓库根目录执行：

```bash
cargo test --workspace --locked
cargo build --release --locked -p lanveriq-agent
cp apps/agent/config/agent.example.toml /tmp/lanveriq-agent-demo.toml
cargo run --locked -p lanveriq-agent -- \
  --config /tmp/lanveriq-agent-demo.toml
```

配置文件必须是**绝对路径**，内容是纯合成/无密钥配置。按 `Ctrl+C` 退出；结束后可手动删除你创建的 `/tmp/lanveriq-agent-demo.toml`。此程序目前**不会**向后端发送心跳、读取设备库存或执行监控。Windows 11 可在 PowerShell 中使用：

```powershell
Copy-Item .\apps\agent\config\agent.example.toml "$env:TEMP\lanveriq-agent-demo.toml"
cargo run --locked -p lanveriq-agent -- --config "$env:TEMP\lanveriq-agent-demo.toml"
```

这是前台进程检查，**不是 Windows SCM 服务安装**。

### 本地测试命令

```bash
# 仓库根目录：Rust（真实单元测试，合成数据）
cargo test --workspace --locked
cargo fmt --all -- --check
cargo clippy --workspace --all-targets --locked -- -D warnings

# Java：单元测试、构建、编译 PostgreSQL 集成测试代码（不启动 Docker）
cd apps/server
./gradlew test bootJar compileIntegrationTestJava
```

数据库集成测试是**另外的手动门槛**：`cd apps/server && ./gradlew --no-build-cache --rerun-tasks integrationTest`，必须在明确授权的隔离 Docker 环境中使用合成数据。真正通过还需要验证 7 类至少 58 个用例、零失败/跳过，并运行 JUnit 证据校验器；**当前尚无成功的真实 PostgreSQL 18.6 执行记录**。参见 [PG18 手动验收](docs/testing/server-postgres18-manual.md) 与 [GitHub 手动工作流](https://github.com/kunlong-luo/lanveriq/actions/workflows/server-postgres18-integration.yml)。

## 四、部署：当前能够做什么？

### 实验性部署（仅获授权的测试机）

服务端目前可作为 Java 进程本地运行，但**没有**正式 Docker Compose、生产 Caddy 配置、前端构建产物或可用的管理界面。不能执行尚不存在的 `docker compose up -d` 来安装整套产品。

Linux 的 Rust Agent 仅提供**实验性 systemd 单元** `deploy/systemd/lanveriq-agent.service`。如果你拥有一台**隔离的测试 VM**并明确允许安装一个不采集数据的空闲服务，可在仓库根目录先构建，再手动安装：

```bash
cargo build --release --locked -p lanveriq-agent
sudo install -m 0755 target/release/lanveriq-agent /usr/local/bin/lanveriq-agent
sudo install -d -m 0755 /etc/lanveriq-agent
# 本例配置没有密钥；未来存放密钥的配置不能使用 0644 权限。
sudo install -m 0644 apps/agent/config/agent.example.toml /etc/lanveriq-agent/agent.toml
sudo install -m 0644 deploy/systemd/lanveriq-agent.service /etc/systemd/system/lanveriq-agent.service
sudo systemd-analyze verify /etc/systemd/system/lanveriq-agent.service
sudo systemctl daemon-reload
sudo systemctl start lanveriq-agent
sudo systemctl --no-pager status lanveriq-agent
```

**不执行 `systemctl enable`，不自动开机启动。** 此参考单元还需要在目标 Linux/systemd 上进行真实权限与停止测试。Windows 端有 SCM dispatcher 源码，但没有受验收的安装器、卸载器或正式服务安装命令。参见 [Agent 生命周期验收](docs/testing/agent-lifecycle-manual.md)。

### 正式生产部署：尚不可执行

计划采用 **Caddy + Spring Boot + PostgreSQL** 的中心端部署和静态 React 前端、独立 Windows/Linux 原生 Agent。正式部署前至少需要：

1. 完成 `apps/web`、构建并验证真实登录/设备列表/监控总览页面。
2. 提供经审查的 Compose、专用数据库卷与备份/恢复、保密配置和受限网络；PostgreSQL 不对局域网发布端口。
3. 验收 Caddy HTTPS、控制台 Host/Origin/CSRF/cookie、独立 Agent mTLS/上游 TLS 信任边界、证书轮换与密钥保存。
4. 完成 PostgreSQL 18.6 的 **7 类 / 至少 58 用例**真实集成测试，以及 Windows/Linux 安装、停用和权限验收。
5. 获得组织/法律要求的员工告知、数据字段、留存、访问权限批准和恢复演练。

**任何真实设备注册、采集、自动上传、活动审计或屏幕录像都不能仅靠打开开发开关部署。** 更多信息见 [安装运维方案](docs/operations/installation.md)、[安全与隐私](docs/security/privacy-and-consent.md) 和 [部署架构](docs/architecture/deployment.md)。

## 五、停止与卸载

| 对象 | 停止 / 卸载方式 | 数据与安全提醒 |
|---|---|---|
| 本地 Spring Boot | 在 `bootRun` 终端按 `Ctrl+C` | 不会自动删除 PostgreSQL 数据 |
| 本地一次性 PostgreSQL | `docker stop lanveriq-pg-dev` | **只对上文建立的 `--rm` 合成数据容器执行**；停止即删除 |
| 前台 Agent | `Ctrl+C` | 删除自己创建的临时 TOML 即可 |
| Linux 实验性 Agent systemd | 见下方命令 | 只删除由本 README 示例安装的文件 |
| Windows SCM Agent | 目前**没有**正式安装/卸载包 | 若已手动注册测试服务，须先按实际服务名停止、删除 SCM 服务，并经管理员确认后清理二进制/配置 |
| Web 前端 | 目前无可安装产物 | 无需卸载 |
| 正式中心端 | **尚未提供一键卸载** | 后续必须先备份、验明数据所有者、遵守留存/审计与密钥撤销流程 |

**仅当你已在测试 VM 按上节命令安装 systemd 服务时**，执行以下卸载（先确认没有其他应用在使用这些路径）：

```bash
sudo systemctl stop lanveriq-agent
sudo systemctl disable lanveriq-agent  # 仅当曾经启用开机启动
sudo rm /etc/systemd/system/lanveriq-agent.service
sudo systemctl daemon-reload
sudo rm /usr/local/bin/lanveriq-agent
sudo rm /etc/lanveriq-agent/agent.toml
sudo rmdir /etc/lanveriq-agent
```

这里**不会自动删除**任何 SQLite WAL、服务状态目录、数据库卷、证书、审计或备份数据。真实数据的删除需要单独的授权、保留政策和审计记录；不要直接删除 `/var/lib`、数据库卷或其他路径来“彻底卸载”。

## 六、前端进度和下一步

截至当前仓库检查：**前端页面没有完成，甚至还没有 `apps/web` 应用目录。** 后端已有 Springdoc → Orval → TanStack Query 的类型安全 API 契约验证工程 `apps/server/contract-client-check`，但它不能被启动为管理页面。前端规划见 [设计系统](docs/development/frontend-design-system.md) 与 [管理后台 UX](docs/development/dashboard-ux.md)。

建议的后续交付顺序是：先落实真实 PG18、Caddy/TLS 与会话负向验收；并行搭建不连接真实员工数据的 React 前端骨架、布局和合成数据界面；随后联调经授权且默认关闭的 API。验收前不可对外宣布“可安装并开始监控”。

## 七、文档与许可

[文档索引](docs/README.md) · [需求](docs/product/requirements.md) · [开发路线](docs/planning/roadmap.md) · [安全](SECURITY.md) · [贡献指南](CONTRIBUTING.md) · [设计图库](docs/visuals/README.md)

当前仓库**尚未提交正式软件 LICENSE**；公开可见不代表授予使用或再分发权。详见 [D0 治理决策](docs/governance/d0-decision-package-2026-10-30.md)。

Copyright (c) 2026 Lanveriq contributors.
