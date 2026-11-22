# Lanveriq v0.1 发布阻塞清单

**LIVE-GAP · 2026-11-22 · Linux-first / Pop!_OS 24.04 · v0.1 NOT RELEASE READY。** 当前已完成事实和功能 SHA 以 [CURRENT_STATUS](../CURRENT_STATUS.md) 为准。下面角色为**所需授权角色**，并非已签字人。

| Gate | 当前缺口 | 允许关闭的实际证据 | 状态 |
| --- | --- | --- | --- |
| **P0 G-01 官方 FIDO 根与 CRL** | D4-C9–C16 仅未可信离线合成材料 | 真实官方 BLOB 受控获取、证书链/R46 独立根批准、CRL CDP/签名/撤销/版本/时间防回退，来源独立审计 | **OPEN** 安全/PKI |
| **P0 G-02 外部 WORM/KMS 见证** | D4-C17 离线高水位与 D4-C18a PostgreSQL18 owner 级回退模拟同 SHA 合成测试 PASS（C18a 7/7），但**非真正 pg_dump/restore**；仍无独立真实 WORM/KMS 存储、托管与可信时间 | 真正数据库外 WORM、独立高水位、密钥托管/轮换、可信时间、PG 备份恢复回退拒绝/离线保护实测 | **OPEN** 安全/SRE |
| **P0 G-03 管理员双审与 WebAuthn** | 暂存/合成凭证；无真人 MFA | 不同真人硬件 MFA、RP/Origin/会话/挑战/计数器、管理员不可自审/吊销真实回归 | **OPEN** 身份安全 |
| **P0 G-04 生产 CA 与密钥** | 合成 CSR/证书；签发默认 OFF | 正式 CA/HSM/KMS 权限、签发/吊销/轮换/恢复审批与实测 | **OPEN** PKI |
| **P0 G-05 Pop!_OS 实机 L3** | Linux 隔离 PG18/TLS/Rust PASS，真实主机未验 | 实测桌面会话、systemd 非 root、密钥、设备启停、断网 SQLite、升级卸载、隐私与负向用例 | **OPEN** 实机负责人 |
| **P0 G-06 正式业务闭环** | Agent 真调度/采集和 Next.js 正式登录/数据链路仍未放行 | 授权管理员/RBAC、设备心跳/库存/恢复、真实 API/Next.js/WSS、失败/重试/边界一体测试 | **OPEN** 全栈 |
| **P0 G-07 法律与软件许可** | D0 只许可研发；员工数据/保留/RustDesk 许可未决 | 合法目的、告知、保留/删除和许可证审查的组织批准记录 | **OPEN** 法务/安全 |
| **P0 G-08 生产运维安全** | 无正式环境与恢复放行 | 最小权限、备份恢复/故障切换、密钥审计、部署回滚/监控和正式发布审批 | **OPEN** SRE/安全 |
| **P1 G-09 UI 实际验收** | Pages/Chromium 仅断开式预览 | 真实授权数据状态、窄屏、键盘、无障碍、滚动和拒绝态 L3 | **OPEN** UI/产品 |
| **P1 G-10 Windows/RustDesk** | Linux-first、RD-2 OFF | Windows L3、RustDesk AGPL/Pro 法律安全许可，或明确本版 OUT OF SCOPE | **OPEN** 产品/法务 |

**建议先后：** D4-C18b/G-02 真实外部 WORM/KMS 托管、正式备份恢复反回退演练，同时 G-01 官方 MDS/CRL 根审批；随后 G-03/G-04 管理员/CA，再 G-05/G-06 Linux 实机业务闭环；最终 G-07/G-08/G-09 的签字。无凭空时间预测。

**Gate 关闭需要**组织批准主体、独立来源凭证、功能 SHA/实际 CI、物理/外部证据、负向案例及签署记录。源码实现/合成通过不能改变生产 feature flags，也不会自动关闭任何 Gate。
