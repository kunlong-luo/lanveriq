# Batch A / 首次心跳的可运行链路 — Stage A1

**日期：2026-11-08。状态：默认 OFF 的心跳 HTTP 入口、Agent 严格回执与 L1 定向单测已提交；尚未完成批次 A 合成 E2E。**
**上级计划：**[v0.1 批量交付计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md)，其优先级高于旧 NXT 逐阶段 UI/验收笔记。

## 当前已有的复用构件

- Agent：Rust/reqwest Rustls `Heartbeat` DTO 和显式 `AgentTlsTransport::send_heartbeat`；现有 `AgentConfig` 和 `runtime` 仍然是闲置循环，**不会自动联网/采集**。
- 服务端：`DeviceHeartbeatPolicy` 验证 wire schema/容量/capabilities/时间戳；`DeviceHeartbeatService` 在事务中执行；`JpaDeviceHeartbeatRepository` 使用 `JpaActiveDeviceCredentialGuard.lock` 检查证书、设备、吊销/过期，然后用 PostgreSQL 接收时间更新 `device_heartbeat_latest`。
- 上游身份：`AgentIngressAssertionPolicy.resolve()` 检查 **实际 servlet TLS peer** 的专用 Caddy upstream 证书 SHA-256、来源 127.0.0.1、精确 Host/URI/method、Caddy 覆盖的客户端证书指纹头，并与 URL 的 device UUID 绑定。该 parser Bean **只有**在 `server.ssl.client-auth=need`、TLSv1.3、127.0.0.1:8443、独立 truststore/key-store 与无代理头信任时才允许构造。
- 服务端其他功能：默认 deny-all；Console Session 与健康只读接口采用各自独立安全链；绝不把 Agent mTLS 当作管理员登录凭据。

## 本批第一份实际代码

1. Rust `transport.rs` 用 `HeartbeatAck` (`serde(deny_unknown_fields)`) + `classify_heartbeat_response` 取代自由 `serde_json::Value` 的宽松 ACK 接受方式。只有格式正确的 `server_time` 和非 nil `request_id`、HTTP 200 才是 `Accepted`；未知/额外字段、损坏时间、超大包不能当作成功。401/403 暂停身份、409 隔离、5xx 有界退避，**不删除数据也不直接重试**。`transport_heartbeat_tests.rs` 用纯合成数据验证，未执行任何联网。
2. Spring `AgentHeartbeatRestController` 是一个 **同时需要两个显式开关** 的精确 POST 路由 `/api/v1/devices/{deviceId}/heartbeats`；`AgentHeartbeatHttpSecurityConfiguration` 仅此路由 sessionless/CSRF-independent，其余 API 沿用默认 deny，GET/异常路径不授权。入口先经 `AgentIngressAssertionPolicy` 解析受信 Caddy upstream mTLS，再由 `DeviceHeartbeatService` + JPA 当前有效证书写入；服务器回包 `no-store`，401/403/400/413 不能泄露设备组。
3. `application.yml` 添加独立的 `lanveriq.agent.heartbeat.http-enabled: ${LANVERIQ_AGENT_HEARTBEAT_HTTP_ENABLED:false}`。**单独开启这个开关不会激活路由**；还需要明确开启 `lanveriq.agent.ingress.assertions-enabled`，且不满足 Tomcat TLS startup 约束会直接失败。部署参考配置、真实 Caddy/Agent/CA 与默认 OFF 状态都未改动。
4. `AgentHeartbeatRestControllerTest` 在**mock** Servlet 请求和 mock proxy 证据下检查双重开关注解、mTLS 身份检查必须先调用、合法 ACK no-store、身份拒绝不能写库、字段无效/超大/撤销后无泄漏。它**不是**真实 TLS 或 PostgreSQL 集成。

## Batch A 接下来的编码任务（尚未完成）

- **A2 证书生命周期：**管理员受限发行一次性接入令牌→Agent 生成/保存密钥与 CSR→人工批准/离线签名→受审核的注册/激活/撤销。必要的证书保护、密钥管理、用户告知与设备授权必须先定。
- **A3 真实 Agent service 链接：**首次激活后受控调度 `send_heartbeat`，依据真实授权/运行状态和 SQLite 元信息构造 wire DTO，持续 401/403 停止而不是无限尝试；保留严格终止/恢复、协议限流。未授权员工终端不允许启动。
- **A4 L2 合成闭环：**在明确的一次性批次验收中生成测试 CA/CSR/签名叶子，Rust Agent→Caddy→Tomcat mTLS→Spring 当前 credential guard→PG18 持久化→受限 GET，验证缺失/伪造/过期/撤销/错设备和离线重试，明确真实测试尚缺的 Windows/Linux 与 CA 托管。此步骤 **不在每次小改动提交上自动运行**。
- **A5 真实受限试点** 需要独立合法/组织批准、设备所有者同意、受控 CA 与 Windows/Linux 验收，暂不执行。

## 风险/边界

- 代理身份指纹头只有在专用 Caddy 确认覆写、独立上下游 mTLS 且 loopback 端口不可直接绕过时才有价值；一个手动添加头的本地请求必须被拒绝。工作站生产部署的 CA/信任/网络隔离 **尚未批准**。
- 严格时间形状校验不等于服务器信任来自 Agent 的时间。只相信 PG18 的 `received_at`，`sent_at` 仅作为经过限制的客户端声明。
- MockMvc 并非真实 mTLS；上一轮 NXT-20 通过的 Caddy/PG18 合成测试是历史证据，不适用于推断新心跳路由已经通过真实网络集成。
- 不会采集用户应用、窗口标题、屏幕、个人文件；Agent 服务仍然默认空闲。不会在静态 GitHub Pages 中引入任何认证/网络客户端。

**CI 记录（填入本阶段实际结束的 run ID）：**Java `./gradlew test` 和 Agent Linux/Windows `cargo test --workspace --locked` + Clippy/rustfmt 属于 L1。只有这两类绿灯且相关新接口 MockMvc negative 通过，才能标记 Stage A1 source-ready；所有 L2/L3 仍待后续批次集中验收。
