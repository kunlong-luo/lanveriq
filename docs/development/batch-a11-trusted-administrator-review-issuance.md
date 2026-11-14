# Batch A11 — 可信管理员审核与签发安全设计（实现前验收合同）

> 状态：A11-A/B/C 与 D1 默认拒绝合成隔离验收已完成；A11-D2-A V16 Passkey JPA 持久化基础待验收；真正 MFA/Passkey step-up、可信签发仍 NOT IMPLEMENTED / NOT RELEASE READY；2026-11-14。仅针对 **Pop!_OS 24.04 Linux-first** 首发路线。本文不批准启动生产 Agent、网络/采集/自动恢复/远控，不批准生成真实 CA/审核私钥或生产注册令牌。所有新增实现先在 `main` 上以合成环境、默认拒绝、负向测试先行推进。
>
> 来源：[上线差距账本](../planning/release-readiness-2026-11-13.md)、[Batch A #5](https://github.com/kunlong-luo/lanveriq/issues/5)、[A10 受测源码 42a3a12f](https://github.com/kunlong-luo/lanveriq/commit/42a3a12f1654834fbef138b049d7ffe870330728)。

## 2026-11-14 A11-D2-A — PostgreSQL 18 Passkey 身份/公钥不可变存储（非可用认证）

- **已提交源码：** Flyway [V16](../../apps/server/src/main/resources/db/migration/V16__stage_administrator_passkey_credential_storage.sql) 新增 `admin_passkey_user_handles`：将 `users.id` 唯一映射到 32 字节、不含登录名或 UUID 明文的 opaque WebAuthn `user.id`；默认**没有历史回填**、没有可用注册或身份申请 API。新增 `admin_passkey_credentials`，要求 credential ID 全局唯一、owner 绑定已有句柄、RP ID/COSE 公钥、计数器、backup 状态和成对吊销标记。数据库触发器禁止身份/公钥/RP 偷换、计数器/验证时间回退、凭证吊销逆转、句柄改写以及历史凭证物理删除；不对有数据库超级用户或修改脚本权限的主体宣称密码学防篡改。
- **JPA 阶段：** `AdminPasskeyUserHandle`/`AdminPasskeyCredential` 仅是 `@Immutable` 只读 Hibernate Entity，`AdminPasskeyUserHandleRepository`/`AdminPasskeyCredentialRepository` 使用 Spring Data JPA 而不是手写应用 JDBC。**尚未**实现官方 `PublicKeyCredentialUserEntityRepository` 和 `UserCredentialRepository` 适配或生产 `Webauthn4JRelyingPartyOperations`；这些数据库行只有库存语义，绝非已注册的可信 Passkey，也不能用于登录、签发或审核。生产 `DenyAllFirstPinReviewStepUpVerifier` 继续固定拒绝。
- **真实数据库负向门禁：** 原有 `PostgreSqlConsoleSecurityWiringIT` 新增三项 PG18 测试：32-byte handle 及唯一约束、credential ID 全球唯一/跨管理员转移与公钥改写拒绝、签名计数器下降/备份状态违约/撤销逆转拒绝；通过 JPA 真正读取库存，失败更新必须完整回滚。V9 旧数据库升级迁移期望同步为 V10–V16 共七次，严格 JUnit evidence checker 在九套中要求新增三项（不准静默漏跑）。
- **范围限制：** 当前没有 WebAuthn 密码学 assertion/attestation 验证、注册挑战或消费、凭证 lifecycle 业务流程、RP ID/Origin 生产配置、用户验证 UV 要求、签名计数器基于真实认证器的更新、跨设备备份策略和实际管理员 L3。未来所有写入必须由正式 Passkey 验证器经安全审查后专门启用；**绝不能把测试写库 helpers 暴露为 API/管理员自助注册入口。**
- **验收未预先宣告：** [V16 源码候选 `1f611749`](https://github.com/kunlong-luo/lanveriq/commit/1f611749f9c92f196f85ec8e91b5bfd9d3456ac8)；[真实 PG18 L2 #43578226960](https://github.com/kunlong-luo/lanveriq/actions/runs/43578226960) 正在排队/运行，结果待确认。任何 Flyway/JPA validation/testcase 失败均要修复，不得关掉数据完整性约束。此阶段不解除 Issue #5、v0.1 NOT RELEASE READY；Agent 联网、采集、自动恢复、远控、真实证书签发仍 OFF。

## 2026-11-14 A11-D1 — 官方 Spring Security WebAuthn 引擎预装（端点仍不可用）

- **成熟组件选型：** 在 Spring Boot 4.1.2 / Java 25 / Gradle 技术栈直接采用 Spring Security 官方 `org.springframework.security:spring-security-webauthn`（由 Spring Boot BOM 管理版本），复用其 WebAuthn4J 底层验证；不自行实现 FIDO/WebAuthn 签名解析、CBOR、COSE 或自定义 JWT 作为 Passkey 代替品。参考官方文档 https://docs.spring.io/spring-security/reference/servlet/authentication/passkeys.html 。Yubico 2.10.1 可作独立选型对比，但不应在现有 Spring Security 中无理由叠加第二套认证流程。
- **仅为依赖预装：** 新增 Gradle 依赖，**没有**调用 `HttpSecurity.webAuthn(...)`，没有 WebAuthn Passkey 注册、验证或登录 Controller，没有改变 Console 密码/CSRF/session 路由，也不开放 `/webauthn/register/options`、`/webauthn/register`、`/webauthn/authenticate/options`、`/login/webauthn` 或 `/api/v1/auth/webauthn/step-up`。正式管理员审核验证器仍只有 hard-deny 实现，未建立成功凭证生成途径；既有二人审核、CA/Agent/远控开关全部保持原样。
- **安全测试：** `ConsoleSessionBrowserFlowWebMvcTest` 用正确 CSRF、Origin/Host 和登录会话负向检查目标 URI；`PostgreSqlConsoleSecurityWiringIT.fullBootPostgresNeverAutoMountsPasskeyRoutesFromDependency` 使用真实 Spring Boot 组件扫描/PG18/Servlet SecurityFilterChain 验证即使已安装依赖，端点也无法绕过默认拒绝。严格九套 PG18 JUnit evidence checker **新增强制案例**，不允许测试遗漏。此时只证明“未意外开放端点”，**不证明**真正 Passkey 密码学验证、用户验证 UV 或设备绑定。
- **实际 Passkey 上线前门槛：** 先设计 `users.id` 对应的永久用户句柄与可信注册/注销、PostgreSQL/Flyway 持久化的 `PublicKeyCredentialUserEntityRepository` 与 `UserCredentialRepository` 适配（**不得**使用 Spring Security 的 in-memory 默认实现，也不得为迎合源码风格写裸 JDBC），锁定唯一 credential ID 与当前授权用户/设备组、签名计数器/多设备备份语义、真实 RP ID 和单一 Console HTTPS origin、严格 `userVerification=required`、挑战的服务器所有权及原子一次消费、CSRF/Origin/Host/重放/撤权/跨用户负向测试。成功的登录 Passkey 验证仍然不等于审核动作 step-up：还必须绑定审核请求/设备/会话版本/时间和单次 proof，且在数据库锁定事务中验证角色权限。生产密钥不写代码、CI 或 Agent。
- **验收红灯→绿灯证据：** 初轮 [Java L1 #43575174776](https://github.com/kunlong-luo/lanveriq/actions/runs/43575174776) **FAILED**：测试类已有 `csrf(session)` 与静态测试帮助器 `csrf()` 同名，导致测试源码编译失败；已显式调用 Spring Security 测试帮助器。初轮 [PG18 L2 #43575168492](https://github.com/kunlong-luo/lanveriq/actions/runs/43575168492) **FAILED**：匿名不可用路由返回的授权 401 并非统一 403，已将匿名测试按所有 4xx 拒绝、已认证管理员仍必须拒绝分开测试。修复后 [源码 `97dcb2af`](https://github.com/kunlong-luo/lanveriq/commit/97dcb2afd66464a83efd822d4210cfd5209e7d26) 对应 [真实 PG18 九套 #43575342539 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43575342539)：`integrationTest` 与严格 JUnit 证据门禁均 SUCCESS，[同 SHA RD-2 #43575342530](https://github.com/kunlong-luo/lanveriq/actions/runs/43575342530) SUCCESS。后续浏览器单测加强为登录轮换后的真实 Session CSRF Token，最新 [L1 #43575465589](https://github.com/kunlong-luo/lanveriq/actions/runs/43575465589) 精确源码 `329041c1` **SUCCESS**，不可用旧 SHA 代替验收。此前 A11-C [精确 SHA `7e4ad3b1`](https://github.com/kunlong-luo/lanveriq/commit/7e4ad3b18f4a7d5d731152e074f3dea5c2a8aa2c) 的 [Java L1 #43573695114](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695114)、[PG18 L2 #43573695132](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695132)、[PG 源码编译 #43573695168](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695168)、[RD-2 #43573695190](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695190) **同 SHA 均 SUCCESS**。这只关闭 A11-C 合成隔离切片，不等于 A11-D 真正 Passkey 生产完成。

## 2026-11-14 A11-C — 审核人二次认证可信证明边界（源码已提交，L1/L2 待验收）

- **安全实情：** 当前 Console Spring Security 提供密码登录与会话版本撤销，**尚未实现真实 Passkey/WebAuthn 或企业 IdP MFA**。不能把用户提交的布尔 `mfa_verified`、客户端时间、普通登录或持有者角色当高风险审核通过的凭证。现有 `@ConditionalOnProperty(... ledger-enabled=true)` 的内部账本仍默认未装配。
- **本次最小切片：** `FirstPinReviewStepUpVerifier` 定义要求独立验证器绑定 `actorId + requestId + deviceId + sessionVersion + first_pin.review` 的身份核验契约；唯一**生产**实现 `DenyAllFirstPinReviewStepUpVerifier` 直接拒绝所有请求，**没有**可用 WebAuthn 验证器、签名或 HTTP API。新增 V15 `first_pin_review_approvals.step_up_proof_id`（非空新票据、全局唯一）、验证时间和 `webauthn` 方法约束；不强制回填历史 V14 审核票据，这些旧票据均不能升级为生产授权。
- 内部审核事务在锁定当前设备、证书、申请人和两名审核人当前角色/会话版本后才向验证器申请证据，拒绝空值、伪造其他管理员或请求/设备/会话/操作、非 `webauthn`、超出 PostgreSQL 相对时间窗口（超过过去两分钟或未来五秒）的票据；一份 `proof_id` 不能绑定两份成功投票。每票必须连同访问审计记录在一个事务中提交，任何失败都回滚。**PostgreSQL 时钟只做测试相对新鲜度，不代表受独立认证的可信时间。**
- 合成 PG18 L2 测试引入仅存在于 `src/integrationTest` 的 `@Primary` 验证器替身，覆盖错身份/请求/设备、错误会话、缺少实际验证、过期/未来、密码代替 Passkey、重复 proof、绕过审核服务直插数据库；`src/main` 从未包含该替身。生产强拒绝另有 L1 单测。严格 PG18 九套 JUnit 证据门禁增加四项测试，现强制 A11 十项。
- **历史 V14 必须拒绝：** 后续源码 [`dd724b82`](https://github.com/kunlong-luo/lanveriq/commit/dd724b82aba5329f5f716b14c000f26235d1a7dd) 额外在第二次审核前读取第一次记录中的 `step_up_proof_id / step_up_verified_at / step_up_method`，任何 V14 历史 `NULL` 证明均不能被第二张新票据追认；增加 L1 正反测试。**仅对完整数据库证明做结构完整性检查，不宣称数据库本身证明 Passkey 密码学真实性。**
- **验收仍待结果：** 首个 A11-C 源码 [5fbe22fc](https://github.com/kunlong-luo/lanveriq/commit/5fbe22fced766146edc47118c9f85fd8b78dff58) 对应 [PG18 L2 #43573481713](https://github.com/kunlong-luo/lanveriq/actions/runs/43573481713) 尚在运行；追加的历史拒绝代码 [PG18 L2 #43573608284](https://github.com/kunlong-luo/lanveriq/actions/runs/43573608284)、[Java L1 #43573616690](https://github.com/kunlong-luo/lanveriq/actions/runs/43573616690) 状态须以最终 GitHub 结果核实，**不得提前宣称通过**。[同最终 L1 源码 RD-2 #43573616715](https://github.com/kunlong-luo/lanveriq/actions/runs/43573616715) SUCCESS。
- **不可越级：** 需要企业授权的真实 WebAuthn 注册/挑战/挑战消费、独立审核人强认证、可撤销凭证与组织租户隔离、受托管签名根/CA/可信时钟以及 Pop!_OS24 L3 才能设计生产审核/发证。现阶段没有可用生产审核入口，成功的合成 `TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN` **绝对不是签发许可**。

- **最新合成测试补齐：** [`7e4ad3b1`](https://github.com/kunlong-luo/lanveriq/commit/7e4ad3b18f4a7d5d731152e074f3dea5c2a8aa2c) 增加错误设备 UUID 和错误高危审核动作的真实 PG18 拒绝验证，修正已有测试实际覆盖范围；[最新九套 PG18 #43573695132](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695132) 和 [Java L1 #43573695114](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695114) 需核对最终状态，**未获得 Success 前不标记最终验收**；[RD-2 同 SHA #43573695190](https://github.com/kunlong-luo/lanveriq/actions/runs/43573695190) 已 SUCCESS。历史票据拒绝和错误动作/设备的修复没有开启正式签发路径。

## 2026-11-14 A11-B V14 PostgreSQL 双人审核账本（L2 同 SHA SUCCESS，内部默认关闭）

- 代码里程碑 [`a512ea2f`](https://github.com/kunlong-luo/lanveriq/commit/a512ea2fb91fe662e7ef14e20f0475aceebebd3b)。新增 Flyway [V14](../../apps/server/src/main/resources/db/migration/V14__durable_first_pin_double_review.sql)：设备/证书 epoch/CSR SPKI/申请者和申请会话版本的单次不可变请求、两次独立审核人签收、撤销占位账本、唯一防重复和审批顺序触发器，以及 UPDATE/DELETE 禁止触发器。**这不是对数据库超级用户的防篡改证明。**
- 新增 `FirstPinReviewLedgerService`，必须显式设置仅测试用的 `lanveriq.first-pin-review.ledger-enabled=true` 才会创建 Bean；默认**没有 Bean、没有 HTTP 路由**。内部 Spring 事务重新锁定真实设备/当前证书及用户/组角色授权，按 UUID 稳定顺序锁定申请人和两个审核人的当前身份与会话版本，原子记录审核投票和 `admin_access_log`；审计被触发器丢弃会回滚整笔投票。**不连接签发方、CA、Agent、TLS Epoch 本地 pin 或隔离解除。**
- 结果类型 `TWO_REVIEWS_RECORDED_NOT_AUTHORIZED_TO_SIGN` 特意不返回生产签名授权；只有数据库中的**两个**独立审核投票，不等于真实管理员 MFA/重新认证、组织审批、信任根、可信时间或正式签发批准。现有模型按 **device_group_id** 授权，尚未实现 A11 方案中的独立 `tenant_id` 真实隔离，必须单列上线阻断项。
- [`PostgreSqlFirstPinDoubleReviewIT`](../../apps/server/src/integrationTest/java/com/lanveriq/server/enrollment/PostgreSqlFirstPinDoubleReviewIT.java) 在一次性 PG18.6 Testcontainers 中覆盖两次签收/自审/错组/重放、撤权和 session 版本变化、审计故障整事务回滚、不可变 SQL 触发器与并发二次审核只能有一个胜者。审计证据脚本必须检查**九套** Testcontainers 及六个 A11 方法。为避免额外每次 push 都跑昂贵测试，PG18 workflow 新增仅匹配 A11 特定文件和 workflow 的 milestone push 条件。
- **真实验收结果（精确源码与证据）：** 首轮 [PG18 #43570485736](https://github.com/kunlong-luo/lanveriq/actions/runs/43570485736) **FAILED：77 项执行、6 项失败**，其中五项是测试重复使用固定模拟证书指纹违反全局唯一键，另一个是升级测试仍要求 V9→V13 的四次迁移。修复夹具指纹隔离和 V9→V14 五次迁移计数后，`main` 精确源码 [`34a50aaf`](https://github.com/kunlong-luo/lanveriq/commit/34a50aafdc397d709535b0c3c28493d0f89524fb) 对应 [PG18.6 九套真实 Testcontainers #43571716873 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43571716873)：`integrationTest` 与强制 JUnit evidence checker **双双 SUCCESS**，日志证明九个类、全部关键 A11 六用例，**零失败/错误/跳过**。[同 SHA RD-2 #43571716881 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43571716881)。上述不代表生产 MFA、正式管理员、HSM/KMS、签发或 Pop!_OS24 L3；Issue #5 OPEN，v0.1 NOT RELEASE READY，生产联网/采集/自动恢复/远控 OFF。

## 2026-11-14 A11-A 首批代码（仅策略预判，L1 SUCCESS）

- 源码基线 [`b681f872`](https://github.com/kunlong-luo/lanveriq/commit/b681f8726c656bb956b534c32d3a3dc6dc1e3664)（在 `main` 上连续提交）：`FirstPinReviewDryRunGate`（内部包级 Java 只读对象，无 `@Service`、无 HTTP 路由、无 DB 写入或签名接口）与 `FirstPinReviewDryRunGateTest`；`AdminAction.REVIEW_FIRST_PIN` 单独提供 owner_security_admin 组级动作，不复用设备 mTLS 身份。
- 预判只读取 Spring Security 已认证的 `AdminPrincipal`，重新查询两个审核人的当前组权限，校验设备/组/租户/epoch/请求及策略和状态 generation，拒绝申请人自审、重复审核人、跨范围、损坏摘要、撤销、权限变化和不具备 MFA/重新认证证据的合成快照。没有完成**第一次**审核时固定拒绝，已经完成第一次但缺第二次审核时返回 `PENDING_SECOND_REVIEW`。
- `APPROVED_FOR_SIGNING` 在该对象中仅是 **dry-run diagnostic**，绝不表示实际生产授权；当前模拟的 `State` **没有**从数据库读取的可信来源，没有真实 MFA/时钟与审计写入，无法作为签发、CA 注册、Agent pin 或隔离解除输入。不要将其接入现有 `ReviewedDeviceCertificateRegistrationService` 或 Agent `main.rs`。
- 与 [Server unit tests #43568599438](https://github.com/kunlong-luo/lanveriq/actions/runs/43568599438)、[RD-2 fail-closed #43568599424](https://github.com/kunlong-luo/lanveriq/actions/runs/43568599424) 对应的最终结果均为 **SUCCESS（源 SHA b681f872）**；此项仅 L1，不代表真实 PG18/签发验收。后续仍需 PG18 事务持久审核、真正的双人身份/撤权竞态与签发权限隔离。

## 1. 边界、已具备能力与信任缺口

A8–A10 已完成合成离线 Ed25519 `first-pin-review/v1` 签名验证、设备级 SQLite 审核公钥不变绑定、唯一 review_id 消耗与首次证书 pin 的同事务提交。L1、真实隔离 Rust→Caddy→Tomcat TLS1.3→PG18 L2、RD-2 OFF 在相同受测 SHA 通过。**它们没有管理员身份、组织信任根、私钥保管、发证审核、可信时间、吊销同步或已授权的 Linux Agent 安装调用边界。**

当前 `install_first_pin_with_offline_review(review, observed, now_unix)` 的 `observed` 和 `now_unix` 均可由调用者提供。现有本地 SQLite 审核公钥初次写入并不能证明组织已授权该公钥。**不得**将该方法直接连接常驻 `main.rs`、HTTP 接口、用户自选公钥、单纯签名成功的 UI 或自动恢复任务。A10 的已有旧 pin/旧 pending 均维持 fail-closed，不允许签发追认或静默迁移。

## 2. 分层架构与权限

| 层 | 允许职责 | 禁止事项 |
| --- | --- | --- |
| Console/Spring 审核 API | 经独立登录会话、组织/设备级 RBAC+ABAC、强认证和审计创建待审请求；查询状态 | 通过 Agent mTLS 冒充管理员；把浏览器传入的公钥、指纹或时间当最终证明 |
| 审核策略服务 | 校验 `tenant_id`、`device_id`、权限、签发/撤权策略版本、两人复核要求、CSR 与现有证书状态 | 审核人与被审核请求之间的绕权；未经审批直接签名 |
| 独立审核签名方 | 仅在授权策略决定通过后对明确域隔离、带唯一 ID 和有效期的审核负载签名，记录不可变签发审计 | 在 Web/Agent/CI/仓库/日志持有生产签名私钥 |
| PKI/CA 发证方 | 独立验证批准、CSR 公钥/持有证明、身份/有效期/用途；签发 mTLS 设备证书；支持吊销 | 把审核人签名直接当作 X.509 CA 签名；从用户提供 CSR 自动发证 |
| Agent 主服务（默认 IDLE） | 已授权后仅按最小权限读取状态与运行受约束心跳 | 直接使用审核签发私钥、管理根信任、隐式安装首 pin、自动解除隔离/轮换或远控 |
| 专用本地安装/审核工具（尚未实现） | 显式管理员监督下验证安装包/策略/根公钥，验证真实 TLS 来源与签名审核、可信时钟及吊销，再请求 A10 SQLite 原子事务 | 从 HTTP 响应或 CLI 随意接受 `observed`/`now_unix` 而宣称已授权 |

**身份分离：** 设备的 P-256 CSR/mTLS 身份、管理员登录身份、审核授权签名密钥、CA 签发密钥、服务端 ACK 都是**不同信任域**。会话 cookie、设备证书、代理头、操作员签名不能相互替代。

## 3. A11–A14 最小实现顺序（不允许越级）

### A11-A：审核身份与只读授权决定（优先，离线合成）

- 定义强类型 `ReviewDecision`：`Denied` / `PendingSecondReview` / `ApprovedForSigning`，而非布尔签名成功；只有由**服务端从已认证身份构造**的请求上下文可参与策略决策。
- 需要 `tenant_id`、`device_id`、`request_id`、`requested_action=first_pin`、CSR SPKI/leaf DER 摘要、管理员主体 ID、授权范围、MFA/重新认证证据、策略版本与事务期内的设备/凭证状态。
- 生产签发至少两人分离（请求人与批准者不可相同，批准人与发证服务职责分离）；可先对合成策略与角色测试，不宣称真实 IdP/MFA 已接入。角色授权失效、跨租户、会话注销、跨设备、重复审核、权限变化竞争等均必须拒绝。
- 在完成服务端角色/审核审计之前，**不新增签名发放或 Agent 调用入口**。

### A11-B：根信任与受控签发（随后）

- 生产审核签名密钥和 CA 密钥分离，优先采用组织管理的非导出 HSM/KMS/PKCS#11 密钥；实际算法须先验证供应商及接口支持：**不能假设 TPM 2.0 原生支持 Ed25519**。当前 A8 Ed25519 v1 可以继续用于临时合成测试，不等于生产托管方案已批准。
- Agent 应通过受控安装物料/签名策略单独锚定**组织根公钥与租户/设备授权范围**（root-of-trust bootstrap），固定本地 SQLite 的任意审核公钥不得自动升级为组织信任根。私钥不下发 Agent、不进 GitHub Actions secrets 的普通日志、不写仓库。
- 新签发负载必须包含：独立域隔离与 `schema_version`、`issuer_key_id`、`tenant_id`、`device_id`、`credential_epoch_id`、证书 DER SHA256、CSR SPKI SHA256、`review_id`、审核操作者身份/角色及策略标识、`issued_at`、`not_before`、`expires_at`、防重放 challenge/nonce、动作类型和签名。
- **协议升级选择：** 上述字段不在 A8 `first-pin-review/ed25519/v1` 的签名负载中。需要新域名/新版本 **v2** 与双端 Golden Fixture，**不可**把 v2 新字段作为未签名 JSON 附加到 v1，亦不可静默认为旧 v1 审核有效。
- CA 验证 CSR 所有权及设备注册范围，发证与授权审核均写服务端不可变审计链；不记录私钥、令牌明文、敏感设备资产。

### A11-C：吊销、时效与防时钟回拨

- **至少三条相互独立的撤销路径：** 管理员审核资格/审核签名密钥撤销；一次性 review grant 撤销；设备 mTLS 证书撤销/停用。签发前和最终消耗前都必须校验最新撤销状态、租户/设备绑定和策略版本，防止 TOCTOU。
- 吊销信息必须具有签名版本、单调递增 epoch、可审计发布时间和可信有效截止；旧版本、无效签名、未知签发者、撤销信息不可用或已过新鲜度阈值时**禁止新授权**。已被撤权的 Agent 不得靠缓存旧 grant 重新绑定。
- 不能仅用 `SystemTime::now()`、Agent 可改系统时钟、HTTP 返回 `server_time` 或 SQLite 写入时间宣称“可信时间”。设计可验证的服务器签名时间/挑战响应与单调状态，结合系统时钟回拨检测；无可信时间证明则**拒绝新的首次 pin/轮换**。离线环境需要明确、经管理员监督的可信时间方案；不能降级为接受任意传入 `now_unix`。
- 证书已吊销、密钥丢失、授权过期只会进入暂停/隔离；不触发静默发新证书、清理 UUID 或继续联网。

### A11-D：Agent 授权调用边界（最后接线）

- Pop!_OS 非 root systemd 主进程继续 `PrivateNetwork=yes`、禁止默认采集/上传/远控；审核安装能力留在单独的显式、可审计、最小权限本地管理路径，不能由应用内可控 JSON/环境变量直接开启。
- 由**实际使用的 Reqwest/Rustls 客户端证书 DER 与通过认证的 TLS 连接上下文**得到证书指纹/epoch 证明，禁止安装方信任外部输入的 `observed`。服务端 epoch GET 结果仅为证书状态观察，不是授权、原 POST ACK 或签名发证证明。
- 只有同时通过管理员授权、信任根链、有效 grant、撤销状态、可信时间、真实 TLS 身份、SQLx 原子防重放后才考虑首 pin。旧 pending/quarantine、A10 之前历史 pin 均**不得追认/自动解除**。
- Linux OS 层需单独验证 root/operator 管理的安装凭据、文件 ownership/mode、服务隔离、审计、防篡改/重启策略；不得把 SQLite 触发器误作对 root 或磁盘管理员的不可篡改保证。

## 4. 强制负向测试清单

| 测试类别 | 必须拒绝或保持的不变量 |
| --- | --- |
| 管理员授权 | 未登录、伪造 session/header、无权限、跨租户/设备、已撤权、错误 MFA、请求人自审/单人绕双审 |
| 信任根 | grant 自带公钥、未知 `issuer_key_id`、本地先行假 pin、旧 v1 冒充 v2、审核密钥更换/撤销 |
| 签发绑定 | CSR/证书公钥不符、错设备/epoch/fingerprint、额外字段、非 canonical 编码、签名字段篡改 |
| 时间 | 未来/过期 grant、离线时钟倒退、重启回拨、恶意伪造服务时间、过期/乱序撤销快照 |
| 竞争/撤销 | 审核通过后签发前撤权；签发后首次消耗前撤权；并发 review_id；签名与原子 pin 的回滚 |
| Agent 权限 | 默认 IDLE；无安装权限不能调用 A10；外部可控 `observed` 被拒；旧 ACK 未决依旧隔离 |
| 网络/存储 | TLS1.2、错 CA/SNI/Host/身份、HTTP 401/403/409/5xx、不确定 ACK、破坏 SQLite seen、存储失败 |
| 发布守卫 | RD-2 仍默认 OFF；没有生产密钥/真实设备/公网请求；仅合成 fixture，无自动安装和采集 |

**验收分级：** L1 Rust/Java policy + sqlite 文件型/重启/并发，优先红灯负例；L2 一次性 Ubuntu 24.04 Rust→Caddy→Tomcat TLS1.3 + PG18 + 合成 CA/签发权限及撤销竞态；L3 真实 Pop!_OS 24.04 仅在独立批准、设备所有者知情同意、密钥托管/日志/隐私审查后执行。CI 必须记录相同代码 SHA 和每个 workflow/job；只在里程碑触发昂贵 L2，不把编译或 Mock 测试当 L2/L3。

## 5. A11 第一份可编码切片及 Done 判定

1. 先实现**无签名/无网络/无写库**的管理员审核策略内核与严格 DTO，验证主体/设备/租户/角色和双审规则；生产调用端保持未接线。
2. 覆盖第 4 节的身份、跨租户、自审、被撤权、权限版本竞争负向；同时验证现有 A10 任何旧授权都不能自动升级。
3. L1 CI 与 RD-2 default-deny 绿灯后，再开始 v2 可信签名材料、KMS/PKCS#11 适配评审及独立发证。
4. 完成每个切片后更新 release ledger 与 Issue #5 的**精确 SHA/CI/未完成项**，不能笼统标记 Batch A 已完成。

**发布结论：** A11 的文档/单测或未来的合成签发 SUCCESS 均不等于生产信任根实际托管、受控组织审批或 Pop!_OS L3；v0.1 继续 NOT RELEASE READY，Issue #5 保持 OPEN。生产联网、采集、自动恢复、RustDesk 远控全部保持关闭。
