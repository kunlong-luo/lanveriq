> **2026-11-13 当前状态优先说明：** [Linux-first 上线差距与验收账本](../planning/release-readiness-2026-11-13.md) 是最新证据入口。`main` `758f8d9a` 的 Rust→Caddy→Tomcat TLS1.3→PG18 严格 ACK 已通过 [隔离 L2 #43356545857](https://github.com/kunlong-luo/lanveriq/actions/runs/43356545857)，不应再按旧记录认定“完全未运行”。但整批 A/B/C/D/E、正式 Pop!_OS 设备运行、Next.js/WSS/生产部署仍未完成；首发范围收敛到 **Pop!_OS 24 Linux-only**，Windows 11 另行 L3 验收，远控 RD-2 继续 OFF。本文原有时间线保留为历史，任何生产或真实数据启用须单独获批。

# Product functional closure and milestone acceptance

**Status as of 2026-11-08:** **部分源代码与合成验收已实现**：Spring Boot/PostgreSQL、Rust SQLx/Reqwest、Next.js 断开式 B+C 控制台及默认 OFF 心跳入口；**没有一条真实 Agent→Caddy→PG18→授权前端业务链路完全闭环**。D0 技术设计已批准，真实员工数据、证书保管与生产发布仍待独立审批。三个独立业务循环中 **仅 Loop A 是 v0.1 P0**；Loop B/C 不得阻塞首次设备管理上线。见 [v0.1 A–E 交付计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md)。

## Loop A — Device management and health (v0.1)
`Admin approves policy → pairs device → Agent authenticates → heartbeats/health/SQLite backlog → server stores → WebSocket status hint → Monitoring Center fetches REST → alert or diagnosis → recover/revoke → audit admin action`

**Linux-first closure criteria:** an authorized real Pop!_OS 24.04 device displays its verified identity (Windows requires later independent acceptance) and online/stale/offline state; unsupported/denied collectors are not shown as healthy; a temporary LAN outage triggers a bounded queue and duplicate-safe replay; unauthorized dashboard views/subscriptions are denied. A `Monitoring Center` is a **dedicated v0.1 screen**, not merely the homepage. No realtime screen images in v0.1.

## Loop B — Scoped activity/security audit (v0.2)
`Published allowlist → platform adapter collects approved metadata → local durable outbox → PostgreSQL canonical event → activity/audit projection → auditor searches device/time → sees gaps and source confidence → authorized export/incident case → retention/deletion`

**Closure criteria:** query results with accurate source and UTC/client times, permission checks and admin access audit; no unannounced collection of private content. Missing records do not prove user inactivity.

## Loop C — Disclosed recording and playback (v0.3)
`Published policy/notice → supported user session → OS permission + visible status → segmented local capture → HTTPS upload/checksum → server media index → recording catalog → approved review case → authorized playback/time-range access → view/export audit → retention deletion`

**Closure criteria:** *Recording & Playback Center* lists metadata, shows authorized timeline and gaps, lets a permitted reviewer request access, checks an independent case approval, serves **server-mediated, verified time-bounded media** with byte-range access restricted to the approved window, logs playback and deletes expired media. Denied OS permission or unapproved case **must fail closed**. A recorder that uploads files but cannot securely search, view and delete them is **not** a completed loop.

## Optional Loop D — Live screen viewing (later separate decision)
A live screen-preview/stream is **different** from heartbeat monitoring and different from replaying stored segments. It requires its **own explicit scope/purpose, per-session OS authorization, visible state, platform validation, stream transport and reviewer permissions**. It is **not committed** to v0.1–v0.3 and should not be implied by the name `Monitoring Center`; consider a separate ADR only if a legal/organizational need is confirmed. No covert capture or permission bypass.

## Milestone status matrix
| Capability | Planned in | As of documentation review |
|---|---|---|
| Identity, registration, heartbeat, online/offline, Monitoring Center | v0.1 | **已具内部身份、证书/心跳服务与默认关闭的 heartbeat HTTP 源码；Next.js 有断开式页面；真实设备完整链路未闭环** |
| Offline replay and event ingestion | v0.1 | **Rust SQLx outbox/ACK 和 Java JPA 事件事务已有合成源代码；还未接入实际 Agent service 到服务端 HTTP 入口** |
| Audit sources, timeline, searches and retention | v0.2 | **Designed, not implemented** |
| Recording permission, segmented upload | v0.3 | **Designed, not implemented** |
| Recording catalog, approval, indexed playback, deletion | v0.3 | **Needs center/UI/API contract implementation and real-device manual verification** |
| Live screen streaming | Not scheduled | **Out of current scope; separate authorization design required** |
| Full security/production hardening | v1.0 | **L1/L2/L3 已建立分级验证；合成 CI 与真实 Windows/Linux、PKI/隐私/生产验收仍是不同门槛** |

## Gate against overstated completeness
A `100% connected` KPI cannot replace collector coverage. A successful recording upload cannot replace playback authorization, segment integrity or deletion. Any screenshot in [visual gallery](../visuals/README.md) is a **concept illustration with fictitious data**, not a shipped UI.

See [Monitoring Center specification](../development/monitoring-center.md), [Recording & Playback Center](../development/recording-playback-center.md), [Roadmap](../planning/roadmap.md) and [manual acceptance](../testing/manual-acceptance.md).
