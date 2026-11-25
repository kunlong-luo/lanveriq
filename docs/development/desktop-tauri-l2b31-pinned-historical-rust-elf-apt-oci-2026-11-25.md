# DESKTOP-L2B-31 — 固定历史 Rust 源码的不同 ELF 原生 APT 降级与恢复

**验收：PENDING L2** · 功能 SHA [`84c5a7174fd4e6b9b56027d4e9c05eef4e0c4407`](https://github.com/kunlong-luo/lanveriq/commit/84c5a7174fd4e6b9b56027d4e9c05eef4e0c4407) · [原生 OCI 工作流 #45562937199](https://github.com/kunlong-luo/lanveriq/actions/runs/45562937199) · **G-01～G-10 OPEN，v0.1 NOT RELEASE READY。**

## 为什么 L2B-30 之后仍需此项

[前一阶段 L2B-30](desktop-tauri-l2b30-native-apt-two-agent-versions-oci-2026-11-25.md) 已在真实 Ubuntu24 原生 APT/dpkg 中证明 13/13：同一个 Rust ELF 仅调整 Debian control `Version` 可进行隔离安装、无人值守降级拒绝、显式覆盖后降级/恢复，配置和私有状态保留。**它没有验证两个不同编译二进制之间的真实文件替换。**

本阶段继续复用系统 APT、`dpkg-deb`、GnuPG `Signed-By`、固定 Ubuntu OCI 和已有 Tauri/Polkit 拒绝门禁，不开发自定义包签名/加密方案。

## 证据闭环（只针对隔离合成源）

- **固定历史源：** Git [`cd031894048dfcdc479aeaddd72e6254abde7303`](https://github.com/kunlong-luo/lanveriq/commit/cd031894048dfcdc479aeaddd72e6254abde7303)。此源码 `apps/agent/src/inventory_platform.rs` 与当前不同，`apps/agent/Cargo.lock` 相同；它是**仓库历史源码快照**，不是曾经批准发行或正式部署的历史产品。workflow 使用独立 `actions/checkout@v7` checkout full SHA、`persist-credentials: false`，仅稀疏检出 `apps/agent`。
- **真正两次 Rust 构建：** 当前 Agent 使用 Rust 1.100 `cargo --release --locked`；历史源也在隔离目录用相同锁依赖及工具链独立编译。校验工作区路径、Git `HEAD` 完全匹配钉住的 SHA、历史源文件与当前源文件不同、两个 ELF 都有 Linux ELF magic 且 SHA-256 **不同**。缺任一项拒绝，不把可自行输入的二进制认作受信任发布物。
- **真实 Debian 包构造：** 保持 dev1 为已审核的当前离线包；dev0 在已有无安装钩子的 `.deb` 中仅替换真实历史编译 ELF 与 `Version`，service unit 仍采用安全的 **当前 idle-only 模板**。再次解包检查确切 ELF/unit SHA256 和无 maintainer hooks。两份不同 ELF 的 `.deb` 均有精确包名、版本、大小、哈希和文件名，由相同**一次性、未批准的实验 signer** 签成 APT 源。
- **原生交易 2 项增量：** `signed-distinct-elf-unattended-downgrade-refused` 必须在实际安装 dev1 后，由原生 APT **拒绝**无人值守显式 dev0 降级，`dpkg-query` 保持 dev1 且文件 SHA256 仍为新版；`signed-distinct-elf-explicit-oci-downgrade` 只允许在一次性 OCI 内**显式例外**，真正核对 dev1→dev0→dev1 的 `dpkg-query` 版本和安装 ELF SHA256 变化，并核对配置/state 保留、无启动/无 boot enable、移除安装文件后保留操作员状态。
- **与先前基线联合回归：** 前 13 项原生 APT 签名、篡改、失效时间、旧索引、同 ELF 两版本交易均须继续 PASS。目标合计 **15 项原生 OCI**。GitHub runner 不能安装 Agent，`docker --network none --cap-drop ALL --security-opt no-new-privileges`，固定 Ubuntu24 镜像 digest、只读挂载，实验私钥不进 OCI。原生 CI 通过之前不能标记 `15/15 PASS`。

## 不获准的能力与待办

- 当前版本只是**旧 Git 源码 vs 当前 Git 源码的 CI 合成二进制**，不是正式历史发行物、已授权的回滚路径或 Pop!_OS 真机 L3。
- 单一 signer `Signed-By` 能证明签名一致性，不保证独立的可信版本高水位；本项若演示可显式降级，反而说明必须**另外设计和独立批准发行签名主体、硬件/隔离密钥托管、独立可信时间、签名元数据单调版本/摘要的外部托管**。
- 冷缓存/离线/重装后旧仓库回放拒绝、正式源码工件签名链、真正 root-side 每次 Polkit 授权 broker、Pop!_OS24 COSMIC 具名设备与书面许可、法务/SRE/生产 CA/WORM 均 **NOT RUN/OPEN**。生产联网、采集、自动恢复、远控、CA/身份提升继续 OFF。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [G-05 发行缺口](../planning/release-gap-register.md) · [Linux 交付路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [发行 runbook](../operations/release.md)。
