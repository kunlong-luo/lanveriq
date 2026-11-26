# Lanveriq 文档导航 / Documentation home

**ACTIVE INDEX · 2026-11-25 · v0.1 NOT RELEASE READY。** [中文项目首页](../README.zh.md) · [English README](../README.md)。只在这里寻找当前文件入口；历史报告中的「下一步」「未实现」应按其日期理解。

> **2026-11-21 current Web baseline:** `apps/web` 是唯一 Next.js 16 Console；[NXT-24 旧 Vite→Next.js 迁移](development/nextjs-path-rename-nxt24-2026-11-21.md) 是历史验收。Tauri 内部使用 Vite 只构建桌面窗口，不存在 `apps/web-next`。

## 唯一现行权威

| 问题 | 现行唯一入口 |
| --- | --- |
| 什么已经实现、哪次真实 CI 通过？ | [CURRENT_STATUS.md](CURRENT_STATUS.md)（LIVE-FACT） |
| 能上线了吗、哪些 G-01～G-10 还缺外部批准？ | [release-gap register](planning/release-gap-register.md)（LIVE-GAP） |
| 下一阶段优先实施什么？ | [Linux 首发实施路线](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)（ACTIVE PLAN） |
| 架构/技术/协议依据？ | [ADR](architecture/adrs/README.md)、[设备通信协议](architecture/device-communication-stack.md)、[技术栈](development/technology.md) |
| 工程规约、DoD 与项目评分？ | [AGENTS](../AGENTS.md)、[工程手册](governance/engineering-playbook.md)、[DoD](planning/definition-of-done.md)、[评分](planning/project-scorecard.md) |

## 每个子目录负责什么

| 目录 | 权威或代表入口 | 不应误解为 |
| --- | --- | --- |
| `architecture/`、`adrs/` | [系统架构](architecture/system-overview.md)、[ADR](architecture/adrs/README.md) | 历史 ADR 自动赋予生产许可 |
| `security/` | [身份](security/identity-and-access.md)、[FIDO 边界](security/fido-trust-boundaries.md) | 已建成正式 CA/FIDO/WORM |
| `development/` | [工程版本](development/technology.md)、[现行 UI 设计系统](development/frontend-design-system.md)、[L2B-28](development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) | 每份日期报告都代表当前进度 |
| `testing/` | [L1/L2/L3 规则](testing/strategy.md)、[Linux/COSMIC 安装验收](testing/linux-desktop-installer-acceptance.md) | Ubuntu/合成 CI 等于真机 L3 |
| `planning/` | [唯一 Gate](planning/release-gap-register.md)、[唯一 Linux 路线](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)、[原始验收账本](planning/release-readiness-2026-11-13.md) | 旧 roadmap、backlog、批次旧稿仍是现行计划 |
| `governance/`、`archive/` | [生命周期](governance/document-lifecycle.md)、[目录审计](governance/repository-layout-audit-2026-11-24.md)、[固定 Git SHA 归档](governance/archive/README.md) | 归档内容需要复制回工作树 |
| `operations/`、`product/` | [开发环境](operations/installation.md)、[发行规则](operations/release.md)、[Linux GUI 用户旅程](product/linux-desktop-installer-v01.md) | 用户端已可安装 |
| `diagrams/`、`visuals/` | [Mermaid 图](diagrams/README.md)、[SVG 图](visuals/README.md) | 产品运行截图 |

## 源码预览发布候选（不等于发行）

[预览版 v0.1.0-dev.1 说明](operations/source-preview-v0.1.0-dev.1.md)、[源级门禁](../tools/release/verify_source_preview.py)和 [#45557337790 实际 L1 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790)。**NOT PUBLISHED**，无 tag/Release/正式 Agent 安装包；所有生产 G 门禁 OPEN。

## 最近通过的 Linux 业务链验收

[断网后的 Rust SQLite 清单恢复 → mTLS ACK → PG18 当前 revision → Next.js 手动刷新 L2](development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md)（**功能 SHA `db570181`：[Linux L2 #45554279736 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736)；仅隔离 L2，正式发布仍 OFF**）。上一个 OS/CPU/RAM 首次入库 L2 已单独 [PASS](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)，不要混淆两次功能 SHA。

**新增 L2 PASS：** [Linux 恢复后迟到重复/旧版本/冲突不改变 PG18 当前行](development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md)，功能 `9ba85399`、[Linux #45571463449 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449)；只适用于隔离合成环境，G-06 OPEN。

**新增 L2 PASS：** [ACK 未交付 Rust、PG18 已提交、原 SQLite 请求重送](development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md)；功能 f83aeab6、[Linux #45581861955 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)。这是合成未交付 HTTP ACK，不是实际 TCP 断线。

**新阶段 L2 PASS：** [PG18 真实 SIGKILL/WAL 恢复后同请求重送](development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md)；功能 3e5b7009、[Linux #45583968475 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583968475)。生产灾备与实际设备仍未验收。

**G-08：** [恢复库重启/计时 #45615090308 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45615090308)；[真实 Spring JPA 验证恢复库](development/linux-pg18-restored-spring-jpa-context-l2-2026-11-25.md) [#45616194271 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45616194271)（Docker none→bridge）；修复 `45683e64` [#45618414247 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45618414247)。[跨 runner PG18 恢复 L2 #45742025955 PASS](development/linux-pg18-cross-runner-artifact-restore-l2-2026-11-26.md)；[备份 runbook](operations/backup.md)；G-02/G-08 OPEN。

## G-05 原生 APT：不同 ELF、分离签名与冷缓存双回放

[L2B-31 不同 Rust ELF 15/15](development/desktop-tauri-l2b31-pinned-historical-rust-elf-apt-oci-2026-11-25.md) · [L2B-32 分离 CI witness 17/17](development/desktop-tauri-l2b32-separated-gpgv-witness-signed-apt-replay-oci-2026-11-25.md) · [L2B-33 冷缓存双回放/分离 RO 批准锚点 20/20](development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md)（[实际 OCI #45569177575 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575)）。真实 APT 可采用旧不同 Packages，CI 分离批准 witness 摘要拒绝同序列旧 witness 联合回放；**仍缺少生产外部高水位、正式安装授权或 Pop!_OS L3**。

## 去重与防断链

已删除旧 `docs/planning/backlog.md`、`roadmap.md`、`v0.1-batched-delivery-plan-2026-11-08.md` 和 `v0.1-implementation-sequence.md` 的**工作树副本**；原始完整内容仍在[固定 Git SHA 历史索引](governance/archive/README.md)。旧全文 AGENTS/目录快照、早期审计也在那里可查。真正独立的失败、攻击负测、FIDO/WORM 和 Agent/PG18 报告保留原专题，不按文件大小批量删。

`docs-governance` 递归检查全部 `docs/**/*.md` 本地相对链接并拒绝重新引入过期目录。它不验证外部网址、标题锚、正式签名、真实用户许可或 Pop!_OS 实机。

**发行边界：无可正式分发的 Agent 安装包；G-01～G-10 OPEN；生产联网、采集、远控、自动恢复和 CA 签发 OFF；v0.1 NOT RELEASE READY。**
