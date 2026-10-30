# D-15 — Dashboard navigation and data boundaries

~~~mermaid
flowchart TB
  Login["Admin sign-in / secure session"]
  Overview["Overview: devices and health — v0.1"]
  Devices["Device list — v0.1"]
  Detail["Device detail: status, queue, gaps — v0.1"]
  Enroll["Enrollment and revoke — v0.1"]
  Activity["Activity / system audit — v0.2"]
  Policy["Groups, roles, policies — v0.2"]
  Record["Recording and playback — v0.3"]
  Cases["Approved recording case — v0.3"]
  Login --> Overview
  Overview --> Devices
  Devices --> Detail
  Detail --> Enroll
  Detail -.-> Activity
  Overview -.-> Policy
  Detail -.-> Record
  Cases --> Record
  Scope["REST scopes + RBAC on every query"]
  WSS["Scoped WSS status hints"]
  Detail --> Scope
  WSS -.-> Overview
  WSS -.-> Detail
~~~

WSS updates metadata only. On connect/reconnect, the dashboard fetches its authoritative scoped REST snapshot. A user without recording approval never sees unprotected screen previews. See [dashboard UX](../development/dashboard-ux.md).
