# Lanveriq Changelog · 里程碑索引

**HISTORICAL SUMMARY · 2026-11-24 · v0.1 NOT RELEASE READY。** 本页只记录可复核的重大能力与文档治理**里程碑**，不再复制每次 CI 的整段执行日志。唯一最新事实以 [CURRENT_STATUS](docs/CURRENT_STATUS.md)、唯一发行 Gate 以 [release-gap register](docs/planning/release-gap-register.md) 为准。

> **原始完整历史不丢失：** 2026-11-24 整理前约 86 KB 的逐次 CI、修复 SHA、历史设计与失败记录，可在 [固定 Git SHA 的旧版 CHANGELOG 全文](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/CHANGELOG.md) 找回；每项真正功能的完整环境/命令/限制/失败原因仍在各 `docs/development/`、`docs/testing/`、`docs/security/` 日期报告和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。删除的是工作树里**重复的历史时间线**，不是源代码或 Git 历史证据。

## 2026-11-24 — L2B-27 原生 APT 已签旧 Release Date 回放风险实测

- [功能 SHA `09a4e3b3` / Ubuntu24 Signed-By Agent OCI #45395513243 — 10/10 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243)：真实 Rust Agent Debian 包经临时签名源 install/reinstall/remove，8 类错误公钥/签名/过期/篡改/包名拒绝，再在**相同 APT lists 状态**先载入较新有效 Release，之后观察系统仍接受**同 signer、同 Packages、较旧但有效的签名 Release Date**。无 Agent 降级/正式发行或真实主机安装。**G-05 独立可信高水位/正式 signer 仍 OPEN**。[实际报告和前期 CI 失败历史](docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md)。

## 2026-11-24 — 目录与文档权威去重第二轮

- 在唯一 `main` 保留 `apps/web` Next.js、`apps/server` Java/Spring/Gradle/PG18、`apps/agent` Rust、`apps/desktop` Tauri；没有新的 `web-next`、Maven、第二控制平面、root 安装器或生产权限。
- 精简 [`CURRENT_STATUS`](docs/CURRENT_STATUS.md)、[唯一 G-01～10 Gate](docs/planning/release-gap-register.md)、[工程评分](docs/planning/project-scorecard.md)、[Linux 首发路线](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)、[发布/回滚操作](docs/operations/release.md)及桌面 README；根 [中文](README.zh.md) / [English](README.md) README 展示真实源代码与文档目录结构。
- 从工作树清理两份重复大快照（约 19 KB 旧 docs 首页、约 189 KB 旧 AGENTS 里程碑流水账），用 [governance/archive pinned Git 索引](docs/governance/archive/README.md) 保留**完整可核验历史内容**。增强 [文档结构负例 CI](apps/web/scripts/verify-documentation-baseline.mjs) 和 [对应突变测试](apps/web/scripts/test_verify_documentation_baseline.mjs)：限制 LIVE 文件过度膨胀、禁止恢复过期目录和旧相对链接。**不批量删除唯一 FIDO/WORM/PKI/真实失败证据**。[精确删除与保留审计](docs/governance/repository-layout-audit-2026-11-24.md)。

## 2026-11-24 — 原生安装/权限边界 L2B-21～26 真实隔离验收

| 阶段 | 核心可核对结果 | 不可推导的发布权限 |
| --- | --- | --- |
| L2B-21/22 | [只读 Tauri GUI #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676)、未安装 Polkit XML all-no 和离线负测 | GUI 仅 `inspect_host`、`inspect_popos_preflight` 两个只读 IPC |
| L2B-23 | [真实 Agent .deb + 临时 Signed-By APT 9/9 #45376989866](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) | 只有 Ubuntu24 无网络 OCI 包生命周期 |
| L2B-24/25 | [真实 Polkit 用户侧 6/6 #45379398181](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)、[root-side D-Bus UID/PID + nonce #45382618806](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | ReviewLab 只审核并拒绝，**没有 APT root 安装事务** |
| L2B-26 | [本机恶意 Polkit .rules 使 OS 真 YES，但 root 仍 NOT_APPROVED #45385719766](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | 规则仅在隔离 OCI 生成，绝不可用于正式设备授权 |

更早 NXT/Next.js UI、PG18/Caddy mTLS/Rust ACK、D4/FIDO/WORM/独立审计在 [旧版完整 CHANGELOG](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/CHANGELOG.md)、[历史验收账本](docs/planning/release-readiness-2026-11-13.md)、[专门文档索引](docs/README.md)和对应原 GitHub Actions 中保留；**不因本页缩短而改变这些功能或证据的可信程度**。

**v0.1 NOT RELEASE READY；G-01–G-10 OPEN；生产联网、设备采集、远控、自动恢复和 CA 签发默认 OFF。**
