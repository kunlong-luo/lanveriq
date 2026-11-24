# Lanveriq 文档导航 / Documentation home

**2026-11-24 · 单一事实入口 [CURRENT_STATUS.md](CURRENT_STATUS.md) · v0.1 NOT RELEASE READY**

> **2026-11-21 current Web baseline:** 唯一 Web 是 `apps/web` Next.js16.4.5/React19/Tailwind4/Base UI；旧 Vite 和 `web-next` 已删除/迁移。[NXT-24](development/nextjs-path-rename-nxt24-2026-11-21.md) 仅旧迁移证据。公开 Pages 是断开式预览。

## 工作规范与当前阻塞
- [DESKTOP-L2B-17 Agent 安装意图协议 DENY-ALL 与 Polkit 威胁模型](development/desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md)：[Ubuntu24 39/39 安全测试 #45324617901 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45324617901)；[仅供拒绝测试的固定样例](planning/desktop-agent-install-intent-deny-all.json)，没有实际 Polkit 桥或签名安装包。
- [DESKTOP-L2B-16 GUI-only L2 事实与 Agent 发行权限隔离的拒绝优先契约](development/desktop-tauri-l2b16-release-evidence-denial-contract-2026-11-24.md)：[Ubuntu24 30/30 测试 #45324049249 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45324049249)；[可审机器状态](planning/desktop-release-evidence-boundary.json) 维持所有生产/签名/安装 Gate 为 OFF/OPEN，**不是可信发行签名或授权**。
- **事实**：[CURRENT_STATUS](CURRENT_STATUS.md)；**流程**：[根 AGENTS](../AGENTS.md) · [工程手册](governance/engineering-playbook.md) · [DoD](planning/definition-of-done.md)。
- **发布**：[P0/P1 release-gap register](planning/release-gap-register.md) · [历史真实证据账本](planning/release-readiness-2026-11-13.md)；**文档权威**：[生命周期](governance/document-lifecycle.md) · [本次审计](governance/documentation-audit-2026-11-22.md)。
- **架构决策**：[ADR 索引](architecture/adrs/README.md) · [成熟库优先](development/reuse-first-policy.md) · [真实依赖](development/technology.md) · [工程规范](development/standards.md)。

- [STACK-PG18.7 维护版本升级与真实 PostgreSQL 隔离验收](development/stack-postgres18-7-upgrade-2026-11-23.md)。

- [Gradle 9.9.2 发行版升级与 Wrapper JAR 残余对齐](development/stack-gradle9-9-2-upgrade-2026-11-23.md)。

- [Spring Boot 4.2.0 与 Springdoc 3.2.1 升级、Java25/PG18.7/TLS 验收](development/stack-spring-boot4-2-upgrade-2026-11-23.md)。

- [Rust 1.100 默认工具链迁移与跨平台 CI（新 SHA 结果待验证）](development/stack-rust1-100-upgrade-2026-11-23.md)。

- [2026-11-23 技术栈五批升级验收总账（真实 CI、遗留差距、回退边界）](development/stack-upgrade-acceptance-2026-11-23.md)。

- [Tauri 2 桌面 UI + Rust 原生只读主机检测（Ubuntu24 源码/本机构建 2/2 L1 CI PASS，非发行版）](development/desktop-tauri-first-source-2026-11-23.md) · [桌面源码 README](../apps/desktop/README.md)。当前为离线预览，**不能安装 Agent**。
- [DESKTOP-L2B-14 Ubuntu24 原生 APT 缓存独立签名索引与完整闭包验收](development/desktop-tauri-l2b14-ubuntu-apt-prefetch-cache-lab-2026-11-24.md)：[双 runner 175 份缓存验收 #45321096944 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944)，且 [GUI-only L2 #45322290248 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 已完整复用；**发行签名/Agent 安装器/Pop!_OS L3 仍未验收**。
- [DESKTOP-L2B-15 指定源码 GUI-only Debian 完整 L2 通过的包 SHA/ABI/OCI 证据及 L3 交接](development/desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md)：[c4f04e44 / #45322290248 **SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248)；**不代表可分发 Agent 安装器**。
- [DESKTOP-L2B-13 当前源码真实 GUI-only .deb 严格审计](development/desktop-tauri-l2b13-current-source-gui-deb-reaudit-2026-11-24.md)：同源码 L2 已通过；真实 Pop!_OS L3、签名和安装授权未执行。
- [DESKTOP-L2B-12 早期图形兼容性只读预检与失败/修复历史](development/desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md)：当时 d44327b5 的 [L1 #45314250801 成功](https://github.com/kunlong-luo/lanveriq/actions/runs/45314250801)；后来当前只读 GUI 已在 [L2B-15 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 通过严格原生 L2，但**仍不能发行安装 Agent**。

## Linux-first 图形安装与后台 Agent（2026-11-23 产品设计，**未实现**）

- [ADR-0013 桌面安装器 + systemd 独立 headless Agent 决策](architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [v0.1 GUI 用户旅程/交互/权限定义](product/linux-desktop-installer-v01.md) · [Pop!_OS24 图形安装 L1/L2/L3 验收矩阵](testing/linux-desktop-installer-acceptance.md)。
- [现有安装与开发说明](operations/installation.md)。**Tauri 2 只读桌面原型源码与真实 Ubuntu24 GUI-only 未签名 .deb 候选已存在；但尚非可分发的 Agent 图形安装器。正式签名、受限安装授权和实际 Pop!_OS24 COSMIC L3 均未通过。**

## 源码与设计
- [系统架构](architecture/system-overview.md) · [设备协议](architecture/device-communication-stack.md) · [注册/PKI](architecture/enrollment-bootstrap.md) · [SQLite/ACK](architecture/agent-persistence.md) · [API](architecture/api-contract.md) · [WebSocket](architecture/websocket-contract.md)。
- [唯一 Next.js 项目](../apps/web/README.md) · [UI 设计系统](development/frontend-design-system.md) · [Craftwork 原创方向](visuals/CONSOLE_CRAFTWORK_DIRECTION.md) · [NXT-28 滚动与窄屏](development/frontend-compact-navigation-nxt28-2026-11-21.md)。
- [C18j 实物 WORM 选型与授权实验交接包（NOT RUN）](security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)。
- [C18i WORM 管理员权限控制图与跨域升级检查（离线，非实物）](security/independent-worm-admin-control-graph-d4-c18i-2026-11-22.md)。
- [C18h WORM 离线证据清单预检（仅结构）](security/independent-worm-offline-evidence-intake-d4-c18h-2026-11-22.md)。
- [C18g 独立 WORM 管理域与实物验收协议（NOT RUN）](security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md) · [C18f 候选研究](security/independent-worm-provider-evaluation-d4-c18f-2026-11-22.md)。
- [FIDO D4-C9–C18e 未可信证据边界总表](security/fido-trust-boundaries.md) · [C17 离线见证历史校验](security/fido-mds-anchored-history-d4-c17-2026-11-22.md) · [身份访问](security/identity-and-access.md) · [威胁模型](security/threat-model.md) · [隐私](security/privacy-and-consent.md)。

- [DESKTOP-L2A Tauri 2 GUI-only 离线 .deb 候选构建／严格包审查（历史真实 Ubuntu CI 通过，不含 Agent）](development/desktop-tauri-offline-deb-candidate-2026-11-23.md)。

## 测试、发布与历史
- [G-05-LNX3 真正安装后 systemd 生效属性只读检查（无安装/无生产授权）](testing/linux-installed-idle-inspector-g05-lnx3-2026-11-23.md)。
- [G-05-LNX2 本地 Debian 离线安装包候选与模拟卸载验收（非真实安装）](testing/linux-native-package-candidate-2026-11-23.md)。
- [G-05-LNX1 Pop!_OS 24.04 systemd 策略安全检查防 -O 绕过](testing/linux-systemd-policy-hardening-2026-11-23.md)（仅 Ubuntu/静态合成，非实机）。
- [L1/L2/L3](testing/strategy.md) · [测试矩阵](testing/test-matrix.md) · [Pop!_OS24 实机 L3](testing/linux-popos24-agent-acceptance.md) · [发布门禁](operations/release.md) · [备份恢复](operations/backup.md)。
- [A–E 批次计划](planning/v0.1-batched-delivery-plan-2026-11-08.md) · [历史 roadmap](planning/roadmap.md) · [历史 backlog](planning/backlog.md) · [D0 历史审批](governance/documentation-gate.md)。
- [历史 AGENTS 完整快照](governance/archive/AGENTS-pre-consolidation-2026-11-22.md) · [旧索引](governance/documentation-index-snapshot-2026-11-21.md) · [CHANGELOG](../CHANGELOG.md) · [贡献](../CONTRIBUTING.md) · [漏洞报告](../SECURITY.md) · [docs-governance CI](../.github/workflows/docs-governance.yml)。

**规范：** 未来功能的详细 SHA、CI run 和失败历史只在 CURRENT_STATUS/里程碑专题维护；AGENTS 与 README 不再堆长报告。所有生产联网、采集、恢复、远控、FIDO 生产信任和 CA 签发仍 OFF。
