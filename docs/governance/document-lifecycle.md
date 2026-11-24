# 文档权威、生命周期与维护规则

**ACTIVE · 2026-11-22 · v0.1 NOT RELEASE READY。**

| 类型 | 唯一入口 | 使用规则 |
| --- | --- | --- |
| **LIVE-FACT** | [CURRENT_STATUS](../CURRENT_STATUS.md) | 当前源码与确切功能 SHA/CI、发布状态；新 PASS 需实际执行 |
| **NORMATIVE** | [AGENTS](../../AGENTS.md)、[工程手册](engineering-playbook.md)、[DoD](../planning/definition-of-done.md) | 稳定规范，禁止每次附上整段最新 CI |
| **LIVE-GAP** | [release-gap register](../planning/release-gap-register.md) | 未完成发布 Gate、职责类型、退出证据；合成 PASS 不自动关闭 |
| **ADR** | [ADR 索引](../architecture/adrs/README.md) | 批准的是架构选择；记录 Proposed/Accepted/Superseded，取代关系 |
| **SPEC** | architecture / security / development 专题 | 接口、行为、反例和限制；不跨 SHA 复用验收 |
| **HISTORICAL** | 日期报告/旧计划/CHANGELOG/[旧 AGENTS](archive/AGENTS-pre-consolidation-2026-11-22.md) | 保留原始失败与日期，不能冒充现行开发规则 |
| **INDEX** | [docs/README](../README.md)、根 README | 只放导航和发布边界，不复制最新 CI 表 |

实际软件事实以代码、lockfile、Flyway、feature flags 为准；CURRENT_STATUS 负责可核对证据；D0/ADR 历史决议不批准正式上线。发生冲突先核对 Git SHA 与原始 CI，再改 LIVE-FACT，不放松安全。

**新现行文件元数据：** 标题、状态 ACTIVE/PROPOSED/BLOCKED/HISTORICAL/SUPERSEDED、核对日期、适用范围、相关权威链接、不可推导的权限。声称测试结果时必须带功能 SHA、run 链接、环境、PASS/FAIL/PENDING/NOT RUN 和不能证明的内容。明确区分 feature commit 与 docs commit。

**更新闭环：** 先检查现行规则和实现 → 修改对应 SPEC → 真实测试完成后更新 CURRENT_STATUS → 发布条件变化更新 LIVE-GAP → 架构变化写 ADR 的 Context/Alternatives/Decision/Consequences/Supersedes → 检查相对链接、版本、隐私、历史矛盾。

执行 `node apps/web/scripts/verify-documentation-baseline.mjs` 和 `node --test apps/web/scripts/test_verify_documentation_baseline.mjs`；[docs-governance workflow](../../.github/workflows/docs-governance.yml) 检查当前入口和本地链接。CI 不能验证外部官网真实性、法律批准或 Pop!_OS 物理 L3。

## 2026-11-24：目录治理与历史引用维护

[仓库目录责任/实际删除清单](repository-layout-audit-2026-11-24.md) 是四应用、deploy 隔离试验目录与文档目录的可审入口；[工程评分](../planning/project-scorecard.md) 是优先级自评，**不能取代 release-gap register 的外部 Gate**。根和四应用 README 只说明目录/开发入口/安全边界；CURRENT_STATUS 不再重复所有历史运行日志。

**删除顺序：** 先确认文件不为生产/CI/测试/唯一历史安全证据依赖，再查当前 README、历史索引和文档相对链接；删除后所有仍需引用的历史记录改为**固定 Git SHA URL**，绝不无声丢失证据。本次 `docs/development/pagevela-reference.md` 的删除曾令历史索引断链并触发真实 CI FAIL，现已修复并保留 [失败运行](https://github.com/kunlong-luo/lanveriq/actions/runs/45372711463)，是后续删除必检的反例。

**自动化：** `verify-documentation-baseline.mjs` 的本地链接/仓库目录守卫覆盖双语 README、四应用 README、评分和目录审计；docs-governance 在其自身及所有 README/CHANGELOG 改动后触发。任何不在白名单的旧第二 Web、Maven/未审真实安装桥都不得因为文档“评分”提高而被重新引入。**v0.1 NOT RELEASE READY。**
