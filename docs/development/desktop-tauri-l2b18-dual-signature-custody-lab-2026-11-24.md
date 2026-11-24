# DESKTOP-L2B-18 — 双角色 Ed25519 离线签名验证实验与正式发行信任根托管退出条件

**SECURITY LAB · 2026-11-24 · OpenSSL 3 两把临时密钥/30 个测试 PASS · 独立发行者与复核方身份 NOT VERIFIED · 正式发布 NOT APPROVED · v0.1 NOT RELEASE READY**

## 为什么需要此阶段

[L2B-15 原生未签名 GUI-only Debian](desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md) 在源码 c4f04e44 上完成严格 L2，但那份 `lanveriq-desktop_0.1.0_amd64.deb` 没有正式发行签名、分发或安装授权。此前 L2B-2 的单一临时签名证明的是校验和密码学机制，不能证明发行者身份或双人审核。

本轮利用成熟 **OpenSSL 3 Ed25519** 实现一个**完全脱离产品运行时**的双角色离线密码学互操作测试：两个不同的临时私钥分别对**同一份原始 JSON 字节**签名，验证器检查双方签名及对应公钥 SHA-256 指纹；同时把该 JSON 内的文件 SHA-256、源码 SHA、GitHub run ID/attempt 与**调用方期望值和实际传入文件字节**精确对应。验证的是同一证据字节的双重密码学绑定，**不是**已批准发行的真实性证明。

## 本阶段实际完成的代码与证据

- [验证器](../../apps/desktop/tools/verify_desktop_dual_signer_lab.py)：标准库读取限长、普通文件且禁止 symlink 的 statement / package / 两把公钥 / 两份各 64 字节 detached signature，通过 OpenSSL 3 `pkeyutl -verify -rawin -pubin` 验签。两个 key 指纹必须不同，且分别匹配传入公钥字节；statement 严格检查文档类型、purpose、`release_approved=false`、仅限 GUI-only 的文件名及 digest / source / run / attempt。拒绝重复 JSON key、未知字段、大小/类型不符合的输入。
- [30 项实际密码学与敌意测试](../../apps/desktop/tools/test_verify_desktop_dual_signer_lab.py)：Ubuntu24 CI 临时生成**两个不同** Ed25519 keypair、真实签名合成测试包字节；接受正确双签名，但返回文本明确 `UNTRUSTED TEST KEYS, NOT RELEASE APPROVED`。负测涵盖任一缺失或篡改签名、角色互换与同一公钥复用、假公钥指纹、包字节追加、收据内容篡改后不重签、已**重新签名**的假 `release_approved=true`、真实源码/run/attempt 重放、错误 artifact 名称、重复字段/伪造 Polkit、symlink、超大输入与异常类型。
- [独立 LAB-only GitHub Actions](../../.github/workflows/desktop-dual-signer-lab.yml)：只读 checkout、Ubuntu24、自带 OpenSSL 3、无持久 key/生产 secret、无安装/网络上传/签发权限；并再次执行 [只读 Tauri 源码守卫](../../apps/desktop/tools/verify_desktop_readonly.py)、[L2B-16 发行事实拒绝门禁](../../apps/desktop/tools/verify_desktop_release_evidence_boundary.py) 和 [L2B-17 Agent 安装意图 DENY-ALL](../../apps/desktop/tools/verify_desktop_agent_install_intent.py)。

**真实验收：** [源码/workflow SHA b1f5cc0f / CI #45326441957](https://github.com/kunlong-luo/lanveriq/actions/runs/45326441957) **attempt 1，overall SUCCESS，30/30 Python tests PASS（Ubuntu24，约 0.248 秒），原有 Tauri/release/Agent-intent 安全检查 PASS**。本实验签的是临时目录的合成字节，**不是正式发行签名，也没有对历史 GUI-only Debian 候选包签署新的信任根**。

## 当前严格不可证明的部分

1. **密钥来源不可自证。** 两个 keypair 都在**同一临时 CI 管理域**生成；其 public-key SHA-256 指纹由调用者提供，测试代码没有独立线下批准的根清单或实体硬件保管。因此双签名虽有效，却不能推断密钥属于两个独立组织/审计人、无法防止同一恶意 CI 主体替换两把 key 和两个指纹。即使攻击者重签 `release_approved=true` 被本实验 schema 拒绝，也不表示真正的外部签发审批已建立。
2. **没有可信时间与撤销。** JSON 不声明可信时间、密钥生命周期、回滚单调高水位、被盗恢复/撤销或已批准保留期限；GitHub 时间或本地系统时间不能自动成为可信审计时间。
3. **没有完整可再现发行工件。** 仅对输入的原始 statement 字节和测试包 SHA 做检查；没有独立签名的工件透明日志、不可变制品托管、真实 Agent 包、完整操作系统运行时 SBOM、发行者法律主体、生产许可证审批。
4. **没有安装授权。** 校验器不接入 Tauri IPC、Polkit/root broker、dpkg、systemd 或 Agent 配置，也不产生成功安装/发布判据。JSON 的 `release_approved=false` 是必须约束，不是一个可置为 true 的隐藏开关。

## 真正可信发行根及双管理域验收的退出规范（全部未通过）

| 退出要求 | 谁提供不可自证的独立材料 | 本阶段状态 |
| --- | --- | --- |
| 法务批准的发行主体、许可、合法数据保留与第三方运行时 SBOM | 产品/法务/许可证责任人 | PENDING |
| 实际签名私钥的独立保管组织、HSM/KMS 或离线设备身份与风险记录 | 发布工程/安全及不同身份域的审核方 | NOT SELECTED |
| 独立批准并分发给**接收端**的信任根/指纹（不可由签名声明自行提供） | 安全根保管方 / 接收设备配置授权方 | NOT IMPLEMENTED |
| 与包/source/run/attempt 绑定的正式双审审批，主体不能共用 root/CI 管理员 | 发行者和独立复核人 | NOT AUTHORIZED |
| 受信任时间、密钥撤销和轮换、审批失效与回滚高水位检查 | 独立时间/见证与恢复责任人 | NOT IMPLEMENTED |
| 不可变软件包、精确构建工件来源、透明审计记录及抗重放 | 制品托管/独立安全域 | NOT IMPLEMENTED |
| 有界 Polkit agent 安装桥、受限操作和用户同意、Agent .deb 原生隔离 L2 | 内核与终端安全审核者 | NOT IMPLEMENTED |
| 经授权的 Pop!_OS24/COSMIC 真物理机器安装/撤销/恢复 L3 | 具名合法操作员 | NOT RUN |

下一阶段应先通过外部组织决策明确**真实签名根、签名者/批准者独立管理域、密钥/撤销/可信时间**，再把密码学实验扩展为仅接受**独立预置**根的接收端安全验证。未批准之前，不提交假 `.policy`、发布密钥、对外包、Agent 安装器或真实联网功能。

**最终状态：仅双角色临时签名及拒绝重放的 L1 实验 PASS。G-02/G-05/G-07/G-08/G-09 OPEN；v0.1 NOT RELEASE READY。**

[L2B-17 安装意图与 Polkit 威胁模型](desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md) · [L2B-16 发行权限边界](desktop-tauri-l2b16-release-evidence-denial-contract-2026-11-24.md) · [发布阻塞清单](../planning/release-gap-register.md) · [当前状态](../CURRENT_STATUS.md)。
