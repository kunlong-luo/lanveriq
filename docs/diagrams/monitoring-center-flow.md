# D-17 — Device monitoring center closure (V0.1)

~~~mermaid
flowchart LR
  A["Admin authorizes and enrolls device"]
  B["Rust Agent: credential + heartbeat + inventory"]
  API["Spring Boot: authenticate, validate, RBAC"]
  C[("PostgreSQL: device, inventory, health")]
  D["Spring after-commit WSS notification"]
  E["Monitoring Center: React"]
  F["Scope-filtered REST refetch"]
  G{"Connectivity or collector degraded?"}
  H["IT diagnoses and logs action"]
  I["Recover / revoke / record gap"]
  A --> B
  B -->|"HTTPS REST"| API
  API -->|"DB transaction"| C
  API -->|"Only after DB commit"| D
  D -->|"Small, scope-filtered metadata"| E
  E --> F
  F -->|"Authorized read via API"| API
  C -.->|"Committed state"| API
  F --> G
  G -->|"No"| E
  G -->|"Yes"| H
  H --> I
  I --> B
~~~

Agent identity is server-assigned UUIDv7; NIC/IP/MAC are mutable inventory, not keys. Inventory updates have an independent `inventory_revision`; dashboard WSS never contains raw MAC/IP or employee audit content. This is **device/collector health monitoring**, not a remote live-screen feed. [Monitoring Center specification](../development/monitoring-center.md).
