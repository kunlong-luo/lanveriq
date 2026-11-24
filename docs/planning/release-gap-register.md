# Lanveriq v0.1 发布阻塞清单（唯一 LIVE-GAP）

**LIVE-GAP · 2026-11-24 · main · v0.1 NOT RELEASE READY。** 表中责任方指**未来必须实际审批的角色**，不是已签字人。任何 Gate 不能因“合成 CI PASS”、技术评分或 GUI 勾选而自动关闭。

[当前已证明的事实](../CURRENT_STATUS.md) · [Linux v0.1 工程交付路线](linux-v01-reuse-first-delivery-path-2026-11-24.md) · [项目评分](project-scorecard.md) · [历史验收完整账本](release-readiness-2026-11-13.md)

## G-01–G-10：不可跳过的发布门禁

| Gate | 已有的真实证据 | 必须补齐的退出条件 | 责任类型 | 状态 |
| --- | --- | --- | --- | --- |
| **G-01 P0 FIDO 官方可信来源** | D4-C9–C16 的未可信合成元数据、DB 拒绝负测 | 官方 MDS BLOB 真实来源、经批准根/签名链、CRL、可信时间、撤销、抗回退 | 安全/PKI | **OPEN** |
| **G-02 P0 独立 WORM/KMS 见证** | 合成 PG18 审计与离线 WORM 候选评估 | 数据库 root 之外的独立管理域、底层真实不可删除性、密钥/时间/高水位独立托管、回退实测 | 安全/SRE/法务 | **OPEN** |
| **G-03 P0 管理员 WebAuthn MFA 双审** | 未可信凭据候选/源码拒绝逻辑 | 不同真人管理员硬件 MFA，真实 RP/Origin/挑战、拒绝自审、撤销和真实授权审计 | 身份安全 | **OPEN** |
| **G-04 P0 生产 CA 与设备证书** | 隔离 Rust CSR→Java PKIX、TLS1.3 mTLS/过期与吊销负测 | 独立 CA/HSM/KMS、发行/吊销/轮换/身份所有人确认及恢复演练 | PKI/安全 | **OPEN** |
| **G-05 P0 Agent 签名图形安装器** | Tauri 只读审核 UI + CI；Agent idle-only Debian 候选在 networkless Ubuntu OCI 中安装/重装/删除过；**L2B-23 真实 Rust Agent 包通过临时 Signed-By 仓库，在 Ubuntu24 OCI 内原生 APT install/reinstall/remove + 八项拒绝 [#45376989866 9/9 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)**，但实验发行身份未获批准；L2B-22 另新增**未安装的 Polkit install/upgrade/remove XML 全 no**，并经 [#45375272477 39+30 项离线拒绝测试 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)；**L2B-24 real Ubuntu24 OCI 原生 Polkit 124/D-Bus + Rust 真 PID/start-time/UID 三动作 × 两 Unix 账号 6/6 拒绝 [#45379398181 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)**；**L2B-25 真正 OCI root-side D-Bus ReviewLab 从系统 bus 取得 UID/PID，经内核核验及 Polkit 8 次拒绝、同 UID nonce 重放拒绝 [#45382618806 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)**；但**仍无可安装的受审 root-side 事务 broker、正式身份/实机会话/管理员授权** | 经批准产品签名根和固定 APT 仓库、受审最小权限 Polkit broker、管理员每次明确授权、实际签名 Agent 工件与安装/升级/卸载/取消/恢复；默认不可联网或自动启动 | 发行/安全/产品 | **OPEN** |
| **G-05 P0 Pop!_OS24 COSMIC 实机 L3** | Ubuntu24/COSMIC **元数据**与真实原生 Tauri 编译 CI，通过并非实机 | 具名合法设备，真实 Wayland/COSMIC、图形授权/键盘/缩放、签名安装/取消/卸载/后台无托盘且可发现状态、性能和失败回滚 | 用户/UX/安全 | **OPEN** |
| **G-06 P0 生产业务端到端** | 实际一次性 Linux runner 只读 OS/CPU/RAM→Rust SQLite→双 mTLS→Spring JPA/PG18→受限 Next.js/Chromium **L2 PASS** | 正式获授权的真实 Agent、CA、管理员、Next.js 生产登录/设备/库存/恢复链和安全配置、生产数据合法告知 | 全栈/安全 | **OPEN** |
| **G-07 P0 法律/隐私/三方许可证** | 隐私技术原则，部分第三方 ABI/copyright 哈希仅作审查输入 | 员工/设备合法用途与告知、数据保留/删除、RustDesk AGPL/Pro 与全部软件分发许可证的书面批准 | 法务/隐私 | **OPEN** |
| **G-08 P0 生产 SRE/回滚/恢复** | PG18/Caddy 合成验证及隔离备份负测 | 最小权限环境、密钥托管/日志监控、备份/灾备/升级/回滚/应急与发布签收 | SRE/安全 | **OPEN** |
| **G-09 P1 Web/GUI 真实 UX L3** | Tauri L1 安装资格界面、真实 Chromium 仅 CI 数据闭环 | 真实 Pop!_OS COSMIC/Wayland 高 DPI/键盘无障碍和错误/取消/卸载；正式 Web 登录/窄屏/滚动/权限拒绝态 | UI/产品 | **OPEN** |
| **G-10 P1 Windows/RustDesk 范围** | Windows CI/远控隔离协议实验；RD-2 默认拒绝 | 独立 Windows L3 与 RustDesk 许可证/用户授权；或书面批准本版 OUT OF SCOPE | 产品/法务 | **OPEN / OUT OF SCOPE 待决定** |

**2026-11-24 G-05 Polkit 仅源审查进展（不关闭 Gate）：** [L2B-22 功能 SHA efb7a558 / CI #45375272477 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477) 的 Polkit XML 3 个 action 全部 no，严格独立默认拒绝/禁止 KEEP 和隐式授权的 30/30 负测，联合原安装意图 39/39 PASS；没有安装 .policy、pkexec、root helper、可执行 broker、生产签名 Agent 包或真实 Pop!_OS24 L3。[具体审计与失败历史](../development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。**G-05/G-09 OPEN、v0.1 NOT RELEASE READY。**

**2026-11-24 G-05 Agent 安装 L2 的重要跃迁（仍 OPEN）：** [L2B-23 功能 SHA 2af1cf34 / 原生 APT OCI #45376989866 attempt 1 SUCCESS，9/9](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)。仓库中真实 Rust Agent deb 经严格原生归档审计后，使用 CI **一次性** Ed25519 签名源和显式 Signed-By：Docker networkless/cap-drop 下 apt-get 真正 install、同版本 reinstall、remove，保留模拟操作员文件、没有 systemd 启动/自启；八项签名/索引/包完整性/包名/日期拒绝均由**真实 APT**检验。先前 [工作流解析 #45376908241 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45376908241) 无 jobs，修复后 PASS；记录失败。**仍然没有受批正式发行根或发行仓库、Polkit broker/每次用户授权、跨版本升级、Pop!_OS24 实机 L3，不能发行/安装到真实用户电脑。** [完整 L2B-23](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。**G-05/G-09/正式 G-06 OPEN、v0.1 NOT RELEASE READY。**

**2026-11-24 G-05 OS 权限拒绝 L2B-24（不关闭 Gate）：** [功能源码 ba04f55c / CI #45379398181 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)。之前的安装意图协议和 Polkit XML 静态 L1 变成**真实系统 `polkitd`+DBus+ `pkcheck` L2**：在隔离无网络 Ubuntu24 OCI 中安装 source policy，仅在容器 overlay 有效； `pkaction` 验证三动作 all-no，对 `nobody` 和 `daemon` 真实 Unix 账号 **6/6** 显式拒绝，且新增不提权的 Rust PID/start-time/UID 自绑定 gate 拒绝动态操作及伪造环境。**不意味着已通过 Polkit 的管理员交互、服务端 D-Bus 同行认证或能以 root 执行 Agent 安装。没有正式发行根/签名、真实会话、Polkit broker 或 Pop!_OS24 实机 L3。** [详证/失败修复](../development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)。**G-05/G-09 OPEN。**

**2026-11-24 G-05 L2B-25 系统总线同行身份核验（Gate 继续 OPEN）：** [功能 SHA 91c4669d / Ubuntu24 OCI #45382618806 attempt 1 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)。已从“只在调用方自己运行 `pkcheck`”提升到真正 root-side **system D-Bus ReviewLab**：官方 `GetConnectionUnixUser/GetConnectionUnixProcessID` 获取真实总线 sender 的 UID/PID、Linux /proc 四 UID 与进程 start-time 复核；`nobody/daemon` 三动作 6 次 + 不同 UID nonce 独立拒绝 + 环境伪造拒绝，**8 次原生 Polkit 拒绝**；同 UID 跨进程请求 ID 重放拒绝、恶意方法参数拒绝，另 **9/9** 同服务决策测试确认**即使 Polkit 返回 0 也 `NOT_APPROVED`**。所有代码只运行于 networkless/disposable Ubuntu24 OCI，没有启动 Agent、APT 安装命令或用户系统 D-Bus。**尚没有正式发行身份、GUI 触发、逐次管理员批准、可执行安装/回滚 broker 或 Pop!_OS24 COSMIC 实机 L3；G-05/G-09/G-06 仍 OPEN。** [完整证明与失败修复](../development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md)。

## 与发布无关的已通过子项（不得偷换概念）

- **Desktop L2B-21：** [功能源码 a42094bb / #45368842676，两 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676)：10 项安装审核组件、29 项全量 React、26 项安全变异、22 项 Pop!_OS 只读预检、6 项 Tauri Rust/Clippy。**没有任何真实 Agent 安装授权。** [未签名 GUI-only Debian 归档 #45368638490 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638490) 是独立源码 fe92d11a 的 GUI 包，**无 Agent/服务/hook，未发布或安装**。[L2B-21 专题](../development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md)。
- **保存失败：** [#45368132407 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45368132407) 揭示门禁正则误用 TypeScript 类型声明；已修复并复验，不曾开启正式安装权限。
- **Linux 实际 CI 样本 L2：** [e6b56c88 / #45365942781，两 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) 是**GitHub runner 的真实 OS/CPU/RAM**和完整数据路径，含断线/吊销/冲突拒绝；**不是获授权员工/Pop!_OS 实机**。[来源核对及失败历史](../testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。
- **底层包与系统成熟能力复用：** [Agent idle-only OCI dpkg L2 #45339799209](https://github.com/kunlong-luo/lanveriq/actions/runs/45339799209)，[APT 官方 Signed-By 合成索引 LAB 9/9 #45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405)。**没有正式签名者和受限 Polkit broker**。

D4/FIDO/WORM、L2B-1～20、NXT、撤回旧 AWS SDK、全部失败运行仍存于 [历史完整账本](release-readiness-2026-11-13.md)、[专题索引](../README.md) 与 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。压缩本清单只是**去重**，不删除唯一安全负测，也不把老 PASS 改成发布授权。

## 下一阶段退出顺序

1. **优先 G-05**：独立批准可信发行身份和签名 Agent 包，复用系统 APT Signed-By/Polkit 进行**固定作用域**安装、升级、卸载和撤销/拒绝/断网负测；不得做通用 root shell 或允许 GUI 自选任意工件、脚本/服务名。
2. **G-05/G-09 实机**：正式获设备所有人许可的 Pop!_OS24 COSMIC L3、独立签名/授权、后台无托盘可发现管理/删除，安装不等于自启/采集授权。
3. **G-01/G-02/G-03/G-04/G-07/G-08** 实际独立受托域、法务和生产 SRE 批准；生产 **G-06** 全链审批只能在前提满足后实施。
4. Gate 状态只能在具名授权主体、确切功能源码 SHA、**真实运行证据**、负例和回滚及责任签收完整时更新，否则维持 OPEN。

**v0.1 NOT RELEASE READY。生产联网、采集、自动恢复、远控、CA 签发 OFF。**
