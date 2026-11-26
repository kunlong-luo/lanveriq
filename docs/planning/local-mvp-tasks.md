# Lanveriq 本机 MVP：一任务一会话执行与交接

**LOCAL TASK LEDGER · 2026-11-26 · main · v0.1 NOT RELEASE READY。** 只针对设备所有人明确授权的 **Pop!_OS 24.04 个人开发机**，完成 `once → SQLite → 手动 send/mTLS → Spring/PostgreSQL 18 → Next.js` 的最小可见链路。它不是生产 Agent 安装、员工采集批准或发布 Gate。产品实施路线见 [Linux 计划](linux-v01-reuse-first-delivery-path-2026-11-24.md)，生产阻塞见 [G-01～G-10](release-gap-register.md)，唯一当前功能事实见 [CURRENT_STATUS](../CURRENT_STATUS.md)。

## 新会话固定协议

1. 必须先读 [AGENTS.md](../../AGENTS.md)、[CURRENT_STATUS](../CURRENT_STATUS.md) 和**本任务清单**，核对 `main` SHA、对应源码、真实 CI。只选**一个任务 ID**；原则上处理最早未完成的前置任务，不能因为用户另选任务就跳过它的身份/安全条件。
2. 只实现所选任务的**允许范围**，不额外新建分支、第二份任务文档、日期报告或无关功能；小步提交 `main`、不得强推。生产联网、自动采集、自动恢复、远控、正式证书签发及默认拒绝配置全部保持 OFF。
3. 证据必须注明来源：`CI L1/L2`、`用户提供的本机终端日志`、`助手真实连接的本机操作`、`NOT RUN`。没有本机执行权限不可声称在 Pop!_OS 上运行成功。单项用户实机日志不等于完整 Pop!_OS L3 或生产验收。
4. 会话结束前仅更新本文件**任务状态 + 末尾单条交接**；确有经验证的项目新事实时才精简更新 `CURRENT_STATUS.md`。状态只能为 `DONE` / `TODO` / `BLOCKED`；逐项记录 PASS/FAIL/PENDING/NOT RUN、实际命令、提交 SHA、CI URL、未验证项和下一项。**完成验收后停止，不自动推进第二个任务；阻塞也要停止并交接。**
5. 不提交口令、密钥、具名私有设备 ID、个人数据、会话 Cookie、本机秘密路径或含机密的原始日志。仅将授权后的最少操作结果作为证据。下方的 `TODO` 不表示已经被批准开启生产服务。

## 唯一本机 MVP 任务索引

| ID | 单次会话应完成的目标 | 依赖 | 状态 |
| --- | --- | --- | --- |
| **MVP-01** | 共享 PG18 + Spring 启动、Flyway、启动/停止脚本 | 本机 Docker/Java/端口可核实 | **BLOCKED**：2026-11-26 仅远端源码审查；本机预检及验收 NOT RUN |
| **MVP-02** | 手动 `once` → OS/CPU/RAM → SQLite 去重 | 自有设备、主动操作 | **DONE**：仅手动本地采集 |
| **MVP-03** | 本地隔离 mTLS ingress 与测试设备身份预检 | MVP-01、测试授权 | **TODO / NOT RUN** |
| **MVP-04** | 手动 `send` → 精确 ACK → 确认本地 pending | MVP-02、MVP-03 | **TODO / NOT RUN** |
| **MVP-05** | 用 PostgreSQL 查询核实同一设备库存 | MVP-04 | **TODO / NOT RUN** |
| **MVP-06** | Next.js 接上已认证/组授权的只读 Spring API | MVP-01、MVP-05、测试管理员会话 | **TODO / NOT RUN** |
| **MVP-07** | 浏览器显示真实 Pop!_OS/CPU/内存及拒绝态 | MVP-06 | **TODO / NOT RUN** |

**下一项默认 MVP-01**，不是重复做 MVP-02；MVP-01 若无法本机执行应维持 BLOCKED，不用云端 CI 冒充实际启动。保留已有 SQLite 待发数据。

## 可执行任务卡

### MVP-01 · PG18 + Spring 在本机运行

- **只读预检**：`docker --version`、`docker inspect -f '{{.State.Running}}' spark-postgres`、`docker port spark-postgres 5432`、`docker exec spark-postgres psql -XAt -U root -d postgres -c 'SHOW server_version;'`、`java -version`、`ss -ltn '( sport = :8080 )'`、`test -f apps/server/gradlew`。缺依赖先报告、征得批准，不擅自安装。
- **允许**：按 [installation.md](../operations/installation.md) 先审核后手动调用 [setup-local-postgres.sh](../../apps/server/scripts/setup-local-postgres.sh)，仅在现有 `spark-postgres` 建独立 `lanveriq` 数据库/非超级用户；Spring 只监听 `127.0.0.1:8080`。密码仓库外 mode `0600`，不出现在 CLI 参数、日志或 Git；必要时仅生成管理本项目 Java 进程的 `start.sh`/`stop.sh`。
- **禁止**：不创建第二个长期 PG、不动 `iot_farm`/其他容器与卷、不开放 LAN/WAN、不启用 Agent 接口/采集/远控；`stop.sh` 不停共享 PG。
- **完成证据**：`curl -fsS http://127.0.0.1:8080/actuator/health` 响应状态 `UP`，`flyway_schema_history` 全部 `success`，`SELECT to_regclass('public.device_inventory_current');` 非 NULL；启动/停止脚本仅管 Spring，验收后 Spring 保持运行。缺任何一项不能标 DONE。

### MVP-02 · `once` 本机采集（已完成限定目标）

- **允许/禁止**：`apps/agent/src/main.rs` 手动 `once` 调用既有 `collect_minimal_linux_inventory()` 与 `OutboxStore::stage_inventory_snapshot()`；只读 OS/CPU/RAM，网卡/磁盘/用户/进程/桌面活动不采集，不发送、不启后台定时器。
- **本次证据**：2026-11-26 **用户提供**的 Pop!_OS 24.04 `sqlite3` 日志：`inventory_pending` 包含 revision 1、729 字节已持久快照；`inventory_latest_state` revision 1，下一 revision 2；再执行 `once` 返回 `already pending ... revision=1; not sent`。已合并 [main bed0152b](https://github.com/kunlong-luo/lanveriq/commit/bed0152bff52bfdee1d31a6e621f5d83d66b4c49)，[同功能 SHA Agent L1 #45804471428 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45804471428)。
- **边界**：`once` 本机手动采集/SQLite 去重 **PASS（用户证据）**；`send`、mTLS、Spring/PG、Web、完整 Pop!_OS L3 **NOT RUN**。不能据此宣布 MVP-03～07 完成。

### MVP-03 · 仅测试的本机 mTLS 身份预检

- **允许**：复用 [双 mTLS 通信设计](../architecture/device-communication-stack.md) 与既有测试 Caddy/Tomcat 配置，为**授权的本机测试设备**核对证书信任、设备注册状态与 Spring 身份绑定。现有 Agent 固定 `https://agent.lanveriq.internal`，需要受控本地解析/受信 HTTPS；不能改成明文 `localhost` 或跳过证书验证。
- **禁止**：不能修改生产默认开关。仅隔离测试配置可显式开启必需的测试能力；不得签发正式 CA、访问员工设备或开放公网。
- **验收**：有效测试身份经 TLS1.3/mTLS 被接受；无证书、错误证书、身份伪造被拒绝；具名测试设备凭据的绑定可核实。未达成时保持 TODO/BLOCKED，**不得**为了方便让 MVP-04 绕过身份。

### MVP-04 · 一次 `send` 和精确 ACK

- **允许**：只执行 `apps/agent` 的 `cargo run --locked -- send --state-file "$STATE_FILE" --device-id "$DEVICE_ID" --ca-pem "$TEST_CA" --identity-pem "$TEST_IDENTITY"`，复用现有 `AgentTlsTransport::send_next_inventory()`；同一个已授权设备 UUID、同一个持久 SQLite 快照。测试密钥仅存仓库外、受限权限。
- **验收**：服务端成功提交该设备的版本并返回精确一致的 `device_id`、`snapshot_id`、`inventory_revision`；只有 `Accepted` 后 `inventory_pending` 才移除对应快照，历史语义版本保持。网络故障、证书错误、错误/延迟 ACK、`stale_ignored` 不得错误清队列；若代码改变安全行为需实际跑定向负测并记录 SHA/Run。
- **禁止**：不实现自动重试/自动采集，不直接删 SQLite 待发记录，不放宽 TLS。

### MVP-05 · PostgreSQL 真正入库

- **只读范围**：以 `lanveriq` 专用角色核对 `public.device_inventory_current`，按 [Flyway V8 列定义](../../apps/server/src/main/resources/db/migration/V8__device_inventory_current.sql) 检查相同设备 `snapshot_id`、`inventory_revision`、`os_name`、`os_version`、`cpu_model`、`memory_total_bytes`。
- **验收**：真实 PG18 查询确实存在与已确认 ACK **同一设备、快照和版本**的行；若不存在 FAIL/BLOCKED；不得以 CI 合成库存或本机 SQLite 代替真实 PG 查询。

### MVP-06 · Next.js 有身份的设备只读读取

- **允许**：仅限现有 `apps/web` 与必要的既有 Spring Console 授权路径，复用同源安全会话和组范围设备读取；先核实真实管理员测试授权。确需扩大后端范围时先说明阻塞，不擅自开启生产路由。
- **验收**：已授权会话正确读取真实 API；无会话、错误组/Origin/CSRF 拒绝。不能把断开 Pages 演示或无认证直接读 PG 当成成功。

### MVP-07 · 浏览器看到这台设备

- **允许**：只在 `apps/web` 的设备列表/详情完成真实数据映射，不扩展首页、监控面板或远控。
- **验收**：浏览器设备页显示与 MVP-05 **相同设备**的 Pop!_OS 24.04、CPU 型号及内存；刷新仍真实，断网/停止后端/无权限时明确显示拒绝或断开状态；必须有用户本机浏览器验收证据。
- **完成不代表上线**：这只证明单台授权个人开发机上的**手动演示**；发布 Gate 全部仍 OPEN。

## 最新一次交接（每个会话只覆盖本区，勿累积报告）

| 字段 | 当前内容 |
| --- | --- |
| 基线 | `main` [f9027923c8a7aff1020f7f27b63d13951e465239](https://github.com/kunlong-luo/lanveriq/commit/f9027923c8a7aff1020f7f27b63d13951e465239)；本轮仅任务账本提交，无新功能 SHA |
| 本轮 | **MVP-01 BLOCKED（2026-11-26）**：读取三份必读文档，远端审查 `setup-local-postgres.sh`、`installation.md`、`application.yml`；确认既有共享 PG 初始化、Flyway、Hibernate validate 与 loopback:8080 配置。根目录与 `apps/server` 下未发现 `start.sh`/`stop.sh`。安装文档仍写 18081，按本任务卡与当前源码采用 8080，未修改配置。 |
| 已验证 | 仅远端源码静态核对；未执行 CI（无功能改动，无本轮 CI URL）。原 MVP-02 用户证据及 [Agent L1 #45804471428](https://github.com/kunlong-luo/lanveriq/actions/runs/45804471428) 保留，不作为 MVP-01 验收。 |
| 仍未验 | 无用户 Pop!_OS 本机执行权限；任务卡全部只读命令 NOT RUN（Docker、容器 running/port、PG 版本、Java、8080 监听、Gradle wrapper）；数据库初始化、Spring 启动、`curl -fsS http://127.0.0.1:8080/actuator/health`、Flyway 全部 success、`SELECT to_regclass('public.device_inventory_current');`、Spring 专用启动/停止及验收后保持运行均 NOT RUN。未创建容器、未修改数据库、未启动或停止进程。 |
| **下次唯一任务** | **MVP-01**：取得授权本机执行环境后从任务卡只读预检开始；按现有初始化脚本及实际端口验证，必要时实现仅管理 Spring 的启动/停止脚本；完成全部验收后方可 DONE。此次停止，不进入 MVP-02/03。 |
| 约束 | 单一 main、每轮只完成一个 ID；生产联网/采集/自动恢复/远控/证书签发 OFF，G-01～G-10 OPEN；不修改共享 PG/其他数据库或已存在 SQLite 待发记录。 |

**本地 MVP 不等于发行批准。v0.1 NOT RELEASE READY。**
