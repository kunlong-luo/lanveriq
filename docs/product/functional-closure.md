# Product functional closure and milestone acceptance

**Status:** Architecture review only. **No working system exists.** The product has three separate business loops; calling v0.1 a complete surveillance/recording platform would be misleading.

## Loop A — Device management and health (v0.1)
`Admin approves policy → pairs device → Agent authenticates → heartbeats/health/SQLite backlog → server stores → WebSocket status hint → Monitoring Center fetches REST → alert or diagnosis → recover/revoke → audit admin action`

**Closure criteria:** managed Windows + selected Pop!_OS device display distinct identities and online/stale/offline state; unsupported/denied collectors are not shown as healthy; a temporary LAN outage triggers a bounded queue and duplicate-safe replay; unauthorized dashboard views/subscriptions are denied. A `Monitoring Center` is a **dedicated v0.1 screen**, not merely the homepage. No realtime screen images in v0.1.

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
| Identity, registration, heartbeat, online/offline, Monitoring Center | v0.1 | **Designed, not implemented** |
| Offline replay and event ingestion | v0.1 | **Designed, not implemented** |
| Audit sources, timeline, searches and retention | v0.2 | **Designed, not implemented** |
| Recording permission, segmented upload | v0.3 | **Designed, not implemented** |
| Recording catalog, approval, indexed playback, deletion | v0.3 | **Needs center/UI/API contract implementation and real-device manual verification** |
| Live screen streaming | Not scheduled | **Out of current scope; separate authorization design required** |
| Full security/production hardening | v1.0 | **Deferred; unit-only CI insufficient** |

## Gate against overstated completeness
A `100% connected` KPI cannot replace collector coverage. A successful recording upload cannot replace playback authorization, segment integrity or deletion. Any screenshot in [visual gallery](../visuals/README.md) is a **concept illustration with fictitious data**, not a shipped UI.

See [Monitoring Center specification](../development/monitoring-center.md), [Recording & Playback Center](../development/recording-playback-center.md), [Roadmap](../planning/roadmap.md) and [manual acceptance](../testing/manual-acceptance.md).
