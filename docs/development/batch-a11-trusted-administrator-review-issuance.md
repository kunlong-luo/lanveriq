# Batch A11 — 可信管理员审核与签发安全设计（实现前验收合同）

> 状态：A11-A/B/C、D1、D2-A/B/C 隔离合成验收已完成；D2-D1/D2/D3/D4 安全基础均已通过隔离 L1/L2 验收；真正 MFA/Passkey step-up 和可信签发仍 NOT IMPLEMENTED / NOT RELEASE READY；2026-11-16。仅针对 **Pop!_OS 24.04 Linux-first** 首发路线。本文不批准启动生产 Agent、网络/采集/自动恢复/远控，不批准生成真实 CA/审核私钥或生产注册令牌。所有新增实现先在 `main` 上以合成环境、默认拒绝、负向测试先行推进。
>
> 来源：[上线差距账本](../planning/release-readiness-2026-11-13.md)、[Batch A #5](https://github.com/kunlong-luo/lanveriq/issues/5)、[A10 受测源码 42a3a12f](https://github.com/kunlong-luo/lanveriq/commit/42a3a12f1654834fbef138b049d7ffe870330728)。

**B2 同 SHA 自动 CI 验收补记（2026-11-17）：** [`e35380c5`](https://github.com/kunlong-luo/lanveriq/commit/e35380c5de28093f0adffc33a077570f8aa4a58c) 的 [Java 单元测试 #44008193882](https://github.com/kunlong-luo/lanveriq/actions/runs/44008193882)、[PG18 集成源码编译 #44008193852](https://github.com/kunlong-luo/lanveriq/actions/runs/44008193852) 与 [RD-2 #44008193822](https://github.com/kunlong-luo/lanveriq/actions/runs/44008193822) **均 SUCCESS**。仅 L1 编译/单测与远控拒绝检查；没有运行该提交的真实 PG18 L2、签名证书链的可信正向验收或者 Pop!_OS L3。以上不会构成上线批准。

**2026-11-17 V21 历史失败说明：** 初次提交 `d92ef99c` 的真实 PG18 曾因测试用 PL/pgSQL 定界符及 V21 迁移数量断言失败；随后修复 SHA `134cc758` 的完整 PG18、Linux TLS、Java 和 RD-2 同 SHA 验收通过。历史失败保留，不改变新迁移的真实 L2 结论。

**V21 B3 最终复验状态（按修复后精确源码 SHA 记账）：** [`134cc758`](https://github.com/kunlong-luo/lanveriq/commit/134cc7580c2ea7616909288d48b76431ee2192f4) 的 [真实 PostgreSQL 18 九套 #44011548316](https://github.com/kunlong-luo/lanveriq/actions/runs/44011548316) **SUCCESS**，其中 Testcontainers/Flyway 真实事务和 mandatory JUnit evidence checker 两个 job steps 均 SUCCESS；[Java L1 #44011548328](https://github.com/kunlong-luo/lanveriq/actions/runs/44011548328)、[PG18 集成源码编译 #44011548311](https://github.com/kunlong-luo/lanveriq/actions/runs/44011548311)、[RD-2 #44011548315](https://github.com/kunlong-luo/lanveriq/actions/runs/44011548315) **均 SUCCESS**。同 SHA [Linux 双跳 #44011548309](https://github.com/kunlong-luo/lanveriq/actions/runs/44011548309) 的 PG18 九套安全事务 job 和 Caddy→Tomcat TLS1.3 双跳合成 job **均 SUCCESS**，该 Linux workflow 整体 **SUCCESS**（同一源码 SHA）；这仍然不是 Pop!_OS 真实设备 L3。以上仅证明 **V21 未可信拒绝回执** 的合成 L1/L2 验收，不是可信 attestation/root/MDS、真正 Passkey 登录、管理员审核 MFA、CA/KMS 或 Pop!_OS L3。v0.1 不批准上线，生产功能 OFF。

**V22 首轮真实 PG18 失败与修复（代码复验待完成）：** 首轮 [源 SHA `803c8721`](https://github.com/kunlong-luo/lanveriq/commit/803c8721f3669967249a0494a0186c01fa9a5993) 的 [PG18 #44086285547](https://github.com/kunlong-luo/lanveriq/actions/runs/44086285547) 执行 116 个用例，其中 1 项审计抑制测试在临时创建 PL/pgSQL 函数时因 Java 夹具中的双美元定界符被错误替换为单美元而失败。故该 SHA **不得判定 L2 通过**；Java L1 #44086285517、源码编译 #44086285554、RD-2 #44086285635 同 SHA SUCCESS，但不能替代失败的 PG18。已在本次修复中恢复正确定界符，安全约束、4 项必测用例与证据门禁均保留；增加不溢出的合成证书 serial 生成方式。新 SHA 的 PG18 和 Linux 仍需独立确认。

**V22 B4-A 同一源码 SHA L1/L2 验收（2026-11-17）：** [`7f6d0b6c`](https://github.com/kunlong-luo/lanveriq/commit/7f6d0b6c880197e727b730360b071ea554b88a16) 的 [真实 PG18 九套 #44086622096](https://github.com/kunlong-luo/lanveriq/actions/runs/44086622096) **SUCCESS**（Testcontainers/Flyway 事务测试及严格 JUnit XML 证据门禁两个 steps 均成功）；[Linux TLS1.3+PG18 #44086622378](https://github.com/kunlong-luo/lanveriq/actions/runs/44086622378) **SUCCESS**（真实 PG18 安全事务及 Caddy→Tomcat 双跳 TLS1.3 两个独立 jobs 均成功）；[Java L1 #44086622202](https://github.com/kunlong-luo/lanveriq/actions/runs/44086622202)、[PG18 集成源码编译 #44086622113](https://github.com/kunlong-luo/lanveriq/actions/runs/44086622113)、[RD-2 #44086622133](https://github.com/kunlong-luo/lanveriq/actions/runs/44086622133) **均 SUCCESS**。首轮代码 [`803c8721`](https://github.com/kunlong-luo/lanveriq/commit/803c8721f3669967249a0494a0186c01fa9a5993) 的 [PG18 #44086285547](https://github.com/kunlong-luo/lanveriq/actions/runs/44086285547) **FAILED（116 项 1 失败）**，由 `7f6d0b6c` 修复夹具后才完整通过，历史失败保留。V22 仅离线 **`untrusted_proposal`** 存储与回滚验收，WebAuthn4J 根仍空；不是组织/FIDO 认可、生产可信时间/撤销/元数据、真实 Passkey MFA/双审、CA/KMS、Pop!_OS L3 或上线批准。生产功能持续 OFF；Issue #5 OPEN，v0.1 NOT RELEASE READY。

### 2026-11-17 D4-B4-B — V23 双人独立离线筛查（不是信任根批准，CI PENDING）

- **仅筛查，不授信：** Flyway V23 新建 `admin_passkey_attestation_root_screenings_staging`，只能记录 `screened_untrusted`、step 1/2、当前管理员及 session_version、V22 数据库生成的证书 SHA-256。V22 根提案永远保留 `untrusted_proposal`；没有 `approved`、`trusted`、`active` 或可给 WebAuthn4J 消费的根记录。双人签收不是 WebAuthn 强认证、MFA、签名或 KMS/CA 授权。
- **PostgreSQL 原子边界：** insert trigger 锁定 V22 根提案为串行化点，并检查证书摘要完全一致、提交人与审查人不同、双方当前未禁用且会话没有轮换、两者存在共同 `owner_security_admin` 设备组授权。step 2 还要求 step 1 当前仍有效的另一名审核人、同组当前授权，防止撤权后继承旧复核。相同提案的 step/审核人唯一且全部 UPDATE/DELETE 拒绝；数据库 owner/superuser 攻击不在此隔离模型范围。
- **非 Bean 限定服务：** `StagedAttestationRootScreeningLedger` 仅接收服务器端提案 ID 与 step，不接受浏览器提交的 root 指纹、trusted/approved 开关、元数据结果和时间字段，必须与审计同一事务；日志 INSERT 被丢弃则回滚。无 HTTP/Agent 调用、root reader、认证凭证发布或信任锚更新。
- **测试门禁：** 真实 PG18 新增 5 项：两名同组审核人及永久未受信任结果、提交人自审/跨组/跳步/重复拒绝、审核人撤权和 session 轮换拒绝、SQL 假指纹和变更/删除拒绝、静默吞审计时回滚重试。V9→V23 迁移数量 14；JUnit 九套 Console 至少 34、总计 106；main push 自动触发真实 PG18 和 Linux TLS。**本源码 SHA 的 L1/L2 尚待验收。**
- **上线仍阻断：** 真实 FIDO MDS 签名与可信时间、根证书审查人 MFA/双人签名、组织级根批准/吊销、实时 CRL/OCSP、可信 registration evidence、Pop!_OS L3 和发行方 KMS 均未完成；全生产开关 OFF，v0.1 NOT RELEASE READY。

### 2026-11-17 D4-B4-A — V22 离线受信根候选暂存（修复 SHA L1/L2 已验收）

- **严格区分候选与信任：** Flyway V22 新增 `admin_passkey_attestation_root_proposals_staging`，只容纳未受信任的离线根证书候选；`trust_state='untrusted_proposal'`、`source='offline_candidate'` 固定为 SQL CHECK；根证书 DER/数据库计算的 SHA-256 指纹必须一一对应，重复根拒绝，UPDATE/DELETE 受不可变触发器约束。不存在 `approved`、`active`、`trusted` 状态，WebAuthn4J `TrustAnchorRepository` 仍始终返回空集。
- **标准密码学库入库前检查：** 非 Bean `StagedAttestationRootCertificateInspector` 使用 JDK X.509/PKIX 证书 API 解析原始规范 DER，要求 V3、CA BasicConstraints、keyCertSign、主体等于签发人、有效期、自签名正确；不写自制 ASN.1/CBOR/COSE。注意：自签名只能说明签名自洽，**并不证明认证器制造商/组织受信**；JVM 时间也未达到生产可信时间标准。
- **审计/撤权与默认拒绝：** 包私有 `StagedAttestationRootProposalLedger` 核对当前安全管理员、会话版本与未停用状态，数据库 INSERT 锁读当前账户与权限；审计丢失时必须事务回滚。没有控制器、Spring Bean、HTTP 入口、审批通过 API、受信根读取或签发调用。
- **L1/L2 门禁：** 新增损坏/非规范 DER 的 L1 拒绝；真实 PG18 新增四项：自签 CA 离线候选的指纹与未信任状态、重放/升级/删除拒绝、撤角色/轮换会话/停用拒绝、审计插入被抑制时原子回滚及重试。原历史 V9→当前迁移数从 12→13；九套严格 JUnit Console ≥29、总计 ≥101。GitHub Actions 自动触发真实 PG18 和 Linux TLS 合成隔离工作流；**修复 SHA `7f6d0b6c` 的 exact-SHA L1/L2 已成功；详见本页当日验收补记**。
- **剩余阻断：** FIDO MDS 签名源与组织 CA 信任根人工双人审核、OCSP/CRL、元数据状态/可信时间、管理员 WebAuthn 注册真实正向 attestation、原子可信证据、撤销、L3、隐私及 KMS/CA 都未实现。V22 不能给 V20/V21 或任何 Passkey 赋予权限；v0.1 NOT RELEASE READY，生产联网/采集/自动恢复/远控/发行方 OFF。

### 2026-11-17 D4-B3 — V21 未可信 attestation 拒绝回执（**仅隔离待验收**）

- **原子来源链：** 新增 Flyway V21 `admin_passkey_attestation_rejections_staging`，通过 V20 新增复合唯一键与六字段复合外键，绑定 **V19 挑战 ID、当前管理员/会话、固定 RP、凭证 ID、原始注册响应 SHA-256**；SQL 插入守卫重新核对已消费 nonce、有效时间、当前所有者身份与权限、凭证未吊销。唯一回执、`evidence_grade='not_trusted'`、`rejection_code='trust_roots_unconfigured'` 均由 SQL CHECK 固定，UPDATE/DELETE 被历史不可变触发器拒绝。禁止 SQL 将此表提升为可信证明。无原始响应、硬件标识或私钥复制。
- **非 Bean 隔离写入：** `StagedAttestationRejectionLedger.recordUnconfiguredTrustRoots` 只接收服务器挑战 ID，从数据库 V20 复制全套来源，不接受浏览器提交的 `verified`、AAGUID、证书链结论或任意信任根/策略 ID；必须与调用方在真实事务内执行，写入 `admin_access_log` `outcome='denied'`，审计缺失则回滚回执。该服务不验证密码学 attestation，不能创建 CredentialRecord、MFA、first-pin 审核票或 CA 签发。
- **L2 强制负向测试：** 真实 PostgreSQL 18 集成套件新增缺失候选拒绝、复合来源/无信任回执、重复及更改/删除拒绝、跨管理员及会话旋转拒绝、审计 INSERT 被吞没时回滚并仅在恢复后重试。九套 evidence checker 要求 Console ≥25、总体 ≥97（原九套要求 ≥93），四项新用例不得缺失。工作流 main push 包含 V21 迁移及服务路径自动触发真 PG18，不依赖用户手工运行。
- **状态：** 本提交 CI 仍未验证，严格声明 `PENDING`；V20 始终 `untrusted_candidate`，V21 始终 `not_trusted`。**这不是“可信证明已落库”**，真正 WebAuthn4J attestation 与受管理的根/MDS/CRL/OCSP、完整 PG18 正向可信证据仍未实施。生产 Agent/联网/采集/自动恢复/远控/签发 OFF；v0.1 不批准上线。

### 2026-11-17 D4-B2 — 严格 WebAuthn4J 证书路径验证器配置（无信任根、未接线）

- 新增非 Bean 的 `StagedStrictAttestationWebAuthn4JFactory`，使用官方 `WebAuthnManager` 显式构造：**仅** `PackedAttestationStatementVerifier`，官方 `DefaultCertPathTrustworthinessVerifier`，强制启用证书状态撤销检查与禁止客户端全链，明确拒绝 self-attestation；**不包含** `NoneAttestationStatementVerifier`、`NullPackedAttestationStatementVerifier`、`NullCertPathTrustworthinessVerifier` 等弱验证实现。官方实现说明警告，混入 no-op 验证器会造成认证器证明绕过（[WebAuthn4J 官方参考](https://webauthn4j.github.io/webauthn4j/en/)）。
- 根仓库两个查找路径（AAGUID 与 U2F key identifier）永远返回空集，**没有批准的根、OCSP/CRL 源、元数据管理或真实注册权限**。此代码只构建一个保守的验证器；即便语法/签名校验通过，也不得视作完整可信注册。未来不可通过任意外部输入直接注入根；必须另行设计与验收版本化受控根/元数据/撤销服务。
- 同步新增 L1 拒绝与配置测试；提交时 CI 尚待本 SHA 结果。没有修改现有 V20 `untrusted_candidate`、注册凭证消费者、first-pin 两人审核、发行方或 Spring Security WebAuthn 开关。**v0.1 未获上线批准**，生产网络/采集/自动恢复/远控/证书签发 OFF。

### 2026-11-17 D4-B1 — attestation policy shape only (NOT cryptographic verification)

- New package-private, unregistered `StagedAttestationAdmissionPolicy` provides a **fail-closed policy-shape guard**. No approved root is configured by default; `none` and self-attestation are rejected, and missing chain/metadata/revocation/UP/UV flags or an unapproved SHA-256 root fingerprint deny admission. Its five JUnit tests use **synthetic values only**. A passing synthetic decision is **not** a WebAuthn4J verified attestation or trusted enrollment.
- **Critical trust boundary:** `VerifiedAttestation` is a temporary internal data contract, not an unforgeable type. Its booleans and fingerprint MUST NOT be populated from browser JSON, V18/V20 rows, or an unverified caller. No production consumer or Bean exists. Before real trust admission, replace/encapsulate this shape behind strict official WebAuthn4J certificate-path and attestation verification, authenticated trust-anchor/metadata administration, current revocation status and PG18 immutable evidence with atomic audit. Do not promote V20.
- This step adds **no Flyway migration, trusted source, login, reviewer step-up, issuer, CA or production endpoint**. It is L1-only until exact-SHA CI passes. v0.1 NOT RELEASE READY; Agent network/collection/auto-recovery/remote and signing remain OFF.

## 2026-11-17 A11-D2-D4-B — 注册凭证可信来源与证明策略（实施合同，未实现）

> **状态：BLOCKED / 仅设计与 L1 输入门禁。** 已有 V19/V20 只证明服务端挑战与 `untrusted_candidate` 来源绑定；WebAuthn4J 注册仪式 `verify()` 成功不等于硬件/组织 attestation 可信，也不授权登录、双审或签发。不得把 V20 的 `evidence_grade` 改为 `trusted`，不得把合成 V16/V18 数据直接装载进生产 `TrustedCredentialSource`。除明确写出的后续受控验收外，所有生产开关仍 OFF。

### 1. 必须分离的四种证明

1. **注册仪式有效：** 服务端发起并在数据库中当前有效、未消费的 V19 随机 nonce；WebAuthn4J 解析并验证 `webauthn.create`、固定 HTTPS Origin/RP ID、UP+UV、可接受算法和注册响应；当前 actor/session/handle、凭证 ID、公钥和备份标志与数据库事务绑定。不得信任浏览器传入的 `verified` 或 `rpId`，不得使用 `WebAuthnManager.createNonStrictWebAuthnManager()` 的宽松 attestation 结果来声称认证器来源可信。
2. **认证器来源可信：** 独立的版本化组织 attestation policy 决定哪些证明格式、设备类别和根证书可接受。对于高权限的 `owner_security_admin` 审核凭证，**默认拒绝** `none`、self-attestation、未知/过期/撤销或不符合组织策略的证明；仅凭 AAGUID、浏览器声明、V18 数据或证书链存在均不构成可信结论。严格采用 WebAuthn4J 官方 `AttestationStatementVerifier` 和 `CertPathTrustworthinessVerifier`/组织批准的 `TrustAnchorRepository`，链路须绑定经审核的根、有效元数据状态、证书有效性与适当撤销/新鲜度检查。是否允许平台同步 Passkey 作为**低权限登录**，必须与高权限审核/签发策略分开审议，不能自动继承。
3. **管理员当前有权使用：** 经独立事务重新锁定真实账户、`session_version`、角色与租户/设备范围，检查注册人和将来审核人的强认证及撤权；注册成功本身不得成为第一设备审核票据。
4. **实际签发获批：** 以后仍必须另行消耗 V17 一次性审核挑战、使用最新持久 `signCount`、独立两人审核、真实审计、批准策略版本、KMS/HSM 和正式 CA。即便上面三步均通过，也不能跳过签发前的撤权和可信时间检查。

### 2. 持久化准入协议与禁止升级边界

- 先建立明确命名的 `VerifiedRegistrationEvidence` / `AttestationTrustDecision` 强类型结果，再设计后续 **独立新迁移** 的持久证据表，不能通过修改 V20 历史或新增一列简单布尔 `trusted` 追认旧数据。只有由服务端调用的严格验证/策略流程可以创建新证据，且该调用端目前不存在；不能允许 JSON/HTTP/测试 fake 直接写出“verified”。
- 证据必须绑定 V19 challenge ID、V20 candidate ID 与响应 SHA-256、真实 WebAuthn4J 验证出的 credential ID/COSE 公钥/userHandle、RP/Origin、actor/session、UP/UV、注册时 counter、备份状态、attestation format/type、根证书指纹/可信锚版本、策略版本和决策审计 ID。对不一致的 V16/V18 字段以及历史合成候选一律拒绝；不得沿用合成候选的认证权限。
- 要求一个可回滚的 PG18 事务：锁定当前挑战、管理员权限、凭证唯一性与凭证撤销状态；调用独立严格验证和策略决定；以数据库服务器时间确认有效期；仅在全部约束满足时写入**新的**不可变证明与审计，并单次消费 nonce；任何错误包括审计丢失、撤权竞争和并发重复提交均回滚。将密码学验证产物与刚锁定的输入/摘要做确定性绑定，阻断“验证 A、持久化 B”的 TOCTOU。
- 可信来源读取应具有单独的显式业务许可门禁，只读取经过审核且当前未撤销的独立证据，**不**把 V20 或 V18 staging 当成 fallback。根/策略/元数据撤销时阻止新的高权限认证和审核；必须有可审计的失效及恢复流程，不能静默追认。
- Attestation 可能暴露设备标识/供应商信息：在正式注册前必须批准隐私告知、最少留存字段、保存期限、访问控制、加密和删除/审计例外。生产 trust anchor 及其密钥管理不得依赖 GitHub Actions 模拟密钥、浏览器指定根证书或 Agent 设备密钥。

### 3. 验收门禁（不能以编译或合成证明替代）

- **L1**：真实 WebAuthn4J 注册正反向向量（含可信根/错误根/格式 `none`/self、origin/RP、nonce replay、UP/UV、算法和非法 UTF-8）；使用库提供的严格验证器而非自制 CBOR/COSE/证书链解析；检查注册结果无法绕开组织 attestation 策略；输入负版本应在密码学解析前拒绝。
- **L2**：真实 PostgreSQL 18 一致性和回滚，包括两名管理员交叉注册、停用/撤角色/会话轮换、相同 ID 并发、挑战过期与二次消费、验证后撤权、审核日志缺失、V20 历史不可修改以及“合成 V18 不能转为可信”。将新必测方法加入严格 JUnit XML 证据门禁，记录单一代码 SHA 和每个 workflow/job 状态。
- **L3**：经设备持有人批准的 Pop!_OS 24.04 浏览器/认证器实机验证、组织根和元数据轮换/撤销、生产级密钥托管与独立两人审核；未完成前 **v0.1 NOT RELEASE READY**，生产 Agent 联网、采集、自动恢复、远控与 CA 签发继续关闭。

**资料依据：** [WebAuthn Level 3 — Registration and Attestation](https://www.w3.org/TR/webauthn-3/)、[WebAuthn4J 官方严格 attestation 配置](https://webauthn4j.github.io/webauthn4j/en/)、[FIDO Alliance attestation 与 AAGUID 注意事项](https://fidoalliance.org/wp-content/uploads/2024/06/EDWG_Attestation-White-Paper_2024-1.pdf)。[Spring Security signCount issue #19994](https://github.com/spring-projects/spring-security/issues/19994) 在没有确定修复与同版本回归前仍是默认 RP 认证接线阻断项。

### 2026-11-16 D4-A 追加验收：V19+V20 单事务失败回滚强制门禁

- **新增不能跳过的负向测试：** `failedUntrustedCandidateInsertRollsBackChallengeAndBothAudits` 在真正 PostgreSQL 18 同一 Spring 事务中，先由合成 verifier 消费 V19 注册挑战，再故意提交一个不存在的 credential ID 导致 V20 FK/插入拒绝。整个事务回滚必须同时恢复 V19 `consumed_at=NULL`，撤回消费审计与候选审计，且不留下任何候选记录；随后仍在有效期内用正确合成暂存凭证重试才可形成**不可信**候选。这条测试不会把合成数据提升成真实 attestation。
- **证据门禁已更新：** 九套 PG18 JUnit XML 强制要求 `PostgreSqlConsoleSecurityWiringIT` ≥21 项、至少 93 项成功执行，且该原子回滚用例必须出现并通过。针对门禁自身的 Python 负向测试也必须通过；GitHub Actions 的 PG18 触发路径已补齐 `scripts/test_assert_postgres18_junit.py`，避免证据规则被修改却不运行真实 PG。
- **最新源码验收：** [`dc889e99`](https://github.com/kunlong-luo/lanveriq/commit/dc889e99a37e877d07e210fc2588b4945398b30c) 的 [真实 PG18 九套 #43828012782](https://github.com/kunlong-luo/lanveriq/actions/runs/43828012782) **SUCCESS**，且 Gradle `integrationTest` 和强制 JUnit evidence checker 均通过；[同源码 RD-2 #43828012805](https://github.com/kunlong-luo/lanveriq/actions/runs/43828012805) **SUCCESS**。新增测试代码 [`4933febc`](https://github.com/kunlong-luo/lanveriq/commit/4933febc3ae033710af423d53f195620898bb289) 的 [Java #43827587262](https://github.com/kunlong-luo/lanveriq/actions/runs/43827587262)、[PG 源码编译 #43827587231](https://github.com/kunlong-luo/lanveriq/actions/runs/43827587231)、[PG18 #43827587196](https://github.com/kunlong-luo/lanveriq/actions/runs/43827587196)、[Linux #43827587144](https://github.com/kunlong-luo/lanveriq/actions/runs/43827587144)、[RD-2 #43827587226](https://github.com/kunlong-luo/lanveriq/actions/runs/43827587226) **全部 SUCCESS**；不能把不同 SHA 的 CI 冒称同一源码结果。最终版证据门禁后的 Linux 工作流仍需以其自身源码 SHA 独立核对。
- **安全状态不变：** `untrusted_candidate` 是唯一合法证据等级；生产注册/可信源、管理员 MFA 审核、CA 签发、Pop!_OS L3、Agent 网络/采集/自动恢复、远控全都未批准。Spring Security [#19994](https://github.com/spring-projects/spring-security/issues/19994)（2026-11-16 核对仍开放）继续阻断默认 WebAuthn 认证接线。Issue #5 OPEN、v0.1 NOT RELEASE READY。

## 2026-11-16 A11-D2-D4 — V20 仅供验收的注册候选溯源（L1/L2 已通过，绝非可信凭证）

- **功能边界：** [V20](../../apps/server/src/main/resources/db/migration/V20__stage_passkey_registration_candidate_lineage.sql) 新建追加式 `admin_passkey_registration_candidates_staging`，复合外键将 `V19 registration_challenge (id,actor,session_version,rp)` 与 `V16/V18 credential_id+user_id+rp_id` 关联，注册响应仅保留 SHA-256 摘要；V20 数据库 INSERT 守卫核对挑战曾在有效期内消费、当前主体的 DB 会话版本/owner 角色/未禁用状态、凭证未吊销和对应 V18 暂存记录。挑战一对一、凭证唯一，不允许更新/删除历史，不允许提升证据级别：`evidence_grade = 'untrusted_candidate'` **SQL CHECK 固定**。
- **内部服务：** `StagedPasskeyRegistrationCandidateLedger.stageUntrusted` 是无 `@Component/@Bean/@Service` 的内部阶段方法，明确要求与 V19 nonce 消费在**同一 PG18 事务**内执行，强制记录 `admin_access_log`；任何异常或审计丢失须由 Spring 事务回滚。**这个接口只生成未验证的候选**，不输出 Spring Security `CredentialRecord`、`VerifiedEvidence`、可信登录/审核证明或签发授权。原有 V18 合成行不得被解释为可信 Passkey。生产所有 WebAuthn 登录/第一设备审核/CA 仍默认拒绝。
- **真实 PG18 拒绝与原子性证据：** `PostgreSqlConsoleSecurityWiringIT` 覆盖未消费挑战/跨账户凭证不准绑定、会话旋转后拒绝、同一 nonce/credential 重复绑定拒绝、候选篡改和删除失败、`UNTRUSTED` 不可提升；在 V20 先前提交还包含合成审计失败时回滚候选记录的测试。历史 V9→V20 执行 11 次迁移；九套严格 JUnit XML 门禁 Console 至少 21 项，所有九套累计至少 93 项，不允许跳过或丢失关键用例。
- **同一代码 SHA 已验收：** [`17f6d7f5`](https://github.com/kunlong-luo/lanveriq/commit/17f6d7f599cd7bfbeb0175ab2577cfcf326be426)：[Java L1 #43827200172](https://github.com/kunlong-luo/lanveriq/actions/runs/43827200172) **SUCCESS**、[PG18 集成源码编译 #43827200135](https://github.com/kunlong-luo/lanveriq/actions/runs/43827200135) **SUCCESS**、[真实 PG18 九套及严格 evidence checker #43827200129](https://github.com/kunlong-luo/lanveriq/actions/runs/43827200129) **SUCCESS**、[Linux 双跳 TLS1.3+PG18 #43827200148](https://github.com/kunlong-luo/lanveriq/actions/runs/43827200148) **SUCCESS**（两个隔离 job 通过）、[RD-2 #43827200226](https://github.com/kunlong-luo/lanveriq/actions/runs/43827200226) **SUCCESS**。上述仅 L1/L2 合成验收，未证明正式 attestation 信任策略、真实 Pop!_OS L3、安全审核人 MFA/双人签发或生产运营。
- **剩余门槛：** 经签名与可信注册策略验证的真实 Passkey/COSE 凭证安全入库、信任根管理与注册来源认证、RP/Origin/UV 实机测试、注册证明与 V19 原子持久化、V17 审核挑战+V16 最新计数器 CAS+双审证据在单事务完成、撤销/可信时间/真实 CA/KMS/签发审计与实机 L3。Spring Security [signCount 上游问题 #19994](https://github.com/spring-projects/spring-security/issues/19994) 在核实修复及回归测试之前仍为阻断项。Issue #5 OPEN；v0.1 NOT RELEASE READY，生产联网/采集/自动恢复/远控与证书签发 OFF。

## 2026-11-16 A11-D2-D3 — V19 可信注册前置挑战与撤销冻结（真实 PG18 L2 通过）

- **代码：** [`b559a1cb`](https://github.com/kunlong-luo/lanveriq/commit/b559a1cbe735ac227f10d58d00ea227ac7f9f3dd) 新增 [Flyway V19](../../apps/server/src/main/resources/db/migration/V19__stage_administrator_passkey_registration_challenges.sql)，独立于 V17 **审核挑战** 的 V19 **注册挑战**；对管理员 `users.id + session_version`、精确 `console.lanveriq.internal` RP、`https://console.lanveriq.internal` Origin、32 字节 CSPRNG SHA-256 摘要、有效期/一次消费做数据库完整性与不可变约束。真实 nonce 仅在请求时返回一次；消费只能从 `NULL` 变为有效 `consumed_at`，不可换人、延长有效期、删除、重复使用。
- **服务端验证：** `StagedPasskeyRegistrationChallengeLedger` 是包私有**非 Bean / 非 Service / 非 Controller** 的 PG18 事务基础，使用当前 `AdminPrincipal` 和数据库锁核对账户未禁用、当前会话版本、不可变 V16 user handle 与 `owner_security_admin` 授权，再签发 nonce；消费时重新核对管理员、会话、摘要和有效期，调用注入的验证器，然后以 PG 原子消费和强制审计提交。任何失败均拒绝且事务回滚。合成验证器仅在 `src/integrationTest` 临时注入，**不能代表密码学注册**；消费成功既不生成可信凭证记录，也不产生审核票、MFA 凭证或签发许可。
- **官方 WebAuthn4J：** `StagedWebAuthn4JRegistrationVerifier` 使用官方 `WebAuthnManager.parseRegistrationResponseJSON/verify`，约束服务端 32 字节挑战、固定 RP/HTTPS Origin、`UP=true`、`UV=true`、ES256 公钥算法。严格 UTF-8 和有界注册响应；异常默认拒绝。此处尚未实现生产级 **attestation 信任链策略**、注册仪式的审计准入、可信注册结果 V16/V18 一致性写入，任何现有合成行不能被提升为 `TrustedCredentialSource`。默认生产 WebAuthn 登录/first-pin 双审仍彻底关闭。
- **向前修复 V16 撤销后变更漏洞：** `CREATE OR REPLACE FUNCTION guard_admin_passkey_credential_update` 保留原 V16 触发器/迁移历史，拒绝任何 `OLD.revoked_at IS NOT NULL` 行的后续更新（包括计数器递增、备份状态和幂等更新），但仍允许第一次按 V16 规则完成吊销。不能靠 SQL 代替数据库超级用户权限控制、KMS/审计和可信时间。
- **PG18 强制验收：** `PostgreSqlConsoleSecurityWiringIT` 新增四项注册 nonce 摘要/官方拒绝、跨管理员/重复消费、会话版本旋转/历史不可变、已吊销凭证全部状态冻结。V9→V19 历史升级需 10 次 Flyway；9 套严格证据门禁 Console ≥17 项、合计 ≥89 项有效测试、禁止跳过/缺项/失败。新增 L1 证据门禁回归测试，不用编译取代数据库证据。
- **同 SHA 已确认：** [Java L1 #43811201232](https://github.com/kunlong-luo/lanveriq/actions/runs/43811201232) **SUCCESS**、[PG18 集成源码编译 #43811201238](https://github.com/kunlong-luo/lanveriq/actions/runs/43811201238) **SUCCESS**、[RD-2 默认拒绝 #43811201202](https://github.com/kunlong-luo/lanveriq/actions/runs/43811201202) **SUCCESS**、[真实 PG18.6 九套 #43811201225](https://github.com/kunlong-luo/lanveriq/actions/runs/43811201225) **SUCCESS**（`integrationTest` 和严格 JUnit evidence checker 双通过）。[Linux 双跳 #43811201228](https://github.com/kunlong-luo/lanveriq/actions/runs/43811201228) **SUCCESS**，两个真实隔离 job（PG18 事务、Caddy→Tomcat TLS1.3）均成功。**仅 L1/L2 合成隔离证据，不是实际受信任的注册/认证/签发或真实 Pop!_OS 24.04 L3**。Issue #5 OPEN；v0.1 NOT RELEASE READY，生产网络、采集、自动恢复、远控与 CA 签发继续 OFF。

## 2026-11-16 A11-D2-D2 — 原生 WebAuthn4J 断言密码学验证桥接（仅内部，生产硬拒绝）

- **现有实现:** 新增 `StagedWebAuthn4JAssertionVerifier`（`src/main`，包私有、**无 Bean/Controller/审核调用链**）：直接依赖项目已经 BOM 管理的官方 WebAuthn4J `WebAuthnManager.parseAuthenticationResponseJSON` + `verify`，不重造签名、COSE 或 ClientDataJSON 解析器。固定 `rpId=console.lanveriq.internal` 和 `origin=https://console.lanveriq.internal`，不从 HTTP/JSON 客户端选择；仅接受严格 UTF-8、规范 32 字节 Base64url 服务器 nonce、`first_pin.review`、有界响应和 32 字节 user handle。用数据库授权主体和唯一 credential ID 调用专门的 `TrustedCredentialSource` 接口后，重新匹配 actor、RP、未撤销、handle、凭证 ID、**最新持久 counter** 与 WebAuthn4J 记录 counter，一致后要求官方 `AuthenticationParameters(userVerificationRequired=true, userPresenceRequired=true)` 同时验证签名、nonce、RP hash、origin、UP 和 UV。任何格式错误、缺失持久可信注册凭证、错 RP、改绑、counter 回退都拒绝。已验证结果仅是 `CheckedAssertion` 诊断结构，不会创建 review proof、session、证书或 Agent 授权。
- **上游克隆计数器缺陷仍阻断:** [Spring Security issue #19994](https://github.com/spring-projects/spring-security/issues/19994) 至 2026-11-16 未关闭；默认 `Webauthn4JRelyingPartyOperations` 不得上线。此处显式检查提供给 WebAuthn4J 的 counter == **最新持久值**，且对 authenticator 的报告值必须严格大于已存计数（0/0 不视为计数器新鲜度信号）。仍须将 V17 challenge 消耗、V16 counter CAS、V18 凭证证据、正式注册来源整合为**单一可回滚事务**，通过真实认证器的并发与撤销测试；该内部桥接目前未做持久事务更新。
- **强制隔离与测试:** Java `StagedWebAuthn4JAssertionVerifierTest` 校验坏 nonce、错误审核 action、非规范 UTF-8、恶意 JSON/未注册凭证不得触达授权，以及 WebAuthn4J 上游 Apache-2.0 **真实签名测试向量**在原绑定 RP 下的正向密码学验证、在 Lanveriq RP/Origin 下的拒绝；当前注册时旧计数器不能冒充数据库最新计数器。真实 `PostgreSqlConsoleSecurityWiringIT.realPostgresBootCannotAutoInstallStagedWebAuthn4JVerifierOrTrustSource` 检查整个 Spring Boot+PG18 上下文里没有该 verifier 和可信凭证来源 Bean；九套严格 JUnit 门禁新增强制用例，不得跳过。真实凭证注册源 `TrustedCredentialSource` **尚未实现**；V18 的随机合成暂存材料**不是已注册可信 Passkey**。
- **隔离验收通过（同源码 SHA）:** [`b34e3821`](https://github.com/kunlong-luo/lanveriq/commit/b34e38211ef67f788c3c35dd01e4f56268f8d6bb)、[Java L1 #43810441500](https://github.com/kunlong-luo/lanveriq/actions/runs/43810441500)、[真实 PG18 #43810441485](https://github.com/kunlong-luo/lanveriq/actions/runs/43810441485) **SUCCESS**：Java 单测与真实 PG18.6 九套 Testcontainers 事务及严格 JUnit evidence checker 均成功；先前基线 V18 [`cd36eac5`](https://github.com/kunlong-luo/lanveriq/commit/cd36eac5079ef977cbc3c5df290e068bcbc4ab45) 已有完整同 SHA L1/L2 成功证据。无生产 WebAuthn 注册/登录、审核签发、KMS/CA、Agent 联网/采集、自动恢复或 RustDesk 远控；Issue #5 OPEN，v0.1 NOT RELEASE READY。

## 2026-11-14 A11-D2-D1 — V18 凭证注册材料暂存与最新计数器原子门禁（未开放认证）

- **原生官方字段协议：** 遵循 Spring Security `CredentialRecord` 的 `credentialId`、`userEntityUserId`、`publicKey`、`signatureCount`、`attestationObject`、`attestationClientDataJSON`、`transports`、`uvInitialized`、`backupEligible`、`backupState` 和显示名等独立字段契约（https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/webauthn/api/CredentialRecord.html）。新 [V18](../../apps/server/src/main/resources/db/migration/V18__stage_complete_passkey_record_and_counter_provenance.sql) 存储补充注册对象和客户端 JSON、UV、传输方式等不可变阶段记录；与 V16 的全局 credential ID、管理员 user handle、公钥/备份状态和 server-side latest counter 以组合外键强绑定。不会回填 V16 旧行，也不允许跨管理员换 handle/RP ID 或更新/删除注册材料。
- **可信等级是 `staging`：** 即使数据库行字段齐全，SQL 不能判断注册真实性，且测试刻意写入随机非有效 attestation 字节；`AdminPasskeyCredentialRecordStaging` + 仅有查询方法的 Spring Data JPA `Repository` 只读，**没有 Spring Security `CredentialRecord` 转换/登录 Bean/证书签发消费者**。生产 `StagedDenyAllWebAuthnCredentialRepository` 和两种 deny-all MFA/verifier 继续原样拒绝所有认证和审核。注册材料可能含隐私元数据，上线前必须审批数据最小化、保留期限及删除与审计策略。
- **计数器独立防回退：** `StagedWebAuthnCounterGate` 同样不是 Bean/Service/HTTP 路由，不是密码学验证器。未来仅在**真实签名/UV/Origin/RP/挑战经 WebAuthn4J 验证后**，可在同一 PG18 事务中将计数器从服务器**当前最新值** `expected` CAS 更新至真实认证器报告的 `observed`。拒绝计数器回退/相等、错误 owner/RP/禁用账号/吊销凭证/不合规 backup 状态；任何审计缺失必须使事务回滚。只有 0→0 的同步 Passkey 明确返回 `NO_COUNTER_SIGNAL`（零写入、零审核权限），依靠未来真正签名的每次新挑战确保新鲜度。
- **上游安全审查：** Spring Security WebAuthn [#19994](https://github.com/spring-projects/spring-security/issues/19994) 报告其断言计数器可能比较注册时历史值而非最新持久值（截至 2026-11-14 仍需核查官方修复状态）；在可靠解决并通过回归测试前不可启用默认 `Webauthn4JRelyingPartyOperations` 生产认证链。这里的独立 DB CAS 是**必要但不充分**的防御，不等于完成签名检查。
- **真实测试门禁：** 在 `PostgreSqlConsoleSecurityWiringIT` 新增 4 项 PG18 约束与事务负向：注册材料组合外键/历史不可改、最新计数器两次 CAS 与 stale replay、零计数器/禁用/吊销无授权、审计触发器静默吞写时整笔事务回滚。旧 V9→V18 需执行 V10–V18 共 9 次迁移；9 套 JUnit checker 强制 Console ≥12 项，至少 84 条实际用例通过且无跳过。通过的源码编译或旧 V17 CI 不替代本轮 L2。
- **精确源码红灯→绿灯：** 初次候选 [`fc8f935c`](https://github.com/kunlong-luo/lanveriq/commit/fc8f935cecc562a3307cad07548d991dbf778c11) 的 [PG18 #43591751129](https://github.com/kunlong-luo/lanveriq/actions/runs/43591751129) **FAILED**，仅因新模拟审计触发器测试的 PostgreSQL 匿名函数界定符误写为单个 `$`；其余实际 PG18 用例已运行。没有删除/跳过测试或放松约束。修复提交 [`cd36eac5`](https://github.com/kunlong-luo/lanveriq/commit/cd36eac5079ef977cbc3c5df290e068bcbc4ab45) 后，[PG18 九套 #43591972223](https://github.com/kunlong-luo/lanveriq/actions/runs/43591972223) **SUCCESS**（Testcontainers 和严格 JUnit evidence checker 双通过）、[Linux 双跳 TLS1.3 + PG18 #43591972213](https://github.com/kunlong-luo/lanveriq/actions/runs/43591972213) **SUCCESS**、[Java L1 #43591972204](https://github.com/kunlong-luo/lanveriq/actions/runs/43591972204) **SUCCESS**、[集成源码编译 #43591972190](https://github.com/kunlong-luo/lanveriq/actions/runs/43591972190) **SUCCESS**、[RD-2 #43591972201](https://github.com/kunlong-luo/lanveriq/actions/runs/43591972201) **SUCCESS**，全部对应**相同代码 SHA** `cd36eac5`。Issue #5 仍 OPEN、v0.1 NOT RELEASE READY；真实 Passkey 加密验证、CA、Agent 网络/采集/远控一律 OFF。

## 2026-11-14 A11-D2-C — V17 单次挑战持久化与防重放（无生产 WebAuthn 通过路径）

- **真实边界：** [Flyway V17](../../apps/server/src/main/resources/db/migration/V17__stage_one_time_first_pin_passkey_challenges.sql) 新建 `admin_passkey_step_up_challenges`，仅保存 Java 25 `SecureRandom` 生成的 32 字节挑战的 **SHA-256 摘要**，而非挑战原文；将每次挑战不可变地绑定 `actor_id + actor_session_version + first_pin_review_requests.id + devices.id + first_pin.review`，要求期限不超过 3 分钟，消费前未过期，仅允许 `consumed_at` 第一次从 NULL 变为有效时间，不允许重绑、延长有效期、撤销消费或删除历史。PG 触发器不防数据库超级用户或未经授权 SQL，也不证明 WebAuthn assertion 密码学成立。
- **内部事务服务：** `StagedFirstPinPasskeyChallengeLedger` **无 `@Service`/`@Bean`/`@Controller`**，不受生产 Console/Agent 路由调用。在真正的 PostgreSQL 事务中按设备→审核请求→当前证书→管理员/申请人稳定顺序加锁，核对管理员当前 owner 组权限/未禁用/会话版本、申请人版本、证书指纹/epoch/SPKI/有效期、审核人独立及未撤销、尚未完成两票；仅在所有条件成立后发服务端 nonce，记录审计。消费时对原挑战重新执行全部身份/状态核对，要求规范 Base64url、挑战摘要恒定时间匹配、仍未被消费且未过期，经注入 `FirstPinWebAuthnAssertionVerifier.verify` 通过才用 PG18 `UPDATE ... WHERE consumed_at IS NULL ... RETURNING` 消费并原子审计，任一失败回滚。
- **默认拒绝：** 唯一生产 `DenyAllFirstPinWebAuthnAssertionVerifier` 不验证任何签名、始终拒绝。测试中临时注入的 lambda **只在 `src/integrationTest` 使用**，可证明事务与重放不代表 WebAuthn 成功认证；即使合成消费成功，服务 `consumeAfterVerifiedAssertion` 返回 `void`，**不会**生成 `FirstPinReviewStepUpVerifier.VerifiedEvidence`，不会投审核票、签发证书或调用 Agent。不能把 `consumed_at` 或任何合成验证器当作真实 MFA 证明。
- **负向证据门禁：** `PostgreSqlFirstPinDoubleReviewIT` 新增 4 项：CSPRNG + 只存摘要/生产强拒绝；错误审核人/nonce/无签名与重复消费拒绝；会话轮换/篡改历史拒绝；过期挑战即使模拟验证器放行也无法消费。历史 V9→V17 升级必须执行 V10–V17 八项迁移、不追认旧凭证。严格 9 套 JUnit evidence checker 要求 A11 至少 14 项、总计至少 80 项成功且不得跳过；不以 Gradle 编译或旧 SHA 代替真正 PG18。
- **L2 验收成功（精确源码）：** [`af54f0e9`](https://github.com/kunlong-luo/lanveriq/commit/af54f0e978f932b353cbd02c5d0ba576a81b83ba) 的 [真实 PG18 九套 Testcontainers #43587738106](https://github.com/kunlong-luo/lanveriq/actions/runs/43587738106) **SUCCESS**，且实际 `integrationTest` 和严格 JUnit evidence checker **双双 SUCCESS**；此前源码 `3ca31563` 的 [Java L1 #43587730437](https://github.com/kunlong-luo/lanveriq/actions/runs/43587730437) **SUCCESS**，两个 SHA 含不同的 workflow/security 文件，不能混成同 SHA。后续增强生产验证器 L1 代码 [`72c94063`](https://github.com/kunlong-luo/lanveriq/commit/72c9406345c674234aeef28f89fd4e6a1d118e1d) 对应 [Java L1 #43587840715](https://github.com/kunlong-luo/lanveriq/actions/runs/43587840715) **SUCCESS**（已核对终态）。真实 WebAuthn4J assertion/UV/RP-origin、持久 `CredentialRecord`、独立管理员审核证据、真实签发授权与 Pop!_OS24 L3 仍未实现。Issue #5 OPEN，v0.1 NOT RELEASE READY，Agent 联网/采集/自动恢复/远控和真实签发持续 OFF。

## 2026-11-14 A11-D2-B — Spring Security 官方 WebAuthn 接口内部适配（未启用真实认证）

- **官方接口：** `StagedJpaWebAuthnUserEntityRepository` 实现 Spring Security 的 `PublicKeyCredentialUserEntityRepository`，通过现有 V16 `AdminPasskeyUserHandleRepository` + `AdminAccountRepository` 用 JPA 查询持久化 user handle；只接受**恰好 32 字节**句柄，重新确认数据库账户未禁用，以用户不可变句柄作为 `ImmutablePublicKeyCredentialUserEntity.id`，显示登录名仅为名称，不能当授权 ID。用户名搜索要求精确存在并有持久句柄，空/坏句柄、缺用户、已禁用用户一律返回空。
- **不可伪造成功路径：** 官方 `PublicKeyCredentialUserEntityRepository.save/delete` 固定拒绝；`StagedDenyAllWebAuthnCredentialRepository` 实现 `UserCredentialRepository`，在尚无**真实** WebAuthn4J 经过密码学验证且具有完整 `CredentialRecord` 持久化/签名计数器逻辑之前，对 credentialId 和 userId 查询均返回空集，对 save/delete 均拒绝。V16 测试写入的合成 COSE bytes 不会被反序列化为可信 Passkey，也不提供任何管理员登录、MFA、审核或签发资格。
- **双适配器都没有 `@Bean` / `@Component` / 控制器：** 无法自动进入 `HttpSecurity.webAuthn(...)`。现有 Console SecurityFilterChain 与生产 `DenyAllFirstPinReviewStepUpVerifier` 完全不变。Spring Security 官方两个接口的方法契约参考 https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/webauthn/management/PublicKeyCredentialUserEntityRepository.html 和 https://docs.spring.io/spring-security/reference/api/java/org/springframework/security/web/webauthn/management/UserCredentialRepository.html 。
- **可核验测试：** `StagedWebAuthnRepositoryTest` 在 L1 上直接执行官方接口，确认有效身份映射/禁用用户否决、错误用户句柄及全部写入拒绝；`PostgreSqlConsoleSecurityWiringIT` 新增真实 PG18 两项测试：已保存的两个用户句柄应互不串户，用户禁用/会话版本旋转后立即无法查询；即使数据库存在合成 V16 credential，官方 credential adapter 不会返回一份可用于登录的 `CredentialRecord`，且生产真实 Spring Boot context 里无两个 WebAuthn adapter Bean。原有九套 PG18 严格 JUnit evidence checker 提升 Console 需 ≥8 项并强制查到这两个测试。
- **L1/L2 精确验收：** [`93cbf4d8`](https://github.com/kunlong-luo/lanveriq/commit/93cbf4d801989591485df4da22d149446c21b7e9) 的 [真实 PG18 九套 #43587165628](https://github.com/kunlong-luo/lanveriq/actions/runs/43587165628) **SUCCESS**（`integrationTest` + 严格 JUnit evidence checker 均 SUCCESS）；代码相同、仅工作流配置此前版本 [`4f69049b`](https://github.com/kunlong-luo/lanveriq/commit/4f69049b95c583a68736ac0d418e21064f79578c) 的 [Java L1 #43587157538](https://github.com/kunlong-luo/lanveriq/actions/runs/43587157538) **SUCCESS**。L1 与 L2 的精确 SHA 不同，不宣称完全同 SHA；当前仍未启用任何实际凭证认证和审核。，此前 V16+D2-A [`7609311f`](https://github.com/kunlong-luo/lanveriq/commit/7609311fce8c8e21618a3ddf0562f7a275047ab3) 的 [PG18 #43579245567](https://github.com/kunlong-luo/lanveriq/actions/runs/43579245567)、[Java L1 #43579245588](https://github.com/kunlong-luo/lanveriq/actions/runs/43579245588)、[Linux Batch A #43579245530](https://github.com/kunlong-luo/lanveriq/actions/runs/43579245530)、[RD-2 #43579245550](https://github.com/kunlong-luo/lanveriq/actions/runs/43579245550) 已全部 SUCCESS，仅证明 D2-A。
- **仍需单列：** 服务端签发不可预测的一次性注册/审核 nonce，密码学 `attestation` / `assertion` 和 UV/Origin/RP 严格检查、完整 V17 `CredentialRecord` 结构（transport、真实公钥序列化、签名计数器备份语义与审计）、管理员独立审核强认证与 challenge 并发消费、吊销与租户隔离。实现之前绝不启用 `HttpSecurity.webAuthn` 或证书签发/Agent 通道。

## 2026-11-14 A11-D2-A — PostgreSQL 18 Passkey 身份/公钥不可变存储（非可用认证）

- **已提交源码：** Flyway [V16](../../apps/server/src/main/resources/db/migration/V16__stage_administrator_passkey_credential_storage.sql) 新增 `admin_passkey_user_handles`：将 `users.id` 唯一映射到 32 字节、不含登录名或 UUID 明文的 opaque WebAuthn `user.id`；默认**没有历史回填**、没有可用注册或身份申请 API。新增 `admin_passkey_credentials`，要求 credential ID 全局唯一、owner 绑定已有句柄、RP ID/COSE 公钥、计数器、backup 状态和成对吊销标记。数据库触发器禁止身份/公钥/RP 偷换、计数器/验证时间回退、凭证吊销逆转、句柄改写以及历史凭证物理删除；不对有数据库超级用户或修改脚本权限的主体宣称密码学防篡改。
- **JPA 阶段：** `AdminPasskeyUserHandle`/`AdminPasskeyCredential` 仅是 `@Immutable` 只读 Hibernate Entity，`AdminPasskeyUserHandleRepository`/`AdminPasskeyCredentialRepository` 使用 Spring Data JPA `Repository` 并且只声明查询方法（不继承 save/delete），而不是手写应用 JDBC。**尚未**实现官方 `PublicKeyCredentialUserEntityRepository` 和 `UserCredentialRepository` 适配或生产 `Webauthn4JRelyingPartyOperations`；这些数据库行只有库存语义，绝非已注册的可信 Passkey，也不能用于登录、签发或审核。生产 `DenyAllFirstPinReviewStepUpVerifier` 继续固定拒绝。
- **真实数据库负向门禁：** 原有 `PostgreSqlConsoleSecurityWiringIT` 新增三项 PG18 测试：32-byte handle 及唯一约束、credential ID 全球唯一/跨管理员转移与公钥改写拒绝、签名计数器下降/备份状态违约/撤销逆转拒绝；通过 JPA 真正读取库存，失败更新必须完整回滚。V9 旧数据库升级迁移期望同步为 V10–V16 共七次，严格 JUnit evidence checker 在九套中要求新增三项（不准静默漏跑）。
- **范围限制：** 当前没有 WebAuthn 密码学 assertion/attestation 验证、注册挑战或消费、凭证 lifecycle 业务流程、RP ID/Origin 生产配置、用户验证 UV 要求、签名计数器基于真实认证器的更新、跨设备备份策略和实际管理员 L3。未来所有写入必须由正式 Passkey 验证器经安全审查后专门启用；**绝不能把测试写库 helpers 暴露为 API/管理员自助注册入口。**
- **L1/L2 精确验收：** V16 + 只读 Repository 接口最终核心受测源码 [`de1e9579`](https://github.com/kunlong-luo/lanveriq/commit/de1e9579431600ba6f08ba645eab5d6f6d842ea4)：[PG18 九套真实 Testcontainers #43578297794](https://github.com/kunlong-luo/lanveriq/actions/runs/43578297794) **SUCCESS**，其中真实集成测试及严格 JUnit evidence checker 两步均 SUCCESS；[Server L1 #43578297842](https://github.com/kunlong-luo/lanveriq/actions/runs/43578297842)、[集成测试编译 #43578297778](https://github.com/kunlong-luo/lanveriq/actions/runs/43578297778)、[RD-2 默认拒绝 #43578297805](https://github.com/kunlong-luo/lanveriq/actions/runs/43578297805) **同 SHA 均 SUCCESS**。额外的 [RP 改绑拒绝负例 `7609311f`](https://github.com/kunlong-luo/lanveriq/commit/7609311fce8c8e21618a3ddf0562f7a275047ab3) 是后续提交，其自身 CI 必须单独核实；不能将上一 SHA 结果直接当作最新测试通过。此阶段仍不解除 Issue #5、v0.1 NOT RELEASE READY；Agent 联网、采集、自动恢复、远控和真实证书签发保持 OFF。

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
