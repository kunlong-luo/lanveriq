# D-14 — Agent SQLite logical model

~~~mermaid
erDiagram
  AGENT_STATE ||--o{ OUTBOX_EVENTS : allocates_sequences
  COLLECTOR_CURSORS ||--o{ OUTBOX_EVENTS : emits
  OUTBOX_EVENTS }o--o{ UPLOAD_BATCHES : selected_by
  AGENT_STATE ||--o{ COLLECTION_GAPS : records
  AGENT_STATE ||--o| INVENTORY_PENDING : has_latest
  AGENT_STATE ||--o{ RECORDING_MANIFESTS : later

  AGENT_STATE {
    string device_id
    bigint next_sequence
    bigint next_inventory_revision
    integer policy_version
    integer schema_version
  }
  OUTBOX_EVENTS {
    string event_id PK
    bigint device_sequence UK
    string source
    string event_type
    string occurred_at_utc
    string payload_json
    string acked_at_utc
  }
  COLLECTOR_CURSORS {
    string source PK
    string cursor_blob
    bigint last_event_sequence
  }
  UPLOAD_BATCHES {
    string id PK
    bigint first_sequence
    bigint last_sequence
    int attempts
  }
  INVENTORY_PENDING {
    string snapshot_id PK
    bigint inventory_revision
    string inventory_digest
    string snapshot_json
  }
  COLLECTION_GAPS {
    string id PK
    string source
    string reason
  }
  RECORDING_MANIFESTS {
    string segment_id PK
    string local_path
    string sha256
  }
~~~

**Conceptual relationships only:** the design does not require an actual FK from each outbox row to a source cursor, nor a strict many-to-many join table between batches/events. The exact schema and types will be finalized in Rust SQLite migrations. A single writer atomically commits new events, monotonic sequence numbers and source cursor. Inventory_pending is a **separate latest-state snapshot**, not an audit event. Its revision is allocated by the same SQLite writer; older inventory ACK cannot clear newer pending data. The video manifest table is **v0.3 only**. See [Agent storage](../architecture/agent-persistence.md).
