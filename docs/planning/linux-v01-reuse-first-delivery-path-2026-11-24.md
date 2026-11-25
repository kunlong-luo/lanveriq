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
| **Signed-APT / 冷缓存双回放** | [不同 Git 源 ELF 15/15](https://github.com/kunlong-luo/lanveriq/actions/runs/45563578245) · [双 CI signer 17/17](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126) · [空缓存 RO 锚点 20/20](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575) | 旧 Release/不同旧 Packages/同序列真签名 witness 在空缓存有效，分离只读 CI 批准摘要拒绝双回放，缺锚点拒绝。**不是生产独立高水位**。[报告](../development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) |
| **OS Polkit + D-Bus 真实身份** | [L2B-25 root-side sender 核验 + 拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)、[L2B-26 OS 规则变 YES 仍拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | ReviewLab 只有隔离 OCI 内审查能力、无事务；未来固定操作 broker 需经过安全审核。[报告](../development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md) |
| **Tauri GUI-only/只读预检** | [桌面集成 #45368842676 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) | 保留身份/兼容性/用户解释状态，不以组件按钮或勾选授权 root |
| **Linux→Spring/PG18→Next.js** | [只读 OS/CPU/RAM Linux 真 runner L2 #45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) | 合法设备注册、实际 CA/MFA/网络/隐私授权全部通过前不能切换生产 |

**2026-11-25 合成业务连续性增量：** [真实 Linux runner 的 Caddy 断网 → SQLite 待发 revision 2 → 恢复同源 mTLS 精确 ACK → PG18 current revision 2 → Chromium/Next.js 手动刷新](../development/linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) 已在功能 SHA `db570181` 的 [#45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两个 L2 jobs 成功；revision 3 的过期/撤销拒绝也在同一运行通过。**这是 CI runner 上的显式测试，不是产品后台自动恢复。** 下一项仍需独立发行信任/高水位/受限安装权限和 Pop!_OS 实机 L3。

**源码预览候选（2026-11-25）**：[v0.1.0-dev.1](../operations/source-preview-v0.1.0-dev.1.md) 源码发布前负测 [#45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790)，只验证文档/配置中的源码限定、无二进制附件和 G 门禁不被错误标绿；**没有创建 tag/Release、没有部署或批准任何正式 Agent 安装能力**。它不能替代后续 G-05 高水位/发行签名/Polkit/实机事务测试。

**G-06 隔离 L2 连续性：** [恢复后乱序/冲突](../development/linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md) 功能 9ba85399、[Linux #45571463449 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449)；[ACK 未送达 Rust 而 PG18 已提交后重送](../development/linux-inventory-ack-withheld-replay-l2-2026-11-25.md) 功能 f83aeab6、[Linux #45581861955 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)。**本轮新阶段 L2 PASS：** [PostgreSQL18 SIGKILL/WAL 恢复→原 SQLite 请求重送](../development/linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md) 功能 3e5b7009、[Linux #45583968475 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583968475)。当前仍需 G-05 发行签收/Polkit、G-06 生产身份授权、G-08 灾备与 Pop!_OS 真机 L3。

**G-08：** [PG18 WAL→新库恢复](../development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) `b64a14ad` / [#45586283694 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283694)；[损坏归档拒绝](../development/linux-pg18-corrupt-backup-atomic-reject-l2-2026-11-25.md) `3687edd7` / [#45588502849 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)；[本地证据工具](../operations/backup.md) `06897384` L1 PASS、`94f21974` / [#45589325627 L2 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45589325627)。仍缺独立保管/密钥、RPO/RTO 和 SRE 批准。

## 下一阶段与退出顺序

1. **P0 · G-05 外部发行/见证托管与不可回退高水位（待批准）：** [L2B-33 20/20](../development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) 仅证明单一 CI 管理域中分离 RO 摘要可拒绝同序列真签名旧 witness + 旧 Packages 的联合回放；**同一管理员可重置锚点**。须独立签收真实发行 signer、可信时间、不可回退版本/摘要、撤销及恢复权限，再验证真实断网/重装/升级及 Polkit 可执行 broker 和 COSMIC 实机 L3。
2. **P0 · 可执行 broker（须独立审批）：** 设计 root-side system D-Bus 的实际请求者 OS peercred + 活跃 logind 会话 + Polkit **每操作一次**批准；服务端绑定**固定可信包来源**，防链接/任意路径/脚本/argv/额外 systemd 服务授权、锁、取消、崩溃重试、真正跨版本升级/回滚。GUI 不获得 shell/root，安装与服务启用仍分开；完成 Ubuntu24 隔离 L2 后才可申请实机。
3. **P0 · Pop!_OS/COSMIC L3：** 取得设备所有人及法务书面许可和经批准的发行物，在具名真实 24.04 COSMIC Wayland 测试设备验证 GUI 安装/升级/卸载、键盘/辅助功能/缩放、明确授权、后台无托盘但可发现、配置/state 留存、取消/回滚及性能。**当前 NOT RUN**。
4. **P0 · 生产信任/合规门禁：** G-01 官方 FIDO、G-02 外部 WORM、G-03 真人管理员 WebAuthn MFA、G-04 CA/KMS、G-06 正式合法业务链、G-07 数据/许可证、G-08 SRE 签收全部独立完成，才可考虑生产联网/采集；G-10 RustDesk/Windows 另行裁定。
5. **P1 后续复用：** osquery/Alloy/Fleet/RustDesk 等需另行 PoC、维护负担/许可证/兼容性评估，不能仅因已有开源实现就增加第二套 Agent、数据控制平面或远控开关。

## 开发/文档治理

- 每阶段先核对同一功能 SHA、准确 GitHub Actions job 状态与原生负测，再更新 [专项验收报告](../development/)、[当前状态](../CURRENT_STATUS.md) 和 [Gate 清单](release-gap-register.md)；历史失败不能改写为从未失败。
- 旧 [2026-11-24 路线图长版](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) 的 L2B-1～26 行动细节通过 Git 历史与每项专题继续可追溯；本文删除重复段落不是删除功能或验收证据。
- 真实上线需满足 [L1/L2/L3 验收矩阵](../testing/linux-desktop-installer-acceptance.md)；禁止将 L1 设计、L2 受控运行或自评分替代 L3/法务/独立发行批准。

**v0.1 NOT RELEASE READY。真实联网、采集、远控、自动恢复、CA 签发 OFF。**
