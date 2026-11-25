# Lanveriq Linux v0.1 — 复用优先的实施路线

**ACTIVE PLAN · 2026-11-25 · 只在 `main` 开发 · v0.1 NOT RELEASE READY。** 唯一事实与 Gate 分别以 [CURRENT_STATUS](../CURRENT_STATUS.md)、[release-gap register](release-gap-register.md) 为准。本文只描述**下一步如何实施、如何退出验收**；各阶段原始记录保留在[带日期的技术报告](../README.md)、[历史 CI 账本](release-readiness-2026-11-13.md) 和 Git 提交，不再把每次测试日志复制到路线图。

## 技术与产品决策保持稳定

- **终端首发：** Pop!_OS 24.04 COSMIC Linux-only；Windows/RustDesk 候选必须另取书面范围/许可证决定。合法告知/授权优先，不能做隐蔽采集或静默持久化。
- **唯一的控制端：** `apps/web` Next.js 16.4.5 + React19 + Tailwind4 + Base UI；`apps/server` Java25 + Spring Boot4.2 + Gradle + JPA/Flyway/PG18；`apps/agent` Rust1.100/Tokio/SQLx SQLite/reqwest-rustls；`apps/desktop` Tauri2/React/Vite 仅桌面窗体。四个应用不重造软件包管理/身份/协议基础设施。
- **现有成熟栈优先：** 原生 APT Signed-By、dpkg、Polkit/system D-Bus/PackageKit（仅作为经审批准入候选）；不写通用 root shell、不自制加密/验签框架、不再增加第二 Web 控制台或冗余终端进程。
- **独立 Agent：** 将来获审批后可作为可发现/可管理的独立 systemd 服务在后台运行，不出现常驻托盘，但**安装不是启动/boot enable/联网/采集授权**。当前 GUI 仅只读，所有这类权限 OFF。

## 已有可复用的真实 L2 积累（不等于生产 L3）

| 现成组件 | 实际证据 | 下一步怎么复用 |
| --- | --- | --- |
| **Rust Agent .deb + 系统 APT** | [L2B-23 真实 Signed-By install/reinstall/remove 9/9](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) | 正式 signer/source 独立审批后复用 native APT，禁止直接信任临时 CI 密钥。[报告](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) |
| **APT 有效签名旧 Release Date 回放** | [L2B-27 真实 native OCI 10/10 #45395513243](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243) | 同临时 signer/同 Packages 的旧签名 Release 被真实系统 APT 接受，**不证明安全回退保护**；独立可信外部高水位/旧索引/旧包降级测试仍 OPEN。[报告](../development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) |
| **OS Polkit + D-Bus 真实身份** | [L2B-25 root-side sender 核验 + 拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)、[L2B-26 OS 规则变 YES 仍拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | ReviewLab 只有隔离 OCI 内审查能力、无事务；未来固定操作 broker 需经过安全审核。[报告](../development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |
| **Tauri GUI-only/只读预检** | [桌面集成 #45368842676 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) | 保留身份/兼容性/用户解释状态，不以组件按钮或勾选授权 root |
| **Linux→Spring/PG18→Next.js** | [只读 OS/CPU/RAM Linux 真 runner L2 #45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 合法设备注册、实际 CA/MFA/网络/隐私授权全部通过前不能切换生产 |

**2026-11-25 合成业务连续性增量：** [真实 Linux runner 的 Caddy 断网 → SQLite 待发 revision 2 → 恢复同源 mTLS 精确 ACK → PG18 current revision 2 → Chromium/Next.js 手动刷新](../development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) 已在功能 SHA `db570181` 的 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两个 L2 jobs 成功；revision 3 的过期/撤销拒绝也在同一运行通过。**这是 CI runner 上的显式测试，不是产品后台自动恢复。** 下一项仍需独立发行信任/高水位/受限安装权限和 Pop!_OS 实机 L3。

## 下一阶段与退出顺序

1. **P0 · L2B-28/G-05 可信发行与独立高水位：** L2B-27 已实际发现旧签名 Release Date 会被 APT 接受；由**独立发行/安全责任主体**审核 signer、公钥预置/撤销与发布根托管、固定 Agent 包名/版本/来源、可信时间、独立单调高水位以及真实旧包/旧索引回退拒绝。未获批准前只做隔离 OCI 的 APT 有效签名旧索引/源回退、事务安全取消/锁和拒绝验证，不能打正式用户包。
2. **P0 · 可执行 broker（须独立审批）：** 设计 root-side system D-Bus 的实际请求者 OS peercred + 活跃 logind 会话 + Polkit **每操作一次**批准；服务端绑定**固定可信包来源**，防链接/任意路径/脚本/argv/额外 systemd 服务授权、锁、取消、崩溃重试、真正跨版本升级/回滚。GUI 不获得 shell/root，安装与服务启用仍分开；完成 Ubuntu24 隔离 L2 后才可申请实机。
3. **P0 · Pop!_OS/COSMIC L3：** 取得设备所有人及法务书面许可和经批准的发行物，在具名真实 24.04 COSMIC Wayland 测试设备验证 GUI 安装/升级/卸载、键盘/辅助功能/缩放、明确授权、后台无托盘但可发现、配置/state 留存、取消/回滚及性能。**当前 NOT RUN**。
4. **P0 · 生产信任/合规门禁：** G-01 官方 FIDO、G-02 外部 WORM、G-03 真人管理员 WebAuthn MFA、G-04 CA/KMS、G-06 正式合法业务链、G-07 数据/许可证、G-08 SRE 签收全部独立完成，才可考虑生产联网/采集；G-10 RustDesk/Windows 另行裁定。
5. **P1 后续复用：** osquery/Alloy/Fleet/RustDesk 等需另行 PoC、维护负担/许可证/兼容性评估，不能仅因已有开源实现就增加第二套 Agent、数据控制平面或远控开关。

## 开发/文档治理

- 每阶段先核对同一功能 SHA、准确 GitHub Actions job 状态与原生负测，再更新 [专项验收报告](../development/)、[当前状态](../CURRENT_STATUS.md) 和 [Gate 清单](release-gap-register.md)；历史失败不能改写为从未失败。
- 旧 [2026-11-24 路线图长版](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) 的 L2B-1～26 行动细节通过 Git 历史与每项专题继续可追溯；本文删除重复段落不是删除功能或验收证据。
- 真实上线需满足 [L1/L2/L3 验收矩阵](../testing/linux-desktop-installer-acceptance.md)；禁止将 L1 设计、L2 受控运行或自评分替代 L3/法务/独立发行批准。

**v0.1 NOT RELEASE READY。真实联网、采集、远控、自动恢复、CA 签发 OFF。**
