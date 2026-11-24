# DESKTOP-L2B-16 — GUI-only L2 证据边界与发行权限默认拒绝契约

**ENGINEERING SECURITY CONTRACT · 2026-11-24 · OFFLINE · 不建立信任根、不签名、不安装、不发行 · v0.1 NOT RELEASE READY**

## 目的与本轮完成内容

真实 [c4f04e44 / GUI-only Debian 严格审计 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 已 PASS，不代表“Agent 一键安装器 / 正式发行 / 真 Pop!_OS L3” PASS。本轮把这种不能混同的证据边界固化为**可机读但不产生权限的仓库回归契约**：

- [版本化证据文件](../../docs/planning/desktop-release-evidence-boundary.json)：只记录历史 GUI-only Ubuntu24 L2 的精确 source SHA、run ID/attempt、未签名 Debian 包名与 SHA256、Ubuntu OCI digest，以及独立桌面原生预览的精确 SHA/run。将真正 Agent 安装、生产发行授权、WORM、法律、运营、完整 UX 和 Pop!_OS24 COSMIC 物理 L3 均显式标记为 OPEN/NOT RUN/NOT APPROVED/OFF。
- [拒绝优先的 Python 标准库验证器](../../apps/desktop/tools/verify_desktop_release_evidence_boundary.py)：拒绝未知/缺失字段、重复 JSON key、符号链接、过大记录、非标准 JSON 值、虚构或漂移的 CI 源码/哈希、把 unsigned 标成正式签名、包上传/安装、伪造 PoP!_OS 实机成功，以及任何生产功能权限开启。只**核对已提交记录与固定已审历史事实**，不访问生产系统。
- [30 项独立合成正负测试](../../apps/desktop/tools/test_verify_desktop_release_evidence_boundary.py) 和 [独立 GitHub Actions](../../.github/workflows/desktop-release-boundary-offline.yml)：Ubuntu24、contents:read、no secrets、无包下载、无 polkit/sudo/安装、无发布上传，在 push 上校验 JSON 与桌面现有 source-level 只读锁。工作流改变只影响这个快速安全检查，不强制重跑耗时原生打包。
- 既有 [verify_desktop_readonly.py](../../apps/desktop/tools/verify_desktop_readonly.py) 继续把 Tauri 两个 IPC、关闭的 bundle、没有 tray/autostart/network 权限与 React release gates 作为独立真实源码断言；新 JSON **不是**可开启功能的配置，绝不反向驱动 React/Rust 功能开关。

**实际 CI 证据：** [desktop release-boundary #45324049249](https://github.com/kunlong-luo/lanveriq/actions/runs/45324049249) 在 SHA dc82b821 上 **overall SUCCESS**：30 项合成测试和当前只读源码 guard 均通过。验收级别 **L1 离线仓库证据一致性**，不包括签名验证、组织认可、可信时间、撤销、真实 .deb 分发或 Pop!_OS 实机。

## 证据权限矩阵

| 子项 | 真实状态与取证 | 不能升级的结论 |
| --- | --- | --- |
| GUI-only Tauri `.deb` | [run #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) actual native L2 SUCCESS；source c4f04e44；package SHA-256 `83ab54d1331213335e3ec77e383c6b8c384690f37826e42413ffc35d2efe504a` | 历史 L2 工程审计不构成公开下载或 Agent 安装许可 |
| React + native readonly preview | [run #45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) 两个 job SUCCESS；source ea62c63c | Ubuntu24 runner 不是真 Pop!_OS/COSMIC L3 |
| 实际发行签名 | **NOT APPROVED**；此前 Ed25519 仅 ephemeral LAB | 没有正式 publisher 身份、独立密钥管理/签名根、受信任时间、吊销或组织双审 |
| Agent 包和安装桥 | **NOT IMPLEMENTED/NOT RUN**；现有 GUI 仅两个只读 IPC | 不能通过修改 JSON/环境变量开启 root、polkit 或 systemd 操作 |
| G-02/G-05/G-07/G-08/G-09 | **全部 OPEN**；L3 为 NOT RUN | 不能用 CI、局部 SBOM、ubuntu OCI APT -s 替代独立 WORM、法律/运维审批或真实物理机 |

## 严格限制与后续退出条件

本工具是**拒绝不实状态声明的版本化回归门禁，不是可信证明服务**；固定事实来自已核对的 GitHub 日志，不对 GitHub 工作流或外部审批签名进行实时验证，已写入 JSON 的信息可以被任何有仓库提交权限的人尝试修改。因此，测试通过**不得视为外部签名、真实不可变存储或正式授权**。未来即使有人将 `v01_release_ready`、`signed_by_release_publisher` 等布尔值改为 true，现行验证器仍**必须失败**；不得将这类变更当作可用的“Release Switch”。

下一步应先通过独立组织流程取得：批准发行主体及可信根、发行密钥独立托管及撤销/可信时间、实际 Agent 包不可篡改工件和第三方许可证审查、具名操作人授权及最小化 polkit 安装/卸载安全审计、对 Pop!_OS 24.04 COSMIC 实机的非生产演练授权。**任何开启安装/签名/生产服务的能力都需要独立新设计、外部批准与新同源码 CI 和 L3 证据；此仓库 JSON 或本验证器永不颁发批准。**在真实授权之前继续执行离线负测和只读兼容性检查即可。

[L2B-15 实际 .deb 审计证据](desktop-tauri-l2b15-current-source-deb-l2-success-handoff-2026-11-24.md) · [发布阻塞清单](../planning/release-gap-register.md) · [实际 Pop!_OS L3 验收](../testing/linux-desktop-installer-acceptance.md) · [当前状态](../CURRENT_STATUS.md)。
