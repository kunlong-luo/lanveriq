# Lanveriq 文档导航 / Documentation home

**更新日期：2026-11-22** · **单一现行状态入口：[CURRENT_STATUS.md](CURRENT_STATUS.md)** · **发布状态：v0.1 NOT RELEASE READY**。本页只负责导航，不重复维护某个阶段的 CI 结论；所有“已通过/仍未完成”以状态页及其精确源码 SHA 为准。旧文档标题、日期和旧目录名是历史事实，不自动代表今天仍有效。

> **2026-11-21 current Web baseline:** `apps/web` 是唯一 **Next.js 16.4.5** UI（`127.0.0.1:3100`）；旧 Vite 已删除、`apps/web-next` 已在 NXT-24 迁移。参见 [NXT-24 路径与 CI 原始记录](development/nextjs-path-rename-nxt24-2026-11-21.md)。当前浏览器预览是 **断开式静态/本地 UI**，不含正式业务数据、登录、远控、采集或证书签发。

## 1. 新成员从这里开始

| 我要做什么 | 当前权威文档 | 限制 |
| --- | --- | --- |
| 看项目当前真实进度、阻塞项、CI | **[CURRENT_STATUS](CURRENT_STATUS.md)** · [Linux-first 上线验收账本](planning/release-readiness-2026-11-13.md) | 不能把隔离合成 CI 当上线批准 |
| 了解产品范围 | [需求](product/requirements.md) · [功能闭环](product/functional-closure.md) · [用户故事](product/user-stories.md) · [非功能需求](product/nonfunctional.md) | 需求不等于已实现 |
| 本地启动唯一 Web | [apps/web README](../apps/web/README.md) · [安装/开发指引](operations/installation.md) | 不运行已删除的 Vite / web-next |
| 开发 Java / Rust / Web | [工程目录规范](development/standards.md) · [当前真实依赖](development/technology.md) · [复用优先](development/reuse-first-policy.md) · [AGENTS.md](../AGENTS.md) | Java 用 Gradle/JPA；不重造组件 |
| 执行自动化与实机验收 | [L1/L2/L3 策略](testing/strategy.md) · [测试矩阵](testing/test-matrix.md) · [Pop!_OS 24.04 L3](testing/linux-popos24-agent-acceptance.md) | CI 不能代替真实 Pop!_OS |
| 审查安全与发布 | [FIDO MDS D4-C11](security/fido-mds-duplicate-json-d4-c11-2026-11-21.md) · [身份与访问](security/identity-and-access.md) · [发布门禁](operations/release.md) | v0.1 NOT RELEASE READY |

## 2. 架构与关键决议

- [系统结构](architecture/system-overview.md) · [组件与边界](architecture/components.md) · [部署](architecture/deployment.md) · [协议栈与 TLS/ACK](architecture/device-communication-stack.md) · [Agent 持久化](architecture/agent-persistence.md) · [设备身份](architecture/device-identity.md) · [设备库存](architecture/device-inventory.md) · [事件交付](architecture/event-delivery.md)。
- [API 契约](architecture/api-contract.md) · [WebSocket 契约](architecture/websocket-contract.md) · [健康/缺口状态](architecture/health-and-gap-semantics.md) · [配置策略](architecture/policy-configuration.md) · [数据模型](architecture/data-model.md)。
- **现行关键 ADR：** [0007 成熟生态优先](architecture/adrs/0007-mature-ecosystem-first.md) · [0008 Next.js](architecture/adrs/0008-nextjs-app-router-console.md) · [0009 设备协议](architecture/adrs/0009-agent-transport-selection.md) · [0010 RustDesk 候选](architecture/adrs/0010-rustdesk-remote-control-integration.md) · [0011 双网访问规划](architecture/adrs/0011-rustdesk-dual-network-access.md)。其余 [ADR 0001–0006](architecture/adrs/0001-monorepo.md) 保留各自历史决议范围，不得覆盖已明确取代的技术选型。
- [全部工程 Mermaid 图](diagrams/README.md) · [示意图与可编辑 SVG](visuals/README.md) 只表达设计/方案，不应作为实机或线上功能证据。

## 3. 实施与前端

- **Linux-first 执行：** [A–E 批次计划](planning/v0.1-batched-delivery-plan-2026-11-08.md) · [当前路线图](planning/roadmap.md) · [需求 backlog（含历史 ticket）](planning/backlog.md) · [历史实施顺序](planning/v0.1-implementation-sequence.md) · [Definition of Done](planning/definition-of-done.md)。
- **前端现行实现：** [设计系统与真实 CSS token](development/frontend-design-system.md) · [NXT-28 低高度侧栏/移动端](development/frontend-compact-navigation-nxt28-2026-11-21.md) · [NXT-27 数据证据状态](development/frontend-evidence-state-nxt27-2026-11-21.md) · [NXT-26 Base UI Select](development/frontend-base-ui-select-nxt26.md)。
- **Web 迁移历史：** [NXT-23/NXT-24 单入口整理](development/nextjs-single-ui-consolidation-2026-11-21.md) · [NXT-24 改名](development/nextjs-path-rename-nxt24-2026-11-21.md) · [NXT-18 视觉验收](development/frontend-visual-regression-nxt18.md)。旧的 Vite 指令仅可作历史。
- **子系统设计：** [监控中心](development/monitoring-center.md) · [后台/Agent 技术栈](development/backend-stack.md) · [Rust Agent](development/rust-agent-stack.md) · [管理员审核/签发阶段计划](development/batch-a11-trusted-administrator-review-issuance.md)。

## 4. 安全、测试、运维与合规

- **D4-C15 V30 离线获取收据 PostgreSQL 18 不可变审计链（CI 待验收）：** [源码/信任边界及真实 Testcontainers 验收](security/fido-mds-acquisition-ledger-d4-c15-2026-11-22.md)，永久仅未可信。

- **D4-C14 MDS/CRL 离线签名获取收据（4/4 同 SHA 触发项 PASS）：** [精确材料绑定与审核边界](security/fido-mds-acquisition-receipt-d4-c14-2026-11-22.md)；来源 URL 及 TLS 声明不能代替真实网络证明。

- **D4-C13 GlobalSign R46 公开根指纹精确识别（5/5 同 SHA 合成 CI PASS）：** [离线候选核对与身份/信任边界](security/fido-mds-public-r46-root-reference-d4-c13-2026-11-22.md)。参考证书和公开 SHA-256 不是正式组织信任批准。

- **D4-C12 两个独立审核公钥与离线来源绑定（5/5 同 SHA 合成 CI PASS）：** [完整安全契约](security/fido-mds-independent-provenance-d4-c12-2026-11-21.md)，仅合成未可信证据，不能视为真实 FIDO 来源或签发授权。

- **D4-C11 最新签名 JSON 安全检查（5/5 同 SHA 合成 CI PASS）：** [原始签名 JWS 的重复 JSON 字段拒绝](security/fido-mds-duplicate-json-d4-c11-2026-11-21.md)；此门禁仅验证合成未可信材料，不是受批准的 FIDO 来源。

- **安全：** [D4-C10 唯一标识门禁](security/fido-mds-duplicate-identifiers-d4-c10-2026-11-21.md) · [D4-C9 条目形状门禁](security/fido-mds-payload-screen-d4-c9-2026-11-21.md) · [RBAC 矩阵](security/authorization-matrix.md) · [威胁模型](security/threat-model.md) · [隐私/告知](security/privacy-and-consent.md) · [数据留存](security/data-retention.md)。
- **测试：** [总策略](testing/strategy.md) · [总矩阵](testing/test-matrix.md) · [PG18 合成验收](testing/server-postgres18-manual.md) · [Console 会话验收](testing/console-session-manual.md) · [网络/TLS+Agent](testing/linux-caddy-spring-pg18-rust-csr.md) · [Pop!_OS 实机验收](testing/linux-popos24-agent-acceptance.md)。
- **运维：** [开发安装](operations/installation.md) · [发布与升级/回滚](operations/release.md) · [备份恢复](operations/backup.md) · [监控](operations/monitoring.md) · [排障](operations/troubleshooting.md) · [RustDesk 隔离试验](operations/rustdesk-pilot.md)。
- **决议/历史：** [D0 技术批准（仅源码开发）](governance/d0-decision-package-2026-10-30.md) · [待决事项](governance/open-decisions.md) · [文档准入规则](governance/documentation-gate.md) · [2026-11-08 文档审计](governance/documentation-audit-2026-11-08.md) · [完整旧版导航与证据快照](governance/documentation-index-snapshot-2026-11-21.md)。[变更日志](../CHANGELOG.md) / [安全报告](../SECURITY.md)。
