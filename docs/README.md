# Lanveriq 文档导航 / Documentation home

**2026-11-22 · 单一事实入口 [CURRENT_STATUS.md](CURRENT_STATUS.md) · v0.1 NOT RELEASE READY**

> **2026-11-21 current Web baseline:** 唯一 Web 是 `apps/web` Next.js16.4.5/React19/Tailwind4/Base UI；旧 Vite 和 `web-next` 已删除/迁移。[NXT-24](development/nextjs-path-rename-nxt24-2026-11-21.md) 仅旧迁移证据。公开 Pages 是断开式预览。

## 工作规范与当前阻塞
- **事实**：[CURRENT_STATUS](CURRENT_STATUS.md)；**流程**：[根 AGENTS](../AGENTS.md) · [工程手册](governance/engineering-playbook.md) · [DoD](planning/definition-of-done.md)。
- **发布**：[P0/P1 release-gap register](planning/release-gap-register.md) · [历史真实证据账本](planning/release-readiness-2026-11-13.md)；**文档权威**：[生命周期](governance/document-lifecycle.md) · [本次审计](governance/documentation-audit-2026-11-22.md)。
- **架构决策**：[ADR 索引](architecture/adrs/README.md) · [成熟库优先](development/reuse-first-policy.md) · [真实依赖](development/technology.md) · [工程规范](development/standards.md)。

- [STACK-PG18.7 维护版本升级与真实 PostgreSQL 隔离验收](development/stack-postgres18-7-upgrade-2026-11-23.md)。

- [Gradle 9.9.2 发行版升级与 Wrapper JAR 残余对齐](development/stack-gradle9-9-2-upgrade-2026-11-23.md)。

## 源码与设计
- [系统架构](architecture/system-overview.md) · [设备协议](architecture/device-communication-stack.md) · [注册/PKI](architecture/enrollment-bootstrap.md) · [SQLite/ACK](architecture/agent-persistence.md) · [API](architecture/api-contract.md) · [WebSocket](architecture/websocket-contract.md)。
- [唯一 Next.js 项目](../apps/web/README.md) · [UI 设计系统](development/frontend-design-system.md) · [Craftwork 原创方向](visuals/CONSOLE_CRAFTWORK_DIRECTION.md) · [NXT-28 滚动与窄屏](development/frontend-compact-navigation-nxt28-2026-11-21.md)。
- [C18j 实物 WORM 选型与授权实验交接包（NOT RUN）](security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)。
- [C18i WORM 管理员权限控制图与跨域升级检查（离线，非实物）](security/independent-worm-admin-control-graph-d4-c18i-2026-11-22.md)。
- [C18h WORM 离线证据清单预检（仅结构）](security/independent-worm-offline-evidence-intake-d4-c18h-2026-11-22.md)。
- [C18g 独立 WORM 管理域与实物验收协议（NOT RUN）](security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md) · [C18f 候选研究](security/independent-worm-provider-evaluation-d4-c18f-2026-11-22.md)。
- [FIDO D4-C9–C18e 未可信证据边界总表](security/fido-trust-boundaries.md) · [C17 离线见证历史校验](security/fido-mds-anchored-history-d4-c17-2026-11-22.md) · [身份访问](security/identity-and-access.md) · [威胁模型](security/threat-model.md) · [隐私](security/privacy-and-consent.md)。

## 测试、发布与历史
- [G-05-LNX3 真正安装后 systemd 生效属性只读检查（无安装/无生产授权）](testing/linux-installed-idle-inspector-g05-lnx3-2026-11-23.md)。
- [G-05-LNX2 本地 Debian 离线安装包候选与模拟卸载验收（非真实安装）](testing/linux-native-package-candidate-2026-11-23.md)。
- [G-05-LNX1 Pop!_OS 24.04 systemd 策略安全检查防 -O 绕过](testing/linux-systemd-policy-hardening-2026-11-23.md)（仅 Ubuntu/静态合成，非实机）。
- [L1/L2/L3](testing/strategy.md) · [测试矩阵](testing/test-matrix.md) · [Pop!_OS24 实机 L3](testing/linux-popos24-agent-acceptance.md) · [发布门禁](operations/release.md) · [备份恢复](operations/backup.md)。
- [A–E 批次计划](planning/v0.1-batched-delivery-plan-2026-11-08.md) · [历史 roadmap](planning/roadmap.md) · [历史 backlog](planning/backlog.md) · [D0 历史审批](governance/documentation-gate.md)。
- [历史 AGENTS 完整快照](governance/archive/AGENTS-pre-consolidation-2026-11-22.md) · [旧索引](governance/documentation-index-snapshot-2026-11-21.md) · [CHANGELOG](../CHANGELOG.md) · [贡献](../CONTRIBUTING.md) · [漏洞报告](../SECURITY.md) · [docs-governance CI](../.github/workflows/docs-governance.yml)。

**规范：** 未来功能的详细 SHA、CI run 和失败历史只在 CURRENT_STATUS/里程碑专题维护；AGENTS 与 README 不再堆长报告。所有生产联网、采集、恢复、远控、FIDO 生产信任和 CA 签发仍 OFF。
