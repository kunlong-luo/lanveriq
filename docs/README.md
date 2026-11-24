# Lanveriq 文档入口 / Documentation home

**ACTIVE INDEX · 2026-11-24 · v0.1 NOT RELEASE READY.** 根 [中文 README](../README.zh.md) / [English README](../README.md) 用于“目录有什么用、怎么开始”；本页用于“哪些文件才是当前规范”。**不要从日期最早的验收报告开始安装 Agent。**

> **2026-11-21 current Web baseline:** 唯一后台 Web 为 `apps/web` **Next.js 16.4.5**，不是旧 React/Vite，也不是 `apps/web-next`。[NXT-24 历史迁移与真实验证](development/nextjs-path-rename-nxt24-2026-11-21.md)。Tauri 的 Vite 只编译桌面窗体，不提供第二套 Web 管理后台。

## 先阅读的五份权威文件

| 什么时候读 | 唯一权威 | 不能推断 |
| --- | --- | --- |
| **这周哪些功能真正可用、验证到什么程度** | [CURRENT_STATUS.md](CURRENT_STATUS.md) | L1/L2 合成 CI ≠ 生产部署 |
| **上线仍缺哪些必要条件** | [G-01–G-10 发布缺口](planning/release-gap-register.md) | 任一 Gate OPEN 就不能宣称 RELEASE READY |
| **以后怎么写代码** | [AGENTS.md](../AGENTS.md) · [工程手册](governance/engineering-playbook.md) · [DoD](planning/definition-of-done.md) | 不用旧 D0 当授权 |
| **工程架构/技术栈** | [ADR 索引](architecture/adrs/README.md) · [技术版本](development/technology.md) · [成熟库原则](development/reuse-first-policy.md) · [开发规范](development/standards.md) | 历史技术栈和未批准的网络模型不构成实现 |
| **目录整理及质量评估** | [文件/目录保留删除报告](governance/repository-layout-audit-2026-11-24.md) · [评分和优先级](planning/project-scorecard.md) | 评分不是安全认证或发布许可 |

[文档生命周期](governance/document-lifecycle.md) 规定 LIVE-FACT、LIVE-GAP、ADR、SPEC、HISTORICAL、INDEX 各自的权威边界。阅读历史报告请看日期和确切 CI SHA，**不能拿旧报告覆盖当前状态**。

## 当前工作领域与规范入口

| 领域 | 当前设计 / 行为规格 | 验收与使用边界 |
| --- | --- | --- |
| Linux Rust Agent | [设备协议](architecture/device-communication-stack.md) · [SQLite/ACK](architecture/agent-persistence.md) · [终端源码](../apps/agent/README.md) | [CI Linux 实际只读 OS/CPU/RAM 端到端 L2](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) · 不是实际 Pop!_OS 客户设备 |
| Spring Boot / PG18 | [系统架构](architecture/system-overview.md) · [安全设备准入](architecture/enrollment-bootstrap.md) · [服务端源码](../apps/server/README.md) | [L1/L2/L3 策略](testing/strategy.md) · 真实生产 PKI/管理域未批准 |
| Next.js 业务与 UI | [唯一 Web 源码](../apps/web/README.md) · [UI 设计系统](development/frontend-design-system.md) | [Agent→Spring→PG18→Next.js 实际 CI L2](testing/agent-inventory-to-nextjs-full-chain-l2-2026-11-24.md) · 正式 Console 登录仍关闭 |
| Tauri GUI 与发行 | [ADR-0013](architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [桌面源码](../apps/desktop/README.md) · [GUI 用户旅程](product/linux-desktop-installer-v01.md) | [L2B-21 只读 GUI](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) · [L2B-22 未安装 Polkit DENY-ALL](development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md) · [L2B-23 真实 Agent signed-APT OCI 9/9 L2](development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) · [L2B-24 真正 polkitd/DBus + Rust OS 身份 6/6 L2 拒绝](development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md) · [L2B-25 root-side 系统 D-Bus 发送方身份 8 次真实拒绝](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) · [L2B-26 真正 Polkit 本地覆盖成 YES 仍被 root-side 独立发行门禁拒绝](development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) · **仍非正式签名/Polkit 用户安装器** |
| TLS/双网/远控候选 | [TLS/Caddy 参考](../deploy/caddy/Caddyfile.reference) · [双网测试](../deploy/network/verify_dual_access.py) · [RustDesk 候选策略](operations/rustdesk-pilot.md) | RD-2 OFF；公网/远控/录屏/访问许可未批准 |
| FIDO/WORM/身份与合规 | [FIDO 来源信任边界](security/fido-trust-boundaries.md) · [威胁模型](security/threat-model.md) · [授权矩阵](security/authorization-matrix.md) · [合法隐私](security/privacy-and-consent.md) | [独立 WORM 实际验收计划](security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)：**NOT RUN** |

## 不要误删、不要照着历史报告运行

- `docs/development/` 和 `docs/testing/` 中的 L2B、NXT、D4 日期文件**以验收事实和失败历史为主**；最新版本/下一任务只读 [CURRENT_STATUS](CURRENT_STATUS.md) 和 [发布 Gate](planning/release-gap-register.md)。
- `docs/architecture/`、`docs/security/` 保留协议/授权设计，不因一些功能默认关闭就删掉。
- `docs/diagrams/` 是 [Mermaid 工程图](diagrams/README.md)；`docs/visuals/` 是 [8 张示意 SVG 图及设计规范](visuals/README.md)，并非真实产品截图，均有用途。
- `docs/governance/archive/` 保存历史 [AGENTS 完整快照](governance/archive/AGENTS-pre-consolidation-2026-11-22.md)。[2026-11-21 旧目录索引](governance/documentation-index-snapshot-2026-11-21.md) 是**历史快照，不是今日入口**。
- 最早 D0/旧网页参考和当时的命令路径已被源码迭代取代。仓库已直接删除不再适用的旧 `pagevela-reference.md`；需要追溯可查 Git 历史。

## 操作与历史凭据

[开发/安装说明](operations/installation.md) · [发布操作与拒绝规则](operations/release.md) · [备份恢复](operations/backup.md) · [Pop!_OS 实机 L3 验收标准](testing/linux-popos24-agent-acceptance.md) · [历史完整 CI/失败账本](planning/release-readiness-2026-11-13.md) · [当前交付计划](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [CHANGELOG](../CHANGELOG.md)。

旧验收过程仍能通过 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions) 与 Git 历史核对。唯一产品发布结论：**v0.1 NOT RELEASE READY；生产网络、采集、恢复、远控、证书签发 OFF。**
