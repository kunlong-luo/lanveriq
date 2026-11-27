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
| **MVP-03** | 本地隔离 mTLS ingress 与测试设备身份预检 | MVP-01、测试授权 | **BLOCKED**：2026-11-27 用户本机双跳 mTLS Health UP、入口 4 项及后端 3 项正负例 PASS；设备未注册、凭据 0 条 |
| **MVP-04** | 手动 `send` → 精确 ACK → 确认本地 pending | MVP-02、MVP-03 | **TODO / NOT RUN** |
| **MVP-05** | 用 PostgreSQL 查询核实同一设备库存 | MVP-04 | **TODO / NOT RUN** |
| **MVP-06** | Next.js 接上已认证/组授权的只读 Spring API | MVP-01、MVP-05、测试管理员会话 | **TODO / NOT RUN** |
| **MVP-07** | 浏览器显示真实 Pop!_OS/CPU/内存及拒绝态 | MVP-06 | **TODO / NOT RUN** |

**下一项仍为 MVP-03（BLOCKED）**：MVP-01 与 MVP-02 已完成限定本机目标。双跳 mTLS 已有用户本机证据；仍需受控测试身份注册、实时业务凭据核验及业务负例。不得跳过 mTLS，保留已有 SQLite 待发数据。

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
- **2026-11-27 本机证据（维护者终端，非助手本机直连）**：本机源码基线 `8a3f7484`，Gradle `bootJar` BUILD SUCCESSFUL（不是单元测试）。Spark Caddy 2.11.9 复用独立客户端 CA 和上游 CA，入口有效证书 502（当时无上游）、无证书/错误 CA/TLS1.2 均 TLS 拒绝。IDEA 网桥地址启动被既有 `AgentIngressAssertionConfiguration` 拒绝（预期安全保护）；恢复 loopback:8443 后启动，Caddy 改 host 模式并显式 bind loopback:443，`caddy adapt` / `ss` / `docker inspect` 确认监听与模式。`curl --resolve` 双跳 Health UP；PG18.7 JPA 初始化成功。直接访问 Spring 无证书、Agent 证书、无证书伪造头三例均 HTTP 000 + TLS alert。只读 PG 查询：设备不存在、匹配证书指纹 0 条。共享库无身份写入；未核实普通 DNS、实际 Rust Agent 网络、业务层有效/伪造/错设备凭据。禁止把 CI-only fixture SQL 直接灌入共享开发库；需评审受控本机测试身份初始化路径。无 send，SQLite 不修改。

- **本机预检工具**：[preflight-local-device-identity.sh](../../apps/server/scripts/preflight-local-device-identity.sh)（功能提交 [`95f0e628`](https://github.com/kunlong-luo/lanveriq/commit/95f0e6287ee25f3b632fc22aeee63e7105178d25)）。从仓库根手动执行 `bash apps/server/scripts/preflight-local-device-identity.sh`；只读取受限目录的测试叶证书、独立 CA、公钥匹配、IPv4 主机名及专用 PG18 库绑定，固定只读事务，不创建可信记录。隔离助手环境按 Git blob SHA `c0bd039c48244e872f4dea9fbae78b755e60e7b1` 校验同一脚本，`bash -n`、额外参数/非交互/CI+PTY 拒绝均 PASS；真实本机执行与 CI NOT RUN/未确认。它不等于服务器完整 PKIX/CSR 审核，不能解除注册阻塞。已复核现有 `PendingEnrollmentService`（服务端分配设备 UUID）、`ReviewedDeviceCertificateRegistrationService`（认证管理员、组范围、CSR/SPKI/PKIX 与审计）、`DeviceActivationService`；暂无可安全复用的本机注册入口。不得伪造管理员、直接置 active 或改写旧 SQLite 身份。下一步需独立可丢弃测试库中的明确测试身份初始化与真实 pending→activation 验收。

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
| 基线 | [main 3636118c](https://github.com/kunlong-luo/lanveriq/commit/3636118cace495e8efc1470b114de78420fc2806)（仅目录文档），本机报告功能基线 `8a3f7484`；前次交接见[固定账本](https://github.com/kunlong-luo/lanveriq/blob/3636118cace495e8efc1470b114de78420fc2806/docs/planning/local-mvp-tasks.md)。MVP-01/02 不变。 |
| 本轮 | **MVP-03 BLOCKED**。增加只读本机身份预检工具；原用户本机双跳与拒绝证据保留。注册入口仍缺失，未创建凭据，无本机直连。 |
| 已验证（源码） | 现有 TLS1.3 双跳、默认 OFF 和 CI-only 边界；真实注册服务要求认证管理员、组范围与待审 CSR，服务端分配 UUID。新增预检 [`95f0e628`](https://github.com/kunlong-luo/lanveriq/commit/95f0e6287ee25f3b632fc22aeee63e7105178d25)，不写库。 |
| 已验证（CI L2 历史原始日志） | [#45742025955](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955) 三 jobs success，job 139649120029 checkout/Source SHA b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f；另 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两 jobs success，job 139002587446 checkout db570181d603a7f069a85a23c009658e9a8bed4c。日志实际执行有效 TLS1.3、无证书/错误 CA/TLS1.2/伪造头无上游证书/错误设备/未注册凭据拒绝；合成身份绑定 PASS。非本轮重跑，compare 显示当前 Agent main.rs 有改动。 |
| 用户本机证据 / 未验证 | 保留任务卡中原始双跳、负例与 DB 0 条证据；新增脚本同 blob 的隔离环境语法及三启动拒绝 PASS（非 CI、非本机），实际证书/PG 分支 NOT RUN；普通 DNS、受信业务身份、错设备/伪造头、实际 Rust 请求仍未验证。文档治理命令 NOT RUN，CI 未确认。 |
| 评分/上线 | [工程评分](project-scorecard.md) **71/100 不变**；新增用户本机传输层证据，不增加自动验收或 L3 评分。G-01～G-10 OPEN，0/10 关闭，v0.1 NOT RELEASE READY / NO-GO。 |
| **下次唯一任务** | **MVP-03**：先运行本机只读预检；实现并测试独立可丢弃测试库的明确测试身份初始化，复用注册校验和 pending→mTLS 激活，解决已有 SQLite UUID 绑定，严禁直接写 active、伪造审核或将 CI fixture 灌入共享库。完成有效身份、未注册/伪造/错设备拒绝后才能关闭；不进入 MVP-04。 |
| 约束 | 单一 main、一次一个 ID；不动共享 PostgreSQL/IoT、已有 SQLite pending 或本机未提交配置，不提交机密；生产联网/采集/自动恢复/远控/CA 签发 OFF。 |

**本地 MVP 不等于发行批准。v0.1 NOT RELEASE READY。**
