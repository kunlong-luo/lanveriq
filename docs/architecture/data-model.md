# Storage model and invariants

**Status:** Conceptual schema, not executable migrations. PostgreSQL is the sole authoritative server database; Rust endpoints use local SQLite and persist only bounded delivery state. Media goes to local disk/NAS.

## PostgreSQL entities
| Entity | Key fields / constraints | Purpose |
|---|---|---|
| users | id UUID PK, normalized login UNIQUE, password_hash or external identity, disabled_at | Administrators |
| roles | id PK, code UNIQUE | Role definitions |
| user_role_scopes | user_id, role_id, device_group_id, UNIQUE composite | Least-privilege device groups |
| device_groups | id, name, policy_id | Authorization scope |
| devices | **id UUID PK DEFAULT uuidv7()**, group_id, hostname, device_label, asset_tag nullable, replaces_device_id nullable FK, os_family, enrollment_state, last_seen_at, revoked_at | Canonical Agent enrollment identity; optional audited physical replacement link |
| device_credentials | id, device_id, public_key_thumbprint, issued_at, expires_at, revoked_at | Rotation and revocation |
| enrollment_tokens | id, hash UNIQUE, group_id, expires_at, consumed_at, created_by | Expiring single-use tokens; never persist plaintext |
| heartbeats | device_id, received_at, agent_version, capabilities_json, queue_depth, errors_json, latest sampled metrics | Small, retained health history; not full hardware inventory |
| device_inventory_current | device_id PK/FK, schema_version, snapshot_id, inventory_digest, observed_at, received_at, typed OS/CPU/RAM fields | One **current** authorized hardware/OS snapshot per device |
| device_network_interfaces | (device_id, interface_key) PK, name, kind, nullable MAC, virtual flag, link_state | Multiple actual adapters, MAC **not an identity** |
| device_interface_addresses | device_id, interface_key, ip_with_prefix `inet`, scope | Zero or many IPv4/IPv6 addresses per interface |
| device_volumes | (device_id, volume_key) PK, mount_point, filesystem, total_bytes, free_bytes | Local mounted volume inventory and capacity |
| device_metric_samples | device_id, sampled_at, received_at, cpu_percent, memory_used_bytes, disk summary; UNIQUE(device_id, sampled_at) | Bounded host performance history, not a full inventory per heartbeat |
| device_connection_observations | device_id, server_observed_ip `inet`, received_at | Distinguish server-observed remote IP from agent-reported local NIC addresses |
| activity_intervals | id, device_id, event_id UNIQUE references endpoint_events, activity_kind, app_id, started_at, ended_at, received_at, policy_version | Derived query projection from canonical events, not independently uploaded |
| endpoint_events | id, device_id, device_sequence, event_id, source, event_type, occurred_at, received_at, schema_version, policy_version, payload_json, content_digest; UNIQUE(device_id, device_sequence), UNIQUE(device_id, event_id) | **Canonical immutable ingested events** (audit, activity and gap) |
| collection_gaps | id, device_id, event_id nullable references endpoint_events, source, range_start, range_end, reason, detected_at, received_at, inferred_by_server | Derived or server-inferred coverage gaps; not proof of user activity |
| recording_sessions | id, device_id, policy_version, started_at, ended_at, state | Disclosed recording sessions (later) |
| recording_segments | id, session_id, begin_at, end_at, object_key UNIQUE, sha256, bytes, codec, state | Video index, not BLOB |
| review_cases | id, reason, owner_id, approved_by, expires_at, device_id, time_range | Playback authorization |
| admin_access_log | id, principal_id, resource_id, action, outcome, recorded_at, reason, request_id | All data viewing/exports/policy changes |
| retention_policies | id, group_id, data_class, days, max_bytes | Scoped retention |

## Hardware and network inventory
The authoritative [Device inventory specification](device-inventory.md) defines collection fields, source, sample frequency, bounded payloads and retention. **Canonical `devices.id` is a server-assigned UUIDv7**, not a MAC, IP or hostname. A computer can have many NICs, IP addresses and volumes; therefore these are child records rather than one `ip`/`mac` column. Inventory upserts are **atomic and idempotent with snapshot ID + digest** and distinct from durable `endpoint_events` event batches. Metrics samples are bounded and may be transmitted in heartbeat summaries; current inventory and history have different retention.

## Database guarantees
- All IDs server-generated or checked; client event IDs have size/format constraints.
- Server receive time is authoritative for visibility/retention decisions. Client event time is untrusted and accompanied by clock drift and source. A new enrollment always receives a new device_id; previous identity and evidence remain immutable historical records.
- Unique (device_id, device_sequence) and (device_id, event_id) allow safe identical retries; store immutable content digest for detecting conflicting reuse. Whole-batch **all-or-nothing** validation/commit happens before acknowledgment; server transaction is single source of truth. See [delivery contract](event-delivery.md).
- Device credentials, token secrets, or raw screen contents are never stored inside event payloads.
- Media paths are opaque server-generated IDs; disallow path traversal and arbitrary client chosen paths.
- Referential integrity and explicit migrations via Flyway. PostgreSQL JSONB only for allowlisted, versioned event detail—not a dumping ground for raw data.
- Index likely query patterns: (device_id, received_at DESC), (device_id, occurred_at), (source, event_type, received_at); confirm with EXPLAIN, avoid speculative over-indexing.
- Retention cleanup runs in bounded batches with access logs; database/index backup lifecycle documented separately. Derived activity/gap rows are deleted consistently with their parent events; no orphan projections. Sensitive queries must enforce [role and device-group scope](../security/authorization-matrix.md).

## Agent SQLite entities
Refer to [Agent Persistence](agent-persistence.md). Store `outbox_events`, `collector_cursors`, `collection_gaps`, `upload_batches`, `schema_migrations`, optional `recording_manifests`. Do not duplicate central RBAC tables locally.

## Migration policy
Every schema change carries a forward Flyway migration and tests; avoid destructive renames without rollout and recovery. Do not use auto-schema mutation in production. Restore from a verified pre-migration backup if rollback cannot be safely expressed.
