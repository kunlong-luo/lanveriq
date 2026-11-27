# Lanveriq 工程成熟度自评（非发布许可）

**ACTIVE · 2026-11-26 · `main` · 工程 71/100 · 发布 Gate 0/10 已关闭 · v0.1 NOT RELEASE READY。** 维护者自定义刻度，不是行业基准、独立审计、安全认证或上市批准。只由[唯一 G-01～G-10 发布门禁](release-gap-register.md)决定正式发行。

## 当前评分（100 分）

| 维度 | 满分 | 得分 | 已有事实 / 核心未完成项 |
| --- | ---: | ---: | --- |
| 成熟技术栈复用 | 20 | **15** | Java25/Boot4.2/JPA/PG18.7、Rust/SQLx、Next16、Tauri2；[技术文档](../development/technology.md) |
| 维护架构与单一入口 | 15 | **12** | 单 Next.js Web + Agent/Server/Desktop 分工；生产能力仍锁定 |
| 实际 L1/L2 自动验收 | 20 | **19** | [Rust/mTLS/PG18/Next.js + 跨 GitHub runner PG18 原生恢复，三个 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955)；非 L3 |
| 安全默认拒绝 | 15 | **13** | mTLS、ACK、Polkit 拒绝；正式 MFA/CA、发行根、外部 WORM 缺失 |
| 文档与目录治理 | 10 | **9** | [CURRENT_STATUS](../CURRENT_STATUS.md)、[发布门禁](release-gap-register.md)、目录清单、文档 CI |
| 可实际安装与正式发行 | 20 | **3** | 真 Agent 仅在隔离测试容器临时安装；**无**获批签名安装包、可用 GUI 安装、受审最小权限 broker、COSMIC L3 |
| **合计** | **100** | **71** | 15+12+19+13+9+3；**分数绝不是发布阈值** |

## 本轮为什么只增加 1 分

[旧版 70/100 及全部阶段历史](https://github.com/kunlong-luo/lanveriq/blob/55d945a30ba8029fe32a1d17302f42526b7d53de/docs/planning/project-scorecard.md) 的自动验收为 18/20。2026-11-26 功能 [`b31f45ef`](https://github.com/kunlong-luo/lanveriq/commit/b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f) 的[跨 runner PG18 恢复 L2 #45742025955 三 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955) 将自动验收升为 **19/20**，总分 70→71。只是同一 GitHub 管理域。

新增[物理 DR 授权后手动操作脚本](../../apps/server/scripts/pg18-physical-dr-lab.sh)、[独立实体机 SRE 执行卡](../operations/g08-physical-dr-operator-card-2026-11-26.md)和[L1 拒绝 CI #45743526852](https://github.com/kunlong-luo/lanveriq/actions/runs/45743526852)，**仅准备与默认拒绝，不增加评分、不视为真实 L3**；正式实机尚 NOT RUN。完整测试结果只按该项原始 CI 结论记录。

**2026-11-26 G-05/G-09（不加分）：** [APT 双 runner #45746180961 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180961)、[Tauri #45746181071 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746181071)、[GUI-only `.deb` #45746180976 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180976) 修复了旧构建闭包漂移；新增[基于真实 Rust AgentConfig v1 的离线 GUI 配置草稿](../development/desktop-tauri-agent-idle-config-ui-2026-11-26.md)（`a70c4243`，[Tauri #45748162585 两 job PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45748162585)）。尚无可供正式用户使用的安装授权，**评分维持 71/100、Gate 0/10**。

**2026-11-27 本机 MVP（不加分）：** 维护者真实测试设备注册、双跳 mTLS 激活、credential epoch、错设备及无证书拒绝 PASS；非独立 L3、未进行库存 send 或正式安装。功能 `f6e83c89` Server CI SUCCESS，隔离 L2 [#45952013366](https://github.com/kunlong-luo/lanveriq/actions/runs/45952013366) 三 jobs SUCCESS；MVP-03 DONE。评分仍 71/100，Gate 0/10。

**2026-11-27 MVP-04（不加分）：** 既有 Agent [L1 #45804471428](https://github.com/kunlong-luo/lanveriq/actions/runs/45804471428) 与隔离 [L2 #45952013366](https://github.com/kunlong-luo/lanveriq/actions/runs/45952013366) 原日志复核 PASS；维护者本机双跳 mTLS/epoch、HTTP200/精确 ACK、新 pending 0、旧 pending 1、revision 1 PASS；维护者确认临时开关/诊断恢复与重启重载成功，MVP-04 DONE；MVP-05 TODO / NOT RUN。新增本机证据非独立 L3 或新增自动验收，无功能代码变更，工程评分仍 **71/100**，Gate 0/10。见[本机任务账本](local-mvp-tasks.md)。

## 生产发布能力：NO-GO（0/10 已关闭）

| Gate 集合 | 阻断条件 |
| --- | --- |
| **G-01/G-03/G-04** | 官方 FIDO 根及可信时间、真人 WebAuthn MFA、正式设备 CA/HSM/撤销均缺 |
| **G-02/G-08** | 独立 WORM/KMS/高水位、实际不同物理机器恢复、真实 RPO/RTO、SRE/安全/法务签收未验 |
| **G-05/G-09** | 签名发行件、获批逐次 Polkit broker、Pop!_OS 24.04 COSMIC 实机 L3 未完成 |
| **G-06/G-07/G-10** | 合法真实设备业务链、隐私/许可审批及 Windows/RustDesk 首发范围决议未完成 |

目前 [GitHub Releases](https://github.com/kunlong-luo/lanveriq/releases) 无正式发行件；`v0.1.0-dev.1` 只有**未发布源码预览候选**，不能提供给普通终端用户作为产品安装器。仅可在允许的隔离开发环境运行合成 CI 或断网界面预览。

**下一次实质加分需要**经授权的真实 Pop!_OS 用户安装/升级/卸载 L3、受审签名与 Polkit 授权、外部 WORM/物理恢复与独立签收、正式 MFA/CA 和法务许可。未取得前，**G-01–G-10 全部 OPEN；生产联网、采集、自动恢复、远控、CA 签发和未经授权安装 OFF；v0.1 NOT RELEASE READY。**
