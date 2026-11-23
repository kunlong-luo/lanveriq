# A11-D4-C18j — 独立 WORM 选型决策与授权实验交接包

**ACTIVE SPEC · 2026-11-23 · main only · 供应商/介质 NOT SELECTED · 真实实验 NOT RUN · G-02/G-08 OPEN · v0.1 NOT RELEASE READY**

本交接包将 [C18g 实物验收协议](independent-worm-custody-acceptance-d4-c18g-2026-11-22.md)、[C18f 候选分析](independent-worm-provider-evaluation-d4-c18f-2026-11-22.md)、[C18i 管理员控制关系图](independent-worm-admin-control-graph-d4-c18i-2026-11-22.md) 转换为可供独立保管方、SRE、安全、法务签署的选型与授权实验计划。本文**不构成授权**，不会执行删除命令、访问介质、启用 SDK 或生产网络。只有明确具名的独立操作人员和书面授权才能开始现场破坏性实验。

## 一、必须先决策的材料（全部 PENDING）

| 责任主体 | 必须提供并签字的参数 | 当前状态 |
| --- | --- | --- |
| 产品与法务 | 证据数据分类、允许保管的内容、保留期限与 legal hold、法域/驻留地、合法到期删除 | PENDING |
| 安全架构 | 威胁主体（DBA、CI、RGW、RADOS root、虚拟化 root、KMS root）、残余风险、真正独立的组织/身份提供方 | PENDING |
| 运营/SRE | 估算每日报文量与峰值，确认外部发表延迟/离线积压、RPO/RTO、成本与退出要求 | PENDING |
| WORM 实际保管方 | 实物供应商、介质/版本、场所、设备和备份 root、快照、紧急账户、读写和法务保留权限 | PENDING |
| 独立密钥/时钟保管方 | 见证私钥、公钥固定/轮换、加密 KMS/备份恢复、可信时间来源、失效和吊销流程 | PENDING |
| 实验授权者 | 专用一次性非生产实验集群/磁带、测试账号、允许破坏范围、观察人、停止/恢复流程 | NOT AUTHORIZED |
| 发布审批者 | 独立审核报告、签字人及 G-02/G-08/G-05 等 Gate 各自的退出证据 | PENDING |

没有保留期限、法律批准、独立保管者或真实可安全销毁的实验环境，禁止自行开始物理或底层 RADOS 破坏测试。所有真实员工/设备数据、私钥和敏感权限图严禁进入公开 CI 或 GitHub Issue。

## 二、供应商中立选型与硬性淘汰条件

**方案 A：独立管理的在线不可变存储 + 外部独立高水位 + 第二管理域恢复副本。** 适合要求及时发表和远程精确版本读回的场景；必须证明账号、组织级超级管理员、身份提供方、KMS、备份与存储底层权限与 Lanveriq 应用/DBA 独立。S3 Object Lock COMPLIANCE 或等价 API 本身不证明这些权限已独立，也不证明服务商底层 root 绝对不可破坏。法律、驻留地、供应商许可及成本均待批准。

**方案 B：由独立物理保管方持有的 LTO WORM（或证实真正只能追加的物理介质）。** 可作为离线实物副本候选，但必须核实正版 WORM 介质序列、驱动器/固件、写入/弹出/双人移交/库存/周期读回、持久索引及钥匙恢复。磁带自身不能证明最新高水位，若需要低延迟发表，还须独立在线高水位发布机制。不能凭 LTO 标签直接认定其符合法律保留。

**方案 C：独立运维的 Ceph RGW Object Lock Compliance。** 仅是 API 版本保留候选或双域架构的一份副本。必须把 RGW/S3 账号与底层 CephX 的 MON/MGR/OSD、RADOS 池和命名空间、服务 root、机器/虚拟化 root、备份与快照管理员**分开证明**。即使 RGW 删除返回 AccessDenied，同一个 Ceph cluster-root 仍可以绕过 RGW 层；若所有最新 H 及副本受同一底层管理员控制，作为唯一 G-02 外部 WORM **REJECT**。不能将 RGW 与同一集群 RADOS 算成两个独立域。

**直接淘汰：** 同管理员 NAS、同 DBA 的数据库审计触发器、chattr 不可变位、容器只读卷、本地 SHA-256 或假冒的 NON_BYPASSABLE_WORM 字符串；都不具备抗共同回滚的独立管理域。

**不预先打总分或指定供应商。** 每个方案按下列字段分别写 MEETS / DOES_NOT_MEET / UNVERIFIED：API 精确版本保留、底层 root 与紧急身份隔离、外部单调高水位、独立钥匙及时间、可达性/发表延迟、第二保管域读回/灾难恢复、法律合规保留/销毁、使用成本和退出方案。任一强制安全项为 UNVERIFIED，决策为 **NOT SELECTED**。建议优先比较在线独立高水位与异域不可变副本的组合，而不是把一个 Ceph RGW 对象当成完整的安全闭环。

## 三、真实权限证明：WORM-01 / WORM-10

必须由各自的独立组织安全管理员导出经签署的权限快照，而非应用 JSON 自述。涵盖数据库超级管理员、应用部署、CI、WAL/PITR/备份；RGW 对象管理员、CephX caps（MON/MGR/OSD）、RADOS pool/namespace、cluster-root、OS/虚拟化管理、快照、恢复和原始盘；组织 IdP 组继承及紧急提权；签名密钥/KMS、独立高水位、第二副本、可信时间和独立审核者。

建立真实的“谁可以控制谁”有向图，包含跨组织超级管理员和多跳角色委托。检查任意攻击者是否能够同时回退 DB、所有高水位、副本、固定的公钥和审计。C18i 的 Python 预检只能检查**声明图的自洽性**，不能认证真实 CephX/HSM/组织管理员，因此即使预检通过也不得关闭 WORM-01。

**合格标准：** 在攻破应用 + DBA + 一处存储底层管理员之后，另一真正独立保管者仍掌握最近已发表的见证 N、固定钥匙指纹、可信读回和足以恢复的数据。若共同最高管理者可清除所有副本或高水位，则 P2/P3 不成立，结果为 FAIL/BLOCKED。

## 四、单调高水位与真实崩溃矩阵

明确区分三个事实：

- DB_TAIL_N：PostgreSQL 已真实提交的 V30 末尾序列 N。
- EXTERNAL_HIGH_WATER_H：由**独立**保管者已持久化并可读取、不可倒退的最高见证序列 H，包含 stream ID、签名头 SHA-256、前驱摘要、key ID、外部签名时间及介质版本。
- VERIFIED_N：独立审核链对最新 H=N、指定 N 的**真实**不可变介质字节/版本、签名与公钥固定、保留证据、备份副本以及可信时间都完成读回验证。它最多成为后续独立安全门禁的输入，绝不自动授权 FIDO/MFA/CA/生产。

| 状态 | 条件 | 必须行为 |
| --- | --- | --- |
| UNPUBLISHED | DB 已提交 N，外部 keeper 未发表或结果未知 | N 不可声称有真实 WORM 保障；信任准入 DENIED/UNKNOWN |
| PENDING_ACK | 写外部 keeper 超时，ACK 丢失 | 不得将本地计数器提升为 H；必须独立读回、幂等重试 |
| ACK_READBACK_PENDING | 收到外部保存声称但指定版本、当前 H 或签名未验证 | DENIED/UNKNOWN，禁止将 latest API 响应视为可信当前 |
| VERIFIED | 最新外部 N、真实版本和外部信任均成功验证 | 仅证据见证门禁可被审查，其他发布 Gate 仍 OPEN |
| CONFLICT_OR_REWIND | DB 低于 H、同序号不同摘要、旧 H、分叉、钥匙异常 | DENIED/QUARANTINE，独立事件处理，不可自动重置 |
| EXTERNAL_UNAVAILABLE | keeper 无法读回或两份副本无法核查 | DENIED/UNKNOWN，不能回退到 DB 自述 SHA |

**授权实验必须实际验证八种情形：**

1. DB 提交前崩溃：未提交的 N 不应出现为有效外部已发布结论。
2. DB 提交之后、外部持久发表之前崩溃：N 仍是 UNPUBLISHED，记录如何受控补发表。
3. keeper 已持久接收但客户端 ACK 丢失：外部实际 H=N 应能被独立查回，幂等重试不能写入异摘要的 N。
4. N+1 并发/乱序到达与旧 N 重放：外部 keeper 必须提供经过证实的单调性/冲突拒绝；Object Lock 的版本列表不能自动视为原子 compare-and-advance。
5. 利用真实隔离 PG18 pg_dump/pg_restore 或 PITR 把 DB 恢复为 N-1，外部 H=N 不动：必须拒绝旧 DB，即使旧签名仍有效。
6. 旧 DB 和整个旧签名历史一起回退：真正独立的 H=N 仍须阻止回退；单纯本地保留 H 不算。
7. 第一介质/root 被控制或介质丢失，第二管理域恢复：必须从独立处实际读回最近 N 并计时；明确区分“阻止底层破坏”和“破坏后发现并恢复”。
8. KMS/签名钥匙不可用、时钟错误、读权限撤销、网络中断：必须停用信任提升、记录最大未发表积压与恢复方案。

**不能假设对象锁本身具备高水位的原子单调更新能力。** 如果选型后无法证明外部 keeper 在并发、重试、崩溃及分区下不会回退/分叉，则标记 DESIGN BLOCKED，先设计并获批准真正的独立单调保管机制。发布周期、未发表时限、RPO/RTO 和法律期限均须由责任方给出，不应自行填入“默认值”。

## 五、获批非生产实验执行顺序

1. **冻结授权范围：** 安全/SRE/法务签署目标实验介质、真实权限、允许的动作、非生产合成数据、停止条件、观察人与恢复责任；未获签字不得执行任何破坏动作。
2. **基线：** WORM-01/02；真实导出权限清单，分两次发布 N-1/N，异域读取指定版本、签名、高水位与副本。
3. **API 否定测试：** WORM-03；指定版本删除/缩短保留/删除标记/保留策略测试，并由另一身份再次读取原始字节。HTTP 403 不是唯一 PASS 证据。
4. **基础设施权限验证：** WORM-04/10；只在具名一次性集群/介质受控模拟底层 root、备份和快照风险，若底层破坏成功，必须证明另一域仍可发现并恢复，不能称 Ceph 单集群“不可删除”。不向公共 Issue 发布敏感破坏命令或凭据。
5. **密钥/崩溃/回退：** WORM-05/06/07；运行八项真实故障矩阵、真实 PG18 N-1 恢复与 H=N 比对、独立钥匙/时间故障注入。
6. **物理与灾备：** WORM-08/09/11；二保管方恢复，若选择 LTO 则验证真实 WORM 介质序列/离线盘，法务确认 legal hold 与到期删除过程（不提前执行真实删除）。
7. **独立结论：** 安全、SRE、法务和发布权限方对外部密封原始证据和残余风险逐项签字。只有组织正式验收才能考虑关闭 G-02/G-08；不能借此关闭 G-01/G-03/G-04/G-05 等其他门禁。

每项记录：真实介质/版本、环境、授权范围和操作人类型、原始日志安全位置、操作前后读回摘要、独立最新 H、外部时间、观察人签字、实际 PASS/FAIL/BLOCKED/NOT RUN、失败及恢复情况。所有敏感原件保留在非 GitHub 的独立保管库，仅在公开仓库写脱敏索引。

## 六、应由外部保管方提供的私有证据目录

| 私有材料编号 | 必须包含什么 | 当前 |
| --- | --- | --- |
| DECISION/retention | 数据分类/法务保留期限/持有与销毁规则及正式签字 | PENDING |
| CUSTODY/roles | 真正不同 IdP、cluster-root、DBA、OS/虚拟化 root、继承授权与紧急账户的签名审核 | NOT RUN |
| KEYS/time | 实际 HSM/KMS 签名/加密、吊销/轮换/恢复、独立可信时间 | NOT RUN |
| WITNESS/N | 真实签名链、指定对象版本或介质序列、外部 H=N、异域最新副本读回 | NOT RUN |
| NEGATIVE/WORM-01-11 | 每一个受控负向实验的真实原始结果与独立复核 | NOT RUN |
| RECOVERY/restore | 真 PG18 N-1 与 N 对照、H=N 存活、第一副本损毁时的实际恢复和计时 | NOT RUN |
| APPROVAL/independent | 安全/法务/SRE/发布四方真实签署与记录的残余风险 | PENDING |

**此前软件结果（仅供关联、不冒充本轮实物验收）：** C18i [功能 SHA 87f7ada9](https://github.com/kunlong-luo/lanveriq/commit/87f7ada98b0f9a7c80c3b44752c90a9f0eca173e) 已通过 4/4 同 SHA 源码与合成 CI；此前 C18f [功能 SHA 118939b4](https://github.com/kunlong-luo/lanveriq/commit/118939b43a764150c1330c5e649557d391900f2f) 的真实隔离 PG18 L2 在不同 SHA 通过。这两轮**均未**运行真正外部 WORM/CephX/root、独立密钥/时间或 Pop!_OS 实机。

## 七、最终状态与下一项

**截至 2026-11-23：介质/提供方 NOT SELECTED，实验 NOT AUTHORIZED，实际外部 H NOT HELD，保管/密钥/时间 NOT VERIFIED，WORM-01–11 NOT RUN。** G-02/G-08/G-05 OPEN，v0.1 NOT RELEASE READY。生产联网、员工/设备采集、自动恢复、RD-2、管理员真实 FIDO 与证书签发全部 OFF。

**下一步只能由有权限的现实主体完成：** 安全/SRE/法务确定选型参数、具名独立保管方和外部高水位，批准牺牲性实验环境，然后才可执行现场验证。这里不构成任何供应商采购、密钥开通或破坏测试授权。

**上游原始资料：** [Ceph RGW Object Lock 安全边界](https://www.ceph.io/en/news/blog/2025/rgw-deep-dive-3/) · [CephX 用户权限](https://docs.ceph.com/en/latest/rados/operations/user-management/) · [RADOS 存储池权限和操作](https://docs.ceph.com/en/latest/rados/operations/pools/)。注意 Ceph latest 文档针对开发版本，实物验收必须按实际部署的 Ceph 版本和发行渠道核查。
