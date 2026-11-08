# Monorepo 目录与工程约定（2026-11-08 实际状态）

**当前仓库已包含可编译代码**，不再是只有文档的脚手架。主线：
- `apps/server/`：Java 25/Spring Boot 4.1.2/Gradle Groovy，Hibernate/JPA + Flyway + PostgreSQL 18；API/Agent 入口仍需要逐项安全验收。
- `apps/agent/`：Rust/Cargo、SQLx SQLite/Reqwest/mTLS 源码；运行时仍为空闲循环，不能宣称实际注册或采集已上线。
- `apps/web-next/`：**唯一主线前端**，Next.js 16.4.5 App Router / React 19 / Tailwind4 / Base UI React/shadcn-style 源码组件；GitHub Pages 是断开式静态预览。
- `apps/web/`：历史 React/Vite 候选，仅作兼容/迁移参考，不新增正式功能。
- `deploy/caddy/`：安全边界与合成参考配置，不是已批准的生产 ingress。
- `docs/`：技术决议、方案/实施状态、测试证据、运维和安全法律审批。**不要把尚未创建的 `apps/desktop`、独立 `crates/`、`packages/ui`、`compose.yaml` 画成实际项目结构。**

**主线计划：**[v0.1 批次 A–E](../planning/v0.1-batched-delivery-plan-2026-11-08.md)，[L1/L2/L3 测试](../testing/strategy.md)，[协议和库选择](../architecture/device-communication-stack.md)。直接 main 的变更要有真实结果和可回退记录；生产前另审分支保护与签名流程。

## Naming
Rust: snake_case modules, PascalCase types; Java: conventional packages such as `com.lanveriq.server.devices`; frontend: feature folders and explicitly named hooks/components. API resources kebab-case; JSON field names snake_case across server and agent with documented mapping in TS client. UUID device/event identifiers, UTC timestamps.

## Java backend build
**Gradle 9.8.2 Wrapper**, Groovy DSL `build.gradle`, Spring Boot Gradle plugin **4.1.2**, Java 25 toolchain and JaCoCo 0.8.16. Start with one `apps/server` Gradle module; Java domain packages stay logical modules. Use **Spring Data JPA/Hibernate** as the only direct application persistence API; use JPA native SQL for PostgreSQL-specific event/inventory operations within explicit transactions, and Flyway for schema migrations. No direct `spring-boot-starter-jdbc`. **Do not introduce Maven, a second Java build system or all dependencies from the unrelated IoT reference.** See [backend stack](backend-stack.md) and [Gradle reference](backend-gradle-reference.md).

## Mandatory reuse-first engineering policy
**The maintainer requires all future Lanveriq design, implementation and code review to start from a mature existing solution, rather than write a generic utility by default.** Prioritize standard platform/database capabilities, official SDKs, then actively maintained open-source dependencies; use bespoke code only for our business rules or a documented incompatibility. For UUIDs use PostgreSQL 18 `uuidv7()` (server), Rust `uuid` crate (Agent) and browser `crypto.randomUUID()` for disposable UI IDs. Do not reimplement randomness/UUID encodings. See [policy](reuse-first-policy.md) and [root AGENTS.md](../../AGENTS.md). Library adoption reduces low-level testing, **not** our integration/security/durability obligations.

## Dependency ownership
React uses pnpm + **Orval-generated OpenAPI REST/Query hooks**, Java uses Gradle + JPA/Hibernate, and Rust Agent uses Cargo + **SQLx SQLite**. Use reviewed third-party packages rather than homemade API code generation, SQLite migrations or OS-service runtimes. See [ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md) and [Rust Agent stack](rust-agent-stack.md).

## Versioning
Single pre-release project version, semver tags at verified milestone; Event `schema_version` and API `/api/v1` evolve compatibly. Avoid breaking installed agents without compatibility window. Database migrations via Flyway server and Rust versioned local migrations.

## Commit strategy
**Direct main** during v0.x pre-release; no feature branches or mandatory PRs. Commit in small functional/doc steps with descriptive conventional subjects (`docs:`, `feat:`, `fix:`, `test:`). No force push to main; revert bad commits, don't hide failures. Once more contributors or a production deployment, reconsider branches.

## Working hygiene
Tests for touched source, local compile/type/lint, formatting and secrets checks. **Daily L1** relevant path-filtered Java JUnit, Rust Linux portable tests and targeted Windows SCM/manifest changes, Next.js Vitest/typecheck/build; security changes must keep immediate targeted negative tests. **L2** explicit batch-wide synthetic PG18/Caddy/TLS; **L3** acceptance tags/dispatch for full Chromium/Windows/Linux and release gates. Do not mark a synthetic green run as real-device test or legal approval. See [testing strategy](../testing/strategy.md).

## Security
No hardcoded secrets, private employee data in fixtures, hidden monitoring, insecure TLS, database credentials in frontend, or network-public PostgreSQL. CI tokens minimal permissions; production logs redacted.
