# Lanveriq 工程协作与交付规范

**ACTIVE · 2026-11-22 · 仅批准源码研发，v0.1 NOT RELEASE READY。**

## 核心工程决策
1. **成熟生态优先**：标准库/平台能力 → 官方框架 SDK → 活跃维护的成熟依赖 → 小量适配 → 证明必要才自研。核对版本锁文件、许可、安全更新、平台兼容和退出策略。不能因复用库就省掉权限/事务/可靠性测试。
2. **单主分支**：当前维护者要求只在 `main` 迭代，先核对远端 SHA，使用非强推提交，失败保留原 SHA+修复 SHA。多人协作和生产前需重新评估分支保护。
3. **Linux-first**：首发目标 Pop!_OS 24.04。实机桌面环境 COSMIC/KDE、会话 X11/Wayland 必须实测，不能按早期计划假定。Windows11 和 RustDesk 另行验收。
4. **安全默认拒绝**：生产联网、采集、自动恢复、远控 RD-2、CA 签发、正式 FIDO 可信根、管理员 WebAuthn MFA 默认 OFF。合成根、签名、CRL、TLS 声明、PostgreSQL 摘要和未托管离线见证不能自己授权。
5. **技术栈以源码为准**：`apps/web` 唯一 Next.js16/React19/Tailwind4/Base UI；Java25 Gradle/Spring Boot4/JPA/Hibernate/Flyway/PG18；Rust/Tokio/SQLx SQLite/reqwest-rustls TLS1.3/mTLS/严格 ACK。无 Vite/`web-next`、Maven、裸 JDBC DAO、自造协议。

## 每次任务的固定工作流
- **核对**：当下 `main`、[CURRENT_STATUS](../CURRENT_STATUS.md)、相关源码/测试、[ADR](../architecture/adrs/README.md)、[发布差距](../planning/release-gap-register.md)。
- **定义**：功能范围、不可打开的生产能力、角色/设备/会话/数据边界和能自动断言的失败路径。
- **实现**：优先维护好的上游库；认证/证书/权限/数据库/ACK 同时写负向测试。Flyway 只向前迁移，校对历史升级计数和回滚，数据库审计真正原子化。
- **证据**：L1 真实执行单元/类型，L2 disposable PG18/Caddy/TLS/Agent/Chromium，L3 授权 Pop!_OS 实机/实际组织审查。报告精确 **功能 SHA、workflow/run ID、环境、PASS/FAIL/PENDING/NOT RUN**。不能借另一 SHA/只编译/伪造 XML/未触发工作流说 PASS。
- **文档**：每次功能改动同步 SPEC、CURRENT_STATUS、相关发布 Gate 和接口/测试规范；架构取舍写 ADR（有 Alternatives/Status/Supersedes），详见 [文档生命周期](document-lifecycle.md)。
- **交接**：具体修改与文件、验证到哪里、缺少什么外部信任/实机证据、下一任务的退出标准。不虚构上线时间。

## UI 与信任边界
- Next.js 应始终表达无数据/未连接/权限不足/未验证状态；不编造在线设备数、操作成功或监控图。复用 [设计系统](../development/frontend-design-system.md)、Base UI，响应式、滚动、键盘、无障碍和减少动画须可验证。Craftwork 只作方向参考，不拷贝未授权素材。
- Rust Agent 在数据库确认后才承认 ACK，未知提交必须保留待审状态；UI WSS 是提示，不代替服务端事务。mTLS/Host/Origin/RBAC/会话绑定在服务器端。
- FIDO D4-C9–C16 只能输出 untrusted，真实官方根和 CRL、组织审核密钥、外部 WORM/可信时间、管理员硬件 WebAuthn MFA 与生产 CA 仍是 [OPEN Gate](../planning/release-gap-register.md)。

**完成标准**：[DoD](../planning/definition-of-done.md) · [L1/L2/L3](../testing/strategy.md) · [当前技术](../development/technology.md) · [复用优先](../development/reuse-first-policy.md)。
