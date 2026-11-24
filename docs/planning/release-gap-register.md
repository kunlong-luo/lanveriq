# Lanveriq v0.1 发布门禁（唯一 LIVE-GAP）

**LIVE-GAP · 2026-11-24 · main · v0.1 NOT RELEASE READY。** 所有责任方是**尚待指定并独立签字的角色**，不是已经取得的发布批准。Gate 不能通过开发评分、CI 合成测试或界面勾选自动关闭。

[当前真实能力](../CURRENT_STATUS.md) · [工程路线](linux-v01-reuse-first-delivery-path-2026-11-24.md) · [验收等级](../testing/strategy.md) · [历史完整报告](release-readiness-2026-11-13.md)

## G-01–G-10：发布所需的真实退出证据

| Gate | 已有的非生产证据 | 必须补齐、由独立主体验收 | 责任类型 | 状态 |
| --- | --- | --- | --- | --- |
| **G-01 FIDO 官方来源** | D4-C9～C16 合成签名与 PG18 拒绝 | 官方 MDS BLOB 来源/证书链/撤销/可信时间/独立高水位 | PKI/安全 | **OPEN** |
| **G-02 独立 WORM** | PG18/离线对象锁证据 | 脱离业务及存储 root 的真实不可删、KMS/高水位、删除权攻击实测 | 安全/SRE/法务 | **OPEN** |
| **G-03 管理员 WebAuthn 双审** | 未可信凭据候选与反例 | 不同真人管理员的合法 RP/Origin/硬件 MFA、撤销/审计与拒绝自审 | 身份安全 | **OPEN** |
| **G-04 生产设备 CA** | 隔离 CSR/PKIX/TLS1.3 mTLS | 正式 CA/HSM/KMS、根托管、身份、签发/撤销/轮换和恢复 | PKI/安全 | **OPEN** |
| **G-05 签名 Agent 与 GUI 安装** | [真实 Agent + 临时 signed-APT OCI 9/9](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)；[root D-Bus ReviewLab 拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)；[原生恶意 Polkit YES 仍拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | **独立批准的发行身份/固定 Signed-By 根与包**，真实管理员每次 Polkit 授权、可审最小权限 root 事务 broker、升级/取消/恢复/卸载，Agent 默认 idle；**Pop!_OS COSMIC 实机验收** | 发行/安全/产品 | **OPEN** |
| **G-06 正式业务端到端** | [一次性 Linux runner OS/CPU/RAM→Rust/TLS→JPA/PG18→Next.js/Chromium](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 合法真实设备、生产 CA/MFA、真实用户登录/RBAC 与隐私/网络/业务全链授权 | 全栈/安全 | **OPEN** |
| **G-07 法务/隐私/许可证** | 隐私设计和第三方素材审查 | 终端与员工明确告知/授权、数据保留删除、RustDesk AGPL/Pro 和发行依赖书面批准 | 法务/隐私 | **OPEN** |
| **G-08 生产 SRE/灾备** | 隔离 PG18/Caddy/备份验证 | 独立密钥、运维、真实升级回滚、监控、灾备恢复、应急签收 | SRE/安全 | **OPEN** |
| **G-09 真实 UX/Pop!_OS L3** | Tauri 只读 GUI、元数据 COSMIC 预检和 CI Chromium | 获设备所有人许可的 Pop!_OS 24.04 COSMIC/Wayland 实机 GUI、键盘/无障碍、权限提示、取消/卸载、后台无托盘仍可发现 | UX/设备所有人 | **OPEN** |
| **G-10 Windows/RustDesk 范围** | Windows CI、RustDesk 合成协议 RD-2 默认拒绝 | 独立许可证/用户许可/L3，或书面批准 v0.1 OUT OF SCOPE | 产品/法务 | **OPEN / OUT OF SCOPE 待决定** |

## 最近实测 ≠ Gate 放行

| 截止阶段 | 功能 SHA / 实际原生 CI | 仅证明 |
| --- | --- | --- |
| **L2B-23** | [2af1cf34 / #45376989866 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) | 临时签名源下真实 Agent APT 安装/同版重装/卸载与 8 个拒绝；无正式发布身份。[报告](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) |
| **L2B-25** | [91c4669d / #45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | networkless OCI root-side D-Bus 实际发送者 UID/PID/Polkit 拒绝 + nonce；**没有安装事务**。[报告](../development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) |
| **L2B-26** | [f062f144 / #45385719766 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | 本机恶意 Polkit .rules 确实覆盖为 YES，root ReviewLab 仍 **NOT_APPROVED**，D-Bus 名称冒充与重放拒绝。[报告](../development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |

早期 [L2B-22](../development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)、[L2B-24](../development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)、NXT/D4/FIDO/WORM 以及所有 CI **FAIL→修复**链保留在原 dated reports、[GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions) 和 [历史验收账本](release-readiness-2026-11-13.md)；不能当成今天的生产许可。

## 下一项退出顺序

1. **G-05/L2B-27**：独立批准发行者身份、受信 signed Agent、实际固定来源；只有外部审批后才能开发/验证可部署 root broker 成功授权事务。无审批前可在无网络 OCI 验证签名回退风险、取消和拒绝，不发用户包。
2. **G-05/G-09 L3**：具名设备/操作者书面许可，真实 Pop!_OS 24.04 COSMIC / Polkit 对话框/升级回滚/卸载和合法独立无托盘 Agent 生命周期。
3. **G-01～04/G-07～08/G-06**：官方可信源、独立 WORM、CA/MFA、法务/SRE 与真实业务全链签收；G-10 另定首发范围。只有真实责任人、对应**功能 SHA+CI+L3 证据**、拒绝负测和回滚均齐全才可改变状态。

**全部 Gate 保持 OPEN；v0.1 NOT RELEASE READY；生产联网、采集、远控、自动恢复、CA 签发 OFF。**
