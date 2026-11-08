# Lanveriq 文档一致性与设备协议审查（2026-11-08）

**审查类型：**现有 `main` 仓库的核心产品/架构/研发/安全/验收文档与真实 `Cargo.toml`、`build.gradle`、`apps/web-next/package.json`、Agent/Java/TLS 源码、GitHub Actions 触发策略之间的一致性复核。  
**结论：**已修复一批 **P0/P1 文档事实矛盾**并确立协议推荐基线；**不等于完整 121 份文档已逐行校对、运行代码/生产发布获准**。旧的 D0/NXT 测试记录应标明日期，不应删除历史失败或把早期 PASS 当作新增功能的测试证据。

## 1. 版本与单一事实来源

| 主题 | 当前权威文档 | 不再作为现状使用 |
| --- | --- | --- |
| **产品优先级/上线边界** | [v0.1 A–E 批次交付计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md)、[路线图](../planning/roadmap.md)、[DoD](../planning/definition-of-done.md) | 2026-10-30/11-01 的逐任务排期、设计图、静态预览完成率 |
| **实际前后端依赖** | [技术栈真实基线](../development/technology.md) + `apps/web-next/package.json`、`apps/agent/Cargo.toml`、`apps/server/build.gradle` | “没有代码/依赖安装”、React/Vite 是正式前端、所有候选库均已安装 |
| **设备传输协议/安全** | [设备协议栈事实与选型](../architecture/device-communication-stack.md)、[ADR-0009](../architecture/adrs/0009-agent-transport-selection.md)、[设备注册](../architecture/enrollment-bootstrap.md) | 只说“自研设备协议”、将 MQTT QoS 当 PG18 commit、将完整 TLS1.3 当既有事实 |
| **业务和持久 ACK** | [Agent outbox](../architecture/agent-persistence.md)、[事件幂等](../architecture/event-delivery.md)、[REST/Agent 载荷](../architecture/api-contract.md) | 仅看 HTTP 200 就清队列；用 UI/WebSocket 状态替代服务器数据库时间 |
| **认证、权限和隐私** | [RBAC](../security/authorization-matrix.md)、[威胁模型](../security/threat-model.md)、[开放决策](open-decisions.md) | “本地网络 = 可信”，默认 ON、任何合成测试代表员工同意 |
| **实际测试** | [L1/L2/L3](../testing/strategy.md)、[发布门槛](../operations/release.md)、每次 CI 的 SHA+run ID | “CI 永久 unit-only”或“每小提交必须跑全部 PG18/Caddy/Chromium” |

## 2. 本轮已经修订的错误/缺漏

| 优先级 | 文件 | 发现与已采取的处理 |
| --- | --- | --- |
| **P0** | [`development/technology.md`](../development/technology.md) | 原文“无代码”、React/Vite、Radix/候选库已采用，与 Next.js 16.4.5/Base UI 1.9.0/React19 源码矛盾；**重建已装/候选/未接线的技术矩阵** |
| **P0** | [`development/standards.md`](../development/standards.md)、[ADR-0007](../architecture/adrs/0007-mature-ecosystem-first.md)、[ADR-0008](../architecture/adrs/0008-nextjs-app-router-console.md) | 假设的 `apps/dashboard`/`crates/*`、旧 Vite“不可改 Next”与后来的实际决定冲突；已明确 Next.js 是唯一主线，历史条款被替代，生态复用原则保留 |
| **P0** | [`architecture/system-overview.md`](../architecture/system-overview.md)、[deployment](../architecture/deployment.md) | 旧架构把 SSR/RSC Next.js 当静态 Vite 应用；改为**私有 Next.js runtime** 候选，Pages 是断开的静态展示，视频仅 v0.3 |
| **P0** | [设备协议栈](../architecture/device-communication-stack.md)、[ADR-0009](../architecture/adrs/0009-agent-transport-selection.md) | 原来缺少跨 Rust/Java/Caddy 组件事实表、REST/gRPC/MQTT 取舍、实际 TLS 最低版本和 ACK/权限执行链；已建立权威协议决议及生产阻塞条件 |
| **P0** | [`architecture/api-contract.md`](../architecture/api-contract.md)、[heartbeats](../testing/server-heartbeat-manual.md) | 旧文档说完全没有 Agent heartbeat HTTP、Console 还没有 HTTP 控制器或 Springdoc；更新为**Batch A1 双开关 default OFF POST 源码与 L1**，仍不冒称真实 Agent/Caddy/PG18 E2E |
| **P0** | [`planning/definition-of-done.md`](../planning/definition-of-done.md)、[functional-closure](../product/functional-closure.md)、[D-10/D-26 open decisions](open-decisions.md) | 旧 DoD 仅单测、Loop A 全未实现；改为**代码实现、L1、L2、L3、合法设备试点、生产**分级，准确描述部分业务源码/合成 CI |
| **P1** | [`architecture/agent-persistence.md`](../architecture/agent-persistence.md)、[Rust stack](../development/rust-agent-stack.md)、[device identity](../architecture/device-identity.md) | 旧 AGT-02 当时的“无 ACK/库存/SQLx uploader”已被后续源码超越；加当前状态前言，不删除有用的历史落地记录，明确**运行桥/私钥 custody 仍空白** |
| **P1** | [ADR-0004](../architecture/adrs/0004-rapid-main-development.md)、[ADR-0005](../architecture/adrs/0005-rest-plus-websocket.md)、[historical v0.1 sequence](../planning/v0.1-implementation-sequence.md)、[test matrix](../testing/test-matrix.md) | 过去 unit-only/逐小提交重测与 [用户 2026-11-08 已批准策略](../planning/v0.1-batched-delivery-plan-2026-11-08.md) 冲突；保留历史原文并增加 **L1/L2/L3 覆盖说明** |
| **P1** | [`security/threat-model.md`](../security/threat-model.md) | 补上代理 header 冒充、TLS 降级、签发/私钥丢失、ACK 误确认和跨组缓存泄露的明确定向负例 |
| **P1** | [`governance/documentation-gate.md`](documentation-gate.md)、[backend stack](../development/backend-stack.md) | D0/2026-11-01 “未跑 PG18”属于历史阶段；增加当前正/负面合成验收与 **新 A1 网络 E2E 尚未验收** 的日期边界 |
| **P1** | [docs index](../README.md)、[English README](../../README.md)、[中文 README](../../README.zh.md)、[frontend design history](../development/frontend-design-system.md) | 顶层补上真实主线、最新 CI、协议/风险、A1 状态与 ADR 链接；原早期 Vite/NXT 细节仅作历史，未来应按批次归档 |

## 3. 尚未关闭的 P0/P1 缺口（不应因为文档改完就标记通过）

| 优先级 | 问题 | 需要的实际工作与验收标准 | 何时执行 |
| --- | --- | --- | --- |
| **P0 协议** | **Agent→Caddy TLS 最低版本与“TLS1.3-only”文案不一致**。Rust `transport.rs` 使用 `.tls_version_min(TLS_1_2)`，Spring 上游使用 TLSv1.3；外部入口版本没有端到端证明 | 代码阶段由维护者确认**允许 TLS1.2 还是严格 TLS1.3**；若要求 TLS1.3，改 Agent/Caddy 的显式设置，L1 配置负例 + L2 两段真实降级拒绝测试。不要凭文档改动声称已修复 | Batch A |
| **P0 证书** | 真实 Caddy `agent` client CA、Caddy upstream pinned client cert、OS 私钥保管、CSR 人工审核/安全轮换和吊销流程尚未贯通 | 合成 CA→CSR→人工批准→叶子签发→激活→指纹映射→撤销，Wrong/expired/missing CA/leaf/SNI，后台 key loss/new enrollment | Batch A |
| **P0 互操作** | Agent Rust Serde JSON 与 Java Jackson **尚无完整双向 golden fixtures**，Console 的 Springdoc+Orval 通过不代表 Agent 协议通过 | 固定 heartbeat/inventory/event JSON、null/字段缺失/上限/日期/ACK 与 error-code 的金样本和跨语言序列化测试；按源文件 SHA 报告 | A/B |
| **P0 业务闭环** | Agent 仍是 idle service；心跳/库存/上传库代码不是已部署产品；默认 OFF HTTP 控制器未真实穿过 Caddy/PG18 | 先打通 authorized synthetic device_id、首次心跳 DB commit/严格 ACK，再接入库存、SQLite crash/offline replay；完成一整批再 L2 测试 | A/B |
| **P0 生产** | 组织/地域/告知/数据目的与保留期限、证书/设备管理审批、真实 Windows/Linux、备份恢复、签名分发、量化负载均无完整批准 | 不接触未获授权设备，执行 OS 网络/服务和安全测试，明确隐私告知、数据删除、日志/审计、恢复/升级回退并取得签字批准 | E/pilot |
| **P1 前端** | Next.js SSR/RSC 正式登录与真实 RBAC/数据新鲜度/权限撤销、WSS/hints 尚未上线 | 合成 A/B 管理员访问按组隔离、CSRF/Host/Origin/no-store、撤权清缓存，WSS reconnect + REST re-fetch，公开 Pages 仍不连真实数据 | C/D |
| **P1 运维** | 设备规模/离线恢复实测、服务器 DB 索引和批次 ACK/限流预算、CPU/RAM/带宽 P95/P99 尚无量化 | 为 1–10 台实测建立初始门槛，若以后大幅扩容再按 ADR 评估 gRPC/Protobuf/MQTT，而不是为“先进”重写协议 | B/E |
| **P1 文档治理** | 老的 D0/NXT 手工日志保留数百条**带日期的阶段判断**，目录有些页面仍写“未实现/未测试”，容易被误认为当前结论 | 每篇旧 runbook 顶部增加“历史快照”的状态提示并指向权威新报告。不要删除历史失败或把先前 CI 完成率移动到不同 SHA | 后续按章节增量处理 |
| **P2 展示资料** | Mermaid/SVG 早期概念仍含 Vite 静态 SPA、v0.3 视频等旧元素 | 明确标注图为历史概念、随最后部署/隐私决策集中更新绘图和中文字体预览，避免业务批次 A 因美化延期 | E/后续 |

## 4. 协议工具包推荐（最终评审依据）

| 范围 | v0.1 建议 | 可选下一阶段 |
| --- | --- | --- |
| Agent transport | `reqwest` + `rustls` + `tokio` + `serde_json` + `sqlx`；固定 origin、无代理/重定向、严格 signed-in 服务 ACK | 大规模双向长流：`tonic` + `prost`，Spring gRPC 1.0.x，Buf lint/breaking/generate；需并行迁移测试 |
| Server/auth | Caddy、Spring Boot Web MVC + Spring Security、Bouncy Castle、Hibernate/JPA + PostgreSQL18 | 为成熟多站点/密钥自动化评估 Smallstep CA 与专用管理体系，非首版依赖 |
| Browser | Next.js App Router、Springdoc→Orval、TanStack Query、选定的 Spring WebSocket 小型受限 WSS hints | 暂不引入额外 broker/STOMP/Kafka；MQTT 5.0 仅在规模和多主题路由有实际价值时评估 |

**为什么不立即改成“更高级协议”：**当前最大的上线阻塞是证书/运行桥/实时授权/离线事务，而不是 JSON 传输极限。引入 gRPC 或 MQTT 不会替代业务准确的 ACK、服务器收到时间、组织授权和生产事故恢复，反而在当前阶段扩大安全验证面。选择成熟工具包并配套领域约束才符合项目复用优先原则。

## 5. 文档审查方法与边界

1. 核对 GitHub `main` 代码树、`build.gradle` / `Cargo.toml` / `package.json` / `transport.rs` / heartbeat controller 和对应的默认-OFF 配置；审阅主要架构、治理、测试、产品、规划与两种语言 README。**并非逐行验证全部 121 份 `docs/` 文件。**
2. **已验证 30 份核心 README/架构/协议/ADR/规划/测试/安全 Markdown，抽取共 414 条仓库相对链接，逐一同 `main` 实际 Git tree 比较，未发现不存在的目标文件。** 此项只验证相对路径目标存在，不验证 URL 外部可用性、Markdown 锚点、GitHub 图片/字体渲染、SVG/Mermaid 可读性或每份历史文档的全部链接；不能把本次抽查当作正式排版/无障碍认证。
3. 核对用户已确认的三个开发测试层级：日常 L1 保留安全负例；L2 按业务批次合成联调；L3 标签/dispatch 完整安全/OS；公开 Pages 保持断开。
4. 任何状态更新都记录日期、实际测试 SHA/运行链接、synthetic 还是已授权设备；无运行日志便只能标成“源码已提交/待验收”。
5. **不在本次文档修订中自动换协议、启用生产 TLS 入口、部署 Agent、采集员工数据或创建真实证书**。必须把具体安全和容量决策带进 Batch A–E 对应实施/验收。
