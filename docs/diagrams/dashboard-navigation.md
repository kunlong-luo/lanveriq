# D-15 — Dashboard navigation and authorization boundaries (planned)

~~~mermaid
flowchart TB
  Login["Admin sign-in: session + role/group scope"]
  Overview["Overview: fleet KPIs (V0.1)"]
  Monitor["Monitoring Center: heartbeat, collectors, backlog (V0.1)"]
  Devices["Device inventory + group scope (V0.1)"]
  Detail["Device detail: identity, CPU/RAM/disks, NIC/IP/MAC (V0.1)"]
  Enroll["Enrollment / revoke (V0.1)"]
  Audit["Audit Center: allowlisted events and timeline (V0.2)"]
  Record["Recording & Playback Center: catalog (V0.3)"]
  Cases["Review cases: separate approval (V0.3)"]
  Play["Case-scoped, audited playback (V0.3)"]
  Admin["Policies, groups, roles, access log"]
  REST["Spring REST: authoritative + RBAC on each request"]
  WS["WSS: scoped small status hints, reconnect then REST"]
  Login --> Overview
  Overview --> Monitor
  Monitor --> Devices
  Devices --> Detail
  Detail --> Enroll
  Detail -.-> Audit
  Audit -.-> Record
  Record --> Cases
  Cases -->|"Valid grant only"| Play
  Overview --> Admin
  Detail --> REST
  Monitor --> REST
  Audit --> REST
  Record --> REST
  Play --> REST
  WS -.-> Monitor
  WS -.-> Overview
~~~

**Monitoring Center** means device and source health; it does **not** stream employee screens. V0.3 media requires separate OS/user-session capture permission and **case-approved read authorization**. The server checks role, group and case on every sensitive request. After WSS subscription acceptance, the dashboard fetches a fresh scoped REST snapshot. See [dashboard UX](../development/dashboard-ux.md), [Monitoring Center](../development/monitoring-center.md), [Recording & Playback Center](../development/recording-playback-center.md).
