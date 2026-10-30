# Event delivery contract — v0.1

**Status:** proposed architecture; no implementation. This document is the canonical design for endpoint sequence numbers, acknowledgments, replay and event/collector gaps. See [API](api-contract.md), [data model](data-model.md) and [Agent persistence](agent-persistence.md).

## Identity and event model
- An enrollment creates a unique **device_id** and local persisted monotonic **device_sequence** counter starting at 1; the server does not infer hardware identity from serial number, MAC or fingerprint.
- **Reinstall with lost device key or SQLite DB = new enrollment and new device_id.** Do not reuse old sequences under the old credential. Preserve old device as revoked/historic and permit an authorized human to link it to a new record as a replacement, without merging evidence.
- **Generate `event_id` once** in the Agent using the maintained Rust [`uuid` crate](https://docs.rs/uuid/latest/uuid/) (prefer v7 for sortable IDs; enable the `v7` feature). Persist alongside event/cursor/sequence **before upload**, and reuse exactly that ID on every retry; never call a UUID generator on a retried event. UUID uniqueness is backed by PostgreSQL unique constraints and immutable payload fingerprint, not an absolute guarantee.
- One canonical event envelope is used for both audit and application-activity sources: `event_id` UUID, `device_sequence` int64 positive, `schema_version` int, `source` enum, `event_type` enum, `occurred_at` UTC, `payload` allowlisted object, `policy_version`. No distinct `kind` field in the wire contract.
- Application activity is ingested as a canonical event and, when needed, projected into a read-only `activity_intervals` query table. **Do not independently upload the same interval to a second endpoint.**
- `collection_gap` is a first-class canonical event; a matching query projection may be maintained. A lack of events is not proof of a user being idle.

## Agent persistence invariants
1. A collector emits an event and its source cursor; the single SQLite writer atomically persists the event, reserves the next sequence number, and commits the cursor. If the transaction fails, no cursor is advanced.
2. Local sequences must never be reused while a device_id is active, including after rollback or restart. `next_sequence` lives in the same transactional store.
3. Uploader chooses a stable batch of at most **100 events and 512 KiB** (draft). Local batches use deterministic order by sequence. All event IDs and payloads remain immutable between retries.
4. The server validates entire batch; rejects malformed/scope-violating batches **without committing a subset**. It inserts new events and treats matching duplicates as already accepted in **one PostgreSQL transaction**.
5. The server responds with all accepted sequences **after commit**. Existing identical records are acknowledged again, making timeout/retry safe. A conflicting reused sequence produces `409 sequence_conflict`; agent quarantines the batch and reports degraded health instead of overwriting or dropping.
6. Agent deletes/marks individual acknowledged rows **only for explicitly acknowledged sequences**, within a SQLite transaction. Do not use a single `last_ack_sequence` to erase unacknowledged lower-numbered rows.
7. On a 5xx, network timeout or 429, retain all rows and retry with bounded exponential backoff and jitter; respect `Retry-After`. On 401/403 pause upload and surface enrollment/revocation status; on 400/422 quarantine and report, no busy retry loop.

## Atomic batch behavior
There are **no partial acknowledgments in v0.1**. A valid batch either commits/acknowledges every event (including identical duplicates), or commits none. This deliberately simplifies endpoint recovery and PostgreSQL dedup. Conflicts with a different payload are rejected for the entire batch. Changing this requires a versioned ADR and updated unit tests.

## Gap and quota behavior
- Reserve a small bounded emergency space for loss/gap metadata. Before dropping ordinary events due to quota, persist a `collection_gap` describing source, interval and reason where possible.
- If SQLite itself is unwriteable, heartbeat carries `integrity_degraded` with affected source/time; server records an observed server-side gap inferred from heartbeat timestamps, **not fabricated detailed activity**. Repair must be explicit.
- Keep `oldest_pending_at`, `queued_bytes`, `dropped_count` and `last_successful_upload_at` in heartbeat so operators can distinguish connection failure from source failure.

## Server invariants
- A server-authenticated device credential must match path `device_id`, body `device_id` and allowed policy. Enforce payload schema, per-batch cap, source allowlist and timestamp drift handling.
- PostgreSQL unique `(device_id, device_sequence)` and unique `(device_id, event_id)` prevent conflicting identities. Store immutable canonical event fingerprint/digest to distinguish identical retry vs mutated retry.
- `received_at` is stamped server-side and used for retention; `occurred_at` is client-provided evidence with clock-offset uncertainty. Keep both.
- Event queries order by `received_at, event_id` for cursor pagination; activity displays `occurred_at` with explicit timezone and late-arrival labeling.

## Required unit tests
Crash between event and cursor, crash after commit before upload, duplicate same sequence/same content, same sequence/different content, duplicate event_id/different sequence, server commit + response loss, 429 retry, out-of-order batches, ack for noncontiguous rows, reinstall new identity, quota gap and DB read-only.

## Sequence diagram
~~~mermaid
sequenceDiagram
  participant O as OS collector
  participant L as SQLite single writer
  participant U as Rust uploader
  participant S as Spring API
  participant P as PostgreSQL
  O->>L: event + source cursor
  L->>L: atomic event + sequence + cursor commit
  U->>L: read pending ordered batch
  U->>S: authenticated POST event batch
  S->>P: validate + transactional insert/dedup
  P-->>S: COMMIT
  S-->>U: accepted_sequences (all)
  U->>L: mark explicit acks and prune
  Note over U,S: lost response => retry same immutable events
~~~
