# Lanveriq 技术栈与依赖准入（实际代码基线，2026-11-08）

**状态：技术栈已实施，v0.1 业务链路未闭环。** 这是**当前仓库真实依赖**的入口，不再以 2026-10-30 “无源码/React+Vite 计划”描述现状。由 [v0.1 批量开发计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md) 管理交付，以 [设备通信协议栈基线](../architecture/device-communication-stack.md) 管理设备协议。各依赖具体版本以 Gradle BOM、Cargo.lock、pnpm-lock.yaml 为准；未列在 manifest 中的 UI/PKI/监控库不是已安装的能力。

| 层 | **当前真实栈** | 状态 / 说明 |
| --- | --- | --- |
| 设备服务 Agent | Rust 1.94、Cargo 2024、Tokio 1.54.1、`tracing`、Windows `windows-service` | 服务主循环**仍为空闲状态**，不进行真实注册/采集/上传；有单独库代码 |
| Agent ↔ Server | `reqwest 0.13.5`（`default-features=false`, rustls、json、http2）、Serde/JSON、X.509/mTLS、显式业务 ACK | 固定 Agent HTTPS origin，禁代理/重定向；业务 Agent HTTP 心跳后端默认 OFF；不能因为 Rust transport 有源码就说已联网 |
| Agent 本地可靠性 | SQLx 0.9.0 SQLite、bundled SQLite WAL、`uuid`、`sha2`、`sysinfo`、`os_info`、`network-interface` | 有 SQLx durable outbox/inventory 库；持久化与运行调度尚未完成整体连接 |
| Java 后端 | **Java 25 LTS + Gradle 9.8.2 Groovy DSL + Spring Boot 4.1.2**、Spring Security/WebMVC、JPA/Hibernate、Flyway、Actuator | 单体模块化架构，非 Maven；仅启用受审核的 REST 子路由，不允许全局 /api/v1/** 通配放开 |
| Java 身份/限流/CSR | Spring Security、Bouncy Castle `bcpkix-jdk18on 1.86`、Bucket4j、Caffeine | 复用库完成 PKCS#10/证书、限流和短期缓存；本地生成/设备 CA 审批及部署尚待复核 |
| 服务端数据库 | PostgreSQL 18、Flyway、Hibernate/JPA；可选 Redis/Lettuce **默认 OFF** | PostgreSQL 是唯一规范存储；Redis 仅可用于 best-effort TTL 提示/健康检查，不作持久 ACK、设备身份或权限源 |
| Web 控制台 | **Next.js 16.4.5 App Router + React 19.2.0 + TypeScript 7.0.2 + Node 24 + pnpm 12.0.0** | `apps/web-next` 唯一前端主线；`apps/web` Vite 8 是历史迁移参考，不再新增功能 |
| 组件与状态 | **Base UI React 1.9.0** + 项目内可维护的 shadcn-style 源码组件、Tailwind 4.3.3、Lucide 1.61.0、TanStack Query 5.105.0 | 当前已安装；不要把 Radix、Motion、TanStack Table、Sonner、Zustand、Recharts 等**尚未安装**的候选写成已完成 |
| 类型生成 | Springdoc 3.2.0 → **Orval（后端 CI 契约候选）** → Fetch/TanStack Query、锁定 Next 依赖类型校验 | 已在合成契约检查跑通过；正式 Next.js 不挂载真实 API hooks；见 [NXT-20](frontend-backend-directory-nxt20.md) |
| 浏览器实时通信 | Spring WebSocket + WSS 方案（同源、管理员会话、RBAC）、React Query 收到提示后 refetch | **设计已选，真实 WSS 业务链路未上线**；不用于 Agent 数据 ACK |
| HTTPS ingress | **Caddy**、独立 console/enroll/agent 主机、专用 CA 与上游 mTLS | 合成验证曾通过，**参考 Caddyfile 禁止直接生产使用**；真实 CA 与网络隔离另审 |
| 可选桌面/高级功能 | Tauri 2、OS 确认的采集/录制、FFmpeg 等 | v0.3 **独立授权后再实现**，不是 v0.1 完成条件 |

## 新增已批准的独立远程控制候选（2026-11-08）

用户批准优先直接复用 RustDesk 作为独立的远程桌面/NAT 穿透/Relay/WebRTC 基础设施，同时采用 [ADR-0010](../architecture/adrs/0010-rustdesk-dual-network-access.md) 内网 + 外网安全访问设计及 [双视角验收手册](../operations/dual-network-acceptance.md)。**这是下一步的集成候选，不是当前仓库已安装的依赖或已上线服务。** 不将 RustDesk Flutter UI 用作 Lanveriq 产品主界面，仍由 Next.js + Spring 接入经许可的远程访问能力。RustDesk OSS hbbs/hbbr 与 Pro API、Web Console、Web Client 能力/授权严格区分；正式集成前必须固定版本/SHA、许可证、硬件目标和 LAN/WAN 真正测试证据。

本次批准**不改变**上表 Agent 业务 `reqwest` + mTLS + 严格事务 ACK、PG18、默认 OFF 和 A–E 研发顺序。RustDesk 的 WebRTC/DataChannel 属于独立远程交互链路，不能拿来替代 Agent 业务可靠性协议。使用新稳定组件需经过安全/许可证/兼容性检查，不能因为所谓“最新最流行”就替换已验收的基础组件。

## 选型底线

- **先完成业务再升级基础设施。** 当前用户批准的优先级是设备注册/证书/首次心跳→库存/离线恢复→授权前端→WSS→真实平台/备份/隐私。v0.1 不新增 MQTT broker、Kafka、gRPC 双栈、视频流或微服务。若未来量化达到需流式/高吞吐的场景，再对比成熟的 `tonic`/`prost` + Spring gRPC + Buf；协议决议见 [ADR-0009](../architecture/adrs/0009-agent-transport-selection.md)。
- **成熟工具优先：**标准库/操作系统/数据库官方能力 → 官方框架 → 稳定维护的第三方库 → 最小 glue；Java 持久化使用 Spring Data JPA/Hibernate，必要的原生 PostgreSQL 语句通过 JPA native query + 参数绑定/事务，而不是自行写 JDBC 客户端层。不要自行写 HTTP/TLS/UUID/ASN.1/数据库迁移器。
- **只有实际依赖可算“采用”。** 版本、许可证、CVE 修复、Windows/Linux 与 Java25/Boot4 兼容都要经过锁定版本和目标环境检查；候选模块不能计入产品完成率。
- **安全性是架构条件。** 设备 UUID/请求头/JSON/MAC/IP 不能证明身份，Agent 需证书+活跃数据库绑定；Console session + HTML/RSC/REST 均要实时角色/组授权；所有业务路由默认关闭。Rust TLS 客户端当前仅设置 TLS1.2 **最小版本**，TLS1.3-only 没有端到端证据；见 [协议差距](../architecture/device-communication-stack.md)。
- **测试改为 L1/L2/L3：**每日变更触发相关 Gradle/Cargo/pnpm 快速测试（包括敏感边界的负例）；批次结束合成 PG18/Caddy/Agent 集成；发布候选再集中执行完整 Chromium/Windows/Linux/隐私/备份验证。[正式测试策略](../testing/strategy.md)。

## 历史框架和参考来源

2026-10-30 初始设计曾使用 Vite 8、Radix、Zustand、Motion 作为候选。2026-11-07 用户明确选择 Next.js App Router，2026-11-08 采取 B+C 设计、Base UI/shadcn-style 和批量开发策略，故早期框架段落仅供迁移/设计历史参考，不构成当前技术决策。参考 [ADR-0007 生态复用](../architecture/adrs/0007-mature-ecosystem-first.md)、[ADR-0008 Next.js 决策](../architecture/adrs/0008-nextjs-app-router-console.md)、[Agent 库基线](rust-agent-stack.md)、[后端依赖审查](backend-stack.md)。
