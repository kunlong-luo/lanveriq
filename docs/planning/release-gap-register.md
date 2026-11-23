# Lanveriq v0.1 发布阻塞清单

**LIVE-GAP · 2026-11-22 · Linux-first / Pop!_OS 24.04 · v0.1 NOT RELEASE READY。** 当前已完成事实和功能 SHA 以 [CURRENT_STATUS](../CURRENT_STATUS.md) 为准。下面角色为**所需授权角色**，并非已签字人。

| Gate | 当前缺口 | 允许关闭的实际证据 | 状态 |
| --- | --- | --- | --- |
| **P0 G-01 官方 FIDO 根与 CRL** | D4-C9–C16 仅未可信离线合成材料 | 真实官方 BLOB 受控获取、证书链/R46 独立根批准、CRL CDP/签名/撤销/版本/时间防回退，来源独立审计 | **OPEN** 安全/PKI |
| **P0 G-02 外部 WORM/KMS 见证** | D4-C18e 云中立源代码 6/6 同 SHA 隔离 CI PASS，AWS SDK 已退出默认主构建；C18f 对独立 WORM 管理域/候选介质制定可证伪验收矩阵，并在功能 SHA 118939b4 上完成 PG18 真数据库尾与离线版本声称联合回归（**7/7 同 SHA 合成 CI PASS，PG18 11 类≥151**）。仍无真实独立 WORM、独立密钥/时间、高水位托管及组织批准 | 真正数据库外 WORM、独立高水位、密钥托管/轮换、可信时间、PG 备份恢复回退拒绝/离线保护实测 | **OPEN** 安全/SRE；[C18j 真实 WORM 选型、独立高水位与受控实验交接](../security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)仍待外部主体批准；[C18h 离线元数据检查](../security/independent-worm-offline-evidence-intake-d4-c18h-2026-11-22.md)不构成独立证据或 Gate PASS；[C18i 跨管理员/底层 root 权限图预检](../security/independent-worm-admin-control-graph-d4-c18i-2026-11-22.md)只验证自述拓扑格式，仍缺真权限审计和介质；[C18g 实物验收协议](../security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md)：WORM-01–11 NOT RUN，供应商/介质/受托方未选 |
| **P0 G-03 管理员双审与 WebAuthn** | 暂存/合成凭证；无真人 MFA | 不同真人硬件 MFA、RP/Origin/会话/挑战/计数器、管理员不可自审/吊销真实回归 | **OPEN** 身份安全 |
| **P0 G-04 生产 CA 与密钥** | 合成 CSR/证书；签发默认 OFF | 正式 CA/HSM/KMS 权限、签发/吊销/轮换/恢复审批与实测 | **OPEN** PKI |
| **P0 G-05 Desktop source/L1** | Tauri/React 首批离线预览源码（只读 OS/dpkg/systemctl、全部禁用安装/运行权限）已开始，真实可签名一键安装包、polkit broker、完整生命周期及 Pop!_OS L3 尚不存在 | [desktop L1 专用 CI](../development/desktop-tauri-first-source-2026-11-23.md) 真实通过后仅能记源码验收；所有签名软件包与实体操作仍需 G-05/07/08/09 L3 | **OPEN** 开发/安全 |
| **P0 G-05 Pop!_OS 实机 L3** | Linux 隔离 PG18/TLS/Rust PASS，真实主机未验；**v0.1 Tauri 图形安装/配置/无常驻图标的独立 systemd Agent 方案已定，但客户端/安装桥接/签名软件包仍未开发** | 实测图形授权安装、签名 DEB/受限 polkit、systemd 非 root/独立于 COSMIC、关闭 GUI/注销后后台状态、无常驻托盘但可查看/卸载、真实升级重启回滚、密钥与隐私负向用例（详见 [桌面 L3 测试](../testing/linux-desktop-installer-acceptance.md)） | **OPEN** 实机负责人；[LNX1 Ubuntu24 合成安全加固 5/5 CI PASS](../testing/linux-systemd-policy-hardening-2026-11-23.md)；[LNX2 原生 .deb 候选、32 项合成负测、release 构建及临时移除 5/5 同 SHA CI PASS](../testing/linux-native-package-candidate-2026-11-23.md)；[LNX3 安装后只读属性检查 14 项合成负测及完整 Linux CI 5/5 同 SHA PASS，但真实 Pop!_OS 主机未运行](../testing/linux-installed-idle-inspector-g05-lnx3-2026-11-23.md)，真实 dpkg install/remove 与 Pop!_OS 主机 L3 NOT RUN，实际 Pop!_OS24 安装/drop-in/COSMIC NOT RUN，由 [Issue #7](https://github.com/kunlong-luo/lanveriq/issues/7) 追踪 |
| **P0 G-06 正式业务闭环** | Agent 真调度/采集和 Next.js 正式登录/数据链路仍未放行 | 授权管理员/RBAC、设备心跳/库存/恢复、真实 API/Next.js/WSS、失败/重试/边界一体测试 | **OPEN** 全栈 |
| **P0 G-07 法律与软件许可** | D0 只许可研发；员工数据/保留/RustDesk 许可未决 | 合法目的、告知、保留/删除和许可证审查的组织批准记录 | **OPEN** 法务/安全 |
| **P0 G-08 生产运维安全** | 无正式环境与恢复放行 | 最小权限、备份恢复/故障切换、密钥审计、部署回滚/监控和正式发布审批 | **OPEN** SRE/安全 |
| **P1 G-09 UI 实际验收** | Pages/Chromium 仅断开式预览；Tauri 安装向导/授权/状态/卸载 GUI 尚未实现 | Web 真实授权数据状态、窄屏、键盘、无障碍、滚动和拒绝态 L3；另需 Pop!_OS24 真实图形安装器 COSMIC/缩放/键盘/权限失败与可发现卸载入口验收 | **OPEN** UI/产品 |
| **P1 G-10 Windows/RustDesk** | Linux-first、RD-2 OFF | Windows L3、RustDesk AGPL/Pro 法律安全许可，或明确本版 OUT OF SCOPE | **OPEN** 产品/法务 |

**建议先后：** C18f 云中立/PG18 合成基线已通过 → [C18g 独立保管和实物破坏验收计划](../security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md)（目前仅文档，未执行）→ 安全/法务/SRE 选真实介质并书面授权专用非生产环境 → G-02 经批准的独立 WORM 实际部署/密钥托管、保留及不可回退高水位、跨环境恢复演练，同时 G-01 官方 MDS/CRL 根审批；随后 G-03/G-04 管理员/CA，再 G-05/G-06 Linux 实机业务闭环；最终 G-07/G-08/G-09 的签字。无凭空时间预测。

> 2026-11-23 技术版本维护：PostgreSQL 18.6→18.7 的隔离 CI 候选由 [STACK-PG18.7 升级与回退](../development/stack-postgres18-7-upgrade-2026-11-23.md) 追踪；功能 SHA 通过前只算 PENDING，不算真实生产数据库迁移或任何 Gate 通过。

**Gate 关闭需要**组织批准主体、独立来源凭证、功能 SHA/实际 CI、物理/外部证据、负向案例及签署记录。源码实现/合成通过不能改变生产 feature flags，也不会自动关闭任何 Gate。
