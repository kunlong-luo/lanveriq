# D-05 — Durable SQLite → PostgreSQL event upload

~~~mermaid
sequenceDiagram
  autonumber
  participant Source as Authorized collector
  participant Agent as Rust Agent
  participant SQL as SQLite single writer
  participant API as Spring REST API
  participant PG as PostgreSQL
  participant Hub as WS notification hub
  participant UI as Dashboard
  Source->>Agent: Versioned, allowlisted event + source cursor
  Agent->>SQL: Atomic insert event + allocate sequence + cursor
  SQL-->>Agent: Durable local COMMIT
  Agent->>SQL: Read pending batch up to 100 events / 512 KiB
  Agent->>API: HTTPS POST /devices/{id}/event-batches
  API->>PG: Validate ALL + transaction INSERT or identical dedup
  alt Entire batch accepted
    PG-->>API: COMMIT
    API-->>Agent: accepted_sequences (all, after commit)
    Agent->>SQL: Mark ONLY explicitly accepted rows
    API->>Hub: Publish scoped metadata after COMMIT
    Hub-->>UI: WSS hint: refresh authorized REST query
  else Any malformed/conflicting event
    PG-->>API: ROLLBACK (no partial ACK)
    API-->>Agent: 422 / 409
    Agent->>SQL: Retain or quarantine; report degraded state
  else Connection breaks after commit
    Note over Agent,API: Agent retains rows, retries immutable batch
    Agent->>API: Identical retry
    API-->>Agent: Same sequences acknowledged (idempotent)
  end
~~~

No partial acceptance in v0.1. WSS is not an acknowledgment channel. See [event delivery contract](../architecture/event-delivery.md).
