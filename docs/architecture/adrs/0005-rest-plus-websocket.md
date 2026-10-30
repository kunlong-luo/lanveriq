# ADR-0005: REST + WebSocket from v0.1

**Status:** Accepted architectural direction, supersedes the removed draft `0005-http-first.md` | **Date:** 2026-10-30

## Decision
Use **HTTPS REST + WSS** for the React dashboard in the **first MVP**. REST owns initial snapshots, searches, mutations, policy, admin and event queries. A **same-origin authenticated WSS** connection sends small, scoped, best-effort status notifications and invalidates React Query cache.

Retain HTTPS REST for Rust Agent enrollment, heartbeats, event batches and later media uploads. Do not transfer durable event acknowledgments to WSS. Use native Spring Boot WebSocket support and an **in-process after-commit dispatcher** for the one-server, 1–10 endpoint scope. Do not add a broker, STOMP, Redis, Kafka or microservices.

## Rationale
The user wants an immediate LAN dashboard and WSS is manageable for a single node. It removes unnecessary polling delay, keeps endpoint fault recovery on the existing transactional HTTP path, and avoids introducing another persistent connection per endpoint.

## Consequences
- Requires authentication of upgrade requests and strict Origin/session/RBAC/device-scope checks.
- WSS messages are **not durable**. On reconnect clients refetch authorized REST state; **even without a disconnect, visible-page low-frequency REST reconciliation and tab-resume refetch close the indefinitely stale view risk**. No missed-event replay contract.
- Offline transitions require a server-side heartbeat-expiry detector, not simply forwarding incoming heartbeats.
- v0.1 UI tests cover messages, subscription/snapshot race, **silent lost hint with a still-connected socket**, reconnect/refetch, access control and error states. Unit-only CI stays unchanged; real-browser/LAN WSS checks are manual at MVP stage.
- Future multi-instance deployments will require a deliberate dispatcher architecture change; not needed for v0.1.

## Contract and visuals
[WebSocket protocol](../websocket-contract.md) · [ADR-0006](0006-agent-rest-dashboard-ws.md) · [WebSocket flow](../../diagrams/websocket-flow.md).
