# Lanveriq Changelog · 里程碑索引

**HISTORICAL SUMMARY · 2026-11-25 · v0.1 NOT RELEASE READY。** 本页只记录可复核的重大能力与文档治理**里程碑**，不再复制每次 CI 的整段执行日志。唯一最新事实以 [CURRENT_STATUS](docs/CURRENT_STATUS.md)、唯一发行 Gate 以 [release-gap register](docs/planning/release-gap-register.md) 为准。

> **原始完整历史不丢失：** 2026-11-24 整理前约 86 KB 的逐次 CI、修复 SHA、历史设计与失败记录，可在 [固定 Git SHA 的旧版 CHANGELOG 全文](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/CHANGELOG.md) 找回；每项真正功能的完整环境/命令/限制/失败原因仍在各 `docs/development/`、`docs/testing/`、`docs/security/` 日期报告和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。删除的是工作树里**重复的历史时间线**，不是源代码或 Git 历史证据。

## 2026-11-25 — L2B-30 两版真实 Debian 包的原生 APT 降级 L2

- [功能修复 SHA `a7899ed1`](https://github.com/kunlong-luo/lanveriq/commit/a7899ed167ccc22b675bdffc07a3f396c456c7f7) · [真实 Ubuntu24 OCI #45561370502 13/13 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45561370502)：从已严格审计的 Rust Agent `dev1` 候选构造控制版本为 `dev0` 的第二份真实 Debian 包（**ELF 与 systemd 单元相同**），临时签名 `Signed-By` APT 在 OCI 中验证无人值守降级拒绝、明确覆盖后实际降级/升级和操作员私有配置/state 保留，无服务启动/开机启用。
- [首次 CI #45560934991 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45560934991) 因 `apt-cache policy | grep -q` 在 `pipefail` 下退出 141，未进入降级交易；修正为消耗完整管道后复验成功。完整范围/限制参见 [L2B-30 专项报告](docs/development/desktop-tauri-l2b30-native-apt-two-agent-versions-oci-2026-11-25.md)。**无真正不同源码/ELF、发行信任根、独立高水位或客户安装授权，G-05 OPEN**。

## 2026-11-25 — L2B-29 原生 APT 旧签名 Release / 不同 Packages 行为 L2

- [功能 SHA `ef995461`](https://github.com/kunlong-luo/lanveriq/commit/ef9954610622c4ff8a09e1548a1418bdf5c10ce0)、[原生 Ubuntu24 无网络 OCI #45559034203 11/11 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45559034203)：复用真实 Rust Agent `.deb` 与一次性签名 APT；确认旧有效签名 InRelease 被接受，而**内容不同的旧 Packages 未被重新下载，APT 继续显示较新元数据**；新增拒绝优先负测在未来旧内容被采用时 FAIL。
- 三次失败及分段定位 [#45558040197](https://github.com/kunlong-luo/lanveriq/actions/runs/45558040197)、[#45558342030](https://github.com/kunlong-luo/lanveriq/actions/runs/45558342030)、[#45558662699](https://github.com/kunlong-luo/lanveriq/actions/runs/45558662699) 保留在[独立 L2B-29 报告](docs/development/desktop-tauri-l2b29-native-apt-different-valid-signed-index-2026-11-25.md)，**不能将旧签名 Release 接受推导成包索引回退或正式防回退**。G-05 OPEN。

## 2026-11-25 — v0.1.0-dev.1 源码预览发布前拒绝门禁 L1

- [功能 SHA `a1473c9e`](https://github.com/kunlong-luo/lanveriq/commit/a1473c9e1908d959113dbf2829ed4636270ee578)、[GitHub Actions #45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790)：新增只读 `tools/release/` 门禁、负面测试、固定 SHA/真实 CI/全 G-OPEN 元数据、候选发布说明与 source-only 工作流；明确拒绝任何已发行/已签名/可安装声明。仅 L1 合约门禁，不是发行信任根。
- [候选说明](docs/operations/source-preview-v0.1.0-dev.1.md) 严格注明 **NOT PUBLISHED**；没有创建 Git Tag、GitHub Release 或 Agent `.deb`。正式签名、高水位、Polkit、Pop!_OS 实机及法务/SRE 依然 OPEN，任何生产开关未变。

## 2026-11-25 — Linux 全链清单断网后人工恢复实际 L2 PASS

- [功能 SHA `db570181`](https://github.com/kunlong-luo/lanveriq/commit/db570181d603a7f069a85a23c009658e9a8bed4c)：CI-only Rust 从断网保留的 SQLite revision 2 重发，真实 Caddy/TLS1.3→Spring/JPA→PG18 当前版本 2、精确 ACK；新 pending revision 3 在凭据过期/吊销后仍保持。授权 Chromium/Next.js 的手动刷新真实 HTTP 取回当前 PG18 项。
- 同 SHA 的 [Linux Batch A #45554279736（两 jobs SUCCESS）](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736)、[Agent #45554279741 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279741)，[专项原始验收文档](docs/development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md)。最初 Rustfmt [#45554071256 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45554071256) 保留。
- **无正式 Agent 复原任务、无真实设备/员工采集、无正式 CA/MFA、Polkit broker、外部 WORM 或 Pop!_OS 实机。G-01～G-10 均 OPEN。**

## 2026-11-24 — 规划文件真实裁剪与目录权威再收敛

- 在唯一 `main` 先修复[历史架构/治理引用](https://github.com/kunlong-luo/lanveriq/commit/479602698cd3d4a4bde61f60aa6f2ee10e6cfa5c)，再修复[现行规范引用](https://github.com/kunlong-luo/lanveriq/commit/234850dfef72b4fbe4dcc5f4157270c4dc7860e6)，将旧规划引用分类为固定 Git SHA 历史或现行 Linux 实施路线。
- [实际清理 `20719b0e`](https://github.com/kunlong-luo/lanveriq/commit/20719b0e6bc020117c64d8ecaf894ee84d972e82)：删除 3 份已被取代的旧规划全文，Git tree **860→857 文件、docs Markdown 211→208**；[固定历史原文](docs/governance/archive/README.md)不丢失，现行[目录审计](docs/governance/repository-layout-audit-2026-11-24.md)由约 9.8 KB 收敛到约 3.7 KB。
- 更新中英文 README、唯一 docs 导航和生命周期规则，并新增三份旧计划重新引入及相对链接的拒绝负测。**仅文档/源级安全回归，不是 Agent、APT 防回退或 Pop!_OS L3 新验收。**

## 2026-11-24 — 文档全域完整性与 L2B-28 发行证据门禁

- [文档全域链接检查 `dc5cb839`](https://github.com/kunlong-luo/lanveriq/commit/dc5cb839cf47959e707a1b5d0b041d484b14d1cf)：将 CI 从重点索引扩展到全部 `docs/**/*.md` 相对链接，包含非入口历史文档负测；[#45400108591 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45400108591)。
- [旧实施工单序列归档 `1a323eb6`](https://github.com/kunlong-luo/lanveriq/commit/1a323eb628965ce5e92385de9542efa13fbd8639)：从当前规划目录删除约 35 KB 过时全文，并在[固定 SHA 归档索引](docs/governance/archive/README.md)保留原始记录；[#45400491080 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45400491080)。
- [L2B-28 离线否认越权证据 v2 `03420e61`](https://github.com/kunlong-luo/lanveriq/commit/03420e61ae61e8e2739eb3cc436685c910d356a1)：旧签名 APT Release 接受风险、无独立高水位及临时发布者边界进入拒绝优先 JSON/测试；[#45400903033 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45400903033)；[文档 CI #45400903073 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45400903073)。**本项未实现真正防回退器或发行安装权限；G-05 OPEN**。

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
