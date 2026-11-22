# Lanveriq 当前状态 / Current status

**核对日期：2026-11-21** · **已核对最新安全功能基线：[`2d39d694`](https://github.com/kunlong-luo/lanveriq/commit/2d39d694fb1724f47276a1ef2f4a94d424580895)**（D4-C12 功能已获同 SHA 的 5/5 合成 CI；后续文档提交不自动视为功能重新验收）。本页是 **当前事实、差距与证据索引**，不是发布批准单。后续提交必须按真实 CI/实机证据更新，不得靠时间推测自动改为 PASS。

> **发布状态：v0.1 NOT RELEASE READY。** 生产联网、真实终端/员工采集、自动恢复、远程控制、CA/设备证书签发与真实管理员授权均未批准启用。已通过的 CI 仅覆盖所注明的隔离合成范围。

## 一眼看懂：已做、已测、未放行

| 领域 | 实际源码或验证状态 | 尚未成立的事实 |
| --- | --- | --- |
| 主分支与目录 | GitHub 远程 **只有 `main`**，无开放 PR；`apps/server`、`apps/agent`、`apps/web` 为真实应用目录 | 不代表仓库或组织已完成发布治理 |
| Web 前端 | `apps/web` 是唯一 **Next.js 16.4.5 / React 19 / Tailwind 4 / Base UI**，本地 `127.0.0.1:3100`；NXT-28 完成移动/短窗口导航与 Chromium 验证；[静态 Pages 预览](https://kunlong-luo.github.io/lanveriq/) | 预览不连接真实设备、PostgreSQL 或业务 API；真实登录和授权后监控未通过生产验收 |
| Java 后端 | Java 25 / Gradle / Spring Boot 4.1.2 / JPA-Hibernate / Flyway / PostgreSQL 18.6；真实临时 PostgreSQL Testcontainers 合成事务与服务端单元测试已运行 | 并非真实终端入网、生产 PKI、生产数据、已批准管理员认证 |
| Rust Agent 与通信 | Rust / SQLx SQLite / reqwest / TLS/ACK；存在隔离 Linux TLS1.3→Caddy→Spring→PG18 合成路径证据 | 生产 Agent 仍无授权的常驻采集、联网或自动恢复；尚无 Pop!_OS 24.04 实机 L3 |
| FIDO MDS 安全源 | D4-C9 条目结构、D4-C10 跨条目模型标识符拒绝，以及 **D4-C11 原始签名 JSON 重复字段拒绝（5/5 同 SHA 合成 CI PASS），新增 **D4-C12 双独立预置审核公钥签名约束（5/5 同 SHA 合成 CI PASS），新增 **D4-C13 公开 GlobalSign R46 根 SHA-256 精确识别（源码提交，CI 待验收）******；ES256/RS256、PKIX/CRL、Ed25519 时间绑定使用临时合成证书；返回仅未可信证据 | **没有独立批准的官方 FIDO 根/来源、可信时间、CRL 来源和强制状态准入**；不能作为 WebAuthn MFA/签发授权 |
| 管理员及签发 | 受限制的注册、离线根提案、双人筛查与撤销候选等防提升门禁已有源码和合成测试 | **真实管理员 WebAuthn MFA、可信双审、证书密钥托管/签发均未批准** |
| 远程控制 | RustDesk 独立候选及默认拒绝 **RD-2** 自动回归存在 | **远控会话、生产网络/NAT/中继与许可放行均未完成** |

**读法：** “源码已实现”≠“测试已通过”≠“实机已验收”≠“发布已批准”。每条 PASS 必须有原始功能 SHA、对应运行编号、测试类型和明确边界。禁止把旧提交成功继承到不同 SHA 或把 GitHub Pages 当生产 Console。

## 精确源码证据（不能跨 SHA 借用）

| 里程碑 | 原始功能 SHA | 已确认工作流 | 证明范围 |
| --- | --- | --- | --- |
| **A11-D4-C12** 独立审核签名绑定 | [`2d39d694`](https://github.com/kunlong-luo/lanveriq/commit/2d39d694fb1724f47276a1ef2f4a94d424580895) | [Java 25 + 10 必测 JUnit #44895483823](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483823) · [PG18 Testcontainers #44895483791](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483791) · [PG 源码编译 #44895483778](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483778) · [Next.js L1 #44895483732](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483732) · [RD-2 OFF #44895483711](https://github.com/kunlong-luo/lanveriq/actions/runs/44895483711) · **5/5 PASS** | 仅双独立模拟审核公钥绑定合成 JWS/根/CRL/时间证明；无真实官方来源或审核员身份 |
| **A11-D4-C11** 原始签名 JSON 重复键拒绝 | [`7282bc44`](https://github.com/kunlong-luo/lanveriq/commit/7282bc446c2b9ac27a8f0fea4830fbcd29f829a6) | [Java L1，9 必测 JUnit #44894793997](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793997) · [PG18.6 容器 #44894793992](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793992) · [PG 源码编译 #44894793990](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793990) · [Next L1 #44894793972](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793972) · [RD-2 OFF #44894793994](https://github.com/kunlong-luo/lanveriq/actions/runs/44894793994) · **5/5 PASS** | 原始签名头/载荷 JSON 的重复字段拒绝，合成证书/CRL/时间；没有官方来源批准 |
| **A11-D4-C10** 重复模型标识符拒绝 | [`2368163e`](https://github.com/kunlong-luo/lanveriq/commit/2368163e588e6e94f74a748bdc6e162b9c4c6b9a) | [Java 25 单元测试，实际 JUnit XML 8 个必需方法](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055170) · [PG18.6 Testcontainers](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055206) · [PG 集成源码编译](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055072) · [Next.js 快速检查](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055069) · [RD-2 OFF](https://github.com/kunlong-luo/lanveriq/actions/runs/44893055089) · **5/5 PASS** | 离线合成签名元数据；没有官方签名源授权 |
| **NXT-28** 短窗口/移动端 UI | [`e76cd345`](https://github.com/kunlong-luo/lanveriq/commit/e76cd34505dd8e49833ab9c0b61421288c43c353) | [Web L1](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686781) · [真实 Chromium](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686713) · [断开式 Pages](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686808) · [RD-2 OFF](https://github.com/kunlong-luo/lanveriq/actions/runs/44882686775) · **4/4 PASS** | 断开式 UI、键盘、触控、导航，不是实际业务登录 |
| **D4-C10 文档证据记录** | [`0989af08`](https://github.com/kunlong-luo/lanveriq/commit/0989af08174eb25666c217caa48a2c2dc9c874f3) | [Java L1](https://github.com/kunlong-luo/lanveriq/actions/runs/44893296333) · [Next.js L1](https://github.com/kunlong-luo/lanveriq/actions/runs/44893296334) · [RD-2 OFF](https://github.com/kunlong-luo/lanveriq/actions/runs/44893296322) · **3/3 PASS** | 仅记录与文档更新后的回归，不是新的 D4-C10 功能验收 |
| **Batch A 合成请求链** | [`758f8d9a`](https://github.com/kunlong-luo/lanveriq/commit/758f8d9a) | [隔离 Rust→Caddy TLS1.3→Tomcat→PG18 严格 ACK L2](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857) | 先前源码上的合成网络/事务链路；不代表 Pop!_OS 实机、真实签发或当前生产网络 |

[D4-C13 R46 公共根身份核验（CI 待验收）](security/fido-mds-public-r46-root-reference-d4-c13-2026-11-22.md) · [D4-C12 独立来源审核门禁（5/5 同 SHA 合成 CI）](security/fido-mds-independent-provenance-d4-c12-2026-11-21.md) · [D4-C11 签名 JSON 重复键门禁（5/5 同 SHA 合成 CI）](security/fido-mds-duplicate-json-d4-c11-2026-11-21.md) · [详细安全验收 D4-C10](security/fido-mds-duplicate-identifiers-d4-c10-2026-11-21.md) · [D4-C9 前序验收](security/fido-mds-payload-screen-d4-c9-2026-11-21.md) · [NXT-28 前端证据](development/frontend-compact-navigation-nxt28-2026-11-21.md) · [完整带日期上线验收账本（含旧失败和历史修复）](planning/release-readiness-2026-11-13.md)

## 尚未完成的发布关键路径（Linux-first）

1. **可信来源与身份：** FIDO 官方 MDS 信任根及独立来源授权、CRL/可信时间获取与托管、元数据状态评估、抗回退和真实管理员 WebAuthn MFA/双审证据。
2. **设备信任与闭环：** 独立批准 CA/密钥托管；在获授权的 Pop!_OS 24.04 设备上以最小权限 systemd/CSR/证书完成真实 Agent → 受信 mTLS → PG18 → 已授权 Next.js 的整条业务闭环，覆盖吊销、重复 ACK、离线重试。
3. **正式 Console 数据路径：** 浏览器会话、Origin/CSRF、权限隔离、授权设备列表、Monitoring、WSS 提示和 REST 重同步在真实隔离部署中验收（静态预览不具备这些能力）。
4. **可恢复性与发布：** 备份还原、密钥轮换、日志、升级/回滚、安装/停止、最小权限、法律通知/数据最小化及组织书面批准。远控/录制属于独立额外安全门禁，未列为默认开启功能。
5. **验收责任：** L1 相关源码测试、L2 独立合成集成、L3 **真实 Pop!_OS 24.04**、外部安全/合规、发布签字均不可互相替代。Windows 11 非本轮 Linux-only 首发前置条件，但不得宣称已正式支持。

当前跟踪入口：[Batch A Issue #5（仍开放）](https://github.com/kunlong-luo/lanveriq/issues/5) · [当前交付批次 A–E](planning/v0.1-batched-delivery-plan-2026-11-08.md) · [L1/L2/L3 测试规则](testing/strategy.md) · [真实 Linux 验收](testing/linux-popos24-agent-acceptance.md) · [发布门禁](operations/release.md)。

## 使用与维护

- **要运行 UI：** [前端运行说明](../apps/web/README.md)；`cd apps/web && corepack pnpm install --frozen-lockfile && corepack pnpm dev`；访问 `http://127.0.0.1:3100/overview`。必须从当前 `main` 拉取并处理本地未提交变更；不要启动旧 Vite/5173。
- **要开发或选择技术：** [实际依赖基线](development/technology.md) · [工程规范](development/standards.md) · [复用优先](development/reuse-first-policy.md)。
- **要查历史：** [文档总索引](README.md) · [原始完整索引归档](governance/documentation-index-snapshot-2026-11-21.md)。旧 D0/早期 NXT/测试失败报告都保留为带日期历史，不是当前操作命令。
- **要修改现状：** 只在通过同一 SHA CI 后更新本页当前矩阵；同步具体里程碑验收、[发布账本](planning/release-readiness-2026-11-13.md)、相关 README/AGENTS/CHANGELOG。未知一律写 **OPEN / NOT TESTED**，不可猜测。
