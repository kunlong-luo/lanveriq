# Lanveriq 设备通信协议与组件基线（v0.1）

**2026-11-08 文档审查。状态：基于现有代码的推荐架构；不是生产协议验收/真实设备开通。**  
**设备到服务端的首选方案：`HTTPS REST/JSON + 独立双向 TLS (mTLS) 信任域 + SQLx SQLite durable outbox + 事务提交后的业务 ACK`。**  
**2026-11-08 补充（独立远程协助通道）：**远程桌面不使用本文件的业务 ACK 或管理 WSS 作为视频传输。已提出 [ADR-0010 — RustDesk 复用](adrs/0010-rustdesk-remote-control-integration.md) 和 [隔离实施计划](rustdesk-integration-plan.md)，候选使用原样部署的 RustDesk hbbs/hbbr 与客户端；main 包含隔离合成验证和默认拒绝的源码基础，但仍无实际已批准的远程会话。**这不改变本文件关于 v0.1 Agent 数据管理面 REST/JSON+mTLS 的已选协议。**OSS 与 Pro 的 API/Web 客户端权限边界、AGPL 与实际连接授权均须完成评估，不得把浏览器嵌入能力视作已实现。
**浏览器到服务端：同源 HTTPS REST 为权威状态；WSS 仅作授权后的、可丢失的变更提示（尚未上线）。**  
本协议优先服务 **LAN 内首批 1–10 台已获授权设备**；未来达到明显吞吐/持续流需求才评估 gRPC/Protobuf。参见 [ADR-0009 评估决议](adrs/0009-agent-transport-selection.md)、[ADR-0005](adrs/0005-rest-plus-websocket.md) 与 [ADR-0006](adrs/0006-agent-rest-dashboard-ws.md)。

## 1. 代码现状：协议、工具包及执行边界

| 通信层 | 实际或已选依赖 | 代码位置与状态 |
| --- | --- | --- |
| Agent 异步应用 | Rust 1.94、`tokio`、`tracing` | `apps/agent/src/runtime.rs` **仍为空闲运行循环**，没有自动上传或活动采集 |
| Agent HTTPS/mTLS | `reqwest =0.13.5`（关闭默认特性，显式 rustls/http2/json）、X.509 客户端身份 | `apps/agent/src/transport.rs` 已实现一次调用尝试：固定 origin `https://agent.lanveriq.internal`，HTTPS-only、禁代理/重定向、自带授权 CA 和客户端证书、固定连接/请求超时；**未接入服务入口** |
| 载荷/ACK | `serde` / `serde_json`，精确字段/大小/序列检验 | 心跳 JSON 与有类型的两字段 ACK；库存 snapshot+revision CAS；事件 whole-batch sequences ACK，**不根据 HTTP 成功本身删除本地待发记录** |
| Agent 离线可靠性 | `sqlx 0.9` + bundled SQLite WAL、单写入连接、`uuid`、`sha2` | 已有持久化 snapshot/outbox、请求身份及部分 retry，尚未接到真实启动/周期调度 |
| LAN 入口 | Caddy h1/h2，独立 `console`/`enroll`/`agent` 虚拟主机、独立客户端 CA | `deploy/caddy/Caddyfile.reference` 为**未批准部署参考**；客户端证书必须在 Agent hostname 上经过 verify；不能转用 Console hostname |
| Caddy → Spring | HTTPS/TLSv1.3、独立 Caddy upstream client X.509、独立 Spring server CA、`127.0.0.1:8443`、`client-auth=need` | 合成测试有历史成功；生产网络/证书隔离未批准 |
| Spring 认证/授权 | Spring Boot 4.1.2、Spring Security、`AgentIngressAssertionPolicy`、`JpaActiveDeviceCredentialGuard` | 先校验 **实际 TLS peer + 固定上游证书 SHA256 + 精确 Host/URI/HTTP method + Caddy 覆盖的叶子指纹**，后以 DB 实时验证当前设备密钥/撤销；不是信任客户端任意 Header |
| Agent heartbeat HTTP | `AgentHeartbeatRestController` + 独立精确 POST `SecurityFilterChain` | **双重默认关闭**：`lanveriq.agent.ingress.assertions-enabled` AND `lanveriq.agent.heartbeat.http-enabled`；已有 Java L1 MockMvc 但没有新路由真实 Caddy+PG18 贯通 |
| 服务器存储/DTO | JPA/Hibernate + Flyway + PostgreSQL 18；Jackson / Spring MVC | 数据库服务器接收时间为权威；不存在开放的生产 Agent API；Console 可用 Springdoc→Orval 类型生成仅为 default-OFF read/auth 阶段 |
| Dashboard | Next.js 16.4.5 / React 19 / TanStack Query；Spring WebSocket (selected) | 公开 GitHub Pages 仅断开预览；WSS REST-refetch 策略选定但 **正式 WSS 登录与实时业务链路未上线** |

**重要协议准确性提醒（Batch A TLS 客户端加固候选，待 PR 验证）：**Rust `AgentTlsTransport::new` 在本独立开发分支中已从 TLS 1.2 最低下限调整为 `.tls_version_min(TLS_1_3)` 且 `.tls_version_max(TLS_1_3)`（具体调用采用 `reqwest::tls::Version`）；仅约束该 `reqwest` 客户端的外侧连接协商，不会自动修改 Caddy `agent` listener、Tomcat 上游或 Agent 运行时，也不等于真实握手已通过。此变更仅为源码候选，尚未合并到 `main`。**P0 合约缺口仍未关闭：**需用 Agent/Caddy/Tomcat 真实握手正负例证明两跳 TLSv1.3（包括 TLS1.2 被拒、双向证书链错误、过期、错误 SNI、伪造 fingerprint、无 Caddy 上游证书），再完成经授权的合成 PG18 心跳 E2E；未运行前不得标记通过。

## 2. 三类通信不能混用

```text
[已授权、可见的 Rust Agent / SQLite outbox]
    │  HTTPS REST + mTLS + bounded JSON, batch_id/event_id/sequence
    ▼
[Caddy agent.hostname: client certificate verified]
    │  独立 HTTPS+mTLS；只覆写 Caddy 已核验叶子指纹
    ▼
[Spring/Tomcat: pinned Caddy upstream leaf + device UUID + live credential guard]
    │  原子事务 / active credential / group checks
    ▼
[PostgreSQL 18: received_at / current inventory / immutable events]
    │
    └─[Console HTTPS REST with admin session + RBAC]─►[受保护 Next.js]
                     └─[future scoped WSS hint]─────►[TanStack Query refetch]
```

- **Agent durable plane：**小型心跳（状态、capabilities/queue、无秘密）、有 revision 的硬件库存、受限业务事件。每次请求是独立、有界、可重试的一次 HTTP 操作。设备身份来自 mTLS/DB，不来自 path UUID、MAC/IP 或 body。
- **Dashboard authority：**仅服务端授权 REST 返回的数据是权威，任何 WSS 消息都只是非持久化的刷新线索；客户端断线、换角色、切标签、撤权时清除旧数据并重新查询，不使用 WSS 当事务 ACK 或实时真值。
- **Enrollment control：**`enroll` 首次无设备证书，使用独立 HTTPS 服务器验证、一次性令牌和 P-256 CSR；人工审批和受信签发后在 `agent` mTLS 路径证明私钥。**不复用 Console session、Agent 叶子 CA 和 Caddy upstream CA 的密钥**。当前审批/凭证保管/Agent 运行桥仍未实现。

## 3. 应用层数据正确性（TLS/MQTT QoS/gRPC 都不能替代）

| 类型 | 交付/可靠性规则 | 成功含义 |
| --- | --- | --- |
| Heartbeat | `schema_version=1`，约束时间/错误码/队列/能力枚举；HTTP200 + 严格 `server_time` / 非 nil `request_id`，不可有意外字段 | HTTP 调用已被服务器验证并提交；状态新鲜度以数据库接收时间而非终端声称时间判定 |
| Inventory | 带 device ID / snapshot UUID / 严格递增 revision / SHA256 完整性；本地 SQLx 先持久化，服务器原子 replace/CAS | 响应需精确匹配发送的 device/snapshot/revision 和 allowed outcome；`stale_ignored` **不等于正向 ACK** |
| Event batch | `schema_version` / `batch_id` / immutable `device_sequence` / `event_id` / 业务有效负载白名单 / 整批 ≤100 条且 ≤512 KiB | 服务端 PostgreSQL 原子提交后返回**完整精确** `accepted_sequences`，本地只删除明确匹配的已 ACK 记录；模糊响应不删、不补写为成功 |
| Network errors | 固定容量/请求超时、无任意重定向/代理、指数退避与抖动/有界队列（运行桥尚待实现）；401/403 停止身份、409 隔离、5xx 重试 | 拒绝 / 超时 / 断开 ≠ 已交付；重试保持相同事件及批次身份 |

**P0 尚缺的统一契约：**Rust Serde 与 Java Jackson 的同名字段、版本、null/缺失、时间精度、严格 ACK、大小限制、未知字段策略、负向错误码应以受控**金样本 JSON（golden fixtures）**由两端读写并进行兼容性检查。Springdoc/Orval 目前主要服务 **Console REST** 的类型生成，不代表 Agent JSON 已获得跨语言 DTO/互操作证明。不要擅自为 Agent 另造手写 SDK 或更换 wire format。

## 4. 协议选型：现在最好与未来最好不是同一个问题

| 协议 | 对 Lanveriq 当前需求的优点 | 缺点与采用条件 | 结论 |
| --- | --- | --- | --- |
| **HTTPS REST/JSON + mTLS + SQLite outbox** | Rust/Java/Caddy 直接兼容；运维/审计/HTTP 状态清晰；支持单次幂等批次和证书级身份；当前已有大量源码及合成验证 | JSON 体积大于 Protobuf；业务 ACK 仍需实现；需要严格请求字节与速率限制 | **v0.1 保留**。先完成真实有界业务链路 |
| **gRPC + Protobuf over HTTP/2** | 跨语言生成客户端和服务、强 schema、更紧凑 payload、服务端/双向流、deadline | 引入新的 transport+proxy+PKI+状态机+错误契约，流式连接恢复及 mTLS/RBAC 更复杂；不解决数据库 ACK 和幂等 | **未来候选**。在明确规模/性能瓶颈并保留兼容迁移通道后评估 |
| **MQTT 5.0 (broker)** | 适合大量间歇连接设备的发布/订阅、会话与 broker QoS，异步解耦 | 额外 broker+ACL+CA+监控；QoS 是 MQTT 会话交付机制，**并不保证 PG18 事务提交或已安装 Agent 重放语义**；设备指令订阅增大安全面 | **不纳入 v0.1**。仅对明确的大规模多主题/集中 broker 运维需求再评估 |
| **Agent WebSocket** | 双向低延迟 | 每个终端持续连接、断开/排队/鉴权复杂；业务 ACK 仍需独立定义 | **不采用**。WSS 留给管理员浏览器的非持久化提示 |
| 原生 TCP、自定义二进制/加密协议、WebRTC | 看似最小或高性能 | 重复发明 TLS/证书/重试/流控/浏览器互操作，审计面更大 | **禁止因“看起来更高级”而引入** |

### 备选成熟工具包（并非要求现在全部安装）

- **保留主栈：**Rust [Reqwest TLS](https://docs.rs/reqwest/0.13.5/reqwest/tls/) / [Tokio](https://tokio.rs/)、[rustls](https://docs.rs/rustls/latest/rustls/)、Serde、SQLx；Spring Boot Web MVC/Security/JPA、Springdoc+Orval（Console 客户端），Caddy [reverse_proxy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)；避免自己实现 HTTP/TLS/ORM/线程池。版本实际以 Cargo.lock / Gradle BOM / pnpm-lock.yaml 为准。
- **如果后期引入 gRPC：**Rust [`tonic`](https://docs.rs/tonic/latest/tonic/) + `prost` + `tonic-build`；Java [Spring gRPC](https://docs.spring.io/spring-grpc/reference/system-requirements.html)（官方 1.0.x 支持 Boot 4.1.x/Java25），schema 采用 [Buf CLI](https://buf.build/docs/introduction/) 的 `lint`/`breaking`/`generate`；Java/Rust 均用生成代码，不写手工帧协议。先测 Caddy 前后 HTTP2/TLS+metadata trust，不用不安全 `h2c` 替代 backend mTLS。
- **若未来经过治理批准采用 MQTT：**先按 [OASIS MQTT5.0](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html) 明确 broker QoS 与数据库 ACK 的不同语义；Rust `rumqttc` 可以列入技术评估，broker 方案（如 EMQX）需另做 ACL、多租户、升级/恢复与运维成本评审，**不是当前必选组件**。
- **设备 CA：**现阶段复用经审批的离线 CA + Bouncy Castle PKCS#10/PKIX 校验、Caddy 验证；不要自行实现 ASN.1/PKI。若之后需要管理数千终端/自动轮换，可单独评估成熟 CA 服务（如 Smallstep `step-ca`），但必须审核私钥 custody、签发审批/吊销、网络拓扑与停机故障影响，不直接取代当前管理员签名审批。

## 5. 什么时候允许改变协议？

只在批次 A/B/C/D **已达到正确性和安全闭环**后，基于以下观察再进入 ADR 决策：

1. 在受控、最小化的数据集上记录最大设备规模、连续事件速率、压缩前后 payload/带宽、P95/P99 上传耗时、进程内存/CPU、断网队列恢复时间与代理资源占用；避免凭“gRPC 更快”作结论。
2. 选择一个客观门槛（例如实际压测发现 JSON 大批次成为主要 CPU/带宽瓶颈，或有必须的双向连续流业务需求），并用 **同样 TLS/DB/ACK/权限** 条件对比 HTTP/JSON 与 gRPC/Protobuf。
3. Protobuf/gRPC 的字段编号、未知字段、服务版本、证书/identity metadata、deadline、重试幂等、流恢复、灰度与旧 Agent 共存、Buf `breaking`、生成工具与 CI 并存必须在同一 ADR 冻结。换协议不可取消 PG18 原子提交、校验证书、隐私审批。
4. 协议迁移先以测试 CA、受限 synthetic side-by-side 双栈实验验证，独立批准后才能修改生产 ingress 或升级已安装 Agent。

## 6. v0.1 必补验证清单

- **L1（每日）**：Rust/Java 两端 JSON golden fixtures、payload 边界、精确 ACK、坏版本/空身份/无权/吊销/时钟偏差/409/429/5xx 负向测试；无真实设备通信。
- **L2（批次 A/B）**：真正生成隔离测试 CA、P-256 CSR、审批注册/签叶子→Agent `reqwest`→Caddy mTLS→Tomcat mTLS→PG18 commit→只读授权页；分别测 TLS1.2/1.3 策略、错 SNI/证书/路径/头、重复/断网/撤销、日志脱敏。
- **L3（发布）**：Windows 11/选定 Linux 的 OS key custody、启动/重启/注销、最小权限与防篡改、更新/回滚、离线网络、证书轮换/销毁、恢复演练、性能/负载与正式法律/组织授权。**L1 green 或以往 synthetic Caddy 成功不构成真实设备上线批准。**

参阅 [v0.1 A–E 批次与三级测试策略](../planning/v0.1-batched-delivery-plan-2026-11-08.md)。
