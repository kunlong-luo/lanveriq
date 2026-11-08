# ADR-0010 — RustDesk 远程会话能力复用与协议隔离

**日期：2026-11-08 | 状态：候选架构，批准隔离 PoC；未批准生产部署、源码合并、录屏或远程会话开放。**

## 背景与现状

Lanveriq 当前有三个真实代码入口：Rust 1.94 的 apps/agent（SQLx SQLite outbox、reqwest/rustls mTLS、心跳/资产/ACK 的待接线组件）、Java 25/Spring Boot 4.1.2 的 apps/server（设备身份与授权、JPA/PostgreSQL18）、Next.js 16/React19 的 apps/web-next（独立预览与部分受保护接口）。目前**没有已实现的远程桌面数据通道或 RustDesk 集成**，也不存在可供替换的 Lanveriq P2P、屏幕采集或中继组件。当前 Agent 主循环尚未接通真实数据采集/上传。

现有 [ADR-0006](0006-agent-rest-dashboard-ws.md) 与 [ADR-0009](0009-agent-transport-selection.md) 决定的是**可靠设备管理业务通道**；交互式远程桌面是不同的数据面，不能用屏幕流代替持久化事件/库存 ACK。此前口头建议的“全面改为 gRPC”**不是现有实现决策**；保留 v0.1 已有 REST/JSON+mTLS，只有量化指标达到 ADR-0009 条件才另行批准迁移。

## 决议：首选 RustDesk，但不整合成单一 Agent

1. **复用独立 RustDesk Server OSS 的 hbbs（ID/会合）和 hbbr（中继）**，优先使用固定版本的上游二进制/容器，保持上游可更新性；不自行发明 NAT 打洞、远程屏幕帧格式、鼠标键盘协议、压缩/编解码或端到端加密。RustDesk 客户端的屏幕采集/编码/交互能力也优先评估**原样运行的独立客户端**，而非在 Lanveriq Agent 中复制/静态链接其 AGPL 代码。
2. **保留 Lanveriq Rust Agent、设备 UUIDv7、CA/证书、库存、事件、SQLite outbox 与 Spring Boot 权限/审计权威**。RustDesk 的 peer ID / server key 不是 Lanveriq 设备身份或管理员授权凭据；必须建立经服务端验证的设备 UUID ↔ RustDesk peer 映射，并可撤销、失效、重新绑定。
3. **自研 Next.js 管理 UI 不使用 RustDesk 的 UI 作为整个产品**。一期远程入口可在明确用户操作后调用受控独立客户端，或验证经过授权的 Web 会话；**不得声称 OSS 内含可直接嵌入 Next.js 的受权 Web SDK**。官方把集中管理 API、Web 客户端自托管与访问控制列为 Server Pro 功能：基于 OSS 的深度浏览器集成必须先有技术与授权 PoC，可能需要商业授权。
4. **远程会话数据流与管理/浏览器 WSS 提示分离**。RustDesk 使用其自身 TCP/UDP/WebSocket 通道；Lanveriq 现有 Agent→Caddy→Spring HTTPS/mTLS+事务 ACK 不变，Next.js 的 WSS 仅为受限状态提示，视频不能经过业务事件 WSS。
5. **不默认启用远程协助、持久化凭证、无人值守会话或录屏**。远程控制需要独立许可、用途声明、受影响终端可见提示/OS 权限、明确会话生命周期和服务端审计；主动远程协助与 v0.3 的持续屏幕录制是不同的功能/法律审批。

## RustDesk 并非“所有依赖都是最新版”的判断

此处选择 RustDesk 是因为**可复用成熟的协议/中继和多平台远控工程实现**，不是因为它的每个内部依赖都是最新。对官方 `rustdesk-server` **1.1.16** 的 `Cargo.toml` 实查可见 `edition = "2021"`、`clap = "2"`、`axum = "0.5"`、`sqlx = "0.6"` 和 `tokio-tungstenite = "0.17"`。这些是该上游发布版的实现依赖，**不能当作 Lanveriq 的新代码技术标准**，也不能仅凭版本较旧推断存在特定 CVE。

**决策：**将未经修改的 RustDesk 作为单独部署、隔离权限的版本化组件，保持 Lanveriq 已有较新的 Rust 2024/SQLx 0.9/Tokio 等管理 Agent 代码与 Next.js、Java 25 主线；不把旧的 Cargo 依赖图复制进 Lanveriq。引入任何 RustDesk 新版本前实施 SBOM、`cargo audit`/RustSec 或等价漏洞通告检查、许可证复核、升级/回滚和跨版本连接验证。若安全漏洞无法按期限修复，先评估升级/商业支持/隔离或替代方案，**不以“Rust 写的”代替安全审计**。

## 外部 OSS 边界与授权风险

- 官方 RustDesk 客户端和 rustdesk-server 仓库截至审查日均标示 **AGPL-3.0**；允许商业用途**不等于**允许不满足许可证义务的闭源改写/分发。
- 独立进程/网络调用可降低架构耦合，**并不自动形成法律上的闭源安全港**。在分发、网络提供修改版服务、复制源码、修改、嵌入 Web 客户端、链接或定制品牌前，须由合规/法律审查具体部署和衍生作品边界，落实版权声明、相应源码提供及升级策略，或取得合适商业许可。
- **OSS 只有会合/中继基础能力**，不能将 Pro 的账户、API、统一 ACL、日志或浏览器自托管宣称为 OSS 自带。Lanveriq 现有 Spring RBAC 也**不能天然约束**用户绕开控制台直接连接 RustDesk peer；此漏洞不关闭就不得上线。
- 对第三方二进制做固定版本与摘要、SBOM/许可证记录、CVE 审核；升级通过双版本互通与回滚验证，禁止无版本的 latest 直接生产自动更新。

官方参考：
- RustDesk Client：https://github.com/rustdesk/rustdesk
- RustDesk Server OSS：https://github.com/rustdesk/rustdesk-server
- Server OSS 配置/默认端口：https://github.com/rustdesk/rustdesk-server/blob/master/docs/environment-variables.md
- OSS 与 Pro 能力边界：https://rustdesk.com/docs/en/self-host/
- Pro 功能和 Web 集成限制：https://rustdesk.com/docs/en/self-host/rustdesk-server-pro/
- WebSocket 安全配置：https://rustdesk.com/docs/en/self-host/rustdesk-server-oss/install/

## 建议的信任边界

**控制面**：管理员浏览器 → 受保护 Next.js → Spring Session/CSRF/RBAC/设备分组权限 → PostgreSQL 审计与设备记录。

**设备管理面**：既有 Rust Agent → 专用 Caddy agent hostname / mTLS → Spring/PG18，使用原业务 ACK 和 SQLx durable outbox。RustDesk 中继**不得**访问 Agent 密钥或伪造 Agent TLS 身份。

**远程会话面（待 PoC）**：明确授权的远程客户端 ↔ 受控 RustDesk hbbs/hbbr ↔ 设备上的独立 RustDesk 端点。所有请求须先在 Lanveriq 服务端校验管理员会话、设备范围、撤销状态、用途与允许的连接方式；其次证明 RustDesk 实际连接也受同等授权约束，不允许仅靠页面隐藏入口来“实施”RBAC。

拟议的远程会话生命周期是 **requested → approved/denied → connecting → active → ended/expired/failed**，具体 session grant 的生成、单次使用、TTL、设备及管理员绑定、泄漏/重放处理需要在设计/合成测试中确定；**本 ADR 不声明现有 OSS 已支持该授权桥**。会话日志只记录必要的授权/连接元信息，不记录会话内容、密码或原始密钥。

### 网络与安全细节

- 默认 hbbs 的 21115/TCP 用于 NAT 检测，21116/TCP+UDP 用于会合/穿透；hbbr 的 21117/TCP 为中继；21118/21119 WebSocket 仅在经过批准的 Web 客户端场景启用。端口会受具体版本/配置影响，不能把这些默认值当作统一防火墙策略。
- 依官方 OSS 文档，若启用 21118/21119，必须只允许可信反向代理访问这些端口，由代理覆盖 X-Real-IP 等头，防止直接连接者伪造来源或规避基于 IP 的限流；没有 Web 客户端时关闭两个端口。
- hbbs/hbbr 的信任根、客户端密钥和 Lanveriq mTLS CA 不共享；不记录私钥；隔离容器网络、持久密钥权限、日志可观测性与带宽限额。
- 默认禁止在公开静态 Pages 演示站暴露会话入口、服务器端口、API 令牌和真实设备状态。不能把默认服务密钥/peer ID 当作强授权。

## 已验证的授权边界：OSS 会合服务器不是 Lanveriq RBAC 执行点

2026-11-08 的 [RustDesk 合成安全验收 #42490761041](https://github.com/kunlong-luo/lanveriq/actions/runs/42490761041) 在原版 RustDesk Server OSS **1.1.16** 上实际证明：对于先经 `RegisterPeer` + `RegisterPk` 注册的**临时合成端点**，不具备任何 Lanveriq 管理员登录、Session 或 RBAC 权限的测试请求者，携带错误 `PunchHoleRequest.licence_key` 会收到 `LICENSE_MISMATCH`、目标端点不会收到请求；但携带匹配**RustDesk 服务器公钥**时，`hbbs` 会向该端点发送 `FetchLocalAddr` / `PunchHole` 会合消息。

此验证**只证明会合消息转发行为**。没有绕过 RustDesk 原生远控密码、被控端同意、端到端加密，也没有建立画面会话。不能说“拿到公钥就能远程操纵屏幕”；同样不能把公钥校验、RustDesk peer ID 或 Next.js 页面按钮当成 Lanveriq 的管理员授权。

**硬性决定：**

- **RD-2/发布默认关闭。** Lanveriq 尚未拥有能限制实际 RustDesk 底层会话的授权执行点。不得开放生产 `hbbs/hbbr`、远控 API、持久密码或浏览器调用仅凭设备 ID 连接的功能。
- 选择方案时必须证明**未授权管理员、跨组管理员、权限已撤销的管理员、外部原版 RustDesk 客户端**均不能通过直接连接服务器、知道 peer ID/服务器公钥、使用旧的授权材料绕过 Lanveriq 规则。仅服务端发放一个“前端可用的 URL”不合格；实际连接/会话持续性及撤权必须受强制约束。
- 可比较有合规和可验证 ACL/会话控制能力的 **RustDesk Server Pro 商业能力**，或经合法授权、能在传输层执行策略的独立授权网关/替代方案；**不能预设 Pro 已满足所有会话级授权、动态撤销和统一身份要求**，必须做真实双客户端正负向验证和法律审查。
- 未取得上述证据前，隔离 CI 可继续验证官方客户端注册、数据中继、握手和模拟视频，但结果只能作为底层兼容性证据，**不提升产品远控发布状态**。

## RD-2 的候选服务端执行方案（2026-11-08 补充）

经过官方文档核对，优先对 RustDesk Server **Pro 的 Access Control** 做授权执行可行性验证，但它**不是已批准产品技术栈**。官方 [Access Control](https://rustdesk.com/docs/en/self-host/rustdesk-server-pro/permissions/) 负责连接准入，支持用户/设备/分组和禁用；其中用户组和设备组授权可能**叠加为 OR 放行**。官方 [Control Role](https://rustdesk.com/docs/en/self-host/rustdesk-server-pro/control-role/) 只约束连接后的操作，还设有 **Not Logged** 角色，不能用它代替未登录请求的连接拒绝。Pro 的 Web Console/API Token 主要是管理功能，[官方文档](https://rustdesk.com/docs/en/self-host/rustdesk-server-pro/console/) 不能作为已验证的“逐会话短期授权 + 立即中断”接口证据。

因此：不能假定购买 Pro、配置单一设备组或禁用 Not Logged 角色就已经证明 Lanveriq 管理员 RBAC 对原生客户端、P2P/Relay、权限撤销和已有会话不可绕过。具体的 **AUTH-01～AUTH-16 双客户端正负向验收**和官方版本/许可前提，见 [RD-2 会话强制授权与防绕过验收契约](../rustdesk-rd2-security-acceptance.md)。

在准入适配层仍未有验证证据时，仅允许 [源码级默认拒绝保护](../../../deploy/rustdesk-synthetic/verify_rd2_release_boundary.py)：`application.yml` 中固定 `lanveriq.remote.enabled: false`，不新增远控 REST/Next.js 路由或生产 Caddy RustDesk 端口。它能避免误发布，**不能**把静态源代码检查当作生产网络层的权限执行证明。RD-2 保持 **BLOCKED**。

## 技术方案对照

| 子系统 | 现有实现 | 复用/处理 |
| --- | --- | --- |
| Agent 身份、心跳、资产、离线 ACK | Rust+reqwest/rustls+SQLx | **保留**；按现有 Batch A/B 继续完成 |
| Java 控制面、管理员认证/RBAC/审计 | Spring Boot+PostgreSQL | **保留**；以后增加独立 remote session domain |
| Next.js B+C UI | React/Next.js | **保留**；新增受保护的远程会话入口和状态页 |
| 远程画面、输入、编解码 | Lanveriq 尚无实现 | **候选复用** RustDesk 客户端；先验证许可/授权 |
| NAT 穿透与中继 | Lanveriq 尚无实现 | **候选复用** hbbs/hbbr，独立服务 |
| 浏览器内远程交互 | 尚无可用 Web SDK | **阻塞项**：核对上游 Web 源码、可部署方式、许可证、Pro 依赖、同源和 CSP |
| Agent 协议 gRPC/Protobuf | 尚未实施 | **不因远程会话而强制迁移**；依 ADR-0009 的性能与迁移门槛 |
| 后台录屏/审查视频 | v0.3 未上线 | **不包含**在远程协助 PoC 内 |

## 实施顺序和不可跳过的验证

详见 [RustDesk 隔离集成与验收计划](../rustdesk-integration-plan.md)。

1. **RD-0：**冻结兼容版本、许可证、Web/Pro/OSS 功能边界与授权桥可行性；完成最小拓扑、端口清单和滥用威胁建模。任何阻塞条件未满足，只允许文档和离线 PoC。
2. **RD-1：**在隔离环境启动未修改上游 hbbs/hbbr；合成 endpoint/客户端验证连接、错误密钥、错误 server、断线、relay fallback；不启用真实员工设备。
3. **RD-2：**最小授权服务端契约 + 前端默认 OFF 的远程会话入口；校验实际底层连接受到授权（含权限撤销、跨组、重放、失效、绕过 UI 直连、锁屏/权限拒绝）。如 OSS 无法实现，改用 Pro 商业接口或重新选型，不自行发明新远控协议。
4. **RD-3：**用户同意、可见标识、Windows/Linux 权限、性能与公网中继、日志最小化、安装/更新/撤销/回滚，独立发布审查。未通过不得以 UI 演示冒充可用远控。

**本 ADR 不改动已通过的合成 TLS/ACK/PG18 测试，也不批准修改正式 Caddy、接入真实终端或上线 RustDesk 服务。**
