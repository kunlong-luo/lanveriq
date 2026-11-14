# Lanveriq v0.1 Linux-first 上线差距与验收状态

**基准：2026-11-13 · `main` [`758f8d9a`](https://github.com/kunlong-luo/lanveriq/commit/758f8d9a9bdedad04db656a5d685871b38ed3af9)。**

**上线判定：NOT RELEASE READY。** 当前已有真实 **隔离合成** Linux Rust/Reqwest→Caddy→Spring/Tomcat TLS1.3→PostgreSQL 18 的激活、心跳和严格 ACK 证据；但未完成获授权的 Pop!_OS 24.04 常驻 Agent、证书运营全生命周期、正式 Next.js 与 WSS、资产和断网恢复、生产部署、备份演练、安全与隐私审批。**L1 源码测试、L2 合成集成、L3 真实设备、授权试点、生产发布必须分别标记，不能互相替代。**

本文件是 A–E 的**当前状态入口**。带日期的旧 `backlog`、NXT、D0 和早期 CI“尚未测试”结论保留为历史，而不是 2026-11-13 的事实；变更完成状态必须附精确源码 SHA、真实 CI job/run 或实机人工验收证据。

## Linux-first v0.1 范围

- 首发只声明 **Pop!_OS 24.04 LTS / COSMIC Linux**：非 root systemd、批准的 CA/CSR/证书、心跳、CPU/RAM/磁盘/OS 和经许可网络资产、SQLx SQLite 断线队列、Spring/JPA/PG18、正式 Next.js 登录/设备列表/Monitoring Center、WSS 元数据提示和 REST 权威重同步；先在受批准 LAN/VPN 内试点，未批准公网入口。
- **Windows 11** 已有 SCM/CNG 合成基础，但原生密钥/服务 SID/MSI/升级/OS L3 单列后续支持门槛。Windows 未完成不应阻塞**明确标注为 Linux-only** 的 v0.1；同时不得宣称 Windows 已正式支持。
- **RustDesk R** 独立轨道，[Issue #3](https://github.com/kunlong-luo/lanveriq/issues/3) RD-2 BLOCKED，未证明服务端不可绕过的会话 admission、撤权、AUTH-01–16 与许可。不能因为 OSS rendezvous 成功而启用生产远控。
- v0.2 活动/安全审计和复杂报表；v0.3 经批准录制/审批/回放/删除，均不阻塞 Linux 设备管理 v0.1；若合同将其列为首发功能，则按相应独立发布门槛验收。
- 本次仅文档：生产 Agent 网络/采集、真实设备与员工数据、Console 正式业务开关、CA 私钥/公网入口与 RustDesk 一律保持默认关闭。不得自动部署。

## 2026-11-14 A11-D1 — 官方 WebAuthn/Passkey 验证引擎依赖已接入（不等于 MFA 上线）

- **技术选型：** 依赖 BOM 管理的官方 `org.springframework.security:spring-security-webauthn`（Spring Security WebAuthn4J 验证能力），与 Spring Boot 4.1.2 / Java 25 / Gradle 对齐；不自行实现 FIDO/COSE/CBOR/签名原语，也不引入双重认证框架。官方指南 https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html 。
- **现阶段只有依赖，任何成功路径仍不可用：** 未调用 `HttpSecurity.webAuthn(...)`，未开 `/webauthn/**` / `/login/webauthn` / Console Step-up 路由，未注册 Passkey 凭证/持久化库，唯一生产 Step-up verifier 仍是 hard-deny。测试代码使用正确 CSRF、Origin/Host 和登录会话检查认证入口不可用；真正 `@SpringBootTest` + PG18 `PostgreSqlConsoleSecurityWiringIT` 新增强制 `fullBootPostgresNeverAutoMountsPasskeyRoutesFromDependency`；JUnit 严格验收门禁同步防跳过。
- **隔离 L2 验收（精确源码）：** 初轮 [Java L1 #43575174776](https://github.com/kunlong-luo/lanveriq/actions/runs/43575174776) 因新增测试的本地 `csrf(session)` 方法与静态 `csrf()` 重名而编译失败；[PG18 #43575168492](https://github.com/kunlong-luo/lanveriq/actions/runs/43575168492) 因匿名 401 与测试误要求 403 而失败。修复后 [源码 `97dcb2af`](https://github.com/kunlong-luo/lanveriq/commit/97dcb2afd66464a83efd822d4210cfd5209e7d26) 的 [PG18 九套集成 #43575342539](https://github.com/kunlong-luo/lanveriq/actions/runs/43575342539) **SUCCESS**（`integrationTest` 和 JUnit evidence checker 均通过），[RD-2 同 SHA #43575342530](https://github.com/kunlong-luo/lanveriq/actions/runs/43575342530) **SUCCESS**。浏览器 L1 又加强为登录后重新颁发的真实 Session CSRF Token，最新 [Java L1 #43575465589](https://github.com/kunlong-luo/lanveriq/actions/runs/43575465589)（`329041c1`）**SUCCESS**；不得用 PG18 `97dcb2af` 的 PASS 冒充新提交的 L1 结果。A11-C 之前的 [四项同 SHA `7e4ad3b1`](https://github.com/kunlong-luo/lanveriq/commit/7e4ad3b18f4a7d5d731152e074f3dea5c2a8aa2c) Java L1/PG18/编译/RD-2 **全部 SUCCESS**。
- **下一最小切片：** 必须先设计用户注册/恢复和唯一用户句柄绑定、JPA/Flyway PostgreSQL credential repository、真实 WebAuthn 原生验证的严格 `userVerification=required`、经 HTTPS Console RP ID/Origin 绑定、服务器挑战单次消耗/撤权、凭证签名计数器与备份语义，再单独将通过的审核人 Passkey 证明绑定现有 A11-C 请求+设备+会话+单次 proof。不得使用 Spring 公开示例的内存默认凭证库、无验证的登录状态或新环境变量跳过验证。不开放生产审批签发。
- **上线判定未变化：** Issue #5 OPEN，v0.1 NOT RELEASE READY；Pop!_OS24 L3、独立租户授权、审核密钥与 CA、可信时间、撤权传播均未实现；Agent 联网/采集/自动恢复/远控默认 OFF。

## 2026-11-14 A11-C — 审核人 Passkey 二次认证凭证边界（生产强拒绝，CI 待验收）

- **已落地代码（main `5fbe22fc`）：** `FirstPinReviewStepUpVerifier` 定义绑定审核人、设备、review request、会话版本和 `first_pin.review` 操作的独立证据接口。**唯一生产实现 `DenyAllFirstPinReviewStepUpVerifier` 固定拒绝**；不会将现有 Spring Security 密码登录、请求头、客户端布尔 MFA 或服务端环境开关变成可信二次认证。内部 `FirstPinReviewLedgerService` 在数据库持锁复验当前人员/范围后才接受验证器返回的精确绑定、`webauthn`、时间窗口与唯一证据 ID，并原子记投票＋访问审计。没有生产 WebAuthn/IdP/MFA、没有审核 HTTP API，服务本身仍默认 OFF。
- **PG18 V15：** 新审核投票必须有独立 `step_up_proof_id` 唯一索引、服务验证的时间和 `webauthn` 方法；`NOT VALID` 约束保留历史 V14 不具 MFA 证明的记录但禁止新票据绕过；**不能追认历史记录**。数据库时间仅相对有效期检查，非独立可信时间。V9 历史升级验收同步为 V10–V15 六次迁移，不删原有负向断言。
- **验收证据：** L1 默认拒绝单测，PG18 第九套新增“错身份/请求/设备/会话、无验证、时间失效、错误验证方式、proof 重放、原生 SQL 无证据投票”负向共四项，强制验证共十项。候选 [PostgreSQL18 L2 #43573481713](https://github.com/kunlong-luo/lanveriq/actions/runs/43573481713) **待最终结果核实，不可宣布通过**；同源码 [RD-2 #43573481597](https://github.com/kunlong-luo/lanveriq/actions/runs/43573481597) 已 SUCCESS。只有两项 L2+Junit 终态都 SUCCESS 才可记本阶段验收。
- **V14 历史审核防追认补丁：** [最终候选 `dd724b82`](https://github.com/kunlong-luo/lanveriq/commit/dd724b82aba5329f5f716b14c000f26235d1a7dd) 在第二审前强制检测第一审已保存的 V15 证明 ID/时间/方式。原 V14 首审缺 MFA 证明的旧票据必须拒绝，新增 L1 单测（不能把新二审附在旧首审上完成授权）。候选 [PG18 L2 #43573608284](https://github.com/kunlong-luo/lanveriq/actions/runs/43573608284) 和 [Server L1 #43573616690](https://github.com/kunlong-luo/lanveriq/actions/runs/43573616690) 状态等待终态核实，不能提前宣布 PASS；[RD-2 同 SHA #43573616715](https://github.com/kunlong-luo/lanveriq/actions/runs/43573616715) 已 SUCCESS。
- **最新负向测试源码：** [7e4ad3b1](https://github.com/kunlong-luo/lanveriq/commit/7e4ad3b18f4a7d5d731152e074f3dea5c2a8aa2c) 追加拒绝与当前审核请求不一致的设备及错误 `first_pin.issue` 高危动作；对应 [PG18 #43573695132](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695132)、[Java L1 #43573695114](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695114) 尚待终态验收；[RD-2 同 SHA #43573695190](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695190) SUCCESS。此前 [V15 基础 PG18 #43573481713](https://github.com/kunlong-luo/lanveriq/actions/runs/43573481713) 已 SUCCESS，但不能证明后续历史拒绝补丁和新增负例。
- **仍然阻断上线：** 真正 WebAuthn/IdP MFA 设施、挑战和 credential 生命周期、独立 tenant 隔离、撤权签名 grant、KMS/HSM 密钥托管、可信时间、正式 CA、设备端授权安装与 Pop!_OS24 L3，均未完成。生产联网/采集/自动恢复/RD-2 OFF，Issue #5 OPEN，v0.1 NOT RELEASE READY。

## 2026-11-14 A11-B — PostgreSQL 18 双审核不可变账本（隔离 L2 SUCCESS，默认 OFF）

- **最新实现：** A11 独立文档与 [`a512ea2f`](https://github.com/kunlong-luo/lanveriq/commit/a512ea2fb91fe662e7ef14e20f0475aceebebd3b) 源码里程碑，Flyway V14 提供唯一证书代际请求、申请人及 session version、第一/第二个不同审核人的不可变记录、审核撤销占位和防跳步/自审/重复审批触发器。`FirstPinReviewLedgerService` Spring/JPA 内部事务同时要求设备当前有效证书、当前设备组 owner 权限、审核人账户未禁用和 session version 未改变，审核与普通管理员访问日志同事务提交；审计丢失触发回滚。
- **默认隔离与权限不足：** 仅当 `lanveriq.first-pin-review.ledger-enabled=true` 才有内部 Bean；**没有** HTTP 审核路由、KMS/CA/签名、Agent 调用、自动恢复或证书注册接线。成功结果明确为 `TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN`，不可当发行许可。当前没有独立租户模型与正式 MFA/重认证、签发信任根、可信时间；`device_group_id` 不是跨组织租户隔离的充分证明。
- **L2 精确验证：** 首次 [#43570485736 — FAILED](https://github.com/kunlong-luo/lanveriq/actions/runs/43570485736) 运行 77 项，5 项新 A11 测试因多个夹具共用固定模拟证书指纹违反全局唯一键而失败，1 项 V9 历史升级测试仍断言 V10–V13 四次迁移。已修复为每份独立指纹及 V10–V14 五次迁移，并给里程碑工作流覆盖历史升级路径。最终代码 [`34a50aaf`](https://github.com/kunlong-luo/lanveriq/commit/34a50aafdc397d709535b0c3c28493d0f89524fb) 的 [真实 PG18.6/Testcontainers 九套与强制 JUnit evidence #43571716873 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43571716873)：两项 CI 步骤均成功、九套无失败/错误/跳过，第九套包含双审核、自审/错组、撤权/会话变化、审计回滚、不可变约束和并发胜者六项测试。[RD-2 同 SHA #43571716881 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43571716881)。早期 [Java L1 #43568599438 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43568599438)，但没有生产 MFA、独立租户、多因素再认证、独立签名根、CA 或实际 Pop!_OS L3；**不能**将 A11-B L2 等同 A11 完整上线批准。
- **发布：** Batch A Issue #5 OPEN，v0.1 NOT RELEASE READY，生产联网/采集/自动恢复/RD-2 全部默认关闭。

## 2026-11-14 A11-A — 内部管理员双人审核策略预判（Java L1 SUCCESS）

- **仅源码、非生产授权：** [受测候选 `b681f872`](https://github.com/kunlong-luo/lanveriq/commit/b681f8726c656bb956b534c32d3a3dc6dc1e3664) 在 Spring 服务端新增 `FirstPinReviewDryRunGate` 和负向 JUnit，并新增 `AdminAction.REVIEW_FIRST_PIN`。Gate **没有** Spring Bean/HTTP 入口、签名操作、数据库审核状态写入、CA 接入或 Agent 执行权。并且 `APPROVED_FOR_SIGNING` 仅为**只读 dry-run**，不能当签名许可。
- **现有实现的真实边界：** Gate 从 Spring Security 读取审核人 `AdminPrincipal`，对当前设备组重新读取两个审核人的权限；合成 Snapshot/State 检查租户、设备、组、申请人、独立审核人、审核记录、策略版本和状态代际，以及 MFA/重新认证/撤销状态。这里的 State **尚未连接可信数据库或 IdP**，仅可作代码策略负向验证。不能凭输入布尔值赋权。
- **CI：** [RD-2 default-OFF #43568599424](https://github.com/kunlong-luo/lanveriq/actions/runs/43568599424) 已在 `b681f872` SUCCESS；[Java L1 #43568599438](https://github.com/kunlong-luo/lanveriq/actions/runs/43568599438) 已完成 **SUCCESS**（精确源 SHA `b681f872`）。L2/Pop!_OS L3 均未为 A11-A 运行。
- **下一步：** 可信持久化审核状态与事务期 RBAC/ABAC 二次验证、实际管理员 MFA/会话再认证、身份与凭证撤销/签发隔离、可信时间、Root-of-Trust/KMS 与 Pop!_OS 管理员授权执行。v0.1 NOT RELEASE READY，Issue #5 OPEN，生产联网/采集/自动恢复/RD-2 OFF。

## 2026-11-14 A11 trusted-administrator review/issuance — 初始设计记录（后续源码状态见上文）

- **已写入验收合同：** [A11 管理员审核/信任根/撤销/可信时间/Agent 授权边界](../development/batch-a11-trusted-administrator-review-issuance.md)（设计提交 [`4bb1e319`](https://github.com/kunlong-luo/lanveriq/commit/4bb1e31989741b79aef0d4c380814ab5af9c43bd)）。
- **下一项最小代码切片：** 在生产签发/联网/Agent 调用保持不可用的前提下，先实现服务端无签名无写库的审核身份与组织/设备权限策略、职责分离和负向单测；随后单独评审 v2 域隔离签名、密钥硬件托管、正式 CA、撤权/时间与 Linux 本地安装者权限。
- **不能混淆：** 旧 Ed25519 `first-pin-review/v1`、A10 本地 reviewer key 与调用者提供的 `observed`/`now_unix` **都不构成**组织管理者的真实授权、可信 TLS 来源或可信时间证明；旧 pin/pending 不能追认。Issue #5 OPEN，真实 Pop!_OS L3 未批准，v0.1 NOT RELEASE READY。生产联网/采集/自动恢复/远控继续 OFF。

## 2026-11-14 A10 — SQLite 首次证书 pin 与审核 review_id 原子消耗，Linux L1/L2 同 SHA SUCCESS

- **代码与真实 CI：** [`42a3a12f`](https://github.com/kunlong-luo/lanveriq/commit/42a3a12f1654834fbef138b049d7ffe870330728)，[Rustfmt/Cargo 所有单测/Clippy #43543552496](https://github.com/kunlong-luo/lanveriq/actions/runs/43543552496)、[Linux 双跳 TLS1.3 Rust→Caddy→Tomcat→PG18 和 PG18 Testcontainers 八组事务 #43543552477](https://github.com/kunlong-luo/lanveriq/actions/runs/43543552477)、[RD-2 默认拒绝 #43543552507](https://github.com/kunlong-luo/lanveriq/actions/runs/43543552507)：**全部 SUCCESS，三个作业均为同一最终代码 SHA**。
- **SQLx V9 和代码：** 增加唯一 `review_id` 的 `heartbeat_first_pin_review_usage`，记录设备、证书 epoch、DER SHA256 指纹、审核公钥/签名摘要与消费时间；禁止改写或删除，`heartbeat_first_pin_review_seen` 发现历史消耗记录被破坏。触发器禁止没有对应已消费审核记录的证书 pin 直接插入；旧 `initialize_heartbeat_credential_pin` 接口固定拒绝。唯一离线审核事务 `install_first_pin_with_offline_review` 先验证已有设备绑定审核公钥、有效 Ed25519 签名、时间窗及完整匹配的当前证书代际和从未发生的心跳历史，再在**同一个 SQLite 事务**写入审核消费、首次证书 pin、两项 seen 标记。失败时全部回滚，成功后跨重启重放/换审核编号不能再次绑定。故障注入在审核写入后拒绝 pin，证实消耗编号也被回滚，不出现部分提交。
- **真实网络验收：** 日志 `PASS: real TLS-bound epoch, immutable local reviewer key, atomic SQLite first pin + unique review-ID consumption, replay denied, old ACK quarantine held.`。使用一次性 GitHub Linux Runner 临时密钥，在独立的**全新 SQLite 文件**完成首次 pin；已有 ACK 丢失 checkpoint 的另一个 SQLite 文件仍强制拒绝追认/复用 UUID。
- **未完成的发布门槛：** SQLite 固定公钥并**不等于**组织批准信任根；本地 API 的 `observed` 字段仍由独立调用方供应，不能凭任意伪造值声称完成 mTLS 证明。受授权审核人签发、身份审计、操作系统信任根托管/撤销、安全时钟、已有旧 pin 迁移策略、正式 Linux Agent 首次安装/调度和真实 Pop!_OS24 L3 均未完成。不能回填旧审核或解开旧隔离，不能启用生产联网/采集/远控。Issue #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-14 A9 — 审核公钥的 SQLite 不可变绑定与当前 mTLS 代际签名验证，L1/L2 同 SHA SUCCESS

- **源码及证据：** [`c6a513d`](https://github.com/kunlong-luo/lanveriq/commit/c6a513d0565a7943cca2eb2fb43d17823ca0b16a)、[Rustfmt/Cargo 全量测试/Clippy #43540816853](https://github.com/kunlong-luo/lanveriq/actions/runs/43540816853)、[PG18 Testcontainers 八组事务与真实 Rust→Caddy→Tomcat 双跳 TLS1.3 + PG18 #43540816930](https://github.com/kunlong-luo/lanveriq/actions/runs/43540816930)、[RD-2 默认 OFF #43540816881](https://github.com/kunlong-luo/lanveriq/actions/runs/43540816881) **全部 SUCCESS，均为同一代码 SHA**。
- **实现：** SQLx V8 不可变设备级审核 Ed25519 **公钥**（仅 32 字节、不含签名私钥），禁止 UPDATE/DELETE，并用 `heartbeat_reviewer_public_key_seen` 检测“曾固定过但记录不见了”；仅在从无心跳历史和本地 credential pin 的**显式离线**调用时初始化。新 `inspect_offline_review_against_local_key` 从 SQLite 取固定审核公钥并复查，再验证 A8 的签名；不信任签名凭据/HTTP 声称的公钥，不安装 credential pin、不消费审核凭据、不产生 ACK、不解隔离。
- **真实隔离日志：** `PASS: real TLS-bound epoch and immutable SQLite reviewer key pin verify detached Ed25519 review; tampered review denied and ACK quarantine unchanged.` 已在真实 Rust TLS1.3→Caddy→Tomcat→PG18 合成链中出现；离线文件 SQLite 测试覆盖重启、重复固定/换钥、毁坏记录、旧心跳不能补绑及跨设备拒绝。
- **边界与后续：** 初次写入 SQLite 的公钥**不能自动证明管理员身份或授权**，现阶段没有正式 CA/审核人公钥托管、证书签发审批与审计、公钥安全轮换/撤销。审核编号 `review_id` 的原子防重放消费必须与后续受授权首次 credential pin 安装同一 SQLite 事务完成，不得单独“消耗签名”或允许旧 pending 心跳自动转移。Pop!_OS24 实机 L3、正式上线、生产联网/采集/远控仍不批准；Issue #5 OPEN，v0.1 NOT RELEASE READY。

## 2026-11-13 A8 — Ed25519 first-pin detached review, L1/L2 same-SHA SUCCESS

- Implementation: [8f94cc28](https://github.com/kunlong-luo/lanveriq/commit/8f94cc2877509f21723d4cba8d6ae4d5bc5bbff9), Rust L1 [#43428246769](https://github.com/kunlong-luo/lanveriq/actions/runs/43428246769), RD-2 [#43428246783](https://github.com/kunlong-luo/lanveriq/actions/runs/43428246783), real Linux double TLS1.3+PG18 [#43428246806](https://github.com/kunlong-luo/lanveriq/actions/runs/43428246806): ALL SUCCESS on exact same SHA.
- The real CI job logs explicitly state: PASS: real TLS-bound epoch and synthetic Ed25519 detached review match; tampered review denied and durable ACK quarantine unchanged.
- Locked ring/hex libraries verify domain-separated detached signature on device, server epoch UUIDv7, actual TLS certificate DER SHA256, review UUIDv7 and short lifetime. Negative tests reject tampered reviews, wrong reviewer key, cross-device, wrong epoch/fingerprint, time windows and unknown fields. The reviewer's key is generated only in disposable synthetic CI memory. No production key material.
- **Critical boundary:** SignatureValidForSuppliedKeyOnly is diagnostic for a supplied public key, NOT an operational administrator authorization or automatically trusted key. No first-pin write/rotation protocol, signed reviewer issuance workflow, trust-anchor custody, audit, anti-replay consumption or runtime Agent enablement has been approved. Previous SQLite quarantine/ACK remains unchanged. Real Pop!_OS L3, authorized users, production release remain OPEN.

## 2026-11-13 A7：当前证书代际与 SQLite 不可变 pin 的只读预检，L1/L2 PASS

- **最终单测源码：** [`53ffad0f`](https://github.com/kunlong-luo/lanveriq/commit/53ffad0fd276e569d419b917d009e6f85042232d)，[Rust L1 #43426057069](https://github.com/kunlong-luo/lanveriq/actions/runs/43426057069) **SUCCESS**（Rustfmt、完整 Cargo tests、Clippy）、[RD-2 #43426057030](https://github.com/kunlong-luo/lanveriq/actions/runs/43426057030) **SUCCESS**。实际网络/PG18 L2 在功能一致的源码 SHA [`9a39345e`](https://github.com/kunlong-luo/lanveriq/commit/9a39345e0ecd48bfe6afec66bdafdf0e49c78444) 上运行，[Linux Batch A #43425686892](https://github.com/kunlong-luo/lanveriq/actions/runs/43425686892) 两项真实隔离作业 **SUCCESS**；最后变动只修复离线测试用非法首次 quarantine 状态创建的问题，**不可冒称是相同 SHA 的 L2**。
- **实现：** 显式只读 `inspect_explicit_heartbeat_epoch_preflight` 校验本地不可变 pin、已有 heartbeat checkpoint/generation，调用 Rust TLS1.3 当前认证叶证书指纹驱动的 GET 后再校验持久状态。任何并发 pin 首次写入或 checkpoint generation 改变都返回 `StaleDuringLookup`，不会采用过时查询结果。未绑 pin 返回 `UnboundNeedsReview`；已绑相同只返回 `MatchingLocalPinOnly`，错代际/指纹为 `MismatchStayHalted`，401/403/404/409/5xx 继续保持暂停/未决/隔离，不得自动确认历史 POST 或重发。
- **验收：** 真实 CI 日志直接证明 Rust→Caddy→Tomcat 双跳 mTLS GET→PG18，且 SQLite 中 ACK 丢失的 pending、quarantine、generation 不变；离线 SQLx 文件库覆盖并发旧 checkpoint、并发初次 pin、旧状态/撤权/错误 UUIDv4 拒绝。未修改实际 Agent 服务、生产开关或后台调度；仍未获得证书运营审批/首次 pin 安装协议，不能上线/进行 L3 Pop!_OS 声明。

## 2026-11-13 A6：Rust 本地叶证书指纹驱动的 mTLS 证书代际只读联调，L1/L2 PASS

- **代码：** [`0468bc43`](https://github.com/kunlong-luo/lanveriq/commit/0468bc43a216e6d2f0294efc237c1d9f36180b34)、最终受测 [`28046a00`](https://github.com/kunlong-luo/lanveriq/commit/28046a00a19793d0ba879976dfe064ff7e669b6b)。仅修改 GitHub `main`，不创建常驻 Agent 任务。
- **已过 CI：** [Rustfmt/Cargo 单测/Clippy #43423162146](https://github.com/kunlong-luo/lanveriq/actions/runs/43423162146) SUCCESS、[RD-2 fail-closed #43423162205](https://github.com/kunlong-luo/lanveriq/actions/runs/43423162205) SUCCESS。[Linux Batch A #43423162368](https://github.com/kunlong-luo/lanveriq/actions/runs/43423162368) 的 PG18 Testcontainers 八组事务 + 真实 Rust→Caddy→Spring/Tomcat 双跳 TLS1.3/PG18 两项作业 SUCCESS，最终 CI 作业日志确证 `PASS: real Rust TLS1.3 read-only credential epoch matches the locally loaded DER leaf, with unchanged SQLite ACK quarantine.`。
- **实现：** 直接使用已锁定的 Rustls `rustls-pki-types 1.15.1` PEM→DER 解析器计算传给 Reqwest 的本地 mTLS 证书叶 SHA-256；新 `AgentTlsTransport.observe_current_credential_epoch` 只接收设备 UUID，固定 Agent HTTPS、TLS1.3、无代理/重定向，4 KiB 响应限制，严格解析匹配服务器当前有效证书与 UUIDv7 代际，无外部可注入的“预期指纹”。真实 Rust 单次合成探针在心跳已提交但本地 ACK 丢失后读取代际，仍保持 pending UUID、generation、隔离状态和本地 pin 未安装。错设备/未注册/过期/吊销、重放与未知查账负例继续通过；生产 HTTP GET 仍受独立三开关 default-OFF。
- **边界：** 成功观察**不是**由服务端签名的独立凭证/运营批准、旧 POST ACK、数据库删除许可、SQLite pin 首次安装授权或证书轮换协议。本轮不执行真实采集、真实设备网络、自动心跳或远控；不能因 Ubuntu 24.04 L2 当作 Pop!_OS 24 L3 或正式发布。Issue #5 保持开放，v0.1 NOT RELEASE READY。

## 2026-11-13 A5：三重默认拒绝的当前证书代际只读 GET，隔离 L1/L2 PASS

- **精确源码：** [`b9e4efc2`](https://github.com/kunlong-luo/lanveriq/commit/b9e4efc279df35e0b73e1f5f01864ceefbd500d6)。[Linux Batch A #43417739656](https://github.com/kunlong-luo/lanveriq/actions/runs/43417739656) 的 PG18.6 Testcontainers 八组事务与真实隔离 Rust→Caddy→Spring/Tomcat 双跳 TLS1.3 两项作业 **SUCCESS**；[Java L1 #43417739502](https://github.com/kunlong-luo/lanveriq/actions/runs/43417739502)、[PG 编译 #43417739729](https://github.com/kunlong-luo/lanveriq/actions/runs/43417739729)、[RD-2 默认 OFF #43417739717](https://github.com/kunlong-luo/lanveriq/actions/runs/43417739717) **SUCCESS**。
- **实现：** 新增 Spring `AgentCredentialEpochRestController` 的 `GET /api/v1/devices/{deviceId}/heartbeats/credential-epoch`。除现有上游 mTLS 断言与心跳 HTTP 两开关外，`lanveriq.agent.credential-epoch.http-enabled` **第三独立默认 OFF 开关**必须显式开启。Caddy 只代理精确候选路径；Spring Security 只允许 GET；入站校验固定 Host、真实双跳证书、有效设备及注册指纹，JPA 当前证书行锁读取 V12 canonical epoch，响应 `Cache-Control: no-store`；不接受客户端提供的 epoch 值，不访问任何操作员密钥、不更新心跳/请求账本。
- **隔离网络负例：** 默认 GET 403；已注册未撤销证书的真实 TLS 200 代际等于 PostgreSQL UUIDv7；同一测试 CA 签发但未注册的叶证书 403；伪造指纹被 Caddy 覆盖；过期和撤销都 403，`last_seen_at`、最新心跳与请求计数前后不变。旧 Rust 真实 ACK 丢失及重放/撤销测试保持成功。
- **尚未实现/未获批准：** 客户端未调用这个新 GET；Rust 只具有先前 A4 严格解析器，尚无已验证的本地 TLS 证书物料→指纹/epoch→SQLite V7 pin 的可信桥接。200 epoch 既不是原 heartbeat ACK，也不是可移植签名授权、换证或解除隔离许可。没有 Agent 常驻联网、采集、远控、Pop!_OS 24.04 实机 L3、生产证书与数据治理批准。Issue #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-13 A4：当前有效证书代际的内部只读查询与 Rust 严格解码，L1/L2 PASS

- **Java/PG18/TLS 受测源码：** [`1f91db49`](https://github.com/kunlong-luo/lanveriq/commit/1f91db49585429bb8ae6edaaa20f7c0ff568ef5f)；[Linux Batch A #43385185651](https://github.com/kunlong-luo/lanveriq/actions/runs/43385185651) 的 PostgreSQL 18 Testcontainers 八组真实迁移/事务及独立 Rust→Caddy→Spring/Tomcat TLS1.3 合成链路两项均 **SUCCESS**，同源码 [Java L1 #43385185594](https://github.com/kunlong-luo/lanveriq/actions/runs/43385185594) 与 [PG 编译 #43385185657](https://github.com/kunlong-luo/lanveriq/actions/runs/43385185657) **SUCCESS**。
- **Rust 最终受测源码：** [`b941a18a`](https://github.com/kunlong-luo/lanveriq/commit/b941a18ae44b3440354dfe79a4e76ffbdc16f83a)；[Agent Rustfmt/全量单测/Clippy #43385582279](https://github.com/kunlong-luo/lanveriq/actions/runs/43385582279) **SUCCESS**，[RD-2 default OFF #43385582281](https://github.com/kunlong-luo/lanveriq/actions/runs/43385582281) **SUCCESS**。中间两次 Rust L1 红灯为新增测试夹具 Rustfmt、默认未启用 `uuid` v4 feature，不涉及新增生产功能，已在最终 SHA 修复且全部重验。
- **实际新增：** `DeviceHeartbeatService.inspectCurrentCredentialEpoch` 是一个**内部事务服务**，经现有 `JpaActiveDeviceCredentialGuard` 按设备→证书锁序检查当前有效的已注册证书，并从 PostgreSQL V12 的不可变 `credential_epoch_id` 取出当前代际；它不会更新 `last_seen_at`、心跳 UUID 账本或解隔离。PG18 负例覆盖错证书、错设备、证书撤销及读取前后状态不变。Rust `classify_credential_epoch_response` 只是内存中的严格解码器：只接受设备 ID、证书 SHA-256 指纹、UUIDv7 代际、服务器 UTC 时间全部匹配且无多余字段的 200；401/403 暂停、404/429/5xx 未决，其他响应隔离，**绝不算原始 POST ACK**。
- **尚未实现/明确禁止：** 没有创建/挂载 HTTP epoch GET 路由；`AgentTlsTransport` 还没有该请求方法；解析器的预期指纹必须由**未来经过实际 TLS 身份验证的客户端上下文**提供，而不是自行信任的字符串。查询结果不是带签名的离线发证/授权令牌，也**未**写入 Agent SQLite V7 pin、未启用 heartbeat sender、未解除已隔离请求、未开启联网/采集/远控。后续须独立评审确切 mTLS GET 路由与 L2 负例、服务端签名/证书匹配和 Pop!_OS 24.04 L3。Batch A #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-13 V13：新心跳 UUID 账本绑定服务端证书代际，隔离 L1 + L2 PASS

- **实际受测源码：** [`ce350bac`](https://github.com/kunlong-luo/lanveriq/commit/ce350bacfbcf1f7413430484a9dc48b73b12af53)。[Linux Batch A #43382446151](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446151) 中 **PG18.6 Testcontainers 八组事务/迁移与独立 Rust CSR/Reqwest→Caddy→Spring/Tomcat 双跳 TLS1.3 两项 L2 作业均 SUCCESS**；[Java 单测 #43382446134](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446134)、[PG 集成源码编译 #43382446098](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446098)、[RD-2 default-OFF #43382446147](https://github.com/kunlong-luo/lanveriq/actions/runs/43382446147) 同源 SUCCESS。
- **已实现：** PostgreSQL V13 在 `device_heartbeat_request_ids` 新增 `credential_epoch_id UUID`。对每条新 INSERT，数据库按 device→credential 锁序确认活跃设备与当前未撤销/未过期证书，从不可变 `device_credentials.credential_epoch_id` 补足代际，拒绝错误指纹和自选错误 epoch（SQLSTATE 23514）。既有 `(device_id,request_id)` 唯一反重放键与 V11 UPDATE 不可变保护不变。V9–V12 历史账本不猜测回填 epoch，`CHECK NOT VALID` 只要求新记录非空；完整数据删除/保留策略仍未批准。
- **明确不代表：** 数据库 SQL 约束不能代替 TLS 身份验证；独立 Caddy mTLS 及 Hibernate/JPA 受控事务原有保护继续必需。V13 **未新增 Agent 签名 epoch 凭证、受审核证书轮换、可信本地 pin 安装、恢复旧 pending 的授权 API、自动重试、定时清理或生产联网**。Agent SQLite V7 仍保持旧发送入口默认拒绝；Pop!_OS 24.04 实机 L3 与隐私/发布审核未完成。Batch A #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-13 SQLite V7：本地证书代际固定保护，L1 已通过

- **实际受测源码：** [`a13a3e7d`](https://github.com/kunlong-luo/lanveriq/commit/a13a3e7dd67f1c327eb4ed9632df68d1ee2306d4)。[Agent Rustfmt / 所有 Cargo 单测 / Clippy #43376633141 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43376633141)；[RD-2 fail-closed #43376633124 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43376633124)。本轮为**实际文件型 SQLite L1**，没有执行新能力的双跳 TLS/PG18 L2，也未在 Pop!_OS 实机 L3 运行。
- SQLx 迁移 `20261113000700_heartbeat_credential_pin.sql` 在 Agent 本地增加唯一固定的 `(device_id,credential_epoch_id,cert_sha256_fingerprint)` 身份及 seen 防丢失标记；SQL 触发器拒绝正常 UPDATE/DELETE。仅可在没有**任何已持久化心跳 checkpoint** 的库中显式首次固定，旧 pending/隔离无法事后归属给新证书。
- 已绑定后，现存未传入可信证书上下文的 `run_explicit_heartbeat_once` 与 `inspect_explicit_heartbeat_reconciliation` **一律拒绝执行**；旧发送器若在 pin 写入前读取过无绑定状态，`save_heartbeat_schedule_checkpoint` 同一 SQLite 写事务仍再次拒绝，从而封闭此竞态。新只读 `inspect_local_heartbeat_credential_continuity` 只输出本地匹配/不匹配/无绑定诊断，**匹配绝非授权证明**。
- **未实现：** 由可信服务端/CA 身份验证到本地 pin 的认证开通协议、携带已验证证书上下文的新版心跳驱动、已获批轮换及旧 pending 人工恢复、硬件密钥托管、真实 Linux systemd 服务联网与 Pop!_OS L3、生产隐私审批。不得将本地输入的指纹/UUID 当 mTLS 或操作员批准，也不得自动换证书、清除 SQLite 隔离、恢复重试或启用采集/远控。Batch A #5 继续 OPEN。

## 2026-11-13 V12 PostgreSQL 证书代际基础：PG18 真实 L2 作业通过

- 每条证书记录的 `credential_epoch_id` 是 PostgreSQL 生成的独立 UUIDv7（含 V9 遗留数据迁移回填），不允许 SQL 客户端指定，也不允许后续改写该身份字段；仍允许证书正常吊销。源码 [`5183ddac`](https://github.com/kunlong-luo/lanveriq/commit/5183ddacfd5b7c5e653b70412f5753c601bb9370)。[PG18 Testcontainers 真实迁移/负例 #43374592095](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592095) 的**数据库作业 SUCCESS**；[Java L1 #43374592206](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592206)、[源码编译 #43374592044](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592044)、[RD-2 default-OFF #43374592077](https://github.com/kunlong-luo/lanveriq/actions/runs/43374592077) SUCCESS。**同一 CI 中真实 Rust CSR/Reqwest→Caddy→Spring/Tomcat TLS1.3 受控作业也已独立 SUCCESS**：#43374592095 两项实际 L2 作业均通过，仍不等于 Pop!_OS L3。
- **严格边界：** 当前 V12 只是数据库内服务端代际身份，不是服务器签名/下发给 Agent 的 epoch 凭据，不允许据此切换私钥、解除 SQLite pending/quarantine、按 TTL 删除 UUID 或恢复生产心跳；Batch A 仍 OPEN。完整安全条件见 [代际/保留策略](../architecture/heartbeat-request-ledger-epoch-retention.md)。

## Batch A V11 账本不可变与代际/保留策略（2026-11-13）

- [`93c24299`](https://github.com/kunlong-luo/lanveriq/commit/93c24299ed031c1c44b6f6158696125d9c97142a) 新增 PostgreSQL V11 `BEFORE UPDATE` 保护，拒绝任何已确认 UUID 或证书指纹归属变更；保留 V9 未绑定旧行及跨证书唯一主键，**没有启用按时限删除、自动轮换或恢复**。
- [Linux Batch A #43371701087](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701087) 的 **PG18 Testcontainers 真实 V9→V11 迁移/反篡改测试作业已成功**；Java 单测 [#43371701100](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701100)、集成测试源编译 [#43371700985](https://github.com/kunlong-luo/lanveriq/actions/runs/43371700985)、RD-2 默认拒绝 [#43371701023](https://github.com/kunlong-luo/lanveriq/actions/runs/43371701023) 均成功。**同运行的真实 Rust CSR/Reqwest→Caddy→Spring/Tomcat TLS1.3/PG18 双跳作业也已 SUCCESS**：两项隔离 L2 均通过，但不是本轮新 V11 的生产流量或真实 Pop!_OS L3。
- **未完成：** 证书生命周期签发/审核与 credential epoch、可安全执行的自动保留期清理、PG 删除权限/灾备回滚、受控操作员恢复、真实 Pop!_OS L3。策略见 [请求账本代际与保留规范](../architecture/heartbeat-request-ledger-epoch-retention.md)。Batch A #5 继续 OPEN。


## 2026-11-13 最新：只读恢复竞态加固 L1 验收成功

- 源码 [`b70b20c2`](https://github.com/kunlong-luo/lanveriq/commit/b70b20c265d45de1aaa53690a05e50949a04bd5f)、实际受测修正 [`cc85897f`](https://github.com/kunlong-luo/lanveriq/commit/cc85897f28949f4938baa69310d61ba44abf9717)。[Rustfmt/Agent 单测/Clippy #43368140495](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140495) SUCCESS；[RD-2 默认拒绝 #43368140522](https://github.com/kunlong-luo/lanveriq/actions/runs/43368140522) SUCCESS。
- 新增 SQLite 同设备 checkpoint 二次核验：查账等待时发生**并发身份/隔离更新或任意 CAS generation 变化**，返回 `StaleDuringLookup`，而不是沿用过期 committed/unknown 观察。两项真实文件型 SQLite 并发负例已通过；查询保持只读，绝不重发或自动清除 quarantine。
- **仅 A3 受控本地检查子项 L1_PASS**，没有声称新检查器通过隔离真实网络 L2，更没有完成运营恢复许可、ledger epoch/retention、A2 PKI/常驻 systemd 及真实 Pop!_OS L3。Batch A 仍 OPEN。

## 最新 Batch A3 安全进展（2026-11-13，单独 L1）

- **只读恢复检查器已实现并通过 L1：** [Agent Rustfmt / 所有 Cargo 单测 / Clippy #43364626478](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626478) SUCCESS（源 SHA [`864297bb`](https://github.com/kunlong-luo/lanveriq/commit/864297bbeb2c5f96191f29526b1afbe4f6eedeb7)）；[RD-2 default OFF #43364626482](https://github.com/kunlong-luo/lanveriq/actions/runs/43364626482) SUCCESS。新增 `heartbeat_reconciliation.rs` 及文件型 SQLite 负向测试：disabled / no pending / identity halt 不发起查账；同设备 pending 查得 committed、unknown、identity rejected、quarantine 和无效响应都**只返回诊断**，不改变持久 SQLite UUID、halt、generation，重启后仍不能发送心跳。已确认核对门槛只读且不会运行真实设备网络。
- **完成度仍是 A3 子项 L1_PASS、Batch A 整体 OPEN。** 没有完成经批准的操作员恢复授权/ledger retention 与 epoch、证书轮换代际验证、Pop!_OS systemd 常驻服务接线、真正设备 L3；切勿把 committed 账本 GET 当首次 POST ACK 或作为自动解除隔离依据。原有 Linux Batch A 网络 L2 证据属于早期不同 SHA，本次新 helper 没有声称单独网络 L2。

## 已通过的实际证据与边界

| 能力 | 实测证据 | 尚不代表 |
| --- | --- | --- |
| Rust CSR/Reqwest→Caddy→Tomcat TLS1.3→PG18 激活/心跳/严格 ACK；降级、Host/SNI、错证书、重放/吊销负例 | [Linux Batch A #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857) SUCCESS，源码 `758f8d9a`；前序连续请求 [#43210930135](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930135) | 真实 Pop!_OS/systemd、生产 CA/证书签发、真实联网 |
| PostgreSQL 18 集成事务和 token→CSR→审核测试源 | 同一 [Batch A #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857) 的 PG18 作业；[集成源编译 #43356545891](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545891) SUCCESS | 源码编译本身不证明数据库运行，也不证明生产发行/轮换 |
| SQLite ACK 丢失 quarantine / V10 绑定提交证书 / V9→V10 迁移 | [#43351181483](https://github.com/kunlong-luo/lanveriq/actions/runs/43351181483)、[#43352639007](https://github.com/kunlong-luo/lanveriq/actions/runs/43352639007)、[#43353817134](https://github.com/kunlong-luo/lanveriq/actions/runs/43353817134) SUCCESS | 安全解除 quarantine、请求 ID ledger retention/epoch、跨证书代际恢复；**404/429/5xx 均不证明未提交，不可凭此更换 UUID 重试** |
| Java 单测/RD-2 发布保护 | [#43356545809](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545809)、[#43356545805](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545805) SUCCESS | RustDesk 底层实际会话 ACL 和生产授权 |
| Next.js B+C UI 与历史合成 Chromium | [Next.js README](../../apps/web-next/README.md)、[合成浏览器 #42335190613](https://github.com/kunlong-luo/lanveriq/actions/runs/42335190613) | 正式 `/login` 仍是 LoginPreview，devices/monitoring 仍 `unconnected`；合成 staged 受保护读组件并未挂到正式页面 |

## A–E 任务差距、验收门槛

| 批次 | 当前状态 | 必须补齐的 P0 | 何时才可以关闭 |
| --- | --- | --- | --- |
| **A 身份和心跳** | 核心合成 L2 PASS；整批 OPEN | 经过批准的 token/CSR→审查签发→激活/吊销/轮换、密钥/代际/账本 retention、非 root Linux systemd 调度、受授权 ACK 不确定状态恢复；[Issue #5](https://github.com/kunlong-luo/lanveriq/issues/5) | 完整同身份正负合成验证，真实授权 Pop!_OS 安装/启停与失效/错误/重放/中断安全验证 |
| **B 资产和离线恢复** | 组件部分存在；运行闭环 OPEN | 实际受限采集 CPU/RAM/disk/OS/授权 NIC-IP，快照和调度接入常驻 Agent、PG18 HTTP/ACK、SQLite 离线队列/重启/旧 ACK/缺口 | 真实受限请求链与恢复/故障注入，没有假健康、无新旧快照倒退 |
| **C 管理员 Console** | 页面预览，正式数据未连 | Next.js 正式登录/注销/Session/CSRF、scoped REST/Orval、设备列表/详情/监控、SSR/RSC/Origin/RBAC、撤权清缓存、错误和新鲜度 UI | 实际 Spring + Chromium 的合成 A/B 分组权限和负例；公开 Pages 永远不连业务 |
| **D WSS/事件** | 完整运行闭环 OPEN | 受限 WSS 元数据提示、初始 REST 与重连/静默丢推送/标签恢复重同步、Agent 事件批次 ACK、基础诊断 | 状态在线/迟到/离线正确，断线/乱序/背压/跨组撤权与 A–D 连续业务 E2E 通过 |
| **E Linux 发布候选** | 生产/实机验收 OPEN | Pop!_OS DEB/systemd、安全密钥/证书、Caddy/私有 Node/Spring/PG18 部署、升级回滚、备份恢复、监控/日志、许可、安全/隐私审批 | Linux L1+L2+**真实获授权 Pop!_OS L3** + 恢复演练和独立发布签字；Windows 另行验收 |
| **R RustDesk** | RD-2 BLOCKED/default-OFF | 真正原生画面/输入/停止、不可绕过会话授权/撤销、AGPL/Pro 许可、AUTH-01–16 | 独立真实设备及授权/许可签字后才可解除发布锁 |

## 八个 P0 发布关口

1. G1：获授权的 Pop!_OS 24 非 root Agent 安装、身份/私钥/启停和受限上报。
2. G2：短期注册令牌、CSR、人工审批/签发、证书激活/吊销/轮换/遗失处置。
3. G3：完整心跳/资产/指标到 PG18，SQLite 断网/重启恢复，严格 ACK 与身份代际，未知提交不得重发。
4. G4：真正可登录的 Next.js 管理界面与受限设备/监控 API，撤权与跨组拒绝。
5. G5：授权 WSS/REST 实时状态、静默漏提示纠正、离线/错误/新鲜度语义。
6. G6：经审查的 Linux DEB/systemd、升级、回滚、卸载和制品校验。
7. G7：隔离 Caddy/Next.js/Spring/PG18/CA 部署、备份实际还原、日志与故障响应。
8. G8：真实 OS、最小化收集和合法告知/数据保留、安全/许可评审与发布签字。

## CI、治理与时间

- **L1** 按路径跑 Gradle/Cargo/pnpm、安全负例；**L2** 一次性 PG18/Caddy/Tomcat/Rust/Chromium 真实合成服务及错误场景；**L3** 授权真实 Pop!_OS 24、实际网络/证书/升级/恢复/浏览器/隐私。单测 PASS 或 L2 成功不可标为生产上线。
- 实际 [Linux Batch A workflow](../../.github/workflows/linux-batch-a-caddy-pg18-acceptance.yml) **仍对匹配的 `main` push 自动运行重型 L2**，也支持手动触发；与“重型全部 tag/dispatch”策略尚有差距。本轮仅修正文档，不修改 CI 触发器。
- [GitHub Pages workflow](../../.github/workflows/pages-static-preview.yml) 当前只有 `acceptance/v0.1-*` 标签或 `workflow_dispatch` 触发，只发布断开的静态界面，不是生产控制台。
- 历史旧功能分支/Draft PR 须先与 `main` 比对再清理；正式 GitHub Release 和分支规则/签名产物尚需建立。不会为本次文档更新合并/删除未检验代码。
- 2026-11-08 的“4–8 周合成闭环/2–4 个月试点”是历史计划区间，不是上线承诺。是否能上线取决于 G1–G8 的实测证据。

关联：[A–E 原批准计划](v0.1-batched-delivery-plan-2026-11-08.md) · [Roadmap](roadmap.md) · [DOD](definition-of-done.md) · [发布](../operations/release.md) · [Pop!_OS OS 验收](../testing/linux-popos24-agent-acceptance.md) · [人工验收](../testing/manual-acceptance.md)。
