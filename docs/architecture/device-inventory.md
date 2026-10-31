# Endpoint hardware, network and operating-system inventory

**Status:** V0.1 design with **INV-01-F SQLite V5 CAS recovery guard over default-disabled INV-01-E scheduling decisions** and **INV-02 PostgreSQL V8 + JPA internal latest-state implementation**, Java unit tests/PG18 integration source and **INV-01 Rust typed local inventory, opt-in OS adapter and SQLx V3 durable, content-deduped pending snapshots**. The Rust adapter remains disconnected from the Agent service and upload; no actual Windows/Pop!_OS inventory smoke, PG18 run, live mTLS API or authorized browser read. See [INV-02 acceptance](../testing/server-inventory-manual.md). **Purpose:** help authorized IT staff locate, identify and maintain managed devices without treating a network address or hardware fingerprint as a secret identity. See [canonical device identity](device-identity.md), [data model](data-model.md), [monitoring UI](../development/monitoring-center.md), [privacy](../security/privacy-and-consent.md) and [inventory diagrams](../diagrams/device-inventory-er.md).

## One identity, many changing properties
- **Canonical key:** server-generated PostgreSQL 18 `devices.id UUID DEFAULT uuidv7()`; JSON `device_id`. Stable across reboot, renaming, IP/MAC changes and ordinary Agent upgrades.
- **Asset tag:** optional human-managed `asset_tag` identifies a **physical organization asset**. If an installation loses its key/local identity, re-enroll with **new device_id** and create audited `replaces_device_id` link after human verification. Do not silently join event histories.
- **Hostname, IP, MAC, serial and OS machine identifiers** are *attributes*, not enrollment/authentication proofs or globally unique device keys.
- Several NICs and multiple IPv4/IPv6 addresses are normal. Store **interface/address arrays or child tables**, not one string field in `devices`. DHCP, VPN, NAT, Wi-Fi MAC randomization, virtual NICs and IPv6 temporary addresses make these addresses unstable.
- Separate `agent_reported_addresses` (local interface addresses) and `server_observed_remote_ip` (trusted reverse-proxy source). They are often different, and server-observed IP must not be taken blindly from arbitrary client `X-Forwarded-For`.

## Proposed collection schema and release priority

**P0 = required inventory field when OS exposes it; `null` + capability/error is valid for unavailable or redacted data.** Storage amounts use integer **bytes**, CPU percentages 0–100, timestamps UTC with source and server received time.

| Category | Field examples (wire names) | v0.1 default | Notes |
|---|---|---|---|
| Registration | `device_id`, `device_label`, `hostname`, `device_group_id`, `enrolled_at`, `asset_tag` | P0 | ID/group from server; label/tag admin-managed |
| Manufacturer/model | `manufacturer`, `model`, `chassis_type` | P0 if available | E.g. laptop/desktop/VM; never use model as identity |
| CPU | `cpu.model`, `cpu.arch`, `cpu.physical_cores`, `cpu.logical_cores` | P0 | Physical count may be unknown; logical is more portable |
| RAM | `memory.total_bytes`, `memory.available_bytes`, `memory.used_percent` | P0 | Dynamic available values belong to **metrics**, not static inventory |
| Storage | `volumes[].key`, `mount_point`, `filesystem`, `total_bytes`, `free_bytes`, `is_system` | P0 | Local mounted volumes only by default; exclude ephemeral pseudo-filesystems and unmounted partitions |
| OS | `os.family`, `os.name`, `os.version`, `os.build`, `os.kernel_release`, `os.arch` | P0 | Windows edition/build; Linux distro and kernel; unknown fields nullable |
| Linux session | `desktop.environment`, `session_type`, `window_manager` | P0 **if detectable** | KWin/Plasma alone does **not** prove Wayland; headless service may not see a user's session → report unknown |
| Network NIC | `interfaces[].key`, `name`, `kind`, `mac_address`, `link_state`, `is_virtual` | P0 for policy-approved NICs | MAC may be absent, randomized, locally administered or spoofed; never unique |
| Network addresses | `interfaces[].addresses[].ip`, `prefix_length`, `family`, `scope` | P0 | IPv4/IPv6; suppress loopback and link-local from primary overview, but preserve only useful allowed details |
| Server-observed network | `server_observed_remote_ip`, `last_seen_at` | P0 | Server-controlled metadata from authenticated request and trusted Caddy proxy chain |
| Host metrics | `cpu_usage_percent`, `memory_used_bytes`, `uptime_seconds`, `volume_free_bytes` | P0 lightweight | Sampling intervals separate from 30-second heartbeat |
| Agent health | `agent_version`, `policy_version`, `queue_count`, `queue_bytes`, `last_upload_at`, `collector_states`, `integrity_degraded` | P0 | Heartbeat/health, not inventory fingerprint |
| GPU | `gpus[].model`, `driver_version`, `vram_bytes` | v0.2 optional | NVIDIA/Intel/AMD APIs differ; avoid mandatory vendor utilities |
| Battery | `battery.present`, `percent`, `charging` | v0.2 optional | Laptops only; `unknown` distinct from 0 |
| Security posture | `firewall_state`, `disk_encryption_state`, `update_status`, `antimalware_state` | v0.2 optional | Device/OS-dependent; never claim protection based on missing data |
| More hardware | BIOS/UEFI version, storage health, temperature, NIC speed | v0.2+ optional | Measure value, privileges, resource cost and privacy before enabling |
| Sensitive asset identifiers | serial number, TPM/BIOS ID, Windows SID, Linux machine-id | **OFF by default** | Collect only with separate approved asset-reconciliation requirement; do not use for authentication |
| Personal/network content | logged-in username, precise Wi-Fi location/BSSID/SSID, browsing URLs, command arguments | **Not in basic inventory** | Requires a separate lawful purpose and field policy; no keylogging/clipboard/private content |

## Sampling and publication — pilot defaults (not measured)
| Data | Acquire | Upload/storage | Suggested history |
|---|---|---|---|
| Device identity | enrollment; explicit re-enrollment | current record | registration and audit lifecycle |
| CPU model, installed RAM, OS build, manufacturer/model | enrollment, Agent startup, every 24h **or on detected change** | `POST /devices/{id}/inventory`; update current inventory if changed | current value; optional 30-day change log later |
| IP/MAC/NIC and mounted volume list | startup, link/mount change, every 24h reconciliation | same inventory endpoint; current set replaces previous set atomically | current list; proposed **7-day** network-change diagnostic history only where justified |
| CPU/memory/volume free + uptime | local sampling about every **60–120s** | piggyback most recent summary in 30s heartbeat; server persists metric samples at most once per sampling period | proposed **7 days**, bounded by retention policy |
| Agent heartbeat/queue/capability | **30s** | heartbeat; server stamps receipt | existing heartbeat retention proposal 7 days |
| Security/GPU/battery extras | v0.2 on verified compatible platforms | versioned optional fields | retention decided before enabling |

**Important:** avoid uploading an unchanged full inventory with every 30-second heartbeat. A system that sends one full NIC/volume list every heartbeat creates unnecessary churn. Coalesce concurrent changes and send an inventory update when its **normalized content hash** changes, plus periodic full reconciliation.

## INV-01-E independent scheduling policy (synthetic only)

The staged Rust decision engine is **OFF by default and disconnected from the Agent lifecycle**. With an explicitly supplied clock and a verified SQLite pending flag, it selects startup/24-hour reconciliation/asset-change collection or delivery of a previously persisted UUID+revision. Asset-change signals coalesce for 30 seconds. Retryable delivery failures set a bounded 30-second-to-one-hour exponential delay with device-specific spread and an honored, bounded server retry hint. Authorization failure, stale receipt or conflicting inventory state causes a persistent pause/quarantine, not blind retry. SQLx V4 persists the small scheduler checkpoint independently of content-deduplicated V3 inventory; both are bound to the same device ID. An unchanged successful reconciliation refreshes only the schedule timestamp, not an inventory revision or upload.

This policy provides **decisions only**: no Tokio timer, process startup hook, OS notification watcher, event or heartbeat trigger, HTTP request, new runtime configuration or automatic collection has been added. Actual wiring requires separate organizational permission, authenticated server and live mTLS/OS/security acceptance. The 24-hour interval is a pilot design target rather than a measured freshness guarantee.

## INV-01-F checkpoint recovery and race protection (synthetic only)

A V5 schema extension adds positive checkpoint generation and the persistent `inventory_schedule_seen` marker. Restart recovery reads and verifies the checkpoint and pending inventory **atomically** in one local SQLite transaction, failing closed for corrupt/wrong-device records or a checkpoint deleted after first save. Mutations require the expected generation and a matching enrolled device, so a stale writer cannot reset a newer identity pause or quarantine. SQLx aborts the entire update on injected failure. None of these methods calls the OS, starts a timer or performs HTTP. A real agent driver must later coordinate authenticated ACKs with checkpoint updates across crash boundaries; replay/custody/clock-skew acceptance is still open.

## Proposed API contract (separate from event batches)
- `POST /api/v1/devices/{id}/inventory` — bound Agent credential; schema-validated snapshot with stable `snapshot_id` UUID, monotonically increasing `inventory_revision` scoped to this **device_id**, `schema_version`, `observed_at`, `hardware`, `os`, `interfaces`, `volumes`, `capabilities`. Same revision/digest is idempotent; same revision/different content produces 409; **older revisions are acknowledged as stale without overwriting the current snapshot**. It is not an `endpoint_events` batch and does **not** advance `device_sequence`.
- `GET /api/v1/devices/{id}/inventory` — authenticated role + group scope; returns server-recorded current snapshot, independent timestamps and explicit unsupported/unavailable states.
- `GET /api/v1/devices/{id}/metrics?from=&to=&limit=` — optional scoped bounded metric history. The monitoring overview should only request summary/latest, not unlimited series.
- `GET /api/v1/monitoring/devices` — latest device health and **summary** specs; keep raw MAC/address lists in the restricted device-details view.

Example (fictional, no real device identifiers):
~~~json
{
  "schema_version": 1,
  "snapshot_id": "014d9348-d2b1-44d7-a234-8e7be9838fe9",
  "inventory_revision": 17,
  "device_id": "018f9fad-5c71-7db1-b89e-b71f8d620f44",
  "observed_at": "2026-10-30T09:00:00Z",
  "hardware": {"manufacturer":"ExampleVendor","model":"ExampleBook","cpu":{"model":"Example CPU","arch":"x86_64","physical_cores":8,"logical_cores":16},"memory":{"total_bytes":34359738368}},
  "os": {"family":"linux","name":"Pop!_OS","version":"24.04","kernel_release":"example-kernel","arch":"x86_64"},
  "interfaces": [{"key":"nic-01","name":"eth0","kind":"ethernet","mac_address":"02:00:00:00:00:01","link_state":"up","is_virtual":false,"addresses":[{"ip":"192.0.2.10","prefix_length":24,"family":"ipv4","scope":"global"}]}],
  "volumes": [{"key":"root","mount_point":"/","filesystem":"ext4","total_bytes":536870912000,"free_bytes":268435456000,"is_system":true}],
  "capabilities": {"gpu_inventory":"not_collected","desktop_session":"unavailable"}
}
~~~
Example `192.0.2.10` is TEST-NET-1 documentation space, not an actual LAN address. Example NIC `02:00:00:00:00:01` is synthetic. The server records **received_at** and **server_observed_remote_ip** separately; neither comes from trusting arbitrary payload fields.

## Server database design (V8 current-state schema; additional metrics/projections remain conceptual)
- `devices`: server-owned canonical ID, group, hostname/display/asset tag, replacement link, last seen/status.
- `device_inventory_current`: `device_id` PK/FK, `schema_version`, `snapshot_id`, `inventory_revision`, `inventory_digest`, `observed_at`, `received_at`, typed common CPU/RAM/OS fields, optional validated versioned JSONB extension for OS-specific fields.
- `device_network_interfaces`: PK `(device_id, interface_key)`, name, kind, **nullable** MAC, virtual flag, link state, last observed.
- `device_interface_addresses`: FK `(device_id, interface_key)`, IP/prefix using PostgreSQL `inet`, scope, last observed. A device may have zero or many interfaces and each interface zero or many addresses.
- `device_volumes`: PK `(device_id, volume_key)`, mount, filesystem, total/free bytes and system flag; volumes can disappear without implying the device was wiped.
- `device_metric_samples`: `device_id`, `sampled_at`, `received_at`, CPU/RAM/Agent usage, bounded disk usage. `UNIQUE(device_id,sampled_at)` with idempotent duplicate handling. Time series is not the canonical inventory identity.
- `device_connection_observations` or latest heartbeat extension: server-observed source IP (PostgreSQL `inet`), server receipt time, optional trusted proxy origin; limit history by approved retention.
- Any inventory update applies to current snapshot, child NICs, addresses and volumes in **one server transaction** after validating full payload. Ambiguous retry must not produce extra inventory-change records. Use **device-scoped `inventory_revision`**, not client wall-clock timestamps, to prevent older snapshots overwriting newer current values.
- Strict bounds (proposed): payload ≤128 KiB, up to 32 NICs / 16 IPs per NIC / 64 volumes, strings ≤256 characters; reject invalid masks, huge integers, negative capacity and unsupported schema versions.
- Asset fields are **not** stored inside immutable audit event payloads by default. JSONB is allowed only for validated, bounded extension fields.

## Offline upload and revision guarantees
- Agent SQLite single writer maintains `next_inventory_revision` and a **single latest pending inventory snapshot** (or a small bounded supersession list) with immutable `snapshot_id` and digest. Increment revision and persist pending snapshot atomically; do not rebuild a new ID on HTTP retry.
- During outage, newer complete inventory snapshots may supersede older **pending** inventory snapshots, because v0.1 stores **latest state**, not a full change-event log. Server retains the highest accepted inventory revision per device ID. Send the latest pending revision on reconnect; if an older in-flight HTTP request arrives last, the server responds `stale_ignored` without rolling back current NIC/IP/volume records.
- A receipt/ack of revision N removes only a matching pending revision N; never erase a newer unsent revision. Schema/permission/credential errors surface an explicit degraded status, not infinite retry. A full-disk failure reports degraded inventory freshness rather than fabricating a current snapshot.
- Inventory outbox **does not** use canonical audit-event `device_sequence` and must never bypass the 100-event/512KiB event batch contract. Re-enrollment assigns new device ID and resets the *new* identity's inventory revision without mixing histories.

## Security, privacy and data quality
- Record only the minimum network inventory necessary for IT troubleshooting; MAC/IP and hostname are **internal-sensitive metadata**, not public telemetry. Restrict detailed reads to assigned device groups and authorized IT/security roles; log exports.
- Retain IP/MAC change history only for a stated operational purpose and limited duration; proposed 7 days, not indefinite tracking. Encrypt hosts/backups where applicable, apply TLS in transit, and do not include real MACs/IPs in public issues or screenshots.
- Never silently collect hardware serials/TPM identifiers, precise Wi-Fi location, full running process lists, personal account names, browser histories or arbitrary file listings in a basic inventory request.
- Missing field means `null` with **reason** (unsupported, permission_denied, not_collected, transient_error), not a made-up zero. Cache/change detection must account for suspend/resume, clock drift and OS upgrades.
- Protect against spoofed payload: bound device credential + `device_id`, field validation, rate limits, coherent timestamps. **Client telemetry is operationally useful but not tamper-proof** on an administrator-compromised host.
- OS data sources at implementation: Windows supported CIM/Win32 / system/network APIs; Linux `/proc`, `/sys`, `/etc/os-release`, kernel/desktop APIs. Prefer supported native/system interfaces over invoking untrusted shell commands; capability-gate proprietary GPU drivers and portal features.

## Unit and manual acceptance
- Multiple NICs (wired/Wi-Fi/VPN/virtual), both IPv4/IPv6, DHCP change, MAC randomization, no interface, disconnected NIC, invalid prefix, missing/renamed volume, disk full, no physical core count, null GPU, WSL/container environments.
- Idempotent duplicate inventory snapshot, older snapshot arriving late, partial/invalid update **must not wipe** valid current child rows, unauthorized role/group reads, wrong-bound-device inventory upload, excessive snapshot sizes.
- CPU/memory/volume sampling does not flood DB; Agent continues reporting health when one hardware source is unavailable; no system changes during read.
- Pop!_OS 24.04 KDE/KWin pilot: report Linux OS/kernel/CPU/RAM/volumes/NICs; `XDG_SESSION_TYPE` is **unknown** until checked in the actual user session, not guessed from KDE.
- Windows 11 pilot: collect the equivalent common fields; missing OS-specific fields must not break dashboard.
- No new E2E CI; pure unit tests plus documented targeted manual LAN validation in the existing test plan.
