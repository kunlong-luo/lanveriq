# D-09 — PostgreSQL logical entity relationships

~~~mermaid
erDiagram
  USERS ||--o{ USER_ROLE_SCOPES : holds
  ROLES ||--o{ USER_ROLE_SCOPES : grants
  DEVICE_GROUPS ||--o{ USER_ROLE_SCOPES : scopes
  DEVICE_GROUPS ||--o{ DEVICES : contains
  DEVICE_GROUPS ||--o{ ENROLLMENT_TOKENS : enrolls_to
  DEVICES ||--o{ DEVICE_CREDENTIALS : uses
  DEVICES ||--o{ HEARTBEATS : sends
  DEVICES ||--o{ ENDPOINT_EVENTS : produces
  ENDPOINT_EVENTS ||--o| ACTIVITY_INTERVALS : projects
  ENDPOINT_EVENTS ||--o| COLLECTION_GAPS : may_project
  DEVICES ||--o{ COLLECTION_GAPS : observed_for
  DEVICES ||--o{ RECORDING_SESSIONS : records
  RECORDING_SESSIONS ||--o{ RECORDING_SEGMENTS : includes
  DEVICES ||--o{ REVIEW_CASES : scoped_to
  USERS ||--o{ REVIEW_CASES : requests
  USERS ||--o{ ADMIN_ACCESS_LOG : creates
  DEVICE_GROUPS ||--o{ RETENTION_POLICIES : defines

  USERS {
    uuid id PK
    string login
    string password_hash
  }
  DEVICE_GROUPS {
    uuid id PK
    string name
  }
  DEVICES {
    uuid id PK
    uuid group_id FK
    string os_family
    datetime last_seen_at
  }
  ENDPOINT_EVENTS {
    uuid id PK
    uuid device_id FK
    uuid event_id
    bigint device_sequence
    string event_type
    datetime occurred_at
    datetime received_at
    string content_digest
  }
  ACTIVITY_INTERVALS {
    uuid id PK
    uuid event_id FK
    datetime started_at
    datetime ended_at
  }
  COLLECTION_GAPS {
    uuid id PK
    uuid device_id FK
    datetime range_start
    datetime range_end
  }
  RECORDING_SESSIONS {
    uuid id PK
    uuid device_id FK
    string state
  }
  RECORDING_SEGMENTS {
    uuid id PK
    uuid session_id FK
    string object_key
    string sha256
  }
~~~

**Invariants:** `UNIQUE(device_id, device_sequence)` and `UNIQUE(device_id, event_id)`; derived activity rows come from canonical `endpoint_events`; do not store video as PostgreSQL BLOBs. The drawing is conceptual; complete constraints, access-log details and later tables are in [data model](../architecture/data-model.md).
