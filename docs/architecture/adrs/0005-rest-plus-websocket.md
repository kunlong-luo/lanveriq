# ADR-0005: REST + WebSocket from v0.1

**Status:** Accepted architectural direction, supersedes the removed draft `0005-http-first.md` | **Date:** 2026-10-30

## Decision
Use **HTTPS REST + WSS** for the React dashboard in the **first MVP**. REST owns initial snapshots, searches, mutations, policy, admin and event queries. A **same-origin authenticated WSS** connection sends small, scoped, best-effort status notifications and invalidates React Query cache.

Retain HTTPS REST for Rust Agent enrollment, heartbeats, event batches and later media uploads. Do not transfer durable event acknowledgments to WSS. Use native Spring Boot WebSocket support and an **in-process after-commit dispatcher** for the one-server, 1–10 endpoint scope. Do not add a broker, STOMP, Redis Pub/Sub, Kafka or microservices. **Maintainer addendum (2026-11-01):** an opt-in standalone Redis client is allowed for transient best-effort refetch hints and dependency health, but is **not** used for dashboard WebSocket delivery or durable event storage.

## Rationale
The user wants an immediate LAN dashboard and WSS is manageable for a single node. It removes unnecessary polling delay, keeps endpoint fault recovery on the existing transactional HTTP path, and avoids introducing another persistent connection per endpoint.

## Consequences
- Requires authentication of upgrade requests and strict Origin/session/RBAC/device-scope checks.
- WSS messages are **not durable**. On reconnect clients refetch authorized REST state; **even without a disconnect, visible-page low-frequency REST reconciliation and tab-resume refetch close the indefinitely stale view risk**. No missed-event replay contract.
- Offline transitions require a server-side heartbeat-expiry detector, not simply forwarding incoming heartbeats.
- v0.1 UI acceptance must cover messages, subscription/snapshot race, **silent lost hint with a still-connected socket**, reconnect/refetch, access control and error states. **2026-11-08 update:** L1 tests on relevant pushes; L2 integration at business-batch boundary; full Chromium/WSS L3 at explicit `acceptance/v0.1-*` milestone/dispatch. The actual authorized WSS business loop remains unimplemented; synthetic browser checks for other features do not prove WSS readiness.
- Future multi-instance deployments will require a deliberate dispatcher architecture change; not needed for v0.1.

## Contract and visuals
[WebSocket protocol](../websocket-contract.md) · [ADR-0006](0006-agent-rest-dashboard-ws.md) · [WebSocket flow](../../diagrams/websocket-flow.md).
