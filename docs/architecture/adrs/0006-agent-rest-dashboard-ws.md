# ADR-0006: Split durable Agent data plane from live dashboard notifications

**Status:** Accepted design direction | **Date:** 2026-10-30

## Decision
Separate traffic and guarantees:
1. **Rust Agent ↔ server:** authenticated HTTPS REST, bounded SQLite outbox, immutable device sequences, whole-batch PostgreSQL transaction and explicit acknowledgments. Retries are safe and durable.
2. **React dashboard ↔ server:** REST for authority and actions; WSS for ephemeral, filtered, tiny status notifications. TanStack Query refetches based on notifications and reconnects.
3. **Recording (v0.3, independently approved):** authorized user-session capture and authenticated HTTPS chunk/segment upload; WSS only notifies eligible reviewers about session state, not media itself.

## Why
Transport is chosen by delivery semantics, not by novelty. Long-lived sockets do not improve transaction correctness of event batches, and streaming video in admin WSS would expand privacy exposure and message memory. Keeping the Agent protocol stable reduces endpoint update burden.

## Constraints
No arbitrary remote command channel, raw screen frames, secret logging or covert monitoring. RBAC for every WS subscriber and read; same-origin upgrade checks; WSS only; off-line status remains server derived.

## Revisit
If measured fleet size, push volume or interactive needs change, evaluate dedicated messaging and media transports in a new ADR, with versioned compatibility and security review.
