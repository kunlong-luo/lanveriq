# DESKTOP-L2B-13 — 当前源码 Tauri GUI-only Debian 候选包复验

**SPEC · ACTIVE · 2026-11-24 · workflow SHA [161879ea](https://github.com/kunlong-luo/lanveriq/commit/161879ea3f1b94713b413260ae6852546f92df81) · [候选 CI #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) IN PROGRESS · v0.1 NOT RELEASE READY。**

## 为什么必须重新验收

已通过的 [L2B-10 原生 GUI-only Debian/干净 Ubuntu OCI APT 审计 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) 对应较早源码。随后 [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) 将只读 Pop!_OS/COSMIC Python 检查器以两个固定 Tauri IPC 之一嵌入原生 GUI，现有源码基线是 [107dc8b4](https://github.com/kunlong-luo/lanveriq/commit/107dc8b40bcd7cb5c60984dafd2f545398de9b39)。旧源码的成功归档不能替代新二进制的严格检查，也不能证明 Agent 安装权限。

## 本轮代码修改与自动化范围

仅修改 [.github/workflows/desktop-deb-candidate.yml](../../.github/workflows/desktop-deb-candidate.yml)。新增实际 Tauri Rust/React 源码、Cargo/pnpm 锁文件、原生构建输入、Python 预检与相关安全负测变更的 push 触发；纯 README/文档改动不触发昂贵的候选包构建。workflow_dispatch 仍可用，所有作业都是隔离 Ubuntu24 CI，绝不接触生产设备。

打包前在同一 SHA 上验证：严格双 IPC 源码安全门禁和敌意归档/证据单测、22 项 Pop!_OS 合成及真实 Ubuntu 非目标拒绝测试、pnpm 冻结依赖和 React/Vitest/TypeScript/构建、Cargo.lock 与 Rustfmt、实际 Rust workspace 单测、Clippy --all-targets --locked -D warnings。APT 前置步骤在一次性 runner 中安装 Tauri 的 WebKitGTK 构建依赖，限时 12 分钟、30 秒网络超时、两次重试；未更改 Ubuntu 镜像，也未安装候选包。

上述测试全部通过才执行现有官方 Tauri CLI 的**未签名 GUI-only .deb 构建**。源代码中 bundle.active=false、targets=[] 不变，只有 CI 运行时临时启用仅 deb 打包的 CLI 覆盖。生成文件仅在 runner 的临时目录中审计，**不得上传、分发、签发正式密钥、执行安装或启动 systemd Agent**。

原有归档链仍需实际完成：未授权 maintainer scripts/Agent payload/shell/tray 的拒绝检查；真实提取 payload SHA 和两个 Syft SPDX 检查；ELF loader/DT_NEEDED 与 SONAME；Ubuntu24 包归属和版权材料；已安装强制依赖闭包；官方 dpkg-shlibdeps 直接版本最低要求与 Debian Depends 零差距；全新 Ubuntu24 OCI 只读 bind + apt-get -s 求解及已解析容器 digest；**仅临时实验密钥**的 Ed25519 验签。源端/包端、run ID、run attempt 与 SHA 都必须同源，不可跨提交继承。

## 验收记录（2026-11-24 核对）

| 验收 | Exact SHA / Workflow | 当前可证明事实 | 尚未证明 |
| --- | --- | --- | --- |
| 历史旧 GUI .deb | [旧 L2B-10 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) | 当时原生候选及 Ubuntu OCI L2 PASS | 新的 COSMIC GUI 源码 |
| 新 GUI 原生测试 | [107dc8b4 / #45315213247](https://github.com/kunlong-luo/lanveriq/actions/runs/45315213247) | frontend、安全 L1 与 Rustfmt PASS，WebKitGTK APT 前置执行中 | 该 SHA 完整 Rust/Clippy L2 |
| 新源码未签名候选 | [161879ea / #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) | 源码敌意单测、冻结 pnpm 测试/类型/构建、Cargo lock 与 Rustfmt PASS；原生 APT 前置进行中 | 新 .deb 是否构建、ABI/归档/OCI 全流程是否 PASS |
| Pop!_OS24 COSMIC 实机 | [G-05 L3 issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) | NOT RUN | 正常用户 GUI 启动、WebKitGTK/Wayland、授权安装生命周期 |

**L2B-13 退出条件：** 必须确认真实工作流整体 SUCCESS，并分别确认对应同源包真实存在、文件哈希、归档脚本、Syft、ELF、官方 dpkg-shlibdeps 10 组 ABI 最低版本零缺口、OCI APT 模拟与先行严格 Rust/Clippy 成功；记录实际源 SHA、run/attempt、包 SHA-256。当前包哈希未知，不得复制旧包 SHA 或假定新审计成功。失败/取消保持原样。

**发布另行批准：** 正式发布签名身份/密钥托管、依赖许可证、最小 polkit 安装权限、真实 Pop!_OS COSMIC L3、Agent 自愿授权安装/卸载/升级与隐私、G-02 独立 WORM、G-07 法务、G-08 运维及组织审批均保持 OPEN。没有可分发一键 Agent 安装包，v0.1 NOT RELEASE READY。

[当前状态](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [L2B-12](desktop-tauri-l2b12-cosmic-readonly-gui-integration-2026-11-24.md) · [L2B-10](desktop-tauri-l2b10-clean-ubuntu-oci-apt-2026-11-24.md)。
