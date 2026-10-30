# D-20 — Hardware/network data collection and refresh

~~~mermaid
sequenceDiagram
  autonumber
  participant OS as Windows 11 / Pop!_OS APIs
  participant Agent as Authorized Rust Agent
  participant API as Spring Boot REST
  participant DB as PostgreSQL
  participant WS as Scoped WS notifier
  participant UI as Device details / Monitoring Center
  OS->>Agent: Read allowed OS, CPU, RAM, disk, NIC, IP/MAC
  Agent->>Agent: Normalize, redact, validate, compare snapshot digest
  opt Changed snapshot, startup or 24h reconciliation
    Agent->>Agent: Allocate inventory_revision and persist pending SQLite snapshot atomically
    Agent->>API: HTTPS POST /devices/{id}/inventory
    API->>API: Validate credential, ID, schema, bounds
    API->>DB: Upsert only if inventory_revision is newer; atomic NIC/IP/volumes
    DB-->>API: COMMIT (or identical retry)
    API-->>Agent: Accepted / identical replay / stale_ignored
    Agent->>Agent: Clear only matching pending revision
    API->>WS: Optional device.inventory.changed AFTER COMMIT
    WS-->>UI: Small notification, no raw IP/MAC
    UI->>API: Refetch scoped REST inventory
  end
  loop About 30s heartbeat
    Agent->>API: Health/queue, latest 60–120s metric summary
    API->>DB: Bounded metric sampling + received heartbeat time
    DB-->>API: Commit
  end
~~~

This stream is **not** the audit event outbox; inventory snapshots use a persisted independent `inventory_revision` plus snapshot ID/hash. Retry after outage uses the same snapshot; late old revisions cannot overwrite the server's current inventory and must not consume the event `device_sequence` counter. Multiple interfaces/addresses and actual source states are supported; no serial/fingerprint default. The UI does not infer identity from IP/MAC. [Inventory contract](../architecture/device-inventory.md).
