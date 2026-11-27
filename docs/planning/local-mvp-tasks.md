# Lanveriq 本机 MVP：一任务一会话执行与交接

**LOCAL TASK LEDGER · 2026-11-27 · main · v0.1 NOT RELEASE READY。** 只针对设备所有人明确授权的 **Pop!_OS 24.04 个人开发机**，完成 `once → SQLite → 手动 send/mTLS → Spring/PostgreSQL 18 → Next.js` 的最小可见链路。它不是生产 Agent 安装、员工采集批准或发布 Gate。产品实施路线见 [Linux 计划](linux-v01-reuse-first-delivery-path-2026-11-24.md)，生产阻塞见 [G-01～G-10](release-gap-register.md)，唯一当前功能事实见 [CURRENT_STATUS](../CURRENT_STATUS.md)。

## 新会话固定协议

1. 必须先读 [AGENTS.md](../../AGENTS.md)、[CURRENT_STATUS](../CURRENT_STATUS.md) 和**本任务清单**，核对 `main` SHA、对应源码、真实 CI。只选**一个任务 ID**；原则上处理最早未完成的前置任务，不能因为用户另选任务就跳过它的身份/安全条件。
2. 只实现所选任务的**允许范围**，不额外新建分支、第二份任务文档、日期报告或无关功能；小步提交 `main`、不得强推。生产联网、自动采集、自动恢复、远控、正式证书签发及默认拒绝配置全部保持 OFF。
3. 证据必须注明来源：`CI L1/L2`、`用户提供的本机终端日志`、`助手真实连接的本机操作`、`NOT RUN`。没有本机执行权限不可声称在 Pop!_OS 上运行成功。单项用户实机日志不等于完整 Pop!_OS L3 或生产验收。
4. 会话结束前仅更新本文件**任务状态 + 末尾单条交接**；确有经验证的项目新事实时才精简更新 `CURRENT_STATUS.md`。状态只能为 `DONE` / `TODO` / `BLOCKED`；逐项记录 PASS/FAIL/PENDING/NOT RUN、实际命令、提交 SHA、CI URL、未验证项和下一项。**完成验收后停止，不自动推进第二个任务；阻塞也要停止并交接。**
5. 不提交口令、密钥、具名私有设备 ID、个人数据、会话 Cookie、本机秘密路径或含机密的原始日志。仅将授权后的最少操作结果作为证据。下方的 `TODO` 不表示已经被批准开启生产服务。

## 唯一本机 MVP 任务索引

| ID | 单次会话应完成的目标 | 依赖 | 状态 |
| --- | --- | --- | --- |
| **MVP-01** | 共享 PG18 + Spring 启动、Flyway、独立启停 | 本机 Docker/Java/端口可核实 | **DONE**：2026-11-27 用户本机 V1～V30、loopback:18081 Health UP、IDEA 独立停止/重启 PASS（限定本机开发） |
| **MVP-02** | 手动 `once` → OS/CPU/RAM → SQLite 去重 | 自有设备、主动操作 | **DONE**：2026-11-27 复核；保留既有用户本机 PASS 与 Agent L1 PASS，本轮重跑 NOT RUN |
| **MVP-03** | 本地隔离 mTLS ingress 与测试设备身份预检 | MVP-01、测试授权 | **BLOCKED**：2026-11-27 历史隔离 L2 日志复核 PASS；指定本机测试身份及实测 NOT RUN |
| **MVP-04** | 手动 `send` → 精确 ACK → 确认本地 pending | MVP-02、MVP-03 | **TODO / NOT RUN** |
| **MVP-05** | 用 PostgreSQL 查询核实同一设备库存 | MVP-04 | **TODO / NOT RUN** |
| **MVP-06** | Next.js 接上已认证/组授权的只读 Spring API | MVP-01、MVP-05、测试管理员会话 | **TODO / NOT RUN** |
| **MVP-07** | 浏览器显示真实 Pop!_OS/CPU/内存及拒绝态 | MVP-06 | **TODO / NOT RUN** |

**下一项仍为 MVP-03（BLOCKED）**：MVP-01 与 MVP-02 已完成限定本机目标。需本机授权、隔离测试身份及真实双跳验证；不得跳过 mTLS，保留已有 SQLite 待发数据。

## 可执行任务卡

### MVP-01 · PG18 + Spring 在本机运行

- **只读预检**：`docker --version`、`docker inspect -f '{{.State.Running}}' spark-postgres`、`docker port spark-postgres 5432`、`docker exec spark-postgres psql -XAt -U root -d postgres -c 'SHOW server_version;'`、`java -version`、`ss -ltn '( sport = :18081 )'`、`test -f apps/server/gradlew`。缺依赖先报告、征得批准，不擅自安装。
- **允许**：按 [installation.md](../operations/installation.md) 先审核后手动调用 [setup-local-postgres.sh](../../apps/server/scripts/setup-local-postgres.sh)，仅在现有 `spark-postgres` 建独立 `lanveriq` 数据库/非超级用户；本机 Spring 只监听 `127.0.0.1:18081`（维护者已确认；8080 为其他项目占用，18080 亦冲突）。通过本地 IDE 环境变量 `LANVERIQ_HTTP_BIND=127.0.0.1`、`LANVERIQ_HTTP_PORT=18081` 配置，不改变仓库默认端口。密码仓库外 mode `0600`，不出现在 CLI 参数、日志或 Git。复用 IDEA 独立启停，或仅管理本项目 Java 进程的 `start.sh`/`stop.sh`；本机 MVP 不强制新增脚本。
- **禁止**：不创建第二个长期 PG、不动 `iot_farm`/其他容器与卷、不开放 LAN/WAN、不启用 Agent 接口/采集/远控；任何启停方式都不得停止共享 PG 或其他服务。
- **完成证据**：`curl -fsS http://127.0.0.1:18081/actuator/health` 响应状态 `UP`，`flyway_schema_history` 与当前源码全部版本一致且全部 `success`，`SELECT to_regclass('public.device_inventory_current');` 非 NULL；独立停止后 18081 无监听、共享 PG/IoT 容器仍运行，重新启动 Health UP，验收后 Spring 保持运行。缺任何一项不能标 DONE。

### MVP-02 · `once` 本机采集（已完成限定目标）

- **允许/禁止**：`apps/agent/src/main.rs` 手动 `once` 调用既有 `collect_minimal_linux_inventory()` 与 `OutboxStore::stage_inventory_snapshot()`；只读 OS/CPU/RAM，网卡/磁盘/用户/进程/桌面活动不采集，不发送、不启后台定时器。
- **本次证据**：2026-11-26 **用户提供**的 Pop!_OS 24.04 `sqlite3` 日志：`inventory_pending` 包含 revision 1、729 字节已持久快照；`inventory_latest_state` revision 1，下一 revision 2；再执行 `once` 返回 `already pending ... revision=1; not sent`。已合并 [main bed0152b](https://github.com/kunlong-luo/lanveriq/commit/bed0152bff52bfdee1d31a6e621f5d83d66b4c49)，[同功能 SHA Agent L1 #45804471428 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45804471428)。
- **2026-11-27 复核**：GitHub compare 确认功能 SHA `bed0152b` 到基线 `14f8f39e` 的 4 个提交仅改文档；`main.rs`、`inventory_platform.rs`、`store.rs`、`Cargo.toml`、`Cargo.lock` blob SHA 一致。直接读取 #45804471428 的 agent-unit job `139855945215` 日志，checkout 确切功能 SHA、125 tests passed / 0 failed / 0 ignored，Rustfmt、Clippy、锁文件检查均 SUCCESS；其中手动 once 持久化/重启、最小字段、相同语义不推进 revision 的测试实际通过。此为历史 CI 复核，非本轮重跑。无本机执行通道，未改 Agent 或既有 SQLite。保持 DONE。
- **边界**：`once` 本机手动采集/SQLite 去重 **PASS（用户证据）**；`send`、mTLS、Spring/PG、Web、完整 Pop!_OS L3 **NOT RUN**。不能据此宣布 MVP-03～07 完成。

### MVP-03 · 仅测试的本机 mTLS 身份预检

- **允许**：复用 [双 mTLS 通信设计](../architecture/device-communication-stack.md) 与既有测试 Caddy/Tomcat 配置，为**授权的本机测试设备**核对证书信任、设备注册状态与 Spring 身份绑定。现有 Agent 固定 `https://agent.lanveriq.internal`，需要受控本地解析/受信 HTTPS；不能改成明文 `localhost` 或跳过证书验证。
- **禁止**：不能修改生产默认开关。仅隔离测试配置可显式开启必需的测试能力；不得签发正式 CA、访问员工设备或开放公网。
- **验收**：有效测试身份经 TLS1.3/mTLS 被接受；无证书、错误证书、身份伪造被拒绝；具名测试设备凭据的绑定可核实。未达成时保持 TODO/BLOCKED，**不得**为了方便让 MVP-04 绕过身份。

- **2026-11-27 复核**：当前 `main` 基线 [`3fd73554`](https://github.com/kunlong-luo/lanveriq/commit/3fd7355454d6197c0f30b17a7eff5006bb630bca)。[Rust transport](../../apps/agent/src/transport.rs) 固定 HTTPS origin、TLS1.3、禁代理/重定向；[Caddy 参考](../../deploy/caddy/Caddyfile.reference) 要求受信客户端证书、移除不可信身份头；[Tomcat 验收 profile](../../apps/server/src/main/resources/application-agent-upstream-mtls-acceptance.yml) 仅 loopback:8443、TLS1.3、client-auth need；默认 `application.yml` Agent 开关仍 false。参考配置非本机实际生效配置。
- **真实历史 CI 复核**：功能 [`b31f45ef`](https://github.com/kunlong-luo/lanveriq/commit/b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f)，[Linux L2 #45742025955](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955) 三 jobs success；直接读取 job `139649120029` 原始日志（Source SHA 一致），有效双跳 TLS1.3 health、默认业务 403、无证书、错误 CA、TLS1.2、伪造头无上游证书、错设备、未注册/过期/吊销拒绝及合成凭据 epoch 与 PG18 一致均 PASS。另复核 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) job `139002587446`，源码 `db570181d603a7f069a85a23c009658e9a8bed4c`，相同边界 PASS。compare `b31f45ef` → 基线显示 92 commits、Agent `main.rs` 有改动，不能宣称当前 HEAD 全量功能 CI PASS；本轮重跑 NOT RUN。
- **本机 BLOCKED**：未连接 Pop!_OS 本机，未核实指定设备的授权测试 UUID/凭据、实际 DNS/CA 信任、Caddy/Tomcat 配置、DB 注册绑定或任何正负例。无本机执行与配置变更；不得以 18081 明文 health 替代双 mTLS，不能复用 CI 临时私钥或伪设 CI 环境绕过脚本限制。无 send、无 SQLite 修改；下一次仅完成本机 MVP-03。

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
| 基线 | [main 3fd73554](https://github.com/kunlong-luo/lanveriq/commit/3fd7355454d6197c0f30b17a7eff5006bb630bca)；前一轮 MVP-01 用户本机原始交接保留于该固定 SHA 的[任务账本](https://github.com/kunlong-luo/lanveriq/blob/3fd7355454d6197c0f30b17a7eff5006bb630bca/docs/planning/local-mvp-tasks.md)。MVP-01/02 状态不变。 |
| 本轮 | **MVP-03 BLOCKED**，仅现有代码与历史 CI 核实；无功能修改、无本机操作。选择复用已有双 mTLS 实现，不新增绕过安全的本机脚本或测试 CA。 |
| 已验证（源码） | 当前 transport.rs HTTPS-only/TLS1.3、Caddyfile.reference 双信任域与覆盖请求头、application-agent-upstream-mtls-acceptance.yml loopback:8443 TLS1.3 client-auth need、application.yml 默认 OFF、verify-agent-upstream-tls.sh 正负例及 CI 限制。不是本机有效配置证明。 |
| 已验证（CI L2 历史原始日志） | [#45742025955](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955) 三 jobs success，job 139649120029 checkout/Source SHA b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f；另 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两 jobs success，job 139002587446 checkout db570181d603a7f069a85a23c009658e9a8bed4c。日志实际执行有效 TLS1.3、无证书/错误 CA/TLS1.2/伪造头无上游证书/错误设备/未注册凭据拒绝；合成身份绑定 PASS。非本轮重跑，compare 显示当前 Agent main.rs 有改动。 |
| 未验证 / 命令 | 本机 `getent hosts agent.lanveriq.internal`、隔离测试证书链/有效期及 DB 身份绑定核验、有效/无/错误证书与伪造身份请求正负例均 NOT RUN；未获得本机执行通道或指定测试身份材料证据。不得提交私钥、密码或真实设备 ID。文档治理两条 Node 命令本轮 NOT RUN；文档 CI 尚未验证。 |
| 评分/上线 | [工程评分](project-scorecard.md) **71/100 不变**；仅复核旧 L2，不增加自动验收或 L3 评分。G-01～G-10 OPEN，0/10 关闭，v0.1 NOT RELEASE READY / NO-GO。 |
| **下次唯一任务** | **MVP-03**：在获准本机隔离环境核实专用测试身份与 DNS、Caddy/Tomcat TLS1.3 双向认证、PG18 实时设备绑定，逐项记录有效身份接受和无证书、错误证书、伪造/错设备拒绝；无证据继续 BLOCKED，禁止进入 MVP-04。 |
| 约束 | 单一 main、一次一个 ID；不动共享 PostgreSQL/IoT、已有 SQLite pending 或本机未提交配置，不提交机密；生产联网/采集/自动恢复/远控/CA 签发 OFF。 |

**本地 MVP 不等于发行批准。v0.1 NOT RELEASE READY。**
