# D-11 — Connectivity versus collection coverage

~~~mermaid
flowchart LR
  HB["Last authenticated server-received heartbeat"]
  Age{"Age since receipt"}
  Online["online ≤90s"]
  Stale["stale >90s, ≤300s"]
  Offline["offline >300s"]
  Revoked["revoked credential (separate)"]
  Source["Each collector"]
  Supported{"Capability/permission"}
  Collecting["collecting"]
  Missing["unsupported / denied / degraded"]
  Coverage["coverage: observed / partial / gap / unknown"]
  HB --> Age
  Age --> Online
  Age --> Stale
  Age --> Offline
  HB -.-> Revoked
  Source --> Supported
  Supported -->|OK| Collecting
  Supported -->|Not available| Missing
  Collecting --> Coverage
  Missing --> Coverage
~~~

An online device can have a stopped collector or no permitted recording. A missing event does not imply employee inactivity. Time thresholds are *proposed defaults* and server-side timestamps rule. See [health semantics](../architecture/health-and-gap-semantics.md).
