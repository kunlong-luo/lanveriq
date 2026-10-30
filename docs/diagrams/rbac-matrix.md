# D-08 — Authorization decision and role matrix

~~~mermaid
flowchart TB
  Request["Admin request / WSS subscribe"]
  Auth{"Authenticated session?"}
  Role{"Role permits action?"}
  Group{"Device group in scope?"}
  Data{"Data class and time range allowed?"}
  Case{"Sensitive recording requires approved case?"}
  AllowAudit["Log authorized sensitive access"]
  DenyAudit["Log denied access"]
  Allow["Read minimal authorized data / subscribe"]
  Deny["Deny without revealing hidden resources"]
  Request --> Auth
  Auth -->|No| Deny
  Auth -->|Yes| Role
  Role -->|No| Deny
  Role -->|Yes| Group
  Group -->|No| Deny
  Group -->|Yes| Data
  Data -->|No| Deny
  Data -->|Yes| Case
  Case -->|Not required / valid approval| AllowAudit
  Case -->|Required but invalid| Deny
  AllowAudit --> Allow
  Deny --> DenyAudit
~~~

| Action | Security admin | IT operator | Auditor | Recording reviewer |
|---|---|---|---|---|
| View assigned device health (non-sensitive) | Allowed | Allowed | Scoped | Scoped |
| View full NIC/MAC/IP inventory | Allowed | Assigned scope only | Additional explicit grant | Denied |
| Pair/revoke device | Allowed | If delegated | Denied | Denied |
| Read detailed activity/audit | Logged scoped access | Denied by default | Scoped reason required | Denied |
| Change collection policy | With organizational approval | Denied | Denied | Denied |
| Watch/download video (v0.3) | **Approved case only** | Denied | Denied unless additionally granted | **Approved case only** |
| Export audit evidence | Logged purpose | Denied | Separately granted | Denied |
| View admin-access audit | Authorized | Denied | If separately granted | Denied |

Any access decision applies **on the server**, including WSS push audiences; browser controls are not authorization. See [permission matrix](../security/authorization-matrix.md).
