# DESKTOP-L2B-15 — 当前源码 GUI-only Debian L2 完整审计与 Pop!_OS L3 交接

**EVIDENCE · VERIFIED · 2026-11-24 · GUI-only disposable Ubuntu CI L2 PASS；Agent 安装器 L2 和 Pop!_OS L3 NOT RUN；v0.1 NOT RELEASE READY。**

## 已通过的真实原生候选包审计

[精确源码和工作流 SHA c4f04e442be7da8181e9e62293764fbbb80e41d0](https://github.com/kunlong-luo/lanveriq/commit/c4f04e442be7da8181e9e62293764fbbb80e41d0) 的 [Actions #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) **run attempt 1、overall SUCCESS、原生审计 job SUCCESS**。执行时间 2026-11-24 03:50–03:57 UTC，Ubuntu24 runner 镜像版本 20261116.133.1。此次证据仅适用于该源码 SHA，不得从旧版 .deb 的成功结果继承。

| 证据类别 | 实测结论 | 不能据此宣称 |
| --- | --- | --- |
| 175 份构建依赖 | 通过 [L2B-14 双 runner 预热和来源认证 #45321096944](https://github.com/kunlong-luo/lanveriq/actions/runs/45321096944)；此候选 runner **再次独立刷新已签名 Ubuntu APT 索引**，逐包比对包名、版本、架构、Size、SHA256、完整安装闭包，使用 --no-download 只安装一次性 CI 开发依赖，PASS | 非不可变 Builder、非给用户安装软件 |
| 源码和 Rust | 前端冻结锁、源码敌意负测、Rust 1.100/Cargo lock/fmt、真实 Rust workspace test、严格 Clippy -D warnings PASS | 不代表生产 Agent 工作流或用户权限 |
| Tauri 2 GUI-only Debian | **lanveriq-desktop_0.1.0_amd64.deb**（约 2.87 MiB），真实编译及恶意归档/非授权 hook 检查 PASS；仅 native GUI launcher 和图标，无 Agent、service、config 或安装脚本 | 不是图形 Agent 一键安装器；未执行 dpkg --install |
| 候选包 SHA-256 | **83ab54d1331213335e3ec77e383c6b8c384690f37826e42413ffc35d2efe504a** | 未签名、没有发布上传，校验和不是发行可信身份 |
| Anchore Syft v1.54.0 | 源码 SPDX 识别 608 包；提取载荷 SPDX 识别 1 包，回执/交付文件哈希一致性 PASS | Payload SPDX 是局部 SBOM，不是完整操作系统运行库/许可证证据 |
| ELF/运行时包归属 | ELF loader/DT_NEEDED/直接 SONAME 和 Ubuntu24 所属包材料只读审计 PASS，覆盖 **15 个 SONAME** | 不证明传递依赖、所有动态插件或真实 Pop!_OS |
| 官方 dpkg-shlibdeps | Ubuntu24 官方最低依赖 **10 组**，Debian 包已声明 **12 组**，未声明 0、版本过弱 0、直接缺口 0，PASS | 非生产签名、安装或 G-07 法务批准 |
| 干净 Ubuntu24 OCI | 未安装候选包，仅运行依赖 APT 模拟，PASS；固定解析镜像 manifest **ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b** | 没有真实 dpkg 安装；非 Pop!_OS/COSMIC L3 |
| 离线 Ed25519 试验 | 一次性临时测试密钥的签名验真 PASS | 不是真实发行者密钥、签名根或签署授权 |

审计还记录 Ubuntu24 必须依赖图摘要 ec67375f062c20f992391f5c177122ef30e672cf024ef8be8bb6a5796b15559b，仅为日志证据，不是已签名外发工件。实际 GUI-only .deb 只在一次性 CI 内打包与审计，未上传、未安装、未分发；仓库 Tauri 源配置 bundle.active=false、targets=[] 不变，构建时仅通过一次性 CLI 配置覆盖开启 Debian 候选包打包。

## 结论的准确边界

**当前已验收的工程子项：** L2B-14 官方 Ubuntu APT 缓存独立认证；L2B-13 精确源码的真实未签名 GUI-only .deb、Rust/Clippy、安全归档、源和局部载荷 SBOM、ELF/直接 ABI、干净 Ubuntu24 APT 模拟和实验临时签名 L2。

**尚未获得通过的发布及实机子项：**

- 正式签名发行者/密钥独立托管、撤销/可信时间、许可证与**完整外部运行时**依赖审查、可追溯不可变发布工件及组织审批。
- 真正的 Tauri GUI 安装/配置 **独立 headless Agent**：经授权的最小 polkit 安装桥、来源已签的 Agent .deb、显式 opt-in、用户拒绝后不安装、可卸载和升级/回滚；当前 GUI 只有两个只读 IPC，安装操作禁用。
- 经合法批准的 **Pop!_OS 24.04 COSMIC 物理机器 L3**：Wayland、WebKitGTK 原生窗口、用户告知、零权限拒绝、真实安装/启停/升级/卸载的政策边界及 systemd 默认 disabled/inactive；CI 没有实体机也未执行 L3。
- 其它 v0.1 上线发布门禁：G-02 独立 WORM、G-05 Linux 实机、G-07 法务、G-08 运维/恢复、G-09 完整产品体验仍 OPEN；生产联网、采集、自动恢复、远控、生产证书签发继续 OFF。

## 下一阶段拒绝优先原则

将这次 exact SHA、Actions run/attempt、Debian SHA-256 和容器 digest 固定为只读证据；后续源码或安全能力改动必须重新对本次候选包做 **同新 SHA** 的全部完整检查，不允许跨 SHA 借用。未获得正式签名及最小权限安装授权前，只允许推进可自动验证的 L1/L2 负测和实机只读检查规范，禁止把现有 GUI-only 包部署为 Agent installer。真 Pop!_OS L3 只能在已授权物理设备上独立记录结果，不能用 Ubuntu24 OCI/ACTIONS PASS 代替。

**发行状态始终为 v0.1 NOT RELEASE READY。**

关联：[CURRENT_STATUS](../CURRENT_STATUS.md) · [L2B-13 完整审计](desktop-tauri-l2b13-current-source-gui-deb-reaudit-2026-11-24.md) · [L2B-14 缓存](desktop-tauri-l2b14-ubuntu-apt-prefetch-cache-lab-2026-11-24.md) · [发行缺口](../planning/release-gap-register.md) · [安装器 L1/L2/L3](../testing/linux-desktop-installer-acceptance.md)。
