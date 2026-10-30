# ADR-0005: HTTPS REST + polling first; WebSocket later

**Status:** Accepted as a pre-release implementation simplification | **Date:** 2026-10-30

## Decision
The **v0.1** Agent only uses authenticated HTTPS REST for enrollment, heartbeat and immutable event-batch submission. React dashboard fetches current devices and last-seen via **TanStack Query polling** (proposed 10–30 s while visible), not a WebSocket channel.

WebSocket may be introduced in v0.2+ for live notifications if measured UX needs it. Screen streaming is a separate later problem; do not silently use REST polling as a substitute for video streaming. No Redis, Kafka, MQTT, WebRTC, GraphQL or gRPC in v0.1.

## Reasons
One LAN server and 1–10 endpoints; lower implementation/debugging cost, well-understood retry behavior, easy observability, simpler reverse proxy and firewall. Existing event batches already provide transport durability; maintaining a socket adds no essential MVP function.

## Consequences
Near-real-time device display is limited by poll interval, but true availability is derived from authenticated server receipt of heartbeats, not UI connection. Polling pauses or backs off when hidden/offline. Upgrade to push only with an explicit data-flow proposal and performance evidence.

## Test
Unit tests for heartbeat timing, stale/offline derivation, pagination and query cache invalidation; small manual LAN disconnect/reconnect check.
