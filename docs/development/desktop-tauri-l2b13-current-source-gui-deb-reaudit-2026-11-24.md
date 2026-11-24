# DESKTOP-L2B-13 — 当前源码 Tauri GUI-only Debian 候选包复验

**SPEC · ACTIVE · 2026-11-24 · workflow SHA [161879ea](https://github.com/kunlong-luo/lanveriq/commit/161879ea3f1b94713b413260ae6852546f92df81) · [候选 CI #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) IN PROGRESS · v0.1 NOT RELEASE READY。**

## 为什么必须重新验收

已通过的 [L2B-10 原生 GUI-only Debian/干净 Ubuntu OCI APT 审计 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) 对应较早源码。随后 [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) 将只读 Pop!_OS/COSMIC Python 检查器以两个固定 Tauri IPC 之一嵌入原生 GUI，现有源码基线是 [107dc8b4](https://github.com/kunlong-luo/lanveriq/commit/107dc8b40bcd7cb5c60984dafd2f545398de9b39)。旧源码的成功归档不能替代新二进制的严格检查，也不能证明 Agent 安装权限。

## 本轮代码修改与自动化范围

仅修改 [.github/workflows/desktop-deb-candidate.yml](../../.github/workflows/desktop-deb-candidate.yml)。新增实际 Tauri Rust/React 源码、Cargo/pnpm 锁文件、原生构建输入、Python 预检与相关安全负测变更的 push 触发；纯 README/文档改动不触发昂贵的候选包构建。workflow_dispatch 仍可用，所有作业都是隔离 Ubuntu24 CI，绝不接触生产设备。

打包前在同一 SHA 上验证：严格双 IPC 源码安全门禁和敌意归档/证据单测、22 项 Pop!_OS 合成及真实 Ubuntu 非目标拒绝测试、pnpm 冻结依赖和 React/Vitest/TypeScript/构建、Cargo.lock 与 Rustfmt、实际 Rust workspace 单测、Clippy --all-targets --locked -D warnings。APT 前置步骤在一次性 runner 中安装 Tauri 的 WebKitGTK 构建依赖，限时 12 分钟、30 秒网络超时、两次重试；未更改 Ubuntu 镜像，也未安装候选包。

上述测试全部通过才执行现有官方 Tauri CLI 的**未签名 GUI-only .deb 构建**。源代码中 bundle.active=false、targets=[] 不变，只有 CI 运行时临时启用仅 deb 打包的 CLI 覆盖。生成文件仅在 runner 的临时目录中审计，**不得上传、分发、签发正式密钥、执行安装或启动 systemd Agent**。

原有归档链仍需实际完成：未授权 maintainer scripts/Agent payload/shell/tray 的拒绝检查；真实提取 payload SHA 和两个 Syft SPDX 检查；ELF loader/DT_NEEDED 与 SONAME；Ubuntu24 包归属和版权材料；已安装强制依赖闭包；官方 dpkg-shlibdeps 直接版本最低要求与 Debian Depends 零差距；全新 Ubuntu24 OCI 只读 bind + apt-get -s 求解及已解析容器 digest；**仅临时实验密钥**的 Ed25519 验签。源端/包端、run ID、run attempt 与 SHA 都必须同源，不可跨提交继承。

## 候选包输入覆盖与并发取证保护（2026-11-24 更新）

- [7cf23f3e](https://github.com/kunlong-luo/lanveriq/commit/7cf23f3e8da72b0a646f8edcc0edd965a53c619d) 在此前 [161879ea](https://github.com/kunlong-luo/lanveriq/commit/161879ea3f1b94713b413260ae6852546f92df81) 的严格候选 workflow 上补全三类遗漏的实际构建/安全输入：`apps/desktop/src-tauri/capabilities/**`（Tauri IPC 权限范围）、`apps/desktop/src-tauri/rust-toolchain.toml`（Rust toolchain 审计）、`apps/desktop/.node-version`（桌面 Node pin）。任一输入变化都必须触发新的来源一致性检测；README-only 变更依旧不触发。此修改**没有开启新 Tauri 权限，没有改变 Agent/桌面源代码，也没有批准签名或安装**。
- 该候选 workflow 的 `cancel-in-progress=false` 用于保留已经开始的长耗时归档取证及失败诊断；后续提交以独立 SHA 排队验收，不会因新推送直接废弃进行中的证据。相比旧 `cancel-in-progress=true`，这可能延长等待队列，但更适合耗时的首次真实原生审核。不把旧运行的包 SHA/ABI 结果映射为新提交通过。
- **当前独立证据：** [161879ea / #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) 已完成安全负测、冻结前端测试/类型/构建、Cargo lock/Rustfmt 等前置步骤，实际 Ubuntu WebKitGTK 依赖步骤尚在执行；[7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) 为排队的新 workflow，**NOT RUN**（尚无 job 结论）。当前源码没有经过这两次运行确认的完整 `.deb` 构建/官方 ABI/Syft/ELF/OCI 成功结果。

## 当前源码原生预览真实失败与构建依赖隔离原则

[功能 SHA 107dc8b4 / Preview #45315213247](https://github.com/kunlong-luo/lanveriq/actions/runs/45315213247) 已最终 **FAIL**。desktop-ui 源码负测/Pop!_OS 合成及真实 Ubuntu 拒绝/React/TypeScript 与构建 PASS，native Cargo lock 和 Rustfmt PASS；但 **Ubuntu24 runner 原生构建前置 APT 步骤超出 12 分钟**，原生 Rust 单测、Clippy **SKIPPED**。Runner 日志保留 `us-east-2.ec2.archive.ubuntu.com` 来源、`4 upgraded, 171 newly installed` 计划及下载未完成记录。不得把该失败重写为仍在执行或把历史其它提交的 6/6 Rust 单测转记为此 SHA 通过。

**验收基础设施的可执行前置条件：** 先验证基于 Ubuntu24、受控来源、来源/版本/摘要可核验的 WebKitGTK 4.1 GTK/Tauri 构建依赖环境，确认最小工具链与 ABI、不使用不明镜像/未审签名、没有扩张构建机到终端或 Agent 权限。可以通过单独批准的固定开发镜像/隔离 runner 进行原生编译；若更换镜像或预制依赖，必须记录其 digest、包来源、受控维护/更新流程和失败回退，然后再让完全相同的 Rustfmt、`cargo test --workspace --locked`、严格 Clippy 和真实 `.deb` ABI/归档/OCI 流程跑完。此处**只规定目标，不代表已有可信镜像或完成验收**。不应简单延长超时、忽略 Clippy 或把旧 .deb 证据当作当前二进制。

## 验收记录（2026-11-24 核对）

| 验收 | Exact SHA / Workflow | 当前可证明事实 | 尚未证明 |
| --- | --- | --- | --- |
| 历史旧 GUI .deb | [旧 L2B-10 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) | 当时原生候选及 Ubuntu OCI L2 PASS | 新的 COSMIC GUI 源码 |
| 新 GUI 原生测试 | [107dc8b4 / #45315213247](https://github.com/kunlong-luo/lanveriq/actions/runs/45315213247) | **overall FAIL**；frontend、安全 L1、Cargo lock/Rustfmt PASS；Ubuntu APT 前置 12 分钟超时 | Rust 单测/Clippy SKIPPED；该 SHA 无完整 native L2 |
| 新源码未签名候选 | [161879ea / #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) | 源码敌意单测、冻结 pnpm 测试/类型/构建、Cargo lock 与 Rustfmt PASS；原生 APT 前置进行中 | 新 .deb 是否构建、ABI/归档/OCI 全流程是否 PASS |
| Pop!_OS24 COSMIC 实机 | [G-05 L3 issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) | NOT RUN | 正常用户 GUI 启动、WebKitGTK/Wayland、授权安装生命周期 |

**L2B-13 退出条件：** 必须确认真实工作流整体 SUCCESS，并分别确认对应同源包真实存在、文件哈希、归档脚本、Syft、ELF、官方 dpkg-shlibdeps 10 组 ABI 最低版本零缺口、OCI APT 模拟与先行严格 Rust/Clippy 成功；记录实际源 SHA、run/attempt、包 SHA-256。当前包哈希未知，不得复制旧包 SHA 或假定新审计成功。失败/取消保持原样。

**发布另行批准：** 正式发布签名身份/密钥托管、依赖许可证、最小 polkit 安装权限、真实 Pop!_OS COSMIC L3、Agent 自愿授权安装/卸载/升级与隐私、G-02 独立 WORM、G-07 法务、G-08 运维及组织审批均保持 OPEN。没有可分发一键 Agent 安装包，v0.1 NOT RELEASE READY。

[当前状态](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) · [L2B-10](desktop-tauri-l2b10-clean-ubuntu-oci-apt-2026-11-24.md)。
