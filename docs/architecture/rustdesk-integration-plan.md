# Lanveriq × RustDesk 隔离集成实施计划（RD-0 至 RD-3）

**日期：2026-11-08 | 计划状态：候选/待验收；尚无远控代码、运行服务或已批准的真实设备会话。**

决策见 [ADR-0010](adrs/0010-rustdesk-remote-control-integration.md)。本计划为远程控制能力另开项目轨道；**不替代** [v0.1 设备身份/心跳/库存/授权前端批次 A–E](../planning/v0.1-batched-delivery-plan-2026-11-08.md)，也不允许在完成该批次之前将“远程控制”记为已交付。

## 1. 本次代码库盘点

2026-11-08 审阅 main 的仓库树（共 592 个 tree entries）、apps/agent/Cargo.toml、apps/web-next/package.json、架构 ADR 与协议基线，GitHub 仓库代码搜索尚未发现 RustDesk 集成。下述状态必须区分**有源码**、**已接通运行时**和**真实设备已验收**。

| 领域/路径 | 实际状态 | 决策 |
| --- | --- | --- |
| apps/agent/src/{transport,store,inventory}.rs | 有 HTTPS/mTLS、SQLite、资产、严格 ACK；服务主循环未启用 | 保留、按 Batch A/B 接线 |
| apps/server/.../enrollment,events,security | 有证书/管理员/事件/库存域与合成测试；尚无远控 session API | 保留，日后独立增 remote 域 |
| apps/web-next/src/app/(console) | 有 B+C 控制台多页面；远控非已实现路由 | 保留，新入口 default OFF |
| deploy/caddy/Caddyfile.reference | 现有 console/enroll/agent TLS 参考，生产未批准 | 不重用 agent 证书信任域服务远控；隔离部署 |
| 独立 RustDesk hbbs/hbbr | 未添加到 Lanveriq | 选定候选上游；等待锁版本隔离 PoC |
| RustDesk 客户端/嵌入 Web | 未添加到 Lanveriq | 单独安装/隔离验证；不声称 OSS 内有受权嵌入 SDK |
| 录制/回放 | v0.3 设计，非运行产品 | 不和远程交互合并上线 |

## 2. RD-0 — 许可与技术可行性审查（第一项 P0）

- [ ] 记录 rustdesk/rustdesk 和 rustdesk/rustdesk-server 的**固定上游 commit/tag、发布日期、架构、镜像 digest 与许可证版本**；不使用 production latest。
- [ ] 确认 OSS 的 hbbs/hbbr 原生端口、TLS/密钥配置、OS 支持、连接方式；分别记录 LAN、WAN、直连、Relay、断线、反向代理/WebSocket 的限制。
- [ ] 分别验证原样独立客户端、Web 客户端与 RustDesk Server Pro 的 API/集中 ACL/单点登录是否可用；区别开源代码、付费许可和可自托管的能力。**没有上游支持证据前不开发“自定义嵌入式远控画布”。**
- [ ] 完成 AGPL-3.0 法律评估（修改版网络使用、分发/客户端捆绑、源码提供、版权声明、联合/独立程序边界）。“独立容器=一定没有 copyleft 义务”不接受。
- [ ] 证明管理员经 Spring RBAC 授权后，**不能通过共享 peer ID/密码/绕开 Lanveriq 页面**直接访问超出权限的设备；若 OSS 无法实现统一会话授权，则阻塞 RD-2，评估 Pro 授权接口或候选替换。
- [ ] 明确端点可见提示、被控端确认、撤销、权限拒绝、停止会话、无管理员时无启动通道；密码和长期授权不得写入 Lanveriq 的前端资源或日志。
- [ ] 补充威胁模型：peer 映射偷换、远控凭据泄漏、跨租户会话、绕过 Spring RBAC 的客户端直连、WebSocket 伪造 X-Real-IP、relay 劫持与 DoS、录屏误启用。

**RD-0 退出条件：**通过的许可证/产品形态/连接授权证明和明确部署边界。否则停止“直接嵌入网页”的实施，不新增后台远控命令路由。

## 3. RD-1 — 独立 hbbs/hbbr PoC（完全合成）

- 在 **隔离测试网络** 使用固定版本且校验摘要的未修改上游服务镜像；测试专用密钥，与 Lanveriq mTLS CA、生产环境数据、正式设备证书严格隔离。
- 对 hbbs、hbbr 做容器健康、重启、错误端点/错误密钥、双向连接、断网恢复、relay fallback 与正常清理的自动化检查。不要把进程存活当作成功的远控会话。
- 用合成的测试设备与测试连接凭证验证建立/结束会话；确认仅有 hbbs/hbbr **不构成**授权、组织权限管理或自研 Next.js 画面嵌入。
- 不使用默认对公网暴露的全端口：默认仅最小端口集合；21118/21119 若未启用 Web 客户端则不暴露。若启用，需要反向代理专属访问控制并覆盖来源头（见 RustDesk 官方 OSS 安装指南）。
- 测量 LAN P95 握手/延迟、WAN relay 消耗、CPU/RAM、带宽、丢包、会话恢复、日志/密钥可见性并记录运行 SHA；固定配置和回滚脚本。
- 不改正式 Caddyfile.reference、Agent runtime、Spring 安全过滤链或公开 Pages 预览。

**RD-1 退出条件：**可重复的合成实验、明确端口表、误配置负例和失败恢复证据。环境不可用时只标记未执行。

## 4. RD-2 — Lanveriq 控制面授权桥与自研 UI

先记录协议契约，再实施。以下为**候选接口**，不是仓库已存在的 API：
- POST /api/v1/devices/{deviceId}/remote-sessions：验证管理员会话、CSRF、RBAC/组织与设备范围、被控端能力/许可状态和用途，创建短时会话请求，默认拒绝。
- GET /api/v1/remote-sessions/{sessionId}：只返回被授权管理员可见的连接状态与受限审计元数据；敏感连接材料不得缓存、嵌入静态 HTML 或写日志。
- POST /api/v1/remote-sessions/{sessionId}/terminate：显式终止并撤销底层会话凭据，**实际传输必须可被取消**，而不只是 UI 状态变化。
- 由服务端生成的 session grant 必须是短期、一次性、绑定管理员+设备+权限版本+用途的授权；具体实现受所选 RustDesk 集成形态约束。不能将仅 Spring “允许”的结果误作 RustDesk 网络层已经强制执行。

前端遵循独立的远程会话入口、权限/同意/连接进度/错误/退出页面状态；默认 OFF，遵循 Next.js SSR 会话保护。不能直接在浏览器保存 RustDesk 长期密码、服务器 key、连接令牌。集中审计记录 request/approve/deny/start/stop/fail/expiry，并对权限撤销与超时进行正负向验收。

**RD-2 阻塞条件：**证明不了服务端到 RustDesk 实际连接的不可绕过授权，或 AGPL/Pro 许可不符，绝不提供“页面有远程按钮即可连接任何设备”的伪集成。

## 5. RD-3 — 发布候选的真实平台与安全验收

完成合规批准后才可使用明确获授权的 Windows/Linux 设备与网络；核对系统权限、Wayland/UAC/锁屏/会话切换、可见告知、断线撤权、录屏分离、不可静默接入、并发会话限制、凭据轮换/销毁、带宽/限流、日志最小化、更新回滚、备份恢复。验证跨公网和受防火墙限制的场景，严格区分直连成功和中继成功。

| 测试级别 | 触发与范围 | 不可省略的断言 |
| --- | --- | --- |
| L1 | 涉及远程领域源码的 PR/提交 | RBAC、跨组/跨设备拒绝、session TTL、禁持久密钥、默认 OFF、授权撤销和幂等 |
| L2 | RD-1/RD-2 批次边界的隔离合成环境 | hbbs/hbbr 端口与密钥、错误客户端、中继降级、断线回收、会话实际授权、审计一致性 |
| L3 | 独立里程碑/明确授权的发布候选 | 真实 Windows/Linux、OS 权限、用户可见性、LAN/WAN 延迟与回滚、许可证/安全复核 |

**不得**把“所有单元测试成功”“RustDesk 服务可以启动”“页面能显示远程按钮”解释为已通过真实会话授权/录屏合规/发布门槛。

## 6. 与既有优先级的关系

建议仍先完成 v0.1 Batch A 首次设备认证/心跳，Batch B 库存/离线恢复，Batch C/D 授权控制台与监控闭环。RD-0 的许可证和 API 研究可与 A/B 并行；RD-1 隔离实验可在没有真实设备时推进，但**不因 RustDesk 而改现有 Agent REST、Caddy mTLS 或业务 ACK**。RD-2 需要与 Spring/Next.js 安全域结合，必须在 RD-0/RD-1 证据充分后开始。

可参考 [现有通信协议基线](device-communication-stack.md) 和 [生产/测试策略](../testing/strategy.md)。远程桌面是独立协议栈，不是把所有设备管理接口迁移到 RustDesk、gRPC 或 WebSocket 的理由。
