# ADR-0009 — v0.1 Agent 传输协议保留 HTTP/mTLS，按指标评估 gRPC/MQTT

**日期：2026-11-08。状态：推荐 / 待维护者复核具体生产策略。**
**不覆盖：**已接受的 [ADR-0005 REST + WSS](0005-rest-plus-websocket.md)、[ADR-0006 Agent 与 Dashboard 双平面](0006-agent-rest-dashboard-ws.md)。本 ADR 为它们补充所依赖的**工具包、加密信任域、业务 ACK、不足和迁移门槛**，不是新协议上线批准。

## 问题

Lanveriq 首发目标为经过授权的 1–10 台 LAN Windows/Linux 设备；需要安全注册与证书轮换、设备身份+组别隔离、低频心跳、变更时完整资产、100 条/512 KiB 有界事件批、离线可重放、PostgreSQL 事务确认及安全的 Next.js 状态展示。需要选择 **成熟、可维护、跨 Java/Rust、无需另起 broker** 的协议栈，而不是追逐单一吞吐技术。

## 当前决策：不迁移既有 REST/mTLS

- **设备数据面：**Rust `reqwest 0.13.5` / rustls / Tokio / Serde，通过固定 Agent 主机 HTTPS 上的 mTLS REST 对接 Caddy，再通过有独立证书的 Caddy→Spring/Tomcat mTLS，Spring Security + PostgreSQL 有效证书绑定。客户端保留 SQLx SQLite outbox 并验证服务端业务 ACK。当前已实现零散源码和默认关闭心跳入口；**没有可用的生产设备业务闭环**。
- **浏览器平面：**Next.js 16 App Router，Spring Session + 受限 REST、未来按管理员/组权限的 WSS 小提示 + TanStack Query 数据重新拉取。**WSS 不运载事件 ACK、资产快照或设备远程命令**。
- **限制：**`AgentTlsTransport` 当前最低 TLS 版本是 1.2，而上游 Tomcat TLSv1.3-only；**必须在 L2 验证外部 Agent→Caddy 段的版本要求**，如果 v0.1 定为 TLSv1.3-only 则应显式改动客户端与 Caddy 并补负例。无依据不得写“全链路仅 TLS1.3”。
- **正确性：**任何 HTTP/2/gRPC/MQTT 的成功交付都不替代 PostgreSQL transaction commit、不可重复事件/快照、服务端活跃证书/当前设备权限、精确 ACK 和 SQLite crash-replay。数据的允许字段和“未知/过期”语义仍由业务契约约束。

## 排除与条件性候选

| 方案 | 当前取舍 | 可重新评审条件 |
| --- | --- | --- |
| REST/JSON + mTLS（继续） | 已有 Java/Rust/Caddy 现成能力，端点数量和 payload 有界，单次 HTTP/ACK 适合离线重试；迁移风险不值得 | v0.1 主线 |
| gRPC/Protobuf（`tonic`/`prost`、Spring gRPC 1.0.x、Buf） | 更强生成式 schema、HTTP/2 流式、紧凑 wire；需完整新的授权/代理/ACK/恢复/多版本实验 | 测得大规模持续流/CPU 或带宽瓶颈、迁移收益显著、双栈可灰度 |
| MQTT 5.0（如 `rumqttc` + 受控 broker） | 需要 broker ACL/QoS/session 运维；broker 交付 QoS 与 PG18 提交不同 | 有确切设备规模、broker 管理、主题路由/发布订阅需求与资金授权 |
| 原生 TCP/WebRTC/自定义加密 | 重造 TLS/语义且安全面大 | v0.1 不允许 |

**优先选择库而不是自己写协议栈：**官方 [Reqwest](https://docs.rs/reqwest/latest/reqwest/tls/)、[Caddy](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy)、[Spring gRPC（Boot4.1兼容）](https://docs.spring.io/spring-grpc/reference/system-requirements.html)、[tonic](https://docs.rs/tonic/latest/tonic/)、[Buf breaking](https://buf.build/docs/breaking/) 和 [OASIS MQTT5](https://docs.oasis-open.org/mqtt/mqtt/v5.0/mqtt-v5.0.html)。选型有维护/许可/版本兼容/OS 安全能力和真实基准测试要求。

## 交付和复核门槛

1. **批次 A/B**：正式冻结 Rust/Java JSON 黄金样本/字段、null、UTC、结果大小、HTTP 错误及重试；完成真实合成 CA/CSR/mTLS/PG18 的负面测试、ACK/撤权/断网恢复，暂不启用真实设备。
2. **批次 C/D**：落实生产授权的浏览器 REST/WSS 并复核细粒度 RBAC 与旧身份缓存失效；无需为此改变 Agent 到 Spring 的 REST。
3. **v0.1 L3**：TLS 版本策略、密钥托管/轮换、Windows/Linux 真实经批准设备、性能与恢复/法律/运维；上线需要独立批准。
4. **如果要引入 gRPC/MQTT**：另开 ADR 列明量化瓶颈、proto schema/backward compatibility、Buf 自动检查、限流/认证/审计、双栈期限和安全负测；**不能用这份推荐文档作为直接修改生产协议的依据**。

完整事实/组件列表见 [设备协议栈基线](../device-communication-stack.md)。
