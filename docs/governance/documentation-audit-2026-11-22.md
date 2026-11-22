# 2026-11-22 文档查漏补缺与整改

**STRUCTURAL AUDIT COMPLETED · 生产发布缺口仍 OPEN。** 基线 `436fc403b1e6b514c5c25b09d5d481cf73efdae7`。

| 问题 | 整改 |
| --- | --- |
| 根 AGENTS 超过 18 万字节、70 多条历史进度先于规则 | 原样 [完整归档](archive/AGENTS-pre-consolidation-2026-11-22.md)；根 [AGENTS](../../AGENTS.md) 精简为现行约束 |
| CONTRIBUTING 称“没有代码/仅单元 CI”、SECURITY 状态过期 | 更新为真实 Java25/PG18/Rust/Next.js 合成 CI 与不放行范围 |
| 状态、测试、发布许可混写 | [CURRENT_STATUS](../CURRENT_STATUS.md) 是唯一事实；[release-gap register](../planning/release-gap-register.md) 是唯一 OPEN Gate，禁止借合成 PASS 推断上线 |
| FIDO 每阶段进度在导航重复 | 增设 [信任边界总表](../security/fido-trust-boundaries.md)，历史 D4-C9–C16 报告仍保留 |
| 旧 ADR/roadmap、D0/Vite、unit-only 易被误当现行规范 | 增设 [ADR 索引](../architecture/adrs/README.md)、[文档生命周期](document-lifecycle.md)、[工程手册](engineering-playbook.md)、[DoD](../planning/definition-of-done.md) |
| 原文档链接检查不覆盖 AGENTS/SECURITY/缺口等新入口 | 新建 [docs-governance workflow](../../.github/workflows/docs-governance.yml) 与 JS 拒绝测试（另行源码提交） |

**不得删除历史失败、虚构组织签字或实机证据；v0.1 NOT RELEASE READY。** 本轮文档整理不修改生产功能开关、Agent 行为或正式 FIDO 信任。
