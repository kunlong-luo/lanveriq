# DESKTOP-L2B-14 — Ubuntu 24.04 Tauri 开发依赖预热与缓存独立验收

**SPEC · ACTIVE · 2026-11-24 · 仅限隔离构建基础设施，不是签名 Builder / 发行授权 · v0.1 NOT RELEASE READY。**

## 背景与目标

旧版 [L2B-10 原生 Debian 候选 #45303829710](https://github.com/kunlong-luo/lanveriq/actions/runs/45303829710) 的成功审计不覆盖新增 Pop!_OS 只读 IPC 的当前源码。[L2B-13 当前候选 #45315837623](https://github.com/kunlong-luo/lanveriq/actions/runs/45315837623) 已因 Ubuntu WebKitGTK/GTK 开发依赖下载超过 12 分钟而 FAIL，Rust 单测、Clippy、真实包、ELF/Syft、官方 ABI 和 OCI 检查全部 SKIPPED。[只读依赖规划 #45317328366](https://github.com/kunlong-luo/lanveriq/actions/runs/45317328366) 显示约 171 项计划安装或升级、0 删除，但没有下载或安装。

本阶段使用 Ubuntu 系统 APT 的官方仓库签名索引与 GitHub Actions 官方 actions/cache，单独预下载开发依赖归档，再由另一个干净 runner 核实缓存可读。所有缓存仅用于加速构建，不属于完整 Builder 签名证据或可分发的 Lanveriq 软件。

## 实现和最小权限

- [独立 APT prefetch CI](../../.github/workflows/desktop-ubuntu-apt-prefetch.yml) 仅由其工作流文件变化或明确手动触发；只读 contents 权限，先检查 Ubuntu24 amd64 和桌面源代码默认拒绝。
- APT 使用 runner 原有已配置的 Ubuntu 仓库，不改镜像 URL、不绕过签名。执行 apt-get update 和 apt-get -s 后，拒绝删除计划，并把有版本的传递依赖模拟计划、APT 源文件哈希、runner 镜像版本、架构和仓库标识绑定到 SHA-256 缓存键。此缓存键是加速身份标识，**不是发布签名**。
- 仅执行 apt-get --download-only install 来下载 WebKitGTK4.1、GTK 等九组原生构建入口的依赖，设置网络重试和超时界限。下载前后用 dpkg-query 检查已安装软件包状态必须逐字一致。没有装软件、启动服务、执行真实 GUI 或制作 Agent 安装包。
- 缓存只接受完整 .deb 归档，不包含 root-only lock 或 _apt 私有 partial 目录。CI 改用 /var/tmp 下可遍历的单独缓存目录，_apt 用户独占部分下载目录（0700），避免第一次运行的不安全 root 下载回退。
- cache-probe 第二个 Ubuntu runner 通过精确 key 恢复缓存，必须 cache-hit=true、完整 .deb 数量与预下载步骤输出一致、全部可读，否则整个工作流 FAIL。它不安装这些包，不将缓存宣布为受信任软件。

**下一步不能直接跳过：** 只有缓存保存和跨 runner 恢复 PASS 后，才能单独审批是否让实际 Tauri 严格 CI 使用缓存。后续仍需每次依赖 Ubuntu APT 已验证的索引核对文件 hash 和版本，并保留 Rustfmt、cargo test、Clippy -D warnings、源代码默认拒绝、真实 .deb、Syft/ELF、dpkg-shlibdeps、干净 OCI APT 与 Pop!_OS L3 全部门禁。

## 后续真实候选包 FAIL 的确认（2026-11-24）

[候选工作流 7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) 现已 **overall FAIL**：Ubuntu24 APT 从其配置的 us-east-2.ec2.archive.ubuntu.com 下载开发依赖，在 12 分钟步骤界限终止（日志至第 69 项）。所有源检查、React、Cargo lock 和 Rustfmt PASS；真正 Rust 单测/Clippy、GUI .deb、Syft/ELF、官方 shlibdeps 和清洁 OCI 验收均 **SKIPPED**。必须保留这项失败，不能将它描述为缓存实验中的 PASS 或当前发布就绪。

## 本轮精确来源证据

| 来源 | 实际结论 | 仍然不足 |
| --- | --- | --- |
| [首版 SHA dac7f6ad / #45318418790](https://github.com/kunlong-luo/lanveriq/actions/runs/45318418790) | GitHub CI 总体 SUCCESS；Ubuntu APT 真实下载 **175 份 .deb，约 70 MB**，已安装 dpkg 状态未变；但日志显示 _apt 沙箱权限问题，后置 GitHub 缓存 tar 因无法读取 partial / lock **保存失败** | 不得把 overall SUCCESS 视为缓存保存或恢复通过，更不是安全 Builder |
| [修订 SHA b695945c / #45318613848](https://github.com/kunlong-luo/lanveriq/actions/runs/45318613848) | 改为 _apt 可访问的 /var/tmp、完整 .deb 文件匹配、跨作业缓存恢复拒绝优先；验收运行中 | 最终 cache-probe PASS、可复用缓存、正式候选打包未验证 |
| [候选 SHA 7cf23f3e / #45316573023](https://github.com/kunlong-luo/lanveriq/actions/runs/45316573023) | 当前源码候选仍在原生 APT 依赖环节，结果未完结 | 同源码 Rust/Clippy、真实归档及 ABI 未通过 |

**关闭本实验的条件：** 同 SHA 下预热作业与独立恢复作业均 SUCCESS，工作流整体 SUCCESS，明确记录完整 .deb 数量和缓存键，且无已安装包状态变化、无不安全 root 下载回退。若任何 post-cache 或 probe 失败，保持 FAIL / NOT VERIFIED。

G-05/G-07/G-08/G-09、签名、授权安装、Pop!_OS24 COSMIC 物理 L3 与独立 WORM 均 OPEN；**v0.1 NOT RELEASE READY**。

[当前状态](../CURRENT_STATUS.md) · [L2B-13](desktop-tauri-l2b13-current-source-gui-deb-reaudit-2026-11-24.md) · [发布缺口](../planning/release-gap-register.md)。
