# DESKTOP-L2B-13 — 当前源码 Tauri GUI-only Debian 候选包复验

**SPEC · VERIFIED exact SHA c4f04e44 · [real GUI-only .deb full audit #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) attempt 1 overall SUCCESS · Agent installer/Pop!_OS L3 NOT RUN · v0.1 NOT RELEASE READY。**

## 最新指定源码原生完整审计已通过（2026-11-24）

[exact source c4f04e44 / CI #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) **overall SUCCESS**，实际一次性 Ubuntu24 CI 生成并严审未签名 **lanveriq-desktop_0.1.0_amd64.deb**，SHA-256 **83ab54d1331213335e3ec77e383c6b8c384690f37826e42413ffc35d2efe504a**；Rust workspace/Clippy、恶意归档、Syft 源码与局部 payload SPDX、15 个 ELF SONAME、官方 dpkg-shlibdeps 10 组最低要求/12 组包声明/直接依赖缺口 **0**、干净 Ubuntu24 OCI APT 模拟和临时 LAB Ed25519 验签全部 PASS。固定 OCI manifest **ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b**。

严格界限：这是**仅 GUI-only，指定源码的未签名 Debian L2 工程成功**；包没有上传、安装或发布，临时签名不是受信任发行签名，载荷 SPDX 只是局部 SBOM。真正的 Agent 安装器、签名/Polkit 授权、Pop!_OS COSMIC 物理 L3 均 NOT RUN；G-05 整体继续 OPEN。[L2B-15 精确证据与后续验收](desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md)。

以下历史运行中的条目保留为开发快照，不覆盖上述最终结论。

## 为什么必须重新验收

已通过的 [L2B-10 原生 GUI-only Debian/干净 Ubuntu OCI APT 审计 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) 对应较早源码。随后 [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) 将只读 Pop!_OS/COSMIC Python 检查器以两个固定 Tauri IPC 之一嵌入原生 GUI，现有源码基线是 [107dc8b4](https://github.com/kunlong-luo/lanveriq/commit/107dc8b40bcd7cb5c60984dafd2f545398de9b39)。旧源码的成功归档不能替代新二进制的严格检查，也不能证明 Agent 安装权限。

## 本轮代码修改与自动化范围

仅修改 [.github/workflows/desktop-deb-candidate.yml](../../.github/workflows/desktop-deb-candidate.yml)。新增实际 Tauri Rust/React 源码、Cargo/pnpm 锁文件、原生构建输入、Python 预检与相关安全负测变更的 push 触发；纯 README/文档改动不触发昂贵的候选包构建。workflow_dispatch 仍可用，所有作业都是隔离 Ubuntu24 CI，绝不接触生产设备。

打包前在同一 SHA 上验证：严格双 IPC 源码安全门禁和敌意归档/证据单测、22 项 Pop!_OS 合成及真实 Ubuntu 非目标拒绝测试、pnpm 冻结依赖和 React/Vitest/TypeScript/构建、Cargo.lock 与 Rustfmt、实际 Rust workspace 单测、Clippy --all-targets --locked -D warnings。APT 前置步骤在一次性 runner 中安装 Tauri 的 WebKitGTK 构建依赖，限时 12 分钟、30 秒网络超时、两次重试；未更改 Ubuntu 镜像，也未安装候选包。

上述测试全部通过才执行现有官方 Tauri CLI 的**未签名 GUI-only .deb 构建**。源代码中 bundle.active=false、targets=[] 不变，只有 CI 运行时临时启用仅 deb 打包的 CLI 覆盖。生成文件仅在 runner 的临时目录中审计，**不得上传、分发、签发正式密钥、执行安装或启动 systemd Agent**。

原有归档链仍需实际完成：未授权 maintainer scripts/Agent payload/shell/tray 的拒绝检查；真实提取 payload SHA 和两个 Syft SPDX 检查；ELF loader/DT_NEEDED 与 SONAME；Ubuntu24 包归属和版权材料；已安装强制依赖闭包；官方 dpkg-shlibdeps 直接版本最低要求与 Debian Depends 零差距；全新 Ubuntu24 OCI 只读 bind + apt-get -s 求解及已解析容器 digest；**仅临时实验密钥**的 Ed25519 验签。源端/包端、run ID、run attempt 与 SHA 都必须同源，不可跨提交继承。

## 候选包输入覆盖与并发取证保护（2026-11-24 更新）

- [7cf23f3e](https://github.com/kunlong-luo/lanveriq/commit/7cf23f3e8da72b0a646f8edcc0edd965a53c619d) 在此前 [161879ea](https://github.com/kunlong-luo/lanveriq/commit/161879ea3f1b94713b413260ae6852546f92df81) 的严格候选 workflow 上补全三类遗漏的实际构建/安全输入：`apps/desktop/src-tauri/capabilities/**`（Tauri IPC 权限范围）、`apps/desktop/src-tauri/rust-toolchain.toml`（Rust toolchain 审计）、`apps/desktop/.node-version`（桌面 Node pin）。任一输入变化都必须触发新的来源一致性检测；README-only 变更依旧不触发。此修改**没有开启新 Tauri 权限，没有改变 Agent/桌面源代码，也没有批准签名或安装**。
- 该候选 workflow 的 `cancel-in-progress=false` 只确保**已开始的昂贵归档取证**不会被后续提交自动取消。GitHub Actions 同组最多保留一个 pending，新 pending 可能将旧 pending 标记为 CANCELLED，因此**不能保证每个新 SHA 都被保留排队并完整运行**；必须按实际 GitHub run 的 source SHA、run attempt 与 conclusion 核对，若某 SHA 未执行则不能借别的 SHA 通过。此配置只保留已运行中的证据，不做发布豁免。
- **当前独立证据：** [161879ea / #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) 已完成安全负测、冻结前端测试/类型/构建、Cargo lock/Rustfmt 等前置步骤，实际 Ubuntu WebKitGTK 依赖步骤尚在执行；[7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) 为排队的新 workflow，**NOT RUN**（尚无 job 结论）。当前源码没有经过这两次运行确认的完整 `.deb` 构建/官方 ABI/Syft/ELF/OCI 成功结果。

## 当前源码原生预览真实失败与构建依赖隔离原则

[功能 SHA 107dc8b4 / Preview #45315213247](https://github.com/kunlong-luo/lanveriq/actions/runs/45315213247) 已最终 **FAIL**。desktop-ui 源码负测/Pop!_OS 合成及真实 Ubuntu 拒绝/React/TypeScript 与构建 PASS，native Cargo lock 和 Rustfmt PASS；但 **Ubuntu24 runner 原生构建前置 APT 步骤超出 12 分钟**，原生 Rust 单测、Clippy **SKIPPED**。Runner 日志保留 `us-east-2.ec2.archive.ubuntu.com` 来源、`4 upgraded, 171 newly installed` 计划及下载未完成记录。不得把该失败重写为仍在执行或把历史其它提交的 6/6 Rust 单测转记为此 SHA 通过。

**验收基础设施的可执行前置条件：** 先验证基于 Ubuntu24、受控来源、来源/版本/摘要可核验的 WebKitGTK 4.1 GTK/Tauri 构建依赖环境，确认最小工具链与 ABI、不使用不明镜像/未审签名、没有扩张构建机到终端或 Agent 权限。可以通过单独批准的固定开发镜像/隔离 runner 进行原生编译；若更换镜像或预制依赖，必须记录其 digest、包来源、受控维护/更新流程和失败回退，然后再让完全相同的 Rustfmt、`cargo test --workspace --locked`、严格 Clippy 和真实 `.deb` ABI/归档/OCI 流程跑完。此处**只规定目标，不代表已有可信镜像或完成验收**。不应简单延长超时、忽略 Clippy 或把旧 .deb 证据当作当前二进制。

## 正式 GUI-only 未签名包 CI 首次消费已审 APT 缓存（2026-11-24）

**实际 Rust/Clippy 接续证据（2026-11-24）：** [c4f04e44 / #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 已通过固定已审 APT 缓存、构建 runner 新 Ubuntu 签名索引逐包/完整依赖闭包验证，以及仅 disposable CI 的 apt-get --no-download 开发依赖安装；同 SHA 的真实 cargo test --workspace --locked 和 clippy --all-targets --locked -- -D warnings **PASS**。真实未签名 GUI-only .deb 正在编译，后续 Syft/ELF/dpkg-shlibdeps/OCI 和完整 run SUCCESS 尚不可声称通过。[ea62c63c](https://github.com/kunlong-luo/lanveriq/commit/ea62c63cb9523c1ac34ac64c7f7134bbe58fbe3a) 也将常规原生预览迁移到同一不可降级的固定缓存/索引复核/离线 Ubuntu 构建依赖安装；[#45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) 待验证。以上均没有安装 Lanveriq GUI/Agent 或给予生产权限。

[代码 c4f04e44](https://github.com/kunlong-luo/lanveriq/commit/c4f04e442be7da8181e9e62293764fbbb80e41d0) 将旧版阻塞于 Ubuntu WebKitGTK 12 分钟在线依赖下载的关键流程改为 **精确缓存 key 恢复 → 当前 runner 官方签名 `apt-get update` → 按固定 175 包/manifest、逐包官方 SHA256/Size/Version/Arch 和 no-install 精确闭包二次验真 → `apt-get --no-download` 离线安装仅 Ubuntu24 CI 的原生构建依赖**。采用 [已完成完整双 job 严格核验 #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944) 的固定缓存，未允许 `restore-keys` 或无审查缓存降级。

**[严格同源候选 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248)** 在本文记录时仍在运行。仍需逐项看到：同 SHA 实际 Rust `cargo test --workspace --locked` 和 Clippy `-D warnings`，真实 unsigned GUI-only `.deb` 文件/哈希、恶意归档拒绝、Syft source/payload、ELF 直接 SONAME 与 official `dpkg-shlibdeps`、干净 Ubuntu24 OCI `apt-get -s`、实验 Ed25519 验签。历史旧 SHA 成功不可冒充当前通过。Agent 或 GUI 包本身未在生产系统安装，也没有获得任何签名/授权或 Pop!_OS COSMIC 实机 L3 成功证据。**G-05 OPEN。**

## Candidate SHA 7cf23f3e failed native prerequisite timeout

[Strict candidate run #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023), exact workflow SHA 7cf23f3e, has now completed **FAIL** on the 12-minute Ubuntu24 APT WebKitGTK prerequisite step. Source guard, synthetic rejection tests, frozen frontend/type/build, Cargo lock and Rustfmt passed; Rust tests, strict Clippy and all actual archive/ABI/OCI validation were **SKIPPED**. The runner's Ubuntu source downloads only reached the 69th package by timeout. Older source-specific package audit success cannot be substituted. The separate L2B-14 APT download-only cache work is not yet wired into a strict package build and cannot be treated as native L2.

## L2B-14 download-only cache experiment (not L2 native acceptance)

- [Signed-index prefetch first run dac7f6ad / #45318418790](https://github.com/kunlong-luo/lanveriq/actions/runs/45318418790) actually fetched 175 Ubuntu .deb packages (about 70 MB) without altering dpkg installed state, but its _apt sandbox fell back to root and GitHub post-cache save FAILED on private partial/lock directories despite the workflow overall SUCCESS. This cannot be treated as a reusable cache or safe builder pass.
- [Correction b695945c / #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) moves archive storage to /var/tmp with _apt-private partial ownership, matches only full .deb files, and requires an independent GitHub runner to restore the exact-key cache and match the archive count; result IN PROGRESS at this writing. [Separate L2B-14 specification](desktop-tauri-l2b14-ubuntu-apt-prefetch-cache-lab-2026-11-24.md) tracks approval criteria. **No candidate building workflow consumes the experimental cache yet**. Real Ubuntu signed-index package validation and full native Rust/Clippy/deb/ABI/OCI evidence remain required.

## L2B-13 runner dependency evidence and immutable builder exit gate (2026-11-24)

1. [Candidate #45315837623, workflow/source SHA 161879ea](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) has now finished **FAIL**. The source guard and all malicious archive/receipt negative tests, frozen frontend tests/type/build, pinned Rust toolchain/Cargo.lock and Rustfmt passed; **12-minute Ubuntu24 APT prerequisite install failed** while downloading its 171-ish WebKitGTK/GTK transitive dependencies from the runner-configured `us-east-2.ec2.archive.ubuntu.com`. Source-only tests cannot elevate the failure: same-source native Rust test and strict Clippy, Tauri `.deb` build, strict payload/Syft/ELF/official shlibdeps, clean Ubuntu OCI APT and experimental Ed25519 lab were **SKIPPED**. No current-source package bytes/digest exist from this failed run. [Next strict candidate SHA 7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) has begun, overall still unverified at evidence cutoff.
2. A separate **read-only, no-network-refresh/no-install** [Ubuntu24 APT plan diagnostic commit 37a57f7f](https://github.com/kunlong-luo/lanveriq/commit/37a57f7f311c7f7062e38a3634095c8f1583b5fe) introduces [workflow desktop-ubuntu-apt-plan-diagnostic.yml](../../.github/workflows/desktop-ubuntu-apt-plan-diagnostic.yml); [run #45317328366](https://github.com/kunlong-luo/lanveriq/actions/runs/45317328366) **SUCCESS**. It verified Ubuntu 24.04/amd64 and the existing readonly Tauri source gate, printed runner image `ubuntu24 / 20261116.376.1`, read local `apt-cache policy` and executed only `apt-get -s`: **171 simulated install or upgrade actions, 0 removals**. Its cached `libwebkit2gtk-4.1-dev` candidate version `2.52.6-0ubuntu0.24.04.1` is **not equal** to the `2.52.6-0ubuntu0.24.04.2` downloaded by the real job after `apt-get update`. This demonstrates stale-index risk; neither package selection nor origin is thereby approved. It did not run Tauri, Clippy, download packages, create a Debian archive, or install/enable Agent.
3. **Builder infrastructure acceptance, still NOT RUN:** separately review an Ubuntu24 OCI image or isolated runner with exact parent image digest and provenance, approved signed APT repository sources and package manifest/version/hash list, preinstalled WebKitGTK4.1/GTK Tauri build prerequisites, reproducible rebuild instructions, immutable published builder digest and independent review/rollback authority. Before the builder may enter CI, check every relevant toolchain/version and dependency origin, no attached production secrets, no privilege/service/autostart expansion and exact parity to the reviewed ABI. Record source SHA, reviewed builder digest, observed installed package closure, native build/test/Clippy and complete strict `.deb` evidence on the **same** current source. This is a requirement for future engineering, not a claim that such a signed builder exists.
4. **Never claim L2B-13 complete** on an APT simulation PASS, old unsigned candidate PASS, a partial frontend test, or a failed/cancelled CI. Do not bypass Rust/Clippy, increase APT timeout as a substitute for reliable supply-chain evidence, use an unaudited mirror, enable bundle in committed source, sign with publisher keys or install a package on a production host.

[Official Tauri 2 Linux prerequisite background](https://v2.tauri.app/start/prerequisites/) · [L2B-12 GUI prereq](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md).

## 验收记录（2026-11-24 核对）

| 验收 | Exact SHA / Workflow | 当前可证明事实 | 尚未证明 |
| --- | --- | --- | --- |
| 历史旧 GUI .deb | [旧 L2B-10 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) | 当时原生候选及 Ubuntu OCI L2 PASS | 新的 COSMIC GUI 源码 |
| 新 GUI 原生测试 | [107dc8b4 / #45315213247](https://github.com/kunlong-luo/lanveriq/actions/runs/45315213247) | **overall FAIL**；frontend、安全 L1、Cargo lock/Rustfmt PASS；Ubuntu APT 前置 12 分钟超时 | Rust 单测/Clippy SKIPPED；该 SHA 无完整 native L2 |
| 新源码未签名候选 | [161879ea / #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) | **overall FAIL**：源码/冻结 pnpm/Cargo lock/Rustfmt PASS，真实 Ubuntu APT 下载 12 分钟超时 | Cargo tests、Clippy、.deb/Syft/ELF/shlibdeps/OCI 均 SKIPPED，没有新包 digest |
| 只读原生依赖计划诊断 | [37a57f7f / #45317328366](https://github.com/kunlong-luo/lanveriq/actions/runs/45317328366) | **SUCCESS**：Ubuntu24 ImageVersion 20261116.376.1，本地索引 171 模拟安装/升级、0 删除 | 真实下载、受审镜像、构建、Pop!_OS L3 全部 NOT RUN |
| 旧源码候选（历史） | [7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) | **FAIL**：WebKitGTK 下载超时 | 不能用于新 SHA 的 L2 证明 |
| **当前真正 GUI-only 完整审计** | [c4f04e44 / #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) | **attempt 1 overall SUCCESS**：真实 .deb、Rust/Clippy、严格归档/Syft/ELF/官方 ABI、clean OCI/临时 LAB Ed25519 | 不代表真实 Agent 安装器、发行签名、Pop!_OS L3 |
| Pop!_OS24 COSMIC 实机 | [G-05 L3 issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) | NOT RUN | 正常用户 GUI 启动、WebKitGTK/Wayland、授权安装生命周期 |

**L2B-13 退出条件：** 必须确认真实工作流整体 SUCCESS，并分别确认对应同源包真实存在、文件哈希、归档脚本、Syft、ELF、官方 dpkg-shlibdeps 10 组 ABI 最低版本零缺口、OCI APT 模拟与先行严格 Rust/Clippy 成功；记录实际源 SHA、run/attempt、包 SHA-256。当前包哈希未知，不得复制旧包 SHA 或假定新审计成功。失败/取消保持原样。

**发布另行批准：** 正式发布签名身份/密钥托管、依赖许可证、最小 polkit 安装权限、真实 Pop!_OS COSMIC L3、Agent 自愿授权安装/卸载/升级与隐私、G-02 独立 WORM、G-07 法务、G-08 运维及组织审批均保持 OPEN。没有可分发一键 Agent 安装包，v0.1 NOT RELEASE READY。

[当前状态](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) · [L2B-10](desktop-tauri-l2b10-clean-ubuntu-oci-apt-2026-11-24.md)。
