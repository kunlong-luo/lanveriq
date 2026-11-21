> **NXT-24 / 2026-11-21:** 最新唯一主前端是 **`apps/web`（Next.js 16）**，不是历史上的 Vite；仓库中的 `apps/web-next` 已改名并删除旧目录。请在 `apps/web` 安装锁定依赖后使用 `corepack pnpm dev`，浏览器访问 `http://127.0.0.1:3100/overview`。生产 API、采集和远控保持关闭。

# IntelliJ IDEA 导入 Lanveriq（仅后端使用 Gradle）

**工程结构：** 仓库根目录 `lanveriq/` **不是 Gradle 工程**，不提供根级 `settings.gradle` / `gradlew`。Java 后端 `apps/server/` 自带独立 Gradle 9.8.2 Wrapper。Rust 和 Web 分别由 Cargo、pnpm 管理。

## 推荐方式：在 IDEA 查看整个仓库

1. **File → Open**：选择 `lanveriq/` 根目录，将其作为普通项目打开。
2. **View → Tool Windows → Gradle → Link Gradle Project**：选择 `lanveriq/apps/server/build.gradle`。如果 IDEA 提示关联已有工程，保留一个后端 Gradle 链接，避免重复导入。
3. 在 **File → Project Structure → Project SDK** 中选择 **JDK 25**。
4. 在 **Settings → Build, Execution, Deployment → Build Tools → Gradle** 中选择 **Gradle JVM = JDK 25**、**Distribution = Gradle Wrapper**，并等待 Gradle 同步。
5. 如果只需要 Java 后端，亦可直接打开 `apps/server/settings.gradle` 导入，而不打开整个仓库。

**注意：** 不能在根目录执行 `./gradlew` 或 `gradlew.bat`，因为这里故意不放 Gradle Wrapper。必须先进入 `apps/server`。

## 命令行诊断，不启动数据库或采集

Windows PowerShell：

~~~powershell
cd apps/server
java -version
.\gradlew.bat --version
.\gradlew.bat projects
.\gradlew.bat test
~~~

Linux / macOS：

~~~bash
cd apps/server
java -version
./gradlew --version
./gradlew projects
./gradlew test
~~~

Java 25 / Gradle 9.8.2 初次同步需要访问 Gradle Plugin Portal、Maven Central 以下载依赖，除非已缓存。不要因同步失败关闭 TLS 验证。运行 `test` 只执行现有自动单元测试；真实 PostgreSQL 18 集成测试依然是独立的手动验收任务。

## 同仓库的其他项目

- **唯一 Web UI**：`apps/web`（Next.js 16.4.5 / React 19 / Tailwind 4），使用 Node.js 24 + pnpm 12，在该目录运行 `corepack pnpm install --frozen-lockfile`、`corepack pnpm dev`；浏览器打开 `http://127.0.0.1:3100/overview`。原先占用这个目录的旧版 Vite 已被删除，其 Next.js 替代品从 `apps/web-next` 迁移到此目录；5173 不再是产品 UI 端口。当前仅断开业务数据的预览，不登录真实管理员或设备。
- **Rust Agent**：独立 Rust 工程在 `apps/agent/Cargo.toml`，`apps/agent/Cargo.lock` 锁定依赖，`apps/agent/rust-toolchain.toml` 固定 Rust 1.94。根据 IDEA 的具体版本与插件支持来选择 Rust 插件，或另用 RustRover。
- **Java 后端**：`apps/server` 中 Spring Boot 4.1.2、JPA/Hibernate、Flyway 均通过独立 Gradle 工程维护。导入工程不需要运行 `bootRun`，更不需要触碰共享 `spark-db`。

## 常见问题

| 提示 | 检查方式 |
|---|---|
| IDEA 没有识别 Java / Gradle | 关联 `apps/server/build.gradle`，而不是根目录 |
| `Unsupported class file major version` | 确认 IDEA 版本兼容 Java 25，且 Project SDK 与 Gradle JVM 均是 JDK 25 |
| Gradle 插件/依赖下载失败 | 检查代理、证书与 Gradle Offline Mode；不要禁用 TLS 校验 |
| `GradleWrapperMain` 找不到 | 检查 `apps/server/gradle/wrapper/gradle-wrapper.jar` 是否随 Git 正常检出 |
| Web / Rust 目录没有 Java 模块标记 | 正常现象，它们分别使用 pnpm 和 Cargo |
| `bootRun` 连接数据库失败 | 属于运行环境问题；根据[安装文档](../operations/installation.md)配置独立开发数据库，不通过修改项目根目录或共享数据库权限解决 |

项目导入不会开启真实设备采集、员工活动监控或后台服务。参考 [JetBrains 官方 Gradle 指南](https://www.jetbrains.com/help/idea/gradle.html)。
