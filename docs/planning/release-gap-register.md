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
| **G-05 P0 Agent 签名图形安装器** | Tauri 只读审核 UI + CI；Agent idle-only Debian 候选在 networkless Ubuntu OCI 中安装/重装/删除过；官方 APT Signed-By 临时 LAB | 经批准产品签名根和固定 APT 仓库、受审最小权限 Polkit broker、管理员每次明确授权、实际签名 Agent 工件与安装/升级/卸载/取消/恢复；默认不可联网或自动启动 | 发行/安全/产品 | **OPEN** |
| **G-05 P0 Pop!_OS24 COSMIC 实机 L3** | Ubuntu24/COSMIC **元数据**与真实原生 Tauri 编译 CI，通过并非实机 | 具名合法设备，真实 Wayland/COSMIC、图形授权/键盘/缩放、签名安装/取消/卸载/后台无托盘且可发现状态、性能和失败回滚 | 用户/UX/安全 | **OPEN** |
| **G-06 P0 生产业务端到端** | 实际一次性 Linux runner 只读 OS/CPU/RAM→Rust SQLite→双 mTLS→Spring JPA/PG18→受限 Next.js/Chromium **L2 PASS** | 正式获授权的真实 Agent、CA、管理员、Next.js 生产登录/设备/库存/恢复链和安全配置、生产数据合法告知 | 全栈/安全 | **OPEN** |
| **G-07 P0 法律/隐私/三方许可证** | 隐私技术原则，部分第三方 ABI/copyright 哈希仅作审查输入 | 员工/设备合法用途与告知、数据保留/删除、RustDesk AGPL/Pro 与全部软件分发许可证的书面批准 | 法务/隐私 | **OPEN** |
| **G-08 P0 生产 SRE/回滚/恢复** | PG18/Caddy 合成验证及隔离备份负测 | 最小权限环境、密钥托管/日志监控、备份/灾备/升级/回滚/应急与发布签收 | SRE/安全 | **OPEN** |
| **G-09 P1 Web/GUI 真实 UX L3** | Tauri L1 安装资格界面、真实 Chromium 仅 CI 数据闭环 | 真实 Pop!_OS COSMIC/Wayland 高 DPI/键盘无障碍和错误/取消/卸载；正式 Web 登录/窄屏/滚动/权限拒绝态 | UI/产品 | **OPEN** |
| **G-10 P1 Windows/RustDesk 范围** | Windows CI/远控隔离协议实验；RD-2 默认拒绝 | 独立 Windows L3 与 RustDesk 许可证/用户授权；或书面批准本版 OUT OF SCOPE | 产品/法务 | **OPEN / OUT OF SCOPE 待决定** |

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
