# D-17 — Device monitoring center closure (V0.1)

~~~mermaid
flowchart LR
  A["Admin enrolls authorized device"]
  B["Rust Agent: credential + heartbeat"]
  C[("PostgreSQL device / health")]
  D["Spring after-commit WSS hint"]
  E["Monitoring Center: React"]
  F["Scoped REST refetch"]
  G{"Connectivity / source healthy?"}
  H["Operator diagnostics + alert audit"]
  I["Recover / revoke / mark gap"]
  A --> B
  B -->|"Authenticated HTTPS REST"| C
  C --> D
  D -->|"Small metadata push"| E
  E --> F
  F --> G
  G -->|"Yes"| E
  G -->|"No"| H
  H --> I
  I --> B
~~~

This is **device/collection health monitoring**, not a remote live-screen feed. All queries and WSS subscriptions are role/device-group scoped. Browser resyncs REST after subscription; source gaps are distinct from a missing heartbeat. [Monitoring Center UI spec](../development/monitoring-center.md).
