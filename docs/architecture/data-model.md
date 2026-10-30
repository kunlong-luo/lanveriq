# Storage model and invariants

**Status:** Conceptual schema, not executable migrations. PostgreSQL is the sole authoritative server database; Rust endpoints use local SQLite and persist only bounded delivery state. Media goes to local disk/NAS.

## PostgreSQL entities
| Entity | Key fields / constraints | Purpose |
|---|---|---|
| users | id UUID PK, normalized login UNIQUE, password_hash or external identity, disabled_at | Administrators |
| roles | id PK, code UNIQUE | Role definitions |
| user_role_scopes | user_id, role_id, device_group_id, UNIQUE composite | Least-privilege device groups |
| device_groups | id, name, policy_id | Authorization scope |
| devices | id UUID PK, group_id, device_label, os_family, enrollment_state, last_seen_at, revoked_at | Registered managed endpoints |
| device_credentials | id, device_id, public_key_thumbprint, issued_at, expires_at, revoked_at | Rotation and revocation |
| enrollment_tokens | id, hash UNIQUE, group_id, expires_at, consumed_at, created_by | Expiring single-use tokens; never persist plaintext |
| heartbeats | device_id, received_at, agent_version, capabilities_json, queue_depth, errors_json | Small, retained health history |
| activity_intervals | id, device_id, event_id UNIQUE, kind, app_id, started_at, ended_at, received_at, scope_version | Allowlisted application/idle metadata |
| audit_events | id, device_id, device_sequence, source, event_type, occurred_at, received_at, schema_version, payload_json; UNIQUE(device_id, device_sequence) | Idempotent event audit |
| collection_gaps | id, device_id, source, period, reason, detected_at, reported_at | Incomplete collection accounting |
| recording_sessions | id, device_id, policy_version, started_at, ended_at, state | Disclosed recording sessions (later) |
| recording_segments | id, session_id, begin_at, end_at, object_key UNIQUE, sha256, bytes, codec, state | Video index, not BLOB |
| review_cases | id, reason, owner_id, approved_by, expires_at, device_id, time_range | Playback authorization |
| admin_access_log | id, principal_id, resource_id, action, outcome, recorded_at, reason, request_id | All data viewing/exports/policy changes |
| retention_policies | id, group_id, data_class, days, max_bytes | Scoped retention |

## Database guarantees
- All IDs server-generated or checked; client event IDs have size/format constraints.
- Server receive time is authoritative for visibility/retention decisions. Client event time is untrusted and accompanied by clock drift and source.
- Unique device sequence and event ID allow safe duplicate retries. Acknowledgment happens **after** commit; server transaction is single source of truth.
- Device credentials, token secrets, or raw screen contents are never stored inside event payloads.
- Media paths are opaque server-generated IDs; disallow path traversal and arbitrary client chosen paths.
- Referential integrity and explicit migrations via Flyway. PostgreSQL JSONB only for allowlisted, versioned event detail—not a dumping ground for raw data.
- Index likely query patterns: (device_id, received_at DESC), (device_id, occurred_at), (source, event_type, received_at); confirm with EXPLAIN, avoid speculative over-indexing.
- Retention cleanup runs in bounded batches with access logs; database/index backup lifecycle documented separately.

## Agent SQLite entities
Refer to [Agent Persistence](agent-persistence.md). Store `outbox_events`, `collector_cursors`, `collection_gaps`, `upload_batches`, `schema_migrations`, optional `recording_manifests`. Do not duplicate central RBAC tables locally.

## Migration policy
Every schema change carries a forward Flyway migration and tests; avoid destructive renames without rollout and recovery. Do not use auto-schema mutation in production. Restore from a verified pre-migration backup if rollback cannot be safely expressed.
