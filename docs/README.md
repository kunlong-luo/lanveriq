# Lanveriq 文档导航 / Documentation home

**ACTIVE INDEX · 2026-11-24 · v0.1 NOT RELEASE READY。** 从这里读取**当前权威**，而不是按旧 CI 文章末尾的“下一步”执行操作。根入口：[中文](../README.zh.md) / [English](../README.md)。

> **2026-11-21 current Web baseline:** 唯一 Console 在 `apps/web`，Next.js 16.4.5；[NXT-24 旧 Vite→Next.js 路径迁移](development/nextjs-path-rename-nxt24-2026-11-21.md) 是**历史验收**。Tauri 使用 Vite 只构建桌面窗口，不能建立 `apps/web-next`。

## 五个权威入口（不可再复制成多份）

| 用途 | 唯一当前文件 |
| --- | --- |
| 目前实际完成了什么 | [CURRENT_STATUS.md](CURRENT_STATUS.md)（真实功能 SHA、CI、边界） |
| 何时能上线、缺哪项 | [release-gap register](planning/release-gap-register.md)（G-01～G-10 的 LIVE-GAP，全部 OPEN） |
| 怎么实现、怎么审查 | [AGENTS.md](../AGENTS.md) · [工程手册](governance/engineering-playbook.md) · [DoD](planning/definition-of-done.md) |
| 架构/技术/目录归属 | [ADR 索引](architecture/adrs/README.md) · [技术栈](development/technology.md) · [唯一目录审计](governance/repository-layout-audit-2026-11-24.md) |
| 实施目标/自评 | [Linux 首发路线](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [工程自评分](planning/project-scorecard.md)（非发布许可证） |

[文档生命周期](governance/document-lifecycle.md) 分别定义 LIVE-FACT、LIVE-GAP、SPEC、ADR、INDEX、HISTORICAL。**每个事实只在一个当前入口维护，精确旧日志进日期报告。**

## 按责任而不是日期找文档

| 目录 | 定位 | 关键入口 |
| --- | --- | --- |
| `architecture/` · `adrs/` | 系统、设备协议、正式架构决议 | [系统图](architecture/system-overview.md) · [设备通信协议](architecture/device-communication-stack.md) · [ADR](architecture/adrs/README.md) |
| `security/` | 信任根、PKI、授权/隐私/WORM 要求与独立验收计划 | [安全身份](security/identity-and-access.md) · [FIDO 边界](security/fido-trust-boundaries.md) |
| `development/` | 开发规范/UI 设计 + **每阶段 dated 代码与 CI 证据** | [技术版本](development/technology.md) · [UI tokens](development/frontend-design-system.md) · [L2B-26 真实 Polkit 覆盖拒绝](development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |
| `testing/` | L1/L2/L3 验收规则、真实与合成 CI 区分、失败保留 | [验收策略](testing/strategy.md) · [Linux/COSMIC 安装 L1/L2/L3](testing/linux-desktop-installer-acceptance.md) |
| `planning/` | **唯一 Gate + 下一步路线**；旧计划/账本为历史 | [Gate 清单](planning/release-gap-register.md) · [Linux 路线](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [历史账本](planning/release-readiness-2026-11-13.md) |
| `governance/` | 文档所有权、审计规范、有限历史归档 | [生命周期](governance/document-lifecycle.md) · [目录审计](governance/repository-layout-audit-2026-11-24.md) · [archive](governance/archive/README.md) |
| `operations/` · `product/` | 授权安装/备份运维指南，预期产品行为 | [开发环境](operations/installation.md) · [未来 Linux GUI 安装用户旅程](product/linux-desktop-installer-v01.md) |
| `diagrams/` · `visuals/` | Mermaid 可编辑结构图、SVG **概念图** | [工程图](diagrams/README.md) · [8 张示意图](visuals/README.md) |

## 最近 Linux GUI/Agent 安全验收索引

真实 Agent 的临时 Signed-By 原生 APT install/reinstall/remove [L2B-23 9/9](development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)；原生 Polkit 用户侧 [L2B-24](development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)；root-side D-Bus 真实发送方/防重放 [L2B-25](development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md)；本地恶意规则让 OS **实际 YES** 而 root ReviewLab 仍拒绝 [L2B-26](development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md)。 **新增 [L2B-27 原生 APT 10/10 旧签名 Release Date 仍被接受](development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md)：独立防回退高水位尚缺，未进行实际 Agent 降级。**这些都是 **networkless Ubuntu24 OCI L2**，不是正式 Polkit 安装器、用户授权或 Pop!_OS 实机 L3。

**2026-11-21 旧 docs/README 的全文快照已从工作树删除**，不会再与现行导航竞争；原文可从[固定 Git SHA 的历史快照](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/docs/governance/documentation-index-snapshot-2026-11-21.md)核对。较早 D4/NXT/PG18 的真实失败和成功保留在各 dated reports、[历史原账本](planning/release-readiness-2026-11-13.md) 和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。

**安装安全边界：没有可分发的一键 Agent 包，生产联网/采集/远控/恢复/CA 默认 OFF；v0.1 NOT RELEASE READY。**
