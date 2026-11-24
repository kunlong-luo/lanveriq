# Lanveriq v0.1 工程评分与上线成熟度（2026-11-24）

**ACTIVE · 自评，不是外部认证 · `main` only · v0.1 NOT RELEASE READY**

## 评分结论

<details><summary>方法说明</summary>

此评分属于维护者辅助优先级工具，并非经过安全审计的量化行业标准；“修改文档”的分数改善仅评价仓库可理解性，不代表真实设备接入、法律许可或发布批准。真实交付门禁由 [G-01–G-10](release-gap-register.md) 的逐条签收决定，不能按加权总分替代。

</details>

| 维度 | 权重 | 清理前 | L2B-24 后（同口径） | 可核对依据 |
| --- | ---: | ---: | ---: | --- |
| 现代技术栈与成熟组件复用 | 20 | 15 | **15** | Java25/Boot4.2.0/JPA/PG18.7、Rust1.100/SQLx、Next16.4.5、Tauri2；[实际源](../development/technology.md) |
| 可维护架构与边界 | 15 | 12 | **12** | 单一 Web、四应用分层、Agent 默认 idle；仍有许多默认关闭/合成代码 |
| L1/L2 自动化与可复验性 | 20 | 17 | **18** | [CI Linux 实际库存 L2 #45365942781](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)、[Tauri 双 job #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676)、[真 Agent signed-APT OCI 9/9 L2 #45376989866](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)；实机 L3 仍缺 |
| 默认拒绝与安全工程 | 15 | 11 | **12** | mTLS/ACK/RD-2 OFF，新增[实际原生 Polkit/DBus + Rust 主体 6/6 拒绝 #45379398181](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)；官方 FIDO/真实 WORM/CA/MFA/生产签名仍无授权 |
| **文档与仓库可发现性** | 10 | **4** | **9**（已通过结构/文档 CI） | 根中英 README/四个 app README、文档入口、CURRENT_STATUS/Gate 压缩；保留历史证据与明确目录地图；[清理清单](../governance/repository-layout-audit-2026-11-24.md) |
| 真实产品安装与正式发布 | 20 | 2 | **3** | **真实 Agent .deb 经一次性签名 APT 8 项拒绝+install/reinstall/remove L2 PASS**；但 GUI 安装键仍锁定，正式 Polkit/发行根/Pop!_OS24 L3/法务和运维门禁 OPEN |
| **综合工程可维护成熟度** | **100** | **61 / 100** | **69 / 100**（有真实 OS 身份拒绝 L2） | 15+12+18+12+9+3 = 69；此前文档重整 66、实际 APT L2B-23 68 |

**本轮工程维护成熟度自评从 61/100 → 66/100，+5 分只来自实际入口简化、旧参考删除和自动化结构守卫。** [文档/链接 #45373334900 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45373334900)、[Next.js 源码/测试 #45373334701 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45373334701) 在布局守卫功能 SHA 0db21627 已核实；后续文档提交仍需单独检查 docs-governance。**不把文档提升冒充已完成任何新业务功能或生产授权。**保留修改前的 61/100 作为同一自评标尺下的比较，不引用任何未经证实的外部 benchmark。

**2026-11-24 L2B-22 补充说明：** 代码仓库新增未安装的 Polkit 全拒绝动作 XML，并通过 [功能 SHA efb7a558 / 安装意图 39 + Polkit 30 项离线负测 #45375272477 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)。它提高了未来受限 broker 的**可审基线**，但没有真正 OS 授权、已签名发行包或用户实机验收，故**本次工程成熟度自评分保持 66/100，发行能力维度仍 2/20**，不凭安全模拟测试虚增分数。[L2B-22 证据](../development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。

**2026-11-24 L2B-23 实际集成后的第二次评分：66 → 68 / 100（+2）。** 这一次分值提升**来自真实功能验收**而不是增加文档：自动化可复验性 **17→18/20**（真实当期 Agent 包与官方 APT 签名索引、原生安装/重装/卸载及八类篡改负测 [#45376989866 9/9 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)）；发布工程能力 **2→3/20**（真实 OCI 中已能通过签名源完成 Agent 包生命周期，不再只是独立 dpkg 和虚构包的 apt-get update）。但**这仍不包含获批准发行身份、实际 Polkit broker、用户 GUI 安装、真实 Pop!_OS COSMIC L3、跨版本升级**，所以发行能力仅 3/20，**G-05/G-09/G-06 全 OPEN，v0.1 NOT RELEASE READY**。[L2B-23 精确证据](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

**2026-11-24 L2B-24 第三次评分：68 → 69/100（+1）。** 唯一提高项是**默认拒绝与安全工程 11→12/15**：原来 Polkit 只有 XML 解析与假签名实验；本轮在隔离 Ubuntu24 真正启动 `polkitd`+D-Bus，运行独立 Rust1.100 固定动作进程读取自身真实 PID/start-time/UID，三类动作 × 两非 root Unix 身份共 **6/6 由 OS 原生 `pkcheck` 明确拒绝**，且伪造环境、动态动作均拒绝。[同源码 SHA ba04f55c / CI #45379398181 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)。**这一分不是正式管理员认证、可用的 root-side broker、可分发签名包、成功 GUI 安装或 Pop!_OS24 实机；发行安装能力仍 3/20、所有正式发布 Gate 保持 OPEN。** [L2B-24 的局限与真实失败历史](../development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)。

## 独立发布结论：不因为 69 分放行

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
