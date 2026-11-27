# Lanveriq Server — Spring Boot / Gradle / PostgreSQL

**ACTIVE SOURCE · Java 25 · Spring Boot 4.2.0 · Gradle (Wrapper) · Hibernate JPA · Flyway · PostgreSQL 18.x · v0.1 NOT RELEASE READY。**

这是 Lanveriq 的唯一中央业务及安全后端。**不要新增 Maven/pom.xml、手写 JDBC DAO、平行数据库真相源、默认开放的生产 REST 或公网入口。**

## 目录作用

| 路径 | 职责 |
| --- | --- |
| `build.gradle`、`settings.gradle`、`gradlew`、`gradle/` | 唯一 Java 构建/工具链入口；开发者无需系统 Maven/Gradle |
| `src/main/java/` | Spring/JPA 业务域、登录/会话/设备、mTLS 严格身份绑定与安全拒绝 |
| `src/main/resources/` | application 默认安全设置、Flyway schema 迁移 |
| `src/test/`、`src/integrationTest/`（如存在） | 单元/隔离 PostgreSQL18 Testcontainers 集成和安全负测 |
| `scripts/` | Linux/Caddy/PG18/Chromium 合成验收、回退/诊断工具；不能直接用于生产部署 |
| `contract-client-check/` | 与 Next.js TypeScript 客户端保持一致的 OpenAPI 契约验证 |

## 已证明什么，仍未批准什么

真实 Ubuntu 24.04 **隔离 CI** 复验了 PostgreSQL18.7/JPA 事务、双跳 TLS1.3 mTLS、已注册合成设备的激活/心跳/库存严格 ACK，以及管理员当前组的受控 API/Chromium 页面显示。最新 [同来源 Linux 库存→Web L2](../../docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。

这**不是**真实 Pop!_OS 设备、生产 CA、可信 FIDO 来源、正式管理员 WebAuthn MFA、独立 WORM、员工设备/数据授权，也不等于正式生产 Web 登录或业务端点已开放。系统安全开关默认关闭，未获授权时拒绝。

## 开发者使用

要求 JDK 25。**只在开发机/CI 中**运行：

```bash
cd apps/server
./gradlew test
# Docker/Testcontainers 在隔离条件满足时：
./gradlew integrationTest
```

Gradle 在 `apps/server` 下，不在仓库根目录。需要带数据库启动 Spring 时，必须先由 [开发环境/安装手册](../../docs/operations/installation.md) 核验独立 `lanveriq` 角色与数据库、SCRAM、隔离绑定、开关关闭和服务生命周期；不要直接把开发命令暴露到生产 LAN/WAN，也不要操作同一宿主机上其他业务的数据库/卷。

### 本机测试配置（唯一数据库凭据来源）

使用已初始化的 `lanveriq` 数据库；不再创建第二套本机库。首次在 `apps/server` 下执行：

```bash
test -e config/application-local.yml || install -m 600 config/application-local.example.yml config/application-local.yml
chmod 600 config/application-local.yml
nano config/application-local.yml
```

填入本机测试数据库密码。此文件已由根 .gitignore 排除，禁止提交。IDEA Working directory 为 `apps/server`，Active profiles 为 `local`，移除重复的数据库环境变量和 VM 参数。仅已获批准的双跳 mTLS 测试使用 `local,agent-upstream-mtls-acceptance`，保留原 TLS 秘密文件和验证参数，不启用任何其他能力。离线 `initialize-local-device.sh prepare|register` 明确读取同一本机文件并检查所有者及 0600，不再重复要求数据库密码；管理员仍须实际认证。

管理员账号和密码是业务身份而非 Spring datasource 配置，数据库仅存 Argon2id 哈希。测试阶段如维护者明确需要在项目内记录原始测试凭据，可手动保存在 `apps/server/credentials/local-admin.txt`（已忽略，目录 0700、文件 0600），不被 Java、Web、Agent 或部署脚本读取。记录实际创建的账号及原密码，不在 Git 模板中填值，不自动重置或初始化。优先使用密码管理器；此明文副本只限个人本机临时测试，不用于生产。切换配置不会自动创建或修改数据库身份。

源码与规范： [Java 真实技术栈](../../docs/development/backend-stack.md) · [设备通信](../../docs/architecture/device-communication-stack.md) · [身份/准入](../../docs/architecture/enrollment-bootstrap.md) · [测试分级](../../docs/testing/strategy.md) · [当前事实](../../docs/CURRENT_STATUS.md) · [发布缺口](../../docs/planning/release-gap-register.md)。

**v0.1 NOT RELEASE READY；生产联网/采集/自动恢复/远控/生产 CA 签发均未批准。**
