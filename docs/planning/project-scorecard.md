# Lanveriq 工程成熟度自评（非发布审批）

**ACTIVE · 2026-11-24 · `main` · v0.1 NOT RELEASE READY · 70 / 100。** 这是维护者自定义的研发优先级刻度，不是行业基准、独立安全认证、合规评分或发布许可。**唯一发布退出依据：** [G-01–G-10 LIVE-GAP](release-gap-register.md)。

## 当前分数和依据

| 维度 | 满分 | 当前 | 依据 / 最重要的缺口 |
| --- | ---: | ---: | --- |
| 技术栈与成熟库复用 | 20 | **15** | Java25/Boot4.2/JPA/PG18.7、Rust1.100/SQLx、Next16、Tauri2；[版本事实](../development/technology.md) |
| 维护架构和应用边界 | 15 | **12** | 单一 Web、四应用分工清晰；仍多项默认禁用的功能 |
| 可执行 L1/L2 自动验收 | 20 | **18** | [真实 Linux→Spring/PG18→Next.js L2](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)、[实际 Agent APT Signed-By L2](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)；实机 L3 仍缺 |
| 安全默认拒绝 | 15 | **13** | [原生 root-side D-Bus 同行核验](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)、[真正恶意 Polkit YES 仍拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766)；FIDO/CA/WORM/可信发行仍缺 |
| 文档和仓库可发现性 | 10 | **9** | 根中英 README、唯一 [CURRENT_STATUS](../CURRENT_STATUS.md) / [LIVE-GAP](release-gap-register.md)、[目录清单](../governance/repository-layout-audit-2026-11-24.md) 和 CI |
| **真实产品安装与发行** | **20** | **3** | 真 Agent 已在**隔离 OCI** 经临时签名 APT 安装；**无**正式签名信任根、受审可执行 broker、可用 GUI、Pop!_OS24 实机 L3 |
| **合计** | **100** | **70** | 15 + 12 + 18 + 13 + 9 + 3；**不能按总分放行任何 Gate** |

## 历史评分解释（只留可复核摘要）

| 阶段 | 分数 | 变化依据 |
| --- | ---: | --- |
| 旧版文档重整前 → 文档治理 | **61 → 66** | 入口/重复 UI 清理及真实 [文档与 Web CI](https://github.com/kunlong-luo/lanveriq/actions/runs/45373334900)；不是功能上线 |
| L2B-23 | **66 → 68** | 真 Agent 经 [APT install/reinstall/remove + 8 拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) |
| L2B-24 | **68 → 69** | 上游 [Polkit 真实 UID 和 Rust PID/start-time 拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181) |
| L2B-25 | **69 → 70** | [root-side D-Bus 身份核验、Polkit 和 nonce](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) |
| L2B-26 | **70 → 70** | [恶意本地 Polkit 规则真 YES 仍拒绝](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) 是纵深防御回归，不是可用安装能力 |

完整**当时的评分解释/失败**保存在 [Git 旧版本](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/docs/planning/project-scorecard.md)、[dated evidence](../README.md) 和 [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)，不在 LIVE 自评复制每轮 1 KB 里程碑叙述。

## 下一次加分只能来自真正缺失的交付

先取得独立发行者密钥/固定可信源的**真实审批**，完成安全审查的 OS broker、合法逐次用户授权、跨版本升级/失败取消与回滚，然后在具名 Pop!_OS 24.04 COSMIC 实机 L3 复验。并行由外部责任人完成官方 FIDO、独立 WORM、管理员 WebAuthn/CA、隐私法律及 SRE/备份恢复签收。未达成前，**真实安装/发行维度仍 3/20；G-01–G-10 OPEN；v0.1 NOT RELEASE READY。**
