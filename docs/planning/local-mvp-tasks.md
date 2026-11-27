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

- **2026-11-27 后续验收**：维护者实际执行 `bash apps/server/scripts/preflight-local-device-identity.sh`，只读 PG 事务完成，expected_database_role/postgres18 均 true，device_registered=false，matching_credentials/active_current_credentials 均 0。预检数据库分支 PASS；普通 DNS 输出未提供，不作 PASS。新增 [真实签名叶注册事务集成用例](../../apps/server/src/integrationTest/java/com/lanveriq/server/enrollment/PostgreSqlCertificateRegistrationIT.java)：[`bcf093cc`](https://github.com/kunlong-luo/lanveriq/commit/bcf093cc709997279d0ed3a399be4c8f2e42975e) 将既有 token→CSR→review 流程中的伪造叶元数据替换为独立临时 P-256 CA/真实叶/PKIX validator，复用注册 service 和真实 JPA transaction；负例覆盖未认证、无组权限、错 UUID/公钥/CA 及重复注册，预期失败不写凭据/审核/审计。管理员 principal 仅为测试 mock，不是真实登录。[`9984ef2f`](https://github.com/kunlong-luo/lanveriq/commit/9984ef2f25d611172de448045bfa4dcb60a84142) 将该用例加入 JUnit 必选证据，[`8dfdaa41`](https://github.com/kunlong-luo/lanveriq/commit/8dfdaa41030c4a6e214fdec5205ea5347b905a0d) 增加 PG18 workflow path。新代码仅测试域；Java 编译、Testcontainers、CI 实际结果均未确认，不宣称 PASS。未创建本机初始化工具或可信凭据，未启用激活或上传。

- **注册 CI 实证**：功能 `8dfdaa41` 的 [PG18 #45945519700](https://github.com/kunlong-luo/lanveriq/actions/runs/45945519700) success；读取 job `140329219731` 原始日志，checkout 与 Tested Git commit 一致，JUnit gate 确认全部 11 个 Testcontainers 类、至少 151 个通过且无失败/错误/跳过，包含真实签名叶注册 lifecycle 必选用例。[Server unit #45945510994](https://github.com/kunlong-luo/lanveriq/actions/runs/45945510994)（`9984ef2f`）success；早期 PG18 #45945510998 cancelled。此前文档 CI [#45945562073](https://github.com/kunlong-luo/lanveriq/actions/runs/45945562073) / Next [#45945561985](https://github.com/kunlong-luo/lanveriq/actions/runs/45945561985) FAIL（LIVE 页超过 8000 bytes）；修复 `65368d80` 后 [docs #45945973418](https://github.com/kunlong-luo/lanveriq/actions/runs/45945973418) 和 [Next #45945973470](https://github.com/kunlong-luo/lanveriq/actions/runs/45945973470) 均 success，不等于新功能验收。
- **本机初始化入口（新源码，待验收）**：[LocalDeviceEnrollmentConfiguration.java](../../apps/server/src/main/java/com/lanveriq/server/enrollment/LocalDeviceEnrollmentConfiguration.java)（`936d43f9`、`47e65e67`）及 [initialize-local-device.sh](../../apps/server/scripts/initialize-local-device.sh)（`3bf61e08`）：默认不装配；仅明确 opt-in 的非 Web 手动 CLI。早期配置门禁要求固定 loopback PostgreSQL 18 专用库与角色 `lanveriq_local_test`、禁止 Flyway/SQL init、JPA validate、生产/其他 HTTP 开关 OFF、非 CI；运行时查询核对实际库/角色权限。复用真实管理员密码验证与登录审计、scope/token/CSR/pending 事务、独立 CA 的 PKIX 注册；prepare 输出服务端新 UUID，register 只写 pending；无签发、无激活、无 send。配置负例测试 `b9e995f7` 已编写；[PG18 integration source compilation #45946065732](https://github.com/kunlong-luo/lanveriq/actions/runs/45946065732)（`47e65e67`）success，job 140331014671 的编译步骤成功（仅 L1，不启动 PG）。[Server unit #45946124081](https://github.com/kunlong-luo/lanveriq/actions/runs/45946124081)（`3bf61e08`）已 success；job `140331146107` 原始日志确认 checkout SHA、Gradle test 和 bootJar BUILD SUCCESSFUL。真实 CLI 交互/本机初始化仍 NOT RUN。
- **历史隔离库方案（已被维护者决策替代，不再执行）**：独立 `lanveriq_local_test` 已由维护者在 `f44321c8` 成功创建并密码认证；之后 bootJar 成功，但 Zsh 的 `read -p` 不兼容导致读取失败；显式 Bash 重试仍收到 PostgreSQL 28P01，未执行 Flyway/管理员初始化。原流程、初始化 helper 和 CI 证据保留在[固定历史账本 1ccd6145](https://github.com/kunlong-luo/lanveriq/blob/1ccd6145578bc44076b59831fecf0966bc413be4/docs/planning/local-mvp-tasks.md)，不删除该库/角色，不再作为本机默认开发路径。
- **现行单库方案（维护者明确批准，2026-11-27）**：所有本机开发业务统一使用已有 `spark-postgres` 的 `127.0.0.1:5432/lanveriq`、角色 `lanveriq`；不建第二个库，不改共享 IoT 数据库，不提交/硬编码任何密码。仅用于维护者自有且无生产/员工数据的开发环境；确认数据库监听及网络访问受限，简单临时密码不可用于生产。CI 继续独立合成 Testcontainers，不将 fixture SQL 灌入开发库。
- **代码变更（L1 PASS）**：[`81fa9a06`](https://github.com/kunlong-luo/lanveriq/commit/81fa9a0611bbc8f3a3195bd44be5c545dcfbdfed) 固定离线注册 JDBC/实际库和角色为 `lanveriq`，增加显式 personal-development 确认属性和 PUBLIC ACL 核对；[`27c1c44a`](https://github.com/kunlong-luo/lanveriq/commit/27c1c44a82f74ba5093cb9cc1a532844abd8575b) 更新正负例（拒绝旧测试库/角色、未确认）；[`c305cc07`](https://github.com/kunlong-luo/lanveriq/commit/c305cc072d73ac11a6c8789a2282f7d28e449f73) 更新 [launcher](../../apps/server/scripts/initialize-local-device.sh)，固定 Hikari 目标，交互隐藏读密码、Spring 原生环境变量覆盖、不在源码保存秘密。其他安全门禁、真实管理员、CSR/PKIX/审计、固定组 `local-test-devices`、服务端 UUID 分配、pending→激活边界保持。[Server #45947582907](https://github.com/kunlong-luo/lanveriq/actions/runs/45947582907)（`c305cc07`）success；[docs #45947623007](https://github.com/kunlong-luo/lanveriq/actions/runs/45947623007) / [Next #45947623010](https://github.com/kunlong-luo/lanveriq/actions/runs/45947623010)（`718e5e19`）success。真实注册尚未执行。
- **现行本机执行顺序**：① 确认原 `lanveriq` 数据库密码认证及现有迁移 V1–V30；先只读核对 users、devices、目标设备组是否存在，若已有管理员或其他数据则停止首次 bootstrap 并核对现有真实授权，不伪造/重置用户。② 若用户数为零且确认纯本机开发，复用既有 FirstAdminBootstrapRunner（独立 Bash、非 Web、Flyway OFF、JPA validate、固定库/角色、Spring 原生密码环境变量，不继承 IDEA mTLS profile），创建真实管理员及组 `local-test-devices`，随后关闭 bootstrap。③ 新入口 CI PASS 后，在仓库根构建 reviewed main JAR，`bash apps/server/scripts/initialize-local-device.sh prepare`，提供 P-256 公共 CSR，明确确认本机测试，记录新服务端 UUID；独立短期测试 CA 保管者离线签发精确 SAN 的叶，`bash apps/server/scripts/initialize-local-device.sh register`。④ 同一开发库的 IDEA/Caddy loopback mTLS 实例，仅开启必要 activation/credential-epoch 测试能力，验证有效凭据、未注册/错设备/伪造头拒绝；不发送库存，不更改生产默认。新 UUID 与原 SQLite 待发记录独立；不得改绑、删除或覆盖原 outbox。新 outbox 仍需明确批准。

- **2026-11-27 用户本机新证据**：原 `lanveriq` 专用库经密码 TCP 连接成功；只读事务确认 current_user/current_database 均 lanveriq，Flyway 30 条、失败 0，users/devices/device_groups 均 0，COMMIT。单库连接及首次初始化前置条件 PASS（用户提供的终端结果，非助手实机）；不再创建库、不重复迁移。下一项仅离线首次管理员与组初始化，使用既有服务，固定 JDBC/角色、Flyway OFF、JPA validate；密码仅本机隐藏输入，不记录。实际 bootstrap、注册、激活 NOT RUN。

- **2026-11-27 维护者批准本机测试凭据集中管理（本轮）**：根 .gitignore 新增 `/apps/server/config/application-local.yml`（`6547037b`），[无秘密配置模板](../../apps/server/config/application-local.example.yml)（`8b77070b`），离线 launcher（`fb3cfd2c`）改为显式读取同一本机 YAML、检查归属及 0600，移除子 shell 中重复 DB 密码环境覆盖，固定数据库/角色/安全门禁不变。IDEA 使用 `local` profile（获批 mTLS 另加既有 acceptance profile）；详细操作见 [Server README](../../apps/server/README.md)（`8b2f4377`）。管理员测试原密码如确需本机记录，单独存忽略的 `apps/server/credentials/local-admin.txt`，0700/0600，不被应用读取；不在 Git 中保存密码，数据库仍只存哈希，不自动创建/重置。此前用户称未改变首次初始化账号、只设置密码，但未提供实际 bootstrap 成功日志，不能确认管理员已创建。新配置本机文件未创建，CI 待确认；不改生产配置、不动旧库/SQLite。

- **维护者进一步批准直接内置公开临时测试默认值（替代手工配置流程）**：仅显式 `local-test` profile 使用 [历史 application-local-test.yml](https://github.com/kunlong-luo/lanveriq/blob/9bd97f353ece33e7b5e37bebc0294681a4f5c9a7/apps/server/src/main/resources/application-local-test.yml)（`9bd97f35`）；公开测试 fixture，不是生产凭据；原 application.yml 不变，真实秘密仍禁止提交。launcher `fd714fcf` 自动使用该 profile，无需另建本机 YAML 或输入数据库密码；现有外部 local 文件保留不启用。Java guard `a41dfb9c` 限制 JDBC/Hikari/Flyway 目标、角色、loopback 监听、禁止 JNDI 和其他 profile 混用（仅允许既有 mTLS acceptance）；配置负例 `60d0f992` 已编写；[Server #45948791811](https://github.com/kunlong-luo/lanveriq/actions/runs/45948791811)（`6641e85a`）SUCCESS；[docs #45948807334](https://github.com/kunlong-luo/lanveriq/actions/runs/45948807334)（`a3a4f808`）SUCCESS。README `6641e85a` 记录 IDEA 仅设置 profile，已有 TLS 参数保留。所有生产功能仍 OFF；管理员真实密码不硬编码、不重置，初始化实际成功尚缺证据。公开测试密码仅用于本机开发，退出测试阶段必须轮换。

- **本轮维护者决策：唯一测试 profile 名为 `test`**。原 `local-test` 已替换，删除旧 YAML；[application-test.yml](../../apps/server/src/main/resources/application-test.yml) 集中公开可丢弃数据库与管理员测试 fixture（用户名 local-test-admin、组 local-test-devices；管理员初始密码为新公开示例，不声称与现有数据库密码相同）。bootstrap.login/group 引用该配置，bootstrap.enabled=false，密码字段仅供操作者查看及手动输入，不作为登录后门或重置密码。同步 Java guard/负测、launcher 和 README；固定 loopback、拒绝其他 DB/角色及生产 profile，允许既有 mTLS acceptance。原真实密码未知，不提交；本机既有账号与 Argon2id 哈希不变。新 CI 与本机验收待核实。

- **2026-11-27 最新本机证据及 CI**：维护者提供 Spring 启动日志（PG18.7、lanveriq/public、JPA、127.0.0.1:8443 HTTPS），18081 HTTP 不监听；经 Caddy mTLS Health 返回 UP，SQL users/device_groups 各 1。只证明现有记录，不证明管理员密码/组授权已实际验证；不重复 bootstrap。功能 `07ebb357` Server [#45949082428](https://github.com/kunlong-luo/lanveriq/actions/runs/45949082428)、PG 编译 [#45949082591](https://github.com/kunlong-luo/lanveriq/actions/runs/45949082591)、docs [#45949082512](https://github.com/kunlong-luo/lanveriq/actions/runs/45949082512) SUCCESS；公开 fixture 更新 `225ef841` Server [#45949317790](https://github.com/kunlong-luo/lanveriq/actions/runs/45949317790)、RD-2 [#45949317829](https://github.com/kunlong-luo/lanveriq/actions/runs/45949317829) SUCCESS。下一步新建仓库外受限目录生成 P-256 CSR，经 prepare 真实管理员认证及新 UUID；确认独立测试 CA 可签发后再 register，禁止覆写既有证书/SQLite 或提前 send。

- **2026-11-27 本机 prepare PASS（维护者终端证据）**：真实管理员密码认证、组范围 token/CSR 校验与事务创建 pending device 成功，CLI 输出 CSR SHA256 和服务端 UUIDv7，随后正常 shutdown。不记录具体本机 UUID、密钥路径或凭据。既有管理员/组不再初始化，不重复 prepare；使用同一 CSR 和新 UUID，经已核实的独立本机测试 CA 离线签发 clientAuth 叶，准确 URI SAN，随后 register、activation/epoch。CA 私钥是否可用尚未核实；不修改共享 Caddy 信任、不覆写旧证书或 SQLite、不签发生产证书、不提前 send。

- **维护者确认采用 XDG 用户目录（2026-11-27）**：用户报告已改好目录；既有测试 Agent CA 证书有效期 2026-11-27 至 2026-12-04，搜索找到候选私钥，迁移后实际文件路径、权限及 SPKI 匹配仍需核实。统一用户数据根 `$XDG_DATA_HOME/lanveriq`（默认 `$HOME/.local/share/lanveriq`），按职责使用 pki/enrollment/agent，不再新建任务编号目录。AGENTS 和安装手册同步；不声称所有代码或 SQLite 已迁移。既有系统 Caddy 及 /etc 路径不改；同一 CSR/UUID 继续使用，签发/注册/激活待验。

- **2026-11-27 维护者本机 CA 匹配证据**：迁移后 XDG pki 中既有测试 Agent CA 私钥导出的 SPKI SHA256 与 Caddy 信任 CA 证书导出的 SPKI SHA256 完全一致；仅证明配对，不是签发、PKIX 注册或激活 PASS。复用原 CA 和已准备 CSR/UUID，离线签发短期 P-256 clientAuth 叶（CA:FALSE、digitalSignature、唯一 URI SAN），先验证有效期、链和公钥一致，再调用 register。保持 Caddy、旧证书和 SQLite 不变。目录规范提交 `047bfe74` 的 [docs #45950657280](https://github.com/kunlong-luo/lanveriq/actions/runs/45950657280) 与 [RD-2 #45950657240](https://github.com/kunlong-luo/lanveriq/actions/runs/45950657240) SUCCESS。

- **2026-11-27 本机 register PASS（维护者终端证据）**：同一已准备 UUID 与 CSR 的测试 CA 签发叶经真实管理员认证、独立 CA/PKIX 与 CSR 公钥绑定校验，CLI 输出 Certificate registered as pending. No activation or inventory upload performed，JPA/Hikari 正常关闭。仅证明 pending 凭据注册；不保存具体设备 UUID、私钥路径或凭据。下一步本机 8443 mTLS acceptance 仅显式开启 activation、heartbeat（epoch 依赖）、credential-epoch，inventory/console/其他生产能力保持 OFF；以同一新客户端证书先验证 pending epoch/错设备拒绝，再真实 activation 及 epoch 正向/错设备拒绝。保留原 SQLite 与旧证书，不发送库存。前一文档提交 `68a1852e` 的 [docs #45950792019](https://github.com/kunlong-luo/lanveriq/actions/runs/45950792019)、[RD-2 #45950792014](https://github.com/kunlong-luo/lanveriq/actions/runs/45950792014) SUCCESS；非本轮功能重跑。

- **2026-11-27 本机 activation 首次失败（维护者终端证据）**：同一新注册叶经 Caddy Health UP；pending epoch HTTP403（预期）；POST activate HTTP403（非预期），脚本停止，active epoch/错设备测试 NOT RUN。已核对 `AgentActivationRestController`、`AgentIngressAssertionPolicy`、`JpaDeviceActivationRepository` 和 Caddy reference：403 故意合并 ingress/凭据拒绝，Health 不证明业务身份断言，pending 403 也不能独立证明拒绝来自 pending 状态。先只读核对实际 DB 精确设备/叶 DER SHA256、状态、撤销、有效期、CSR review/SPKI 与期限；再核对运行 Caddy 路由/Host/指纹注入及 upstream pin，不放宽 TLS、不开库存、不重建设备、不手工 UPDATE 状态。前一文档提交 `90d968a8` 的 [docs #45951130920](https://github.com/kunlong-luo/lanveriq/actions/runs/45951130920)、[RD-2 #45951130955](https://github.com/kunlong-luo/lanveriq/actions/runs/45951130955) SUCCESS，非功能验收。

- **2026-11-27 activation 403 定位进展（维护者本机只读证据）**：同一设备唯一行 pending_certificate/pending，叶 DER 指纹匹配、设备/凭据未撤销、证书有效、CSR review/期限/SPKI 条件全部 true；读取 Caddy 容器内配置文件显示 activate/epoch matcher、清除入站断言、反代 8443、注入 fingerprint、固定 Host，与参考配置相关行一致。仅证明数据库查询时条件和磁盘配置，不证明实际请求头、上游 pin、运行中 profile/flags 或 HTTP 版本。下一步核对 Caddy 上游客户端叶与 Spring configtree pin 一致、Caddy host 网络及 JVM 实际参数；必要时加仅固定拒绝原因的本机诊断，不输出凭据、不伪造上游、不放宽校验。保持 pending 与原 SQLite。

- **2026-11-27 本机上游诊断**：维护者提供 Caddy proxy 叶 DER SHA256 与 Spring configtree 值一致、容器 host 网络、127.0.0.1:8443 监听（IPv4 mapped 显示）。仍非实际 servlet 请求证据。新增 opt-in DEBUG 固定原因码 `com.lanveriq.server.diagnostics.activation`，覆盖 activation chain 装配、controller 到达、入口条件和 repository 拒绝；不打印请求值、证书、指纹、UUID 或密码，不改变对外 403/400、SQL、TLS 或安全开关。添加诊断单测，CI 待验；IDEA 仅显式添加 `-Dlogging.level.com.lanveriq.server.diagnostics.activation=DEBUG`，重启后用同一叶重试 activate，收集固定码；定位后关闭 DEBUG。前一提交 `372a4609` docs #45951754208、RD-2 #45951754196 SUCCESS。

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
| 基线 | [main 89b6b773](https://github.com/kunlong-luo/lanveriq/commit/89b6b7739e37357b4a7757f766574c67a01a3bef)；MVP-01/02 不变。 |
| 本轮 | **MVP-03 BLOCKED**。DB 全 true、上游 pin/host/监听 PASS；新增默认关闭 DEBUG 原因码，待实际请求定位。 |
| 已验证（源码） | 原 test profile 07ebb357 与 225ef841 Server CI PASS；本轮新增固定原因码及单测，CI 待核实。 |
| 已验证（CI L2 历史原始日志） | [#45742025955](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955) 三 jobs success，job 139649120029 checkout/Source SHA b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f；另 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两 jobs success，job 139002587446 checkout db570181d603a7f069a85a23c009658e9a8bed4c。日志实际执行有效 TLS1.3、无证书/错误 CA/TLS1.2/伪造头无上游证书/错误设备/未注册凭据拒绝；合成身份绑定 PASS。非本轮重跑，compare 显示当前 Agent main.rs 有改动。 |
| 用户本机证据 / 未验证 | 既有 PG18.7/迁移30/0/8443 HTTPS/管理员与组/prepare/CA/register PASS；新叶 Health UP，pending epoch 403（预期），activate 403 FAIL，active epoch/错设备未执行；非 L3。 |
| 评分/上线 | [工程评分](project-scorecard.md) **71/100 不变**；新增用户本机传输层证据，不增加自动验收或 L3 评分。G-01～G-10 OPEN，0/10 关闭，v0.1 NOT RELEASE READY / NO-GO。 |
| **下次唯一任务** | **MVP-03**：核实诊断 CI，IDEA 开启专用 DEBUG，重试同一证书 activate，按固定原因修复后复验 epoch；inventory OFF、原 SQLite 不变。 |
| 约束 | 单一 main、一次一个 ID；不动共享 PostgreSQL/IoT、已有 SQLite pending 或本机未提交配置，不提交机密；生产联网/采集/自动恢复/远控/CA 签发 OFF。 |

**本地 MVP 不等于发行批准。v0.1 NOT RELEASE READY。**
