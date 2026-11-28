# DESKTOP-L2B-14 — Ubuntu 24.04 Tauri 开发依赖预热与缓存独立验收

**SPEC · COMPLETED SUBGATE · 2026-11-24 · [APT 双 runner #45321096944 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) · 仅 Ubuntu 开发依赖缓存证明，不是不可变 Builder 或发布授权 · v0.1 NOT RELEASE READY。**

> 现已确认：[完整 GUI-only .deb 审计 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 及 [原生只读预览 CI #45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) 均已 SUCCESS，均对固定 175 包缓存重新执行 Ubuntu 签名索引/闭包验证。**前者仅 GUI-only 未签名包 L2，后者仅原生只读预览；两者都非真实 Agent 安装器，Pop!_OS L3 仍 NOT RUN**。下文较早“排队中/尚未下载”的条目保留为历史快照。

## 背景与目标

旧版 [L2B-10 原生 Debian 候选 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) 的成功审计不覆盖新增 Pop!_OS 只读 IPC 的当前源码。[L2B-13 当前候选 #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) 已因 Ubuntu WebKitGTK/GTK 开发依赖下载超过 12 分钟而 FAIL，Rust 单测、Clippy、真实包、ELF/Syft、官方 ABI 和 OCI 检查全部 SKIPPED。[只读依赖规划 #45317328366](https://github.com/kunlong-luo/lanveriq/actions/runs/45317328366) 显示约 171 项计划安装或升级、0 删除，但没有下载或安装。

本阶段使用 Ubuntu 系统 APT 的官方仓库签名索引与 GitHub Actions 官方 actions/cache，单独预下载开发依赖归档，再由另一个干净 runner 核实缓存可读。所有缓存仅用于加速构建，不属于完整 Builder 签名证据或可分发的 Lanveriq 软件。

## 实现和最小权限

- [独立 APT prefetch CI](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-ubuntu-apt-prefetch.yml) 仅由其工作流文件变化或明确手动触发；只读 contents 权限，先检查 Ubuntu24 amd64 和桌面源代码默认拒绝。
- APT 使用 runner 原有已配置的 Ubuntu 仓库，不改镜像 URL、不绕过签名。执行 apt-get update 和 apt-get -s 后，拒绝删除计划，并把有版本的传递依赖模拟计划、APT 源文件哈希、runner 镜像版本、架构和仓库标识绑定到 SHA-256 缓存键。此缓存键是加速身份标识，**不是发布签名**。
- 仅执行 apt-get --download-only install 来下载 WebKitGTK4.1、GTK 等九组原生构建入口的依赖，设置网络重试和超时界限。下载前后用 dpkg-query 检查已安装软件包状态必须逐字一致。没有装软件、启动服务、执行真实 GUI 或制作 Agent 安装包。
- 缓存只接受完整 .deb 归档，不包含 root-only lock 或 _apt 私有 partial 目录。CI 改用 /var/tmp 下可遍历的单独缓存目录，_apt 用户独占部分下载目录（0700），避免第一次运行的不安全 root 下载回退。
- cache-probe 第二个 Ubuntu runner 通过精确 key 恢复缓存，必须 cache-hit=true、完整 .deb 数量与预下载步骤输出一致、全部可读，否则整个工作流 FAIL。它不安装这些包，不将缓存宣布为受信任软件。

**下一步不能直接跳过：** 只有缓存保存和跨 runner 恢复 PASS 后，才能单独审批是否让实际 Tauri 严格 CI 使用缓存。后续仍需每次依赖 Ubuntu APT 已验证的索引核对文件 hash 和版本，并保留 Rustfmt、cargo test、Clippy -D warnings、源代码默认拒绝、真实 .deb、Syft/ELF、dpkg-shlibdeps、干净 OCI APT 与 Pop!_OS L3 全部门禁。

## 后续真实候选包 FAIL 的确认（2026-11-24）

[候选工作流 7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) 现已 **overall FAIL**：Ubuntu24 APT 从其配置的 us-east-2.ec2.archive.ubuntu.com 下载开发依赖，在 12 分钟步骤界限终止（日志至第 69 项）。所有源检查、React、Cargo lock 和 Rustfmt PASS；真正 Rust 单测/Clippy、GUI .deb、Syft/ELF、官方 shlibdeps 和清洁 OCI 验收均 **SKIPPED**。必须保留这项失败，不能将它描述为缓存实验中的 PASS 或当前发布就绪。

## L2B-14 SHA-256 跨 runner 增强与 sandbox 负向门禁（2026-11-24）

- 工程提交 [ab1e3cd1](https://github.com/kunlong-luo/lanveriq/commit/ab1e3cd1a1967256426a01017e84b0f498530d0d) 将 APT 下载的 stdout/stderr 同时收集进日志，明确拒绝 `download is performed unsandboxed` 与 `pkgAcquire::Run ... Permission denied`。APT 自己 download-only 成功但实际回退 root 也必须 **FAIL**；不能复用 [第一版 dac7f6ad 的假阳性 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45318418790)。原生 CI 与公开软件包安装权限没有发生变化。
- 在预热作业中通过 `find -maxdepth 1 -name '*.deb' -print0 | sort -z | xargs -0 -r sha256sum | sha256sum` 计算所有下载完成的 `.deb` 的稳定摘要，随任务 outputs 传给独立 cache-probe。第二个 runner 必须检验 `cache-hit=true`、数量和 **SHA-256 manifest 值**全部相等，任一不一致即整体 FAIL。这只是缓存间字节相同的独立验收，不代表缓存中的每个包都已在未来构建 runner 重新按 Ubuntu 签名索引认证。
- 前一版 [b695945c / #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) 在本次记录时仍执行下载，结果 PENDING；新严格版 [ab1e3cd1 / #45319542159](https://github.com/kunlong-luo/lanveriq/actions/runs/45319542159) 已排队，两个 job 整体还没有 SUCCESS。**未验收缓存绝不接入实际 GUI-only Debian 严格审计或开发机**。
- 即使 cache-probe PASS，也只关闭“缓存可被独立恢复且字节一致”的基础设施子项；之后必须在将缓存纳入候选包前，实现并实测每一份缓存 `.deb` 对同期有效 Ubuntu 签名索引所列 `SHA256`、Version、Architecture 的独立认证，再实际安装 runner 构建依赖（仅一次性 CI）并以同功能 SHA 运行全部 Cargo/Clippy、真实 `.deb`、Syft、ELF、dpkg-shlibdeps 与 clean OCI 验收。不能把没有绑定 Ubuntu 索引的两次相同缓存当成软件来源证明。

## Signed-index independent restoration (2026-11-24)

**Verification code + actual CI:** [9027dcf4 read-only verifier](../../apps/desktop/tools/verify_desktop_apt_archive_cache.py), [895a1d94 hostile synthetic tests](../../apps/desktop/tools/test_verify_desktop_apt_archive_cache.py), [dedicated fast offline workflow](../../.github/workflows/desktop-apt-cache-contract.yml) at [#45320263339 / f24e7883](https://github.com/kunlong-luo/lanveriq/actions/runs/45320263339), **13/13 tests PASS (12 hostile rejection cases and 1 valid-cache acceptance); overall SUCCESS**. No APT download/host installation in that contract job. Real signed index scanning of 175 cached archives on a second runner **NOT VERIFIED** and must not be inferred from synthetic PASS.

The verification library is built on Python standard `hashlib` and native Ubuntu `apt-cache show name=version`; it only reads regular `.deb` bytes, not their maintainer scripts or extracted payload. The verifier rejects symbolic links, invalid/duplicate Debian file names, mismatched epoch/version/architecture/size and SHA-256, missing current package metadata, unexpectedly large archive/metadata, and producer/restored SHA-256 manifest drift. [c141cf3e strict prefetch workflow](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-ubuntu-apt-prefetch.yml) makes the second clean Ubuntu24 runner first perform signed `apt-get update` using its own configured distro repository policy, then require each recovered package's name, version, arch, size and SHA-256 to equal **that independently refreshed local apt-cache index**. Changing or stale upstream metadata must fail closed rather than reusing a byte-identical but now unapproved cache. This still does not approve a production Builder or signed Lanveriq release.

**CI efficiency:** [08673541 desktop-preview workflow](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-tauri-preview.yml) now excludes all unrelated APT cache source/test changes from expensive real Tauri WebKitGTK/Clippy CI triggering. Actual embedded Pop!_OS/read-only inspection sources and Tauri build inputs remain covered, and native CI stays strict. The small offline contract workflow independently tests cache validation on every change of its files. Native preview in-progress jobs are preserved across newer source pushes rather than cancelled just for a documentation or unrelated security helper update.

**Observed runs:** [b695945c prefetch #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) remained IN PROGRESS at download; old [ab1e3cd1 #45319542159](https://github.com/kunlong-luo/lanveriq/actions/runs/45319542159) is CANCELLED, not a test pass; signed-index-per-archive version [c141cf3e #45320183856](https://github.com/kunlong-luo/lanveriq/actions/runs/45320183856) was CANCELLED when a newer pending run superseded it. Only an actual entire two-job workflow SUCCESS, including post-cache save and independent restore of all matching packages, can justify L2B-14 lab acceptance. Full production-origin approval, real GUI-only candidate `.deb`, official ABI and Pop!_OS L3 remain separate OPEN gates.

## L2B-14 最终双 runner 归档与 L2B-13 固定缓存接入（2026-11-24）

**状态层级：L2B-14 APT 缓存传输 + 新签名索引逐包认证 + 当前完整依赖闭包独立验收 PASS；L2B-13 同源真实 GUI-only `.deb` L2 仍未 PASS；v0.1 NOT RELEASE READY。**

| 来源与目标 | 真实证据 | 安全边界 |
| --- | --- | --- |
| [原生 APT 预热完整工作流 #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) · source 720e2f18 | **overall SUCCESS**，首个作业从 Ubuntu 原有签名 APT 源 download-only 下载 175 个 `.deb`，约 70MB；dpkg 安装状态无变化、无 root 沙箱回退；GitHub post-cache 保存 SUCCESS | 没有构建、安装或分发 Lanveriq |
| [同 run 独立 runner #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) | **SUCCESS**；精确缓存键命中；新 `apt-get update` 后，缓存 **175/175** 文件的 Package、Version、Architecture、Size、SHA256 与 Ubuntu 签名索引一致；`apt-get -s` 175 项无下载依赖闭包精确匹配；与生产者全量文件 manifest 一致 | 证明缓存来源和跨 runner 可恢复，不是 OCI Builder 镜像 digest、二进制签名或 GUI L2 |
| [更早 b695945c / #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) | **overall SUCCESS**，两个 runner 的缓存保存/恢复及数量检查已通过 | 较旧代码**没有**强制新签名索引逐包加闭包及全文件哈希验收，不可代替新的严格 run |
| [当前严格候选源码 c4f04e44 / #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) | 工程接线已落地，执行中；未有全部 Rust/Clippy/真实 `.deb`/Syft/ELF/ABI/OCI 审计结果 | 只有该新 run 的每个后续步骤实际 PASS 才允许继续下一阶段 |

通过 #45321096944 的**唯一候选固定缓存 key**：`lanveriq-ubuntu24-tauri-apt-v2-d3781616035a7dca93d8cc962be5b96c2430dd26cf28deee45aa287121da8f9e`；**175 份 .deb 文件集合的 SHA-256 manifest**：`5ac2afaff76e962e6c5131c34645248cb8024d4e8581d2790d71f622e988bc35`。该缓存只存 Ubuntu 公共原生构建依赖，不包含 Lanveriq 二进制、生产密钥、用户资料或远控权限。

[新候选 CI c4f04e44](https://github.com/kunlong-luo/lanveriq/commit/c4f04e442be7da8181e9e62293764fbbb80e41d0) 将缓存恢复设置为**精确 key、fail-on-cache-miss、无 prefix/fallback**，把数量和 manifest 锁定在源码中。它在独立 Ubuntu24 runner 使用全新官方 APT 签名索引再次逐一检查归档、版本及模拟安装闭包，然后通过 `apt-get --no-download --no-install-recommends` 仅在**一次性构建 runner**安装构建头文件。全流程不安装 Lanveriq GUI/Agent、不修改被提交的 Tauri bundle.active=false、不签名或上传产品安装包，继续运行原先所有真正的 Rust/Clippy/归档/ABI/OCI 等严格检查。

**阻断及换代规则：** 若缓存过期、Ubuntu 新版索引/镜像基线漂移、任何依赖遗漏/混入、key 或 SHA 不一致、APT 离线安装缺依赖，构建必须 FAIL；不能退回不受审镜像、未经认证软件源或直接重新联网下载等规避。需先重跑独立两 runner 验收取得新 key/manifest，再在 main 经审查更新固定值。**目前 G-05、正式 GUI-only L2 和 Pop!_OS L3 尚未完成，不因本阶段缓存子项 PASS 而关闭。**

## 本阶段增补：APT 当前安装计划的精确闭包证明（2026-11-24）

- [代码提交 720e2f18](https://github.com/kunlong-luo/lanveriq/commit/720e2f183ec01bc98957fde0c13179d1c5df074c)：`verify_desktop_apt_archive_cache.py` 新增 `parse_install_plan`，拒绝 `Remv/Purg`、畸形或重复的 `Inst`，且严格绑定 `package -> version`；恢复缓存不能包含不属于独立 Ubuntu24 runner 的当前计划的归档，亦不能少于模拟计划。预热工作流第二 runner 先通过 Ubuntu APT 签名策略刷新索引，再执行九项构建前置入口的 `apt-get -s`，最后以 `--expected-plan-file` 将闭包绑定到每包签名索引 SHA-256、Size、Version/Architecture 和跨 runner manifest。该加强没有修改产品 Agent/GUI 权限，也没有授予软件安装或构建发行权限。
- [同源离线测试 #45321096899](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096899) **overall SUCCESS，20/20 PASS（18 个拒绝负测、2 个正常输入正测）**，比旧 13/13 多 7 项。它在隔离临时合成 `.deb` 字节上运行，不触及真实 Ubuntu 软件包缓存。
- [真实 Ubuntu24 只读诊断 #45321177263 / 代码 9838b741](https://github.com/kunlong-luo/lanveriq/actions/runs/45321177263) **overall SUCCESS**，从 runner **现有缓存** APT 索引执行 `apt-get -s`，验证真实输出里 **171 条 Inst、0 条 Remv/Purg** 且 Python 解析得 171 项；本次 runner `ImageVersion=20261116.133.1`，本地缓存 `libwebkit2gtk-4.1-dev` 版本 `2.52.6-0ubuntu0.24.04.1`。**没有刷新索引/联网下载**，所以仅可称真实 Ubuntu 的缓存计划解析成功；不能视为受审镜像、当前软件包哈希、缓存恢复或真实 `.deb` 编译成功。
- **GitHub concurrency 的真实边界：** 相同组 `cancel-in-progress=false` 保护已开始的 run，但 GitHub Actions 同一并发组只保留一个 pending；新 pending 可覆盖/取消旧 pending。[ab1e3cd1 #45319542159](https://github.com/kunlong-luo/lanveriq/actions/runs/45319542159) 和 [c141cf3e #45320183856](https://github.com/kunlong-luo/lanveriq/actions/runs/45320183856) 已确认为 CANCELLED，不能把它们写成 PASS 或仍在排队。正在运行的 [b695945c #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) 仍在下载；最新 [720e2f18 #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) 等待中。后续只凭**实际成功 run 的 exact source SHA、两作业结果和缓存 manifest**作出验收结论。

**未解除的阻塞：** 本次仍没有“从 Ubuntu 签名仓库完成下载 → GitHub 缓存保存成功 → 第二 runner 精确恢复并按新签名索引逐包检验 → 当前依赖闭包完全一致”的两作业整体 SUCCESS。不能将只读诊断、离线测试或旧 GUI .deb 成功变成发布许可；严格 native Rust/Clippy、当前源码新 .deb/Syft/ELF/ABI/clean OCI、正式签名/polkit/Pop!_OS24 L3 必须独立验收。

## 本轮精确来源证据

| 来源 | 实际结论 | 仍然不足 |
| --- | --- | --- |
| [首版 SHA dac7f6ad / #45318418790](https://github.com/kunlong-luo/lanveriq/actions/runs/45318418790) | GitHub CI 总体 SUCCESS；Ubuntu APT 真实下载 **175 份 .deb，约 70 MB**，已安装 dpkg 状态未变；但日志显示 _apt 沙箱权限问题，后置 GitHub 缓存 tar 因无法读取 partial / lock **保存失败** | 不得把 overall SUCCESS 视为缓存保存或恢复通过，更不是安全 Builder |
| [修订 SHA b695945c / #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) | 改为 _apt 可访问的 /var/tmp、完整 .deb 文件匹配、跨作业缓存恢复拒绝优先；验收运行中 | 最终 cache-probe PASS、可复用缓存、正式候选打包未验证 |
| [候选 SHA 7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) | **overall FAIL**；Ubuntu APT 12 分钟下载超时，源/前端与 Cargo lock/Rustfmt PASS | 同源码 Rust/Clippy、真实归档、Syft/ABI/OCI 均 SKIPPED |
| [增强 SHA ab1e3cd1 / #45319542159](https://github.com/kunlong-luo/lanveriq/actions/runs/45319542159) | **CANCELLED**，未完成任何实际 cache-probe  | 不得计为 PASS |
| [独立离线协议 f24e7883 / #45320263339](https://github.com/kunlong-luo/lanveriq/actions/runs/45320263339) | **overall SUCCESS**，13/13 缓存攻击/不匹配单测 PASS | 没有运行真正 APT 下载/恢复 |
| [签名索引交叉验证 c141cf3e / #45320183856](https://github.com/kunlong-luo/lanveriq/actions/runs/45320183856) | **CANCELLED**，只是已提交签名索引验证逻辑 | 不能作为真实二阶段恢复证据 |
| [缓存闭包校验 720e2f18 / #45321096899](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096899) | **20/20 离线正负测 PASS** | 未触及真实 .deb 缓存或任何包安装 |
| [Ubuntu24 真正模拟 9838b741 / #45321177263](https://github.com/kunlong-luo/lanveriq/actions/runs/45321177263) | **overall SUCCESS**：171 条 Inst，0 删除，解析数严格 171 | 未刷新签名索引，未执行下载、缓存保存/恢复、编译/发行 |
| [增强版完整预热 720e2f18 / #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) | 仍待整链结论；尚未证明完成 | 两作业 signed-index + exact closure/manifest/cache-probe 全 PASS |

**关闭本实验的条件：** 同 SHA 下预热作业与独立恢复作业均 SUCCESS，工作流整体 SUCCESS，明确记录完整 .deb 数量和缓存键，且无已安装包状态变化、无不安全 root 下载回退。若任何 post-cache 或 probe 失败，保持 FAIL / NOT VERIFIED。

G-05/G-07/G-08/G-09、签名、授权安装、Pop!_OS24 COSMIC 物理 L3 与独立 WORM 均 OPEN；**v0.1 NOT RELEASE READY**。

[当前状态](../CURRENT_STATUS.md) · [L2B-13](desktop-tauri-l2b13-current-source-gui-deb-reaudit-2026-11-24.md) · [发布缺口](../planning/release-gap-register.md)。
