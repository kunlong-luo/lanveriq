# Lanveriq Changelog · 里程碑索引

**HISTORICAL SUMMARY · 2026-11-25 · v0.1 NOT RELEASE READY。** 本页只记录可复核的重大能力与文档治理**里程碑**，不再复制每次 CI 的整段执行日志。唯一最新事实以 [CURRENT_STATUS](docs/CURRENT_STATUS.md)、唯一发行 Gate 以 [release-gap register](docs/planning/release-gap-register.md) 为准。

> **原始完整历史不丢失：** 2026-11-24 整理前约 86 KB 的逐次 CI、修复 SHA、历史设计与失败记录，可在 [固定 Git SHA 的旧版 CHANGELOG 全文](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/CHANGELOG.md) 找回；每项真正功能的完整环境/命令/限制/失败原因仍在各 `docs/development/`、`docs/testing/`、`docs/security/` 日期报告和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。删除的是工作树里**重复的历史时间线**，不是源代码或 Git 历史证据。

## 2026-11-25 — G-06 PostgreSQL18 实际 SIGKILL/WAL 恢复增量（首轮失败后修复 L2 PASS）

- 功能 [3e5b7009](https://github.com/kunlong-luo/lanveriq/commit/3e5b70090aaf35cf4845ae4055877cbbb117515d) 在先前真实 ACK withheld 已提交 revision 2 后，杀掉 PostgreSQL18 容器并经 WAL 重启，要求 PG18 持久化四字段不变，随后 Rust SQLite 原请求取得幂等 ACK。[Linux L2 #45583968475 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583968475)。[报告](docs/development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md)。首轮 [Linux #45583252985 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45583252985) 是 Docker 动态端口在重启后漂移；后改为 kernel 选取并固定回环端口，修复后全链通过。**仍非生产 G-06/G-08 验收。**

## 2026-11-25 — G-06 服务端已提交、Rust ACK 未知的合成重送实验

- [功能 SHA `f83aeab6`](https://github.com/kunlong-luo/lanveriq/commit/f83aeab61199fa59256e3422c84a34b34383ab91) 将 `revision 2` 原始 SQLite JSON 在 Caddy 恢复后先由 CI 驱动通过真实 mTLS 提交到 PG18，故意不向 Rust 交付 ACK；Rust 再按现有 Reqwest 精确重复确认逻辑处理同份 pending，要求 PG18 状态不再改写。[专项报告](docs/development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md) · [原生 L2 #45581861955 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)。**这仅模拟 ACK 未见，不是 TCP 层真断线；生产 OFF。** [Agent #45581861744 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861744)、[Server #45581861736 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861736) 已核验。

## 2026-11-25 — G-06 恢复后乱序/重复清单 L2 与文档治理

- [功能 SHA `9ba85399`](https://github.com/kunlong-luo/lanveriq/commit/9ba853991245765eaf313d3b99d9707c01373942) · [Linux #45571463449 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449) · [server unit #45571463462 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463462)：真实 Caddy mTLS 重送 revision 2 同包、旧 revision 1 迟到和同 revision 2 冲突，要求 PG18 snapshot/revision/received_at/digest 全部不变；[专项报告](docs/development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md)。
- [后续 docs SHA `0a31ab73`](https://github.com/kunlong-luo/lanveriq/commit/0a31ab7318be0a39071bfc7e24b5c4528dec3868) 的 [文档 CI #45571620903 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45571620903)、[Next.js fast #45571621032 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45571621032)：LIVE 页超过既定精简阈值；收敛文字到专项报告并重新验证，不改变阈值。v0.1 **NOT RELEASE READY**。

## 2026-11-25 — G-08 PG18 恢复 L2 PASS 与损坏归档负测 PENDING

- [功能 `b64a14ad`](https://github.com/kunlong-luo/lanveriq/commit/b64a14ad2c0eeeeb2f5db135c0f6f19dbab77d6f)：[全新无网络 PG18 恢复](docs/development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) [#45586283694 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283694)。初版 `abdebe66` 的工作流因后续修复而取消；非生产 DR。
- [初始 `f77d8454`](https://github.com/kunlong-luo/lanveriq/commit/f77d84549a738cc012080971518ce5aa64e15b37) [Linux #45587202877 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45587202877)：错误要求头部损坏文件长度减少。[首修 `b6957916`](https://github.com/kunlong-luo/lanveriq/commit/b69579169f5ecd323021def3e1f39f2c8909bf75) [Linux #45587871391 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45587871391)：原生拒绝已发生，但固定英文措辞断言错误。[修复 `3687edd7`](https://github.com/kunlong-luo/lanveriq/commit/3687edd70df087d7b69e7d061407de49fb941688) [Linux #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)。[完整失败与退出条件](docs/development/linux-pg18-corrupt-backup-atomic-reject-l2-2026-11-25.md)，G-08 OPEN。

## 2026-11-25 — L2B-33 冷缓存双重回放与分离 RO CI 锚点 L2

- [修正功能 SHA c177f281](https://github.com/kunlong-luo/lanveriq/commit/c177f28112c8cdfc4ab681e8314a7e8f3833d3ca) · [原生 Ubuntu24 OCI #45569177575 20/20 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575)：保留前 17 项 Rust Agent、签名 APT、不同 ELF、见证验收，新增三项空缓存原生场景。空缓存 APT **实际采用不同旧 Packages**，与之对应的旧 InRelease 和同序列真签名旧 witness 均正确验签；**另行 RO 挂载的 CI 批准 witness 摘要拒绝联合回放**。正确批准材料通过，缺失锚点默认拒绝；L1 六项负测、Polkit all-no/Tauri readonly 同时通过。
- [#45568609482 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45568609482) 与 [#45568690884 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45568690884) 是 Python 函数缩进造成的编译前快速失败，[#45568837764 CANCELLED](https://github.com/kunlong-luo/lanveriq/actions/runs/45568837764) 为 RO 摘要目录权限修正后的并发取消；历史保留在 [L2B-33 详细报告](docs/development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md)。锚点仍由**同一 CI 管理域**创建，可被该管理员重置，不能等同外部真实不可回退高水位、发行签收或可用安装器。**G-05 OPEN；v0.1 NOT RELEASE READY。**

## 2026-11-25 — L2B-32 原生 APT 与单独 GPG witness 签名 L2

- [功能修复 SHA `55de6e71`](https://github.com/kunlong-luo/lanveriq/commit/55de6e714bde3bb8a3c2f5a0050b413ff38f70c9) · [Ubuntu24 OCI #45566961126 17/17 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126)：仍由 APT 验证 publisher Signed-By，另以**不同的一次性 CI witness signer** 的原生 `gpgv` 见证校验 fixed sequence=2 和实际 `InRelease` SHA256。真实 `apt-get update` 仍接受旧有效签名 Release，但 witness 拒绝其摘要；真正见证签名的低序列、publisher 伪造及篡改均拒绝；原有 15 个不同 Rust ELF / APT 负例回归成功。
- [首轮原生 OCI #45566159779 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45566159779) 因严格源级负测发现见证清单错误写入字面量 `\\n`，还没走到 APT；修正真实 LF 并**将 5 项轻量源级负测前移到双 Rust 编译前**，保留原失败证据。详见 [L2B-32 专项报告](docs/development/desktop-tauri-l2b32-separated-gpgv-witness-signed-apt-replay-oci-2026-11-25.md)。同一 CI 内的 signer、固定序列和只读挂载仍**不是独立管理域托管的防回退高水位**，**G-05 OPEN**。

## 2026-11-25 — L2B-31 固定历史 Rust 源不同 ELF 的原生 APT L2

- [功能提交 84c5a717](https://github.com/kunlong-luo/lanveriq/commit/84c5a7174fd4e6b9b56027d4e9c05eef4e0c4407)、[原生 OCI #45562937199 15/15 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45562937199)：用完整固定历史 Git SHA cd031894 与当前 Agent 通过相同 Cargo.lock 独立构建不同 ELF；新增两项原生 APT 测试证实默认降级拒绝、仅在无网络 OCI 显式覆盖时发生真实 ELF 文件替换与升级恢复，配置/state 保留。
- 复核发现初次 CI-only 负测可能仅由参数数量错误拒绝。修正脚本先核验环境权限、负测传入完整参数并匹配实际拒绝原因；[修正 SHA 473f90d9](https://github.com/kunlong-luo/lanveriq/commit/473f90d9b183f63c30d121cc2d1135fe84e949b)、[严格复验 #45563578245 15/15 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45563578245)，四项 L1 负测、两份真正不同的 Rust ELF 和 15 项 Ubuntu24 原生 APT L2 全部通过。[专项报告](docs/development/desktop-tauri-l2b31-pinned-historical-rust-elf-apt-oci-2026-11-25.md) 保留首轮与修正证据。**G-05 正式签名、高水位、Polkit broker、实机 L3 仍 OPEN。**

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
