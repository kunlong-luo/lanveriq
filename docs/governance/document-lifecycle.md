# 文档权威与生命周期

**ACTIVE · 2026-11-24 · v0.1 NOT RELEASE READY。** 规则变更必须以源码和实际 SHA/CI 为依据；文档不是信任根、发行许可证或产品开关。

| 类别 | 唯一权威 | 维护规则 |
| --- | --- | --- |
| LIVE-FACT | [CURRENT_STATUS](../CURRENT_STATUS.md) | 当前功能、确切 SHA/CI、真实测试范围；不能复制每个旧日志 |
| LIVE-GAP | [release-gap register](../planning/release-gap-register.md) | G-01～G-10 未完成项目及真实退出条件 |
| ACTIVE PLAN | [Linux 交付路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) | 当前单一实施顺序，不再引用旧 roadmap/backlog 为权威 |
| NORMATIVE | [AGENTS](../../AGENTS.md)、[工程手册](engineering-playbook.md)、[DoD](../planning/definition-of-done.md) | 版本、权限、安全与开发规则 |
| ADR/SPEC | [ADR](../architecture/adrs/README.md)、architecture/security/development | 决策、接口与信任边界；历史 ADR 要标记被替代 |
| HISTORICAL | [固定 Git SHA 归档](archive/README.md)、各日期报告与原始 CI | 只作追溯；保留独立真实失败、攻击与合成测试证据 |
| INDEX | [docs/README](../README.md)、[根中英文 README](../../README.zh.md) | 目录职责、入口与边界，不是日常 CI 时间线 |

## 修改、清理和验收规则

1. 核对当前 `main` SHA、代码与测试、相关 ADR、功能标记和 [发布 Gate](../planning/release-gap-register.md)，再决定修改。
2. **删除前**确认文件已被当前唯一规范替代，不含独立证据、编译/运行依赖、真实失败或审批信息。历史原文保留固定 Git SHA，修改所有仍需引用它的相对链接。
3. [目录保留/删除审计](repository-layout-audit-2026-11-24.md) 维护当前结构。旧 `backlog.md`、`roadmap.md`、2026-11-08 批次计划、旧 implementation sequence 已从当前树清理；不能把历史「待开发」「框架迁移」当成今天的事实。
4. 更新相应 SPEC/ADR；测试实际 PASS 才写入 CURRENT_STATUS。记录 exact **functional SHA、run ID、环境、PASS/FAIL/PENDING/NOT RUN**，不要用 docs-only CI 替代产品验收；仅在真实外部验收变化时更新 LIVE-GAP。
5. 执行 `node apps/web/scripts/verify-documentation-baseline.mjs` 与 `node --test apps/web/scripts/test_verify_documentation_baseline.mjs`。[docs-governance workflow](../../.github/workflows/docs-governance.yml) 验证目录、入口、所有 `docs/**/*.md` 本地链接和过时路径负例；不验证外链/标题锚、法务批准或 Pop!_OS L3。

早期目录清理出现过真实断链 FAIL [#45372711463](https://github.com/kunlong-luo/lanveriq/actions/runs/45372711463)，后经固定 SHA 引用修复；不得抹去失败历史。详细旧文档治理过程可从[本次精简前固定原文](https://github.com/kunlong-luo/lanveriq/blob/234850dfef72b4fbe4dcc5f4157270c4dc7860e6/docs/governance/document-lifecycle.md)查询。

**安全 Gate 全部 OPEN。v0.1 NOT RELEASE READY。**
