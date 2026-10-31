# INV-02 — current inventory and monotonic snapshot transaction acceptance

**Status (2026-10-31):** staged **internal Java 25/Spring JPA** implementation, Flyway V8 and Java unit tests. The opt-in PostgreSQL 18.6 synthetic Testcontainers suite is included but **NOT RUN**. No HTTP route, actual Caddy/Agent mTLS identity bridge, genuine Rust OS collection, scoped browser inventory read or live device data exists.

## V0.1-only schema and safety boundary

- V8 creates `device_inventory_current`, `device_inventory_interfaces`, `device_inventory_addresses` (native PostgreSQL `inet`, prefix validation) and `device_inventory_volumes`, referencing `devices.id` and cascading child cleanup. Source-supplied IP and MAC are **mutable asset metadata**, NEVER credentials or unique device identities.
- `DeviceInventorySnapshot` is a complete typed version-1 payload with stable `snapshot_id`, **device-local monotonic** `inventory_revision`, UTC `observed_at`, hardware model/CPU/RAM, OS, NICs with IPv4/IPv6, mounted volumes and capability indicators. It deliberately excludes login names, hardware serial/TPM IDs, Wi-Fi SSIDs/BSSIDs, browser URLs, activity and screen data.
- Validate a max **128 KiB** serialized request, **32 NICs**, **16 IPs per NIC**, **64 volumes**, constrained keys/labels/IP prefix/physical capacity and valid numeric literal addresses. Preserve uncollected/unsupported vs `0`; never fabricate coverage.
- `DeviceInventoryService` requires a package-internal `VerifiedDeviceCertificate` matching the exact path/payload device UUID; `JpaDeviceInventoryRepository` locks **both canonical device and the bound active credential** and checks expiry/revocation after locking. Server computes SHA-256 content digest; no digest is authenticated from Agent JSON.
- Each full inventory replacement updates latest row and all NIC/IP/volume child rows **in one Spring transaction**. Higher revision applies; equal revision + identical `snapshot_id` and digest returns `duplicate` without writes; equal revision with different ID/content fails `REVISION_CONFLICT`; older revision returns `stale_ignored` and the latest version without any mutation. Spring transaction rollback must restore the whole prior state if any insert fails.
- This is a **latest-state** asset record, not an immutable employee event stream; it does NOT advance `device_sequence`, create event audit records, change heartbeat timestamps or send an inventory every 30 seconds.
- Receipt `server_time` currently comes from the server JVM clock; persisted `received_at` comes from PostgreSQL `clock_timestamp()`. Inventory freshness and ordering MUST use recorded server receipt/revision, not Agent timestamps.

## Acceptance matrix

| Check | Expected | Evidence |
|---|---|---|
| INV-DB-01 | Flyway V8 creates current + children, native `inet`, strict FK/check constraints, empty seed | Source written; **PG not executed** |
| INV-DB-02 | Active registered certificate can commit one complete multi-NIC/multi-IP/multi-volume snapshot | Java unit + opt-in Testcontainers source |
| INV-DB-03 | Same revision/ID/digest replay is idempotent | Java unit + opt-in Testcontainers source |
| INV-DB-04 | Late lower revision never regresses network addresses or OS asset state | Java unit + opt-in Testcontainers source |
| INV-DB-05 | Same revision with different ID/content is rejected and current rows remain intact | Java unit + opt-in Testcontainers source |
| INV-DB-06 | Forged/revoked/expired certificate is denied | Java unit + opt-in Testcontainers source |
| INV-DB-07 | Injected NIC/address/volume SQL failure rolls back current and all children; true concurrent revisions | **Needs executed PG18 fault/concurrency evidence** |
| INV-AGT-01 | Real Windows 11 + Pop!_OS asset collector using maintained `sysinfo`, OS/network libraries and an updated locked Cargo dependency graph | **Not implemented** |
| INV-HTTP-01 | Actual Caddy→Spring mTLS, scoped Administrator read, private MAC/IP and no cross-group leakage | **Not implemented / not approved** |

To execute **only on disposable synthetic test databases**, run `cd apps/server && ./gradlew integrationTest` with Java 25 and Docker or manually dispatch [PostgreSQL 18.6 integration workflow](../../.github/workflows/server-postgres18-integration.yml) from GitHub Actions. The standard push CI runs only JUnit and compiles integration-test sources, **not Docker**. The original startup smoke script `apps/server/scripts/verify-postgres18.sh` now checks Flyway V1–V8, current inventory table and absence of inserted inventory rows.

## Unclosed blockers

The first genuine PostgreSQL run, actual embedded Spring/Tomcat mTLS, cert enrollment/activation, scoped asset read API, real Rust NIC collection and platform smoke still require independent acceptance. **Do not turn this internal service into a public HTTP route or collect real employee/device data.**
