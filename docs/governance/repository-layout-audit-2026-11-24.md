# Lanveriq 仓库目录治理与去重审计

**ACTIVE STRUCTURE AUDIT · 2026-11-24 · main · v0.1 NOT RELEASE READY。** 唯一事实在 [CURRENT_STATUS](../CURRENT_STATUS.md)，唯一发布审批缺口在 [release-gap register](../planning/release-gap-register.md)，实施路线在 [Linux v0.1 计划](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)。本文件说明保留/删除原因，**不**作 CI/生产签收。

## 现行目录：保留什么

| 目录 | 保留理由与用途 |
| --- | --- |
| `apps/web/` | **唯一 Next.js 16 / React 19 管理 Web**；Pages 断开式演示，真实后端业务仅隔离合成验收 |
| `apps/server/` | Java 25 / Spring Boot 4 / Gradle / Hibernate-JPA / Flyway / PostgreSQL 18，业务 API、迁移与安全测试 |
| `apps/agent/` | Rust/Tokio/SQLx SQLite/reqwest-rustls Agent、持久 ACK/outbox、Linux 采集只读隔离验证 |
| `apps/desktop/` | Tauri 2 只读本机预检与 GUI、未安装 Polkit 拒绝规则、OCI APT/DBus 实验；**不是正式安装器** |
| `deploy/caddy/`、`deploy/network/` | 双 mTLS 和 LAN/WAN 边界的参考配置/隔离负测，**非公网部署模板** |
| `deploy/rustdesk/`、`deploy/rustdesk-synthetic/` | RustDesk/RD-2 许可证及默认拒绝负测；远控 **OFF** |
| `.github/workflows/` | 快速 L1、隔离 L2 及专门安全/文档 CI，不是 Pop!_OS 真机 L3 |
| `docs/architecture/` (`adrs/`)、`docs/security/`、`docs/product/` | 架构、信任边界、隐私/产品需求与有版本 ADR |
| `docs/development/`、`docs/testing/` | 技术规范与逐阶段唯一测试/失败/攻击证据，**不得批量删除** |
| `docs/planning/` | 仅 [Linux 当前路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)、[开放 Gate](../planning/release-gap-register.md)、[DoD](../planning/definition-of-done.md)、[评分](../planning/project-scorecard.md)及[历史验收账本](../planning/release-readiness-2026-11-13.md) |
| `docs/governance/` (`archive/`)、`docs/operations/` | 文档权威/固定 Git 历史索引、受限的运维与开发手册 |
| `docs/diagrams/`、`docs/visuals/` | Mermaid/SVG 概念图，**不是产品真实运行截图** |

根 `README.md`、`README.zh.md`、`AGENTS.md`、`CONTRIBUTING.md`、`SECURITY.md`、`CHANGELOG.md` 保留；根不是 Gradle 项目。不存在第二 Web (`apps/web-next/`)、Vite Web、Maven 后端或可获 root 的 GUI shell；不能借清理之名删除仍被 workflow/代码使用的 `tools/`、测试、`docs/visuals/assets/` 或尚未批准的安全门禁。

## 本轮真正从工作树移除的内容

在基线 [`cea6cfd4`](https://github.com/kunlong-luo/lanveriq/commit/cea6cfd4c9ff1e1734c47efa418208c97baba752) 的 Git tree 中有 **860 个跟踪文件，211 个 `docs/**/*.md`**。三个剩余早期 planning 全文的「未实施」「待迁移」描述已超出现行真实状态，且当前决策/验收分别有权威入口；故替换所有查得的相对引用后从工作树移除：

| 移除路径 | 原大小（约） | 不丢失的可复核原文 |
| --- | ---: | --- |
| `docs/planning/backlog.md` | 23.5 KB | [固定 Git SHA](https://github.com/kunlong-luo/lanveriq/blob/cea6cfd4c9ff1e1734c47efa418208c97baba752/docs/planning/backlog.md) |
| `docs/planning/roadmap.md` | 7.0 KB | [固定 Git SHA](https://github.com/kunlong-luo/lanveriq/blob/cea6cfd4c9ff1e1734c47efa418208c97baba752/docs/planning/roadmap.md) |
| `docs/planning/v0.1-batched-delivery-plan-2026-11-08.md` | 6.9 KB | [固定 Git SHA](https://github.com/kunlong-luo/lanveriq/blob/cea6cfd4c9ff1e1734c47efa418208c97baba752/docs/planning/v0.1-batched-delivery-plan-2026-11-08.md) |

先前已移除的 `v0.1-implementation-sequence.md` 和旧 AGENTS/docs 快照继续以[历史归档索引](archive/README.md)回溯。约 10 KB 的本审计历次过程/CI FAIL→修复长版也用[原文固定 SHA](https://github.com/kunlong-luo/lanveriq/blob/234850dfef72b4fbe4dcc5f4157270c4dc7860e6/docs/governance/repository-layout-audit-2026-11-24.md)保存，本页只维护**当前目录决策**，不重复日记。**删除文件不代表删除 Git 历史**。

期望新 Git tree：**857 个跟踪文件、208 个 docs Markdown**（无新增文件时）；以提交后实际 tree 复核为准。代码、工作流、测试夹具及生产安全默认值均不变。

## 自动防止误删或旧内容复活

[文档源代码守卫](../../apps/web/scripts/verify-documentation-baseline.mjs) 的 `verifyRepositoryLayout` 禁止恢复旧 Vite/Maven/三份旧 planning 等路径；`verifyNoRetiredSnapshotLinks` 拒绝在各 docs/root Markdown 中相对链接到已退休的文件；`verifyAllDocumentationLinks` 检查所有 `docs/**/*.md` 本地相对目标。负例覆盖重新加入过时文件及从非导航页链接它们。[docs-governance](../../.github/workflows/docs-governance.yml) 执行检查，不能替代逐行调用图、外部链接/标题锚验证、PG18/Agent 联调或独立 L3。

**下一步仍是**实际 Agent→mTLS→Spring Boot→PostgreSQL→Next.js 合成设备业务闭环以及 G-05 官方发行信任、APT 防回退高水位和 Pop!_OS COSMIC L3；独立 WORM/法务/SRE 等 Gate 均 OPEN。**v0.1 NOT RELEASE READY，生产联网/采集/远控/自动恢复/CA OFF。**
