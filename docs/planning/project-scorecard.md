# Lanveriq v0.1 工程评分与上线成熟度（2026-11-24）

**ACTIVE · 自评，不是外部认证 · `main` only · v0.1 NOT RELEASE READY**

## 评分结论

<details><summary>方法说明</summary>

此评分属于维护者辅助优先级工具，并非经过安全审计的量化行业标准；“修改文档”的分数改善仅评价仓库可理解性，不代表真实设备接入、法律许可或发布批准。真实交付门禁由 [G-01–G-10](release-gap-register.md) 的逐条签收决定，不能按加权总分替代。

</details>

| 维度 | 权重 | 清理前 | 本轮实际整理后（同口径） | 可核对依据 |
| --- | ---: | ---: | ---: | --- |
| 现代技术栈与成熟组件复用 | 20 | 15 | **15** | Java25/Boot4.2.0/JPA/PG18.7、Rust1.100/SQLx、Next16.4.5、Tauri2；[实际源](../development/technology.md) |
| 可维护架构与边界 | 15 | 12 | **12** | 单一 Web、四应用分层、Agent 默认 idle；仍有许多默认关闭/合成代码 |
| L1/L2 自动化与可复验性 | 20 | 17 | **17** | [CI Linux 实际库存 L2 #45365942781](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)、[Tauri 双 job #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676)；但缺实机 L3 |
| 默认拒绝与安全工程 | 15 | 11 | **11** | mTLS/ACK/CI 负测、RD-2 OFF；官方 FIDO/真实 WORM/CA/MFA/可信签名仍无授权 |
| **文档与仓库可发现性** | 10 | **4** | **9**（已通过结构/文档 CI） | 根中英 README/四个 app README、文档入口、CURRENT_STATUS/Gate 压缩；保留历史证据与明确目录地图；[清理清单](../governance/repository-layout-audit-2026-11-24.md) |
| 真实产品安装与正式发布 | 20 | 2 | **2** | GUI 安装键仍锁定，Polkit/signing/Pop!_OS24 L3/法务和运维门禁 OPEN |
| **综合工程可维护成熟度** | **100** | **61 / 100** | **66 / 100**（文档及布局 CI 已验证） | 权重和上述加总：15+12+17+11+9+2 = 66 |

**本轮工程维护成熟度自评从 61/100 → 66/100，+5 分只来自实际入口简化、旧参考删除和自动化结构守卫。** [文档/链接 #45373334900 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45373334900)、[Next.js 源码/测试 #45373334701 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45373334701) 在布局守卫功能 SHA 0db21627 已核实；后续文档提交仍需单独检查 docs-governance。**不把文档提升冒充已完成任何新业务功能或生产授权。**保留修改前的 61/100 作为同一自评标尺下的比较，不引用任何未经证实的外部 benchmark。

## 独立发布结论：不因为 66 分放行

| 必过事项 | 真实状态 |
| --- | --- |
| G-01 官方 FIDO 来源/撤销/可信时间 | **OPEN** |
| G-02 独立管理域 WORM/KMS/高水位 | **OPEN** |
| G-03 正式管理员 WebAuthn MFA/双审 | **OPEN** |
| G-04 生产 CA/密钥/吊销 | **OPEN** |
| G-05 Agent 信任签名发行/受限 Polkit/实机安装 | **OPEN** |
| G-06 实际获授权 Agent→Web 业务 | **OPEN**（仅隔离 Linux CI L2 PASS） |
| G-07 法律、数据隐私与第三方许可 | **OPEN** |
| G-08 生产 SRE/备份恢复/应急回滚 | **OPEN** |
| G-09 用户 UI/Pop!_OS COSMIC 实机 | **OPEN** |
| G-10 Windows/RustDesk 正式范围 | **OPEN** 或需书面批准 OUT OF SCOPE |

**可正式发布：否。** 当前不存在可发放普通用户的一键 Agent 安装包、正常生产登录入口或获批准真实采集。评分既不能减免任何一项 Gate，也不能触发运行时开关。

## 下一轮最能提高实质性分数的工作

1. 先复用原生 APT `Signed-By` 和 system Polkit，为**经过授权且可核实**的固定 Agent 工件实现受限安装/升级/卸载事务；测试拒绝/取消/回滚，不允许 GUI 任意执行 root shell，也不默认启动服务。
2. 获取独立批准的发行者身份/签名根、撤销/可信时间及正式 Agent 包来源，并开展带具名授权的 **Pop!_OS24 COSMIC L3**；不能用 Ubuntu CI 代替。
3. 将现有 Rust→PG18→Spring→Next.js CI L2 的正式授权路径补齐，遵守设备/员工数据合法告知和生产网络分离。
4. 独立管理域 WORM、正式 FIDO/WebAuthn、CA、法律许可及 SRE 签收，验证真实缺失项；在此之前不做“上线倒计时”。

参见 [当前实测状态](../CURRENT_STATUS.md)、[统一文档导航](../README.md)、[源码结构审计](../governance/repository-layout-audit-2026-11-24.md)、[完整发布缺口](release-gap-register.md)。

**生产联网、采集、远控、自动恢复、CA 签发默认 OFF；v0.1 NOT RELEASE READY。**
