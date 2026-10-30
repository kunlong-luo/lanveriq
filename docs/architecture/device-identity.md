# Device identity and replacement policy

**Status:** v0.1 design, not implemented. **Canonical field:** PostgreSQL `devices.id UUID PRIMARY KEY DEFAULT uuidv7()`. REST/JSON name: `device_id`. The server **creates** this value at successful enrollment; the Agent persists the returned value and its own key, and presents it on every authenticated heartbeat/event batch.

PostgreSQL 18 provides `uuidv7()` natively: https://www.postgresql.org/docs/18/functions-uuid.html . RFC 9562 defines UUIDv7: https://www.rfc-editor.org/rfc/rfc9562.html . UUIDv7 is time-sortable but **not** secret, a hardware fingerprint, a proof of possession or an access token. Never authorize a request just because its path contains a known device ID.

## Mature ID generation — no custom generator
PostgreSQL 18's built-in `uuidv7()` is the authoritative generator for `devices.id`; on the Java side use the standard JDK `java.util.UUID` and **verify the JPA mapping of a database-generated UUID** on a real PG18 database before implementation. Agent-generated event/snapshot IDs use the maintained Rust [`uuid` crate](https://docs.rs/uuid/latest/uuid/) (`v7` or `v4` as appropriate), not a homegrown UUID generator. Browser-only transient IDs can use secure-context Web Crypto `crypto.randomUUID()` (v4) rather than another npm package. UUIDs are statistically unique identifiers, not guarantees against collision or cryptographic credentials: maintain PK/UNIQUE, authenticated scope and immutable event-content checks. See [reuse-first policy](../development/reuse-first-policy.md).

## Identity fields
| Field | Scope and invariants |
|---|---|
| `devices.id` / `device_id` | **Server-generated UUIDv7**, primary key, immutable for one enrolled Agent identity. Kept across service restarts and normal software upgrades |
| `devices.hostname` | Human-readable changing hostname, **not** unique |
| `devices.device_label` | Editable display label, **not** unique |
| `devices.asset_tag` | Optional organization asset identifier, validated and unique **within the chosen organizational scope** only when supplied; not an authentication secret |
| `devices.replaces_device_id` | Nullable FK to historic device ID; an **audited administrator-approved** replacement link, not a merge of event histories |
| `device_credentials.public_key_thumbprint` | Device key binding; active credentials cannot be shared across devices; rotate under authenticated recovery |
| `endpoint_events.device_id` | FK to `devices.id`; together with `device_sequence`, unique event delivery key |
| `recording_sessions.device_id` | FK for authorized sessions; retaining recording/permission scope |
| `heartbeats.device_id` | FK for identity-scoped health history |

## Identity lifecycle
1. Authorized installer verifies TLS server certificate and accepted notice; Agent generates host-protected asymmetric key.
2. Server atomically consumes a one-use enrollment token and inserts `devices.id = uuidv7()`, a credential binding and policy version.
3. Agent stores the `device_id` in protected SQLite state and the private key in OS-protected storage. Both are required for authenticated uploads.
4. OS reboot, agent service restart, IP/DHCP/MAC change, hostname rename and **ordinary version upgrades** keep the same device identity.
5. Key rotation is authorized and audited under the **same device_id** only when the existing identity can be proved/recovered by the defined process.
6. Loss of local key or local SQLite identity is treated as a **new enrollment**, creating a new UUIDv7. The old identity is revoked/retired. An administrator may link the old identity via `replaces_device_id` after checking the asset; never silently stitch events or reuse prior `device_sequence` numbers.
7. Endpoint cloning, VM snapshots, and copying agent state are not supported shortcuts: if the same credential/identity appears from competing endpoints, **quarantine and require re-enrollment**, rather than overwriting logs.

## Inventory is not identity
IP addresses, MAC addresses, CPU, RAM, disks, OS build, GPU, host model and hostname **are recorded as approved IT inventory properties**, not as device keys. Their collection frequency, multi-NIC schema and privacy rules are in [Hardware/Network Inventory](device-inventory.md). Hardware serial/TPM/OS machine identifiers stay **off by default** unless a separate approved asset reconciliation scope requires them.

## Never use as a primary identity
MAC, IP, BIOS/motherboard serial, disk serial, TPM EK, CPU ID, OS machine-id, Windows SID or hostname: not consistently available/unique, may change, may disclose hardware details. If an enterprise truly needs physical-asset reconciliation, use a **separately approved** asset inventory workflow rather than collecting hardware fingerprints by default.

## Database and API rules
- Only the server assigns `devices.id`; every device API path, authenticated credential and body `device_id` must agree.
- Queries always authorize on **role + device group + data category/time**, and UUID knowledge confers no rights.
- `devices.replaces_device_id` must refer to a different historic device, should prevent replacement cycles, and changes are logged in `admin_access_log`.
- Device asset tags and hostnames are metadata; mask/remove sensitive values from public examples.
- Test collision/re-enrollment, key reuse, clone detection, rollback, device revocation and cross-device access denial.

**Canonical refs:** [Inventory](device-inventory.md) · [Enrollment](enrollment-bootstrap.md) · [Data model](data-model.md) · [Event delivery](event-delivery.md) · [RBAC](../security/authorization-matrix.md).
