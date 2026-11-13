# Batch A / 首次心跳的可运行链路 — Stage A1

**2026-11-13 Batch A3 只读恢复竞态 L1 已通过（不是自动恢复）：** 源码 [`b70b20c2`](https://github.com/kunlong-luo/lanveriq/commit/b70b20c265d45de1aaa53690a05e50949a04bd5f) 引入查账完成后二次加载 SQLite，必须同时匹配 CAS generation 与完整 checkpoint；并发进入 Quarantine / 同状态重复 CAS 改变版本时，只返回 `StaleDuringLookup`，不准把原 `Committed`/未知状态报告为当前结果；不修改 pending UUID 或解隔离。实际验证源码 SHA [`cc85897f`](https://github.com/kunlong-luo/lanveriq/commit/cc85897f28949f4938baa69310d61ba44abf9717)：[Agent Rustfmt + 全量单测 + Clippy #43368140495 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140495)，[RD-2 default-OFF #43368140522 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140522)。新增两项真实文件型 SQLite 并发验证，不是新功能的网络 L2、正式操作员恢复协议、Pop!_OS 实机验收或生产放行。


**2026-11-13 Batch A3 恢复检查竞态加固（源码待当次 L1 CI 验证）：** 只读 `inspect_explicit_heartbeat_reconciliation` 不再仅依据查账开始前的 SQLite 快照，网络 GET 返回后必须**二次加载设备绑定 checkpoint，对比 CAS generation 和完整状态**。如果查账期间同一身份进入暂停/隔离、切换请求 ID，甚至仅发生无业务变动的 generation 增长，返回 `StaleDuringLookup`，绝不把旧 `Committed`、404/429/5xx 推断为当前可恢复证据；不修改 SQLite，也不自动解除隔离。新增基于真实文件型 SQLite 的并发更新、无业务变更 CAS 竞争负例。该读取仍不是正式操作员恢复协议，不启用 systemd、生产 Agent 网络或采集。


**2026-11-13 Batch A3 只读对账诊断 L1 已通过（不批准真实恢复）：** 新增显式调用、只读的 `heartbeat_reconciliation.rs` 恢复检查器。它先加载并验证同设备持久 SQLite checkpoint/seen 标记；仅在显式允许且有 pending、非 Identity halt 时可调用**现有严格 mTLS request-ID GET**（由注入 callback 提供；Agent service 仍不调用）。返回 `CommittedStillHeld` / `UnresolvedStillHeld` / `IdentityRejectedStillHeld` / `QuarantinedStillHeld`，绝不修改 SQLite generation、pending UUID、halt，亦绝不据 404/429/5xx 或已提交账本自动新发 POST/解除 quarantine。新增文件 SQLite 重启、wrong-device、Identity halt、错误响应和无副作用测试。**这是仅合成的诊断接口，不是运营批准、完整恢复协议或生产联网。** **精确 L1 验证：** [Agent Rustfmt/单测/Clippy #43364626478 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626478)，[RD-2 default-OFF #43364626482 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626482)；格式修正后受测源码 SHA [`864297bb`](https://github.com/kunlong-luo/lanveriq/commit/864297bbeb2c5f96191f29526b1afbe4f6eedeb7)。没有实际 Pop!_OS/生产测试，不改变 Batch A OPEN。


**2026-11-13 V9→V10 真实升级验收：通过。** 精确源码 [`be6b6c6`](https://github.com/kunlong-luo/lanveriq/commit/be6b6c6)：[Java L1 #43353817071](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817071)、[PG18 测试源码编译 #43353817114](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817114)、[双跳 TLS1.3 + PG18 #43353817134](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817134)（两个实际任务）及 [RD-2 默认拒绝 #43353817043](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817043) 均成功。独立 PG18 18.6 schema 先迁移到 V9，写入旧请求 ID，再升级到 V10；旧行和原设备级唯一键保留。V10 的 `NOT VALID` 检查允许旧行继续为 NULL，同时拒绝升级后任何新空证书指纹行（SQLSTATE 23514）。因此旧记录仍无法正向对账，且不会再产生新的无归属记录；404 仍不能授权重试。此项不批准自动解除隔离或真实设备运行。

**2026-11-13 V10 同证书请求 ID 对账：真实隔离 L1/L2 通过。** 精确源码 [`f8674a2`](https://github.com/kunlong-luo/lanveriq/commit/f8674a2ef1c6c4afc973adb6c7a6c8c223baeae6)：[Java L1 #43352639410](https://github.com/kunlong-luo/lanveriq/actions/runs/43352639410)、[PG18 测试源码编译 #43352639079](https://github.com/kunlong-luo/lanveriq/actions/runs/43352639079)、[双跳 TLS1.3 + PG18 #43352639007](https://github.com/kunlong-luo/lanveriq/actions/runs/43352639007)（两个实际任务）、[RD-2 默认拒绝 #43352638978](https://github.com/kunlong-luo/lanveriq/actions/runs/43352638978)均成功。V10 在已接受 UUID 账本附加实际提交时的证书 SHA-256 指纹；只读 GET 仍先锁定当前有效证书，且仅同一张证书可确认该 UUID。同设备第二张有效合成证书不能读取第一张证书的 ID，也不能重放同一 UUID；既有 V9 无法归属的记录保留但不会被猜测回填，查询返回未确认的 404。404 绝非重试许可；这只补上证书绑定，不代表跨证书代际恢复、retention/epoch 或人工批准的隔离解除协议。生产 Agent 联网、采集、远控仍默认关闭，Issue #5 保持 OPEN。

**2026-11-13 Batch A3 可信请求 ID 对账读取：真实隔离 L1/L2 全部通过。** [源代码 `1ddca5a5`](https://github.com/kunlong-luo/lanveriq/commit/1ddca5a5517a7ad4d26fe6adfd93af56be457f65)、[Rustfmt + Rust 单测 + Clippy #43284107063 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43284107063)、[Linux Batch A 真实 Rust→Caddy→Spring/Tomcat→PostgreSQL 18 #43284106825 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43284106825)（两个任务均成功）、[RD-2 默认拒绝 #43284106975 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43284106975)。新增的默认 OFF `GET /api/v1/devices/{deviceId}/heartbeats/requests/{requestId}` 只在当前有效的双跳 mTLS 设备证书、精确 Host/路径/方法与 JPA device→credential 锁验证通过后，读取 PG18 V9 已提交请求 UUID 账本。Rust Reqwest/Rustls 使用严格 `serde(deny_unknown_fields)` DTO 验证设备、UUID、schema、`committed=true` 与查询时间。真实隔离 CI 证明已提交 UUID 返回确认，未知 UUID 为 `Unresolved`，错设备及证书过期/吊销无法读取；TLS1.2、伪造身份、错误 CA/Host/SNI 和重放原有负例未退化。**404/429/5xx 不能证明请求未写入，不允许更换 UUID 自动重试；查询时间不是原始事务提交时间，也不等于原始 POST ACK。对账读取不会自动解除 SQLite 的身份暂停/隔离。** 仍缺受授权的恢复协议/确认窗口和代际绑定、账本 retention/epoch、Linux systemd 服务调度/密钥托管以及真实 Pop!_OS 24 设备的合法验收；生产采集、联网、远控保持关闭。

**2026-11-13 A3 对账只读入口（历史源码候选，已由本页最新同源 CI 成功记录取代）：** 新增与既有 Agent heartbeat 两个默认关闭开关绑定的 `GET /api/v1/devices/{deviceId}/heartbeats/requests/{requestId}`。它只提供 PostgreSQL V9 已提交 UUID 账本的正向证明，经 Caddy→Tomcat TLS1.3、精确 Host/路径/方法、实际 proxy peer 指纹验证和同一 JPA `device→credential` 行锁；未知 ID 为 no-store 空体 404、吊销/过期/错设备为 403、已提交才返回严格的 `schema_version/device_id/request_id/committed/server_time`。Rust 客户端用 deny-unknown-fields 的独立 DTO 校验，**404/429/5xx 一律 Unresolved，不能据此重新发送；HTTP 200 也不是第一次 POST 的历史 ACK，而只是当下查询的已提交证据**。当前没有新增自动轮询或解除持久 quarantine 的路径，更没有开启常驻 Agent 运行时。此项已在上方最新同源 CI 中通过；历史候选状态仅保留过程证据。

**2026-11-12 Batch A3 SQLite 心跳尝试/恢复：真实隔离 L1+L2 已通过。** 源代码 [`233b5c8f`](https://github.com/kunlong-luo/lanveriq/commit/233b5c8f98726c95f3ac9242b919f28046b32c73) 对应 [Rust Agent fmt/unit/Clippy #43224096161 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43224096161)、[Linux Batch A 双跳 TLS + PostgreSQL 18.6 #43224096247 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43224096247)（两项实际任务均成功），[RD-2 fail-closed #43224096138 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43224096138)。新 SQLx V6 `heartbeat_schedule_checkpoint`（device-bound、generation CAS 与已保存标记）、Rust `heartbeat_schedule.rs`/ `heartbeat_runtime.rs` 在外部显式批准的**一次性尝试**前先提交 SQLite UUID，再只接受严格匹配 ACK。重启残留 pending 状态要求对账，401/403 身份暂停，409/5xx/不确定网络结果隔离，不能悄悄重置或换 UUID 无限重试。文件 SQLite 重启、并发旧版本、防篡改、写入失败测试已通过；隔离 Linux CI 真实 Rust Reqwest→Caddy→Tomcat→PG18 还验证首次发送→关闭重开 SQLite→第二次发送、精确 ACK、HTTP 409 重放和过期/吊销负向。**仍未将这一协调器接入 `main.rs` 的常驻 Agent 调度，`systemd` 模板仍 `PrivateNetwork=yes`/默认无采集；Ubuntu 24.04 合成 CI 不是 Pop!_OS 实机、组织授权、正式 PKI、持久重试/对账协议或生产发布批准。**

**2026-11-12 Batch A3 本地运行时桥接（源码 / CI 待验证）：** 新增 Rust `heartbeat_schedule.rs`、`heartbeat_runtime.rs` 和 SQLx SQLite V6 心跳 checkpoint。它们不是系统服务入口：只有调用方显式 `enabled=true` 且验证设备身份时才允许**一次**发送尝试；本地事务在发送前持久化非 nil `request_id`，只有调用方经已验证的严格 ACK 才清除。系统意外重启、网络超时、503/409、403/401 或 SQLite 写入失败均不得自动用不同 UUID 重传。任何悬而未决的请求要求重新对账/人工授权，身份证明拒绝则持久暂停。CAS generation 拒绝过时写入，损坏或被删除的 checkpoint 不会自动恢复。**目前未接入 `main.rs`、systemd 定时器、真实数据采集或设备网络；完整服务授权与自动可靠重试仍在 A3 待办。**

**2026-11-12 最新合成验收：单条真实 Rust→Caddy→Spring/Tomcat→PostgreSQL 18 业务链已通过。** [代码 SHA `80298562`](https://github.com/kunlong-luo/lanveriq/commit/80298562d4221c0afc4dd18cfa01d1ac3f7b15ad) 的 [Linux Batch A #43210930135](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930135) **SUCCESS（两个任务均成功）**，独立 [Java L1 #43210930065](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930065) 和 [RD-2 默认拒绝 #43210930301](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930301) 也 **SUCCESS**。一次性 Ubuntu 24.04 runner 上，先在默认关闭状态验证真实 TLS1.2 禁止、双向 mTLS、错误 CA/SNI/Host 和业务 403，再对**同一临时 PostgreSQL 18.6** 以独立双路由 opt-in 启用测试服务：真实 Linux Rust P-256 CSR 对应叶证书发送激活 + 两次不同心跳，精确 ACK 全部通过；旧 `request_id` 的 HTTP 409/空体/no-store、数据库最新状态和 `last_seen_at` 不变、跨设备、受信 CA 但未注册证书、过期/吊销后拒绝均通过。全部合成且只使用测试私钥和固定虚构设备 ID，没有启动设备 Agent service、采集真实机器或改变生产配置。**仅 A4 核心 L2 合成请求闭环通过，不等于 A2 完整接入令牌/审核发行、A3 受控系统服务调度、A5 Pop!_OS 实机、生产 CA、请求 ID retention/epoch 与隐私/发布批准。**

**历史状态说明：**下方的 2026-11-08 A1 源码状态、2026-11-12 「待 CI」和失败诊断仅为历史时间点记录；**本节上方的 `80298562` 同源 CI 成功证据优先**。

**2026-11-12 HTTP/2 Host 安全差异诊断（修复尚待 CI）：** [原始诊断 #43209697578](https://github.com/kunlong-luo/lanveriq/actions/runs/43209697578) 使用同一受信托的 Caddy upstream 证书与已审核合成 PG18 注册行，直接请求 Tomcat 的 HTTP/1.1 激活为 200、HTTP/2 激活为 403。因 HTTP/2 使用 `:authority` 伪头，普通 `Host` 可能缺失，现将 `AgentIngressAssertionPolicy` 限定为：**仅 HTTP/2** 可在普通 Host 缺失时使用 Tomcat 解析的精确 `getServerName()`，任何显式不一致 Host 仍拒绝；HTTP/1.1 继续必须提供唯一准确 Host，所有 TLS peer、来源 IP、路径、设备绑定仍保留。真实链路 CI 通过前不得宣告修复成功。

**2026-11-12 Batch A 单链路候选（源代码已准备、CI 尚未验证）：**
`linux-batch-a-caddy-pg18-acceptance.yml` 在一次性 GitHub Ubuntu 24.04 runner 上先验证所有既有默认-OFF 403 与真实 TLS1.2、错 CA、错 Host 负例，再启动同一个临时 PostgreSQL 18 数据库所对应的受控 Spring 双路由。真实 Rust `AgentTlsTransport` 以刚生成的 Linux P-256 CSR 所签客户端证书，经 Caddy/Tomcat 双向 TLS 发送 activation + 两次 heartbeat；随后重放旧请求，并验证精确 ACK、HTTP 409、数据库最新状态与 last_seen 不被改写、未注册但受信 CA 证书、错设备、过期和吊销后的拒绝。仅合成 `not_collected`，不启动 Agent service、不搜集真实端点。此阶段不要在 CI 完成前宣称 A4 已验收；生产、POP!_OS 实机及受控发行仍未获授权。



**日期：2026-11-08。状态：默认 OFF 的心跳 HTTP 入口、Agent 严格回执与 L1 定向单测已提交；尚未完成批次 A 合成 E2E。**
**上级计划：**[v0.1 批量交付计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md)，其优先级高于旧 NXT 逐阶段 UI/验收笔记。

## 当前已有的复用构件

- Agent：Rust/reqwest Rustls `Heartbeat` DTO 和显式 `AgentTlsTransport::send_heartbeat`；现有 `AgentConfig` 和 `runtime` 仍然是闲置循环，**不会自动联网/采集**。
- 服务端：`DeviceHeartbeatPolicy` 验证 wire schema/容量/capabilities/时间戳；`DeviceHeartbeatService` 在事务中执行；`JpaDeviceHeartbeatRepository` 使用 `JpaActiveDeviceCredentialGuard.lock` 检查证书、设备、吊销/过期，然后用 PostgreSQL 接收时间更新 `device_heartbeat_latest`。
- 上游身份：`AgentIngressAssertionPolicy.resolve()` 检查 **实际 servlet TLS peer** 的专用 Caddy upstream 证书 SHA-256、来源 127.0.0.1、精确 Host/URI/method、Caddy 覆盖的客户端证书指纹头，并与 URL 的 device UUID 绑定。该 parser Bean **只有**在 `server.ssl.client-auth=need`、TLSv1.3、127.0.0.1:8443、独立 truststore/key-store 与无代理头信任时才允许构造。
- 服务端其他功能：默认 deny-all；Console Session 与健康只读接口采用各自独立安全链；绝不把 Agent mTLS 当作管理员登录凭据。

## 本批第一份实际代码

1. Rust `transport.rs` 用 `HeartbeatAck` (`serde(deny_unknown_fields)`) + `classify_heartbeat_response` 取代自由 `serde_json::Value` 的宽松 ACK 接受方式。在 [Batch A 传输硬化 Draft PR #4](https://github.com/kunlong-luo/lanveriq/pull/4) 中，只有格式正确的 `server_time`、**与当次心跳请求完全匹配且非 nil 的 `request_id`**、HTTP 200 才是 `Accepted`；未知/额外字段、错配/旧请求 ID、损坏时间、超大包不能当作成功。原先服务端随机生成而不与请求关联的回执不再被认为有足够的相关性。此阶段仍需完成 Rust/Java 真实跨语言合成联调，不能由单测推断。401/403 暂停身份、409 隔离、5xx 有界退避，**不删除数据也不直接重试**。`transport_heartbeat_tests.rs` 用纯合成数据验证，未执行任何联网。
2. Spring `AgentHeartbeatRestController` 是一个 **同时需要两个显式开关** 的精确 POST 路由 `/api/v1/devices/{deviceId}/heartbeats`；`AgentHeartbeatHttpSecurityConfiguration` 仅此路由 sessionless/CSRF-independent，其余 API 沿用默认 deny，GET/异常路径不授权。入口先经 `AgentIngressAssertionPolicy` 解析受信 Caddy upstream mTLS，再由 `DeviceHeartbeatService` + JPA 当前有效证书写入；服务器回包 `no-store`，401/403/400/413 不能泄露设备组。
3. `application.yml` 添加独立的 `lanveriq.agent.heartbeat.http-enabled: ${LANVERIQ_AGENT_HEARTBEAT_HTTP_ENABLED:false}`。**单独开启这个开关不会激活路由**；还需要明确开启 `lanveriq.agent.ingress.assertions-enabled`，且不满足 Tomcat TLS startup 约束会直接失败。部署参考配置、真实 Caddy/Agent/CA 与默认 OFF 状态都未改动。
4. `AgentHeartbeatRestControllerTest` 在**mock** Servlet 请求和 mock proxy 证据下检查双重开关注解、mTLS 身份检查必须先调用、合法 ACK no-store、身份拒绝不能写库、字段无效/超大/撤销后无泄漏。它**不是**真实 TLS 或 PostgreSQL 集成。

## Batch A 接下来的编码任务（尚未完成）

- **A2 证书生命周期：**管理员受限发行一次性接入令牌→Agent 生成/保存密钥与 CSR→人工批准/离线签名→受审核的注册/激活/撤销。必要的证书保护、密钥管理、用户告知与设备授权必须先定。
- **A3 真实 Agent service 链接：**首次激活后受控调度 `send_heartbeat`，依据真实授权/运行状态和 SQLite 元信息构造 wire DTO，持续 401/403 停止而不是无限尝试；保留严格终止/恢复、协议限流。未授权员工终端不允许启动。
- **A4 L2 合成闭环：核心真实请求正负向已通过（`80298562`）**：一次性 Rust Reqwest 通过 Caddy/Tomcat TLS 1.3 到 PostgreSQL 18.6 的激活、双心跳、精确 ACK、防重放与错证书/错设备/过期/吊销拒绝有 CI 证据。**仍缺**受限浏览器 GET 与同一请求链的授权会话绑定、断网/重启重试、跨语言完整 Golden Fixture、受控审批发行及真实 Pop!_OS 安装服务；这些不得由当前合成通过推断。后续昂贵 L2/L3 按里程碑运行，不在每次普通源码提交全量重跑。
- **A5 真实受限试点** 需要独立合法/组织批准、设备所有者同意、受控 CA 与 Windows/Linux 验收，暂不执行。

## 风险/边界

- 代理身份指纹头只有在专用 Caddy 确认覆写、独立上下游 mTLS 且 loopback 端口不可直接绕过时才有价值；一个手动添加头的本地请求必须被拒绝。工作站生产部署的 CA/信任/网络隔离 **尚未批准**。
- 严格时间形状校验不等于服务器信任来自 Agent 的时间。只相信 PG18 的 `received_at`，`sent_at` 仅作为经过限制的客户端声明。
- MockMvc 并非真实 mTLS；上一轮 NXT-20 通过的 Caddy/PG18 合成测试是历史证据，不适用于推断新心跳路由已经通过真实网络集成。
- 不会采集用户应用、窗口标题、屏幕、个人文件；Agent 服务仍然默认空闲。不会在静态 GitHub Pages 中引入任何认证/网络客户端。

**L1 验收结果（2026-11-08）：**

- [Java 25 / Gradle JUnit 快速验证 #42448164511](https://github.com/kunlong-luo/lanveriq/actions/runs/42448164511)：**SUCCESS**。包括新默认 OFF heartbeat REST 的 MockMvc 负例，以及既有 mTLS parser / heartbeat policy 和 JPA 单元测试；没有 Docker、真实 TLS 或生产部署。
- [Rust Agent Linux/Windows / fmt / `cargo test --workspace --locked` / Clippy #42447913810](https://github.com/kunlong-luo/lanveriq/actions/runs/42447913810)：**SUCCESS**。两平台均通过严格 heartbeat receipt/401/403/409/503 分类测试；没有实际联网、服务启动或采集。
- [Next.js 源码隔离、类型和组件快速验证 #42447944556](https://github.com/kunlong-luo/lanveriq/actions/runs/42447944556)：**SUCCESS**。本批未修改公开预览或启用认证。

**2026-11-09 增补（Draft PR #4，待最终 CI/评审）：**源代码对 Agent HTTPS 限定 TLSv1.3，并为心跳请求增加 UUID 请求关联、服务端核验非 nil、成功写入后回显、Rust 端拒绝旧/错配 ACK。以上为源码/单测层安全改进，不意味着 Caddy/Tomcat/PG18 已完成完整双跳 TLS 正负向验收，也不代表 A2 CSR 与密钥托管或 A3 运行时已连接。后续集中验收跟踪 [Batch A Issue #5](https://github.com/kunlong-luo/lanveriq/issues/5)。

**Stage A1（2026-11-08 历史基线）= source-ready（合成 L1）；2026-11-12 已补齐 A4 的核心 Rust 单请求→双跳 TLS→PG18→精确 ACK L2 合成测试（见文首同源 CI）。** Batch A 仍未完整完成，后续主要工作是正式审核/证书发行生命周期、Agent runtime/SQLite 受控桥接、故障重试与独立设备授权。
