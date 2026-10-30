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
  alt Valid batch and transaction commits
    PG-->>API: COMMIT
    API->>Hub: Publish scoped metadata AFTER COMMIT
    Hub-->>UI: WSS hint: refetch authorized REST query
    alt Response reaches Agent
      API-->>Agent: accepted_sequences (all)
      Agent->>SQL: Mark ONLY explicitly acknowledged rows
    else HTTP response lost after server commit
      Note over Agent,API: Pending rows remain; retry exact immutable batch
      Agent->>API: Identical retry
      API->>PG: Check duplicates / immutable fingerprints
      PG-->>API: Already committed; no new rows
      API-->>Agent: accepted_sequences (same events)
      Agent->>SQL: Mark explicitly acknowledged rows
    end
  else Malformed batch or conflicting event
    PG-->>API: ROLLBACK (no partial commit)
    API-->>Agent: 422 / 409
    Agent->>SQL: Retain or quarantine; report degraded state
  end
~~~

No partial acceptance in v0.1. WSS is not an acknowledgment channel. See [event delivery contract](../architecture/event-delivery.md).
