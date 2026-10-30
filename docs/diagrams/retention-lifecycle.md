# D-13 — Retention and secure deletion lifecycle

~~~mermaid
flowchart LR
  Origin["Allowed endpoint observation"]
  SQLite[("Local bounded SQLite outbox")]
  Ingest["Server accepts transaction"]
  PG[("PostgreSQL event / index")]
  Media[("Video media — optional v0.3")]
  Policy["Approved retention schedule"]
  Delete["Batch delete / expire"]
  Audit["Deletion result + admin audit"]
  Backups["Controlled backup expiry"]
  Origin --> SQLite
  SQLite -->|"HTTPS + durable ACK"| Ingest
  Ingest --> PG
  Ingest -.-> Media
  Policy --> Delete
  PG --> Delete
  Media -.-> Delete
  Delete --> Audit
  Backups --> Audit
~~~

Retention times are **policy proposals** (e.g. event 30 days, video 14 days), not laws or approved production defaults. Backup expiry and authorized legal holds need documented separate controls; deletion should be verifiable without indefinite hidden copies. See [retention policy](../security/data-retention.md).
