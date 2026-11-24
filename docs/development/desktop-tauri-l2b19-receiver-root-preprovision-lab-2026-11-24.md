# DESKTOP-L2B-19 — 接收端预置根/撤销/过期/回滚的离线 LAB 与正式信任根选型门禁

**SECURITY LAB · 2026-11-24 · 精确 SHA 5dc1c9a2 / Ubuntu24 #45328874536 34/34 PASS · 外部批准的信任根/时间/高水位 NOT IMPLEMENTED · 正式发行与安装 NOT AUTHORIZED · v0.1 NOT RELEASE READY**

## 目标与本轮实际验证

L2B-18 的 OpenSSL 3 双签名虽能认证两个测试 key 对相同原始字节的签名，却仍由签名测试调用者自己提供两个 key 指纹；从不可信包中取得公钥再验证签名会导致**自签自证**。本轮做一个**仅限隔离 CI 的接收端 LAB 根引导模型**，要求**签名声明不能自称根**，接收端检查另行传入的根文件 SHA-256、发行者/复核人两个角色、撤销列表、有效时间窗口及根版本高水位，再重用实际 OpenSSL3 双签名验真。

新增文件：
- [LAB-only 接收端 verifier](../../apps/desktop/tools/verify_desktop_receiver_root_lab.py)，无发行或安装权限。要求 root 是限长普通文件、准确命中**调用方预先持有的 root digest**；root schema 限定 `offline-interoperability-only`、`release_approved=false`、publisher/reviewer 两个**不同**的证书公钥字节 SHA-256、严格有效期、整数版号、去重的 SHA-256 撤销列表；校验传入的 LAB 时间位于有效期、root.version 不低于 caller 最低版本，随后把两个已预置角色 pin 对应到实际 Ed25519 公钥，调用 [L2B-18 的 OpenSSL3 双签名 verifier](../../apps/desktop/tools/verify_desktop_dual_signer_lab.py)。成功的唯一输出是 `LAB_ONLY`，不是 release approved。
- [34 项正负测试](../../apps/desktop/tools/test_verify_desktop_receiver_root_lab.py)，在 GitHub CI 内生成两把隔离临时 Ed25519 私钥和**合成非安装包字节**，独立生成 lab root 测试夹具与期望 root digest/时间/version 下限（都来自**同一 CI 管理域**）。检查改根不更新 pin、假冒 signer、两角色合并、任一角色撤销、重复撤销、过期/提前生效、非规范时间/非法日期、回滚、用 bool 混入版本、角色字段冒充生产授权、已重签的 release approved、包字节变动、source/run 重放、错误公钥、符号链接、重复 JSON key 等失败路径。
- [独立无密钥发布权限的 Ubuntu24 workflow](../../.github/workflows/desktop-receiver-root-lab.yml)：固定 Python 标准库与系统 OpenSSL 3，CI `contents:read`；不发布工件、不调用 Polkit/dpkg/systemd、不持久化任何私钥；保持原桌面只读 guard、L2B-16 发行状态门禁和 L2B-17 安装意图 DENIED。

**真实 CI 证据：** [commit 5dc1c9a2 / workflow #45328874536](https://github.com/kunlong-luo/lanveriq/actions/runs/45328874536)，Ubuntu24 runner **overall SUCCESS**，`Ran 34 tests ... OK`，只读 GUI 源码及发行/安装禁止检查 PASS。这是**离线 L1 接收端密码学/策略构型验证**，不代表已具有**生产**可信时间、持久高水位或由独立保管方提供的可信根。

## 必须防止的六类虚假结论

| 现有 LAB 的检查 | 其无法证明的生产事实 |
| --- | --- |
| root 文件与传入 SHA-256 pin 一致 | 根是实验 harness 提供，**并非**经独立审批、由可信软件/系统渠道安装到实际接收端的不可替换根 |
| publisher/reviewer 两个角色公钥不同，签名合法 | 两个私钥仍在同一 disposable runner 生成；**并非**两个管理域或不同具名审核人的密钥托管 |
| 时间落入 root 元数据的有效期 | 时间由 CLI 测试调用者输入，**并非**可信时钟、已签署时间戳或抗时钟回拨来源 |
| root.version 大于等于模拟高水位 | 高水位是 CLI 测试参数，**未**由防回滚介质持久化，也没有可信轮换链 |
| revocation 列表包含 key 时拒绝 | 撤销数据本身仍为 LAB root 文件字段；**不是**签署过的受信任撤销快照，离线过期/撤销分发未解决 |
| LAB_ONLY 且 `release_approved=false` | **不构成**发行批准、Agent 安装器认证、可下载包签名、受限 Polkit 授权或 Pop!_OS 物理 L3 |

任何人若能控制 CI 同时替换根和输入 pin、时间或高水位，都可以自己产生成功的 LAB 互操作证明。这是**测试输入不独立的根本局限**；请勿将该验证器用于生产校验。实际生产客户端不得信任 artifact 自带的 `root.json` 或通过用户可控参数直接提交的 pin。已有 L2B-16 `v01_release_ready=false` 等门禁继续独立约束，不为 LAB 增添发行权。

## 正式生产技术选型方向（推荐成熟标准，不自行实现 TUF）

| 方案层 | 优先审查 | 要求与待核问题 |
| --- | --- | --- |
| 发行仓库/软件包版本、根轮换和防回滚 | [The Update Framework (TUF) 规范](https://theupdateframework.github.io/specification/latest/) 的 Root / Targets / Snapshot / Timestamp；选用经审计的现成 TUF 客户端/发行端实现 | 初始可信根的**独立/安全预置**；threshold、跨版本 root 轮换/吊销、targets 包哈希与长度、timestamp 到期、新版单调序列与离线过期策略；**根丢失/失窃不得仅靠自签覆盖** |
| 二进制/工件签名和身份/透明度材料 | [Sigstore Cosign 离线验证 bundle](https://docs.sigstore.dev/cosign/verifying/verify/) 作为候选验证证据格式，与发行者主体及离线时间策略匹配 | 接收端独立固定的受信任根、证书身份约束、可验证的独立时间和透明度包含证据；**bundle 自带证书和键不能自称可信根** |
| 构建来源/许可、包哈希 | in-toto/SLSA 类结构化工件来源证据（是否采用需独立确定） | source/ref/CI workflow/依赖/真实 Debian `.deb` 字节摘要和完整外部运行时 SBOM；不把源码 SBOM 误称为完整运行环境许可批准 |
| 独立审核人的实际授权 | 具名发行者/审核方不同组织与密钥管理域、独立可核签审核工件 | 一次性 LAB 中两把钥匙不构成双管理域；不允许同一 CI 管理员双签代表“两个真人” |
| 最小系统安装权限 | 经审查的固定包/固定执行路径、独立工件验签后的受限 Polkit broker | 没有批准前不得提供 root helper/action、shell 执行接口或批量启动、联网、采集、远控 |

**注意：** TUF 是正式发行仓库/可信根轮换与版本策略的成熟候选；Cosign 的离线 bundle 是工件签名与证据分发候选，两者可分层组合，但目前**没有批准供应商、部署、密钥格式、签名策略或正式版本**。不应把本实验自行编写的 JSON 根冒充符合 TUF 的 root.json，也不应重复造轮子实现自有生产密码学。

## 外部依赖与下一阶段的真实退出标准

在正式接收端信任链可用以前必须具备：法务批准的发行主体、独立保管方对 TUF 初始根的具名批准与安全下发、至少两管理域私钥/阈值治理决议、经认可的签名/撤销/可信时间机制及备份恢复、设备端防回滚持久性与用户告知、与确切 Agent Debian 字节及源码绑定的可复核签名、许可证与运行时依赖审批、受限 Polkit 安装桥的真实 L2 安全测试，以及具名许可的 Pop!_OS24 COSMIC 实机 L3。任何一个条件未满足都**不得发布/安装**。

**当前明确状态：仅 L2B-19 接收端预置根模拟 LAB 34/34 PASS；生产可信根、真实撤销、可信时间、高水位、正式 Agent 包、Polkit 和 L3 全部 NOT IMPLEMENTED/NOT RUN。G-02/G-05/G-07/G-08/G-09 OPEN，v0.1 NOT RELEASE READY。**

[L2B-18 双签名 LAB](desktop-tauri-l2b18-dual-signature-custody-lab-2026-11-24.md) · [L2B-17 安装意图](desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md) · [发行 Gap](../planning/release-gap-register.md) · [物理 L3 验收](../testing/linux-desktop-installer-acceptance.md) · [当前状态](../CURRENT_STATUS.md)。
