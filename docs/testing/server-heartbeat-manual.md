# HB-01 — internal device heartbeat and latest-state acceptance

**2026-11-12 HB-HTTP-01 L2 实际合成验收已通过。** [代码 SHA `80298562`](https://github.com/kunlong-luo/lanveriq/commit/80298562d4221c0afc4dd18cfa01d1ac3f7b15ad) 的 [Linux Batch A #43210930135](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930135) **SUCCESS（两个任务均成功）**，独立 [Java L1 #43210930065](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930065) 和 [RD-2 默认拒绝 #43210930301](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930301) 也 **SUCCESS**。一次性 Ubuntu 24.04 runner 上，先在默认关闭状态验证真实 TLS1.2 禁止、双向 mTLS、错误 CA/SNI/Host 和业务 403，再对**同一临时 PostgreSQL 18.6** 以独立双路由 opt-in 启用测试服务：真实 Linux Rust P-256 CSR 对应叶证书发送激活 + 两次不同心跳，精确 ACK 全部通过；旧 `request_id` 的 HTTP 409/空体/no-store、数据库最新状态和 `last_seen_at` 不变、跨设备、受信 CA 但未注册证书、过期/吊销后拒绝均通过。全部合成且只使用测试私钥和固定虚构设备 ID，没有启动设备 Agent service、采集真实机器或改变生产配置。**仅 A4 核心 L2 合成请求闭环通过，不等于 A2 完整接入令牌/审核发行、A3 受控系统服务调度、A5 Pop!_OS 实机、生产 CA、请求 ID retention/epoch 与隐私/发布批准。**

**注意：**以下标为 `source-only`、`pending CI` 和 `NOT EXECUTED` 的历史段落，均以本次同源成功记录为准；真实设备、安装服务与生产批准仍缺失。

**2026-11-12 HTTP/2 activation regression under investigation:** Real Rust transport reached
the opt-in synthetic business test but initially received 403 on activation.
The isolated comparison in [run #43209697578](https://github.com/kunlong-luo/lanveriq/actions/runs/43209697578)
returned direct Tomcat HTTP/2=403, HTTP/1.1=200 with the same trusted
upstream certificate and matched reviewed PG18 row. A narrow policy candidate
allows absent ordinary Host only on actual HTTP/2 when Tomcat's parsed
`:authority` / `getServerName()` equals the expected Agent hostname;
conflicting explicit Host, incorrect authority, missing H1 Host, wrong TLS
peer and direct access still fail closed. Source is pending exact-SHA CI.
Do not mark the real Rust-to-PG18 chain accepted until the new request passes.

**2026-11-12 source-only upcoming single-request L2:** An explicit
`LANVERIQ_SYNTHETIC_AGENT_BUSINESS_LOOP=1` runner mode is being added to the
disposable Linux-only Batch A workflow. It first verifies the production-like
default-deny 403 and actual TLS negatives, then restarts only the throwaway
Spring process with activation and heartbeat flags true, seeds a matching
reviewed *synthetic* Rust CSR/leaf in the disposable PG18 database and exercises
real `AgentTlsTransport::send_activation` and `send_heartbeat` through
Caddy/Tomcat to PostgreSQL 18. Rust checks exact response DTOs, request UUIDs
and replay quarantine; an independent curl checks replay 409 with empty
no-store response, and SQL checks the latest-state and last-seen are unchanged.
Expired/revoked and trusted-CA-unregistered keys must still fail. **Pending CI**
until the exact source SHA workflow succeeds; never copy this temporary mode
into deployed infrastructure.



**2026-11-12 replay guard (L1 and real synthetic PG18 CI PASS, source SHA [`7547f5c0`](https://github.com/kunlong-luo/lanveriq/commit/7547f5c0ccc80fdd6d5e248eafdcb7d939734f41); [Batch A #43205182338](https://github.com/kunlong-luo/lanveriq/actions/runs/43205182338)):** Flyway V9 adds a
per-device persistent accepted-request UUID ledger. After device and certificate
locks succeed, the same transaction inserts the validated UUID before updating
`last_seen_at` or `device_heartbeat_latest`. Repeated IDs produce HTTP 409,
no-store, empty body, and no receipt. The synthetic PostgreSQL 18 regression
accepts two distinct IDs and then replays the first, asserting latest state and
server receive time remain unchanged. No Agent runtime/production ingress is
enabled. The ledger retains only device/request UUID pairs, not heartbeat
payloads, and has no purge: define bounded lifetime/epoch and replay-window
policy before production scale without silently weakening security.

**CURRENT UPDATE 2026-11-08 (supersedes the 2026-10-31 test-stage statements below):** V0.1 Java JPA/Flyway latest-heartbeat implementation and synthetic PostgreSQL tests exist; Caddy/Tomcat/PG18 have *historical* combined TLS/RBAC synthetic success. **Batch A1** introduced a `POST /api/v1/devices/{deviceId}/heartbeats` HTTP controller and its exact stateless Spring Security chain behind **two default-OFF flags**; [Java MockMvc/negative L1 #42448164511](https://github.com/kunlong-luo/lanveriq/actions/runs/42448164511) passed. However **this new endpoint has NOT been exercised through a real Rust Agent→Caddy→Tomcat→PG18 L2 chain**. The Agent binary remains idle. Do not treat earlier successful synthetic Console TLS tests as new heartbeat route acceptance; no real employee/device monitoring is authorized. [Batch A record](../development/batch-a-first-heartbeat-implementation.md) · [full protocol/security gate](../architecture/device-communication-stack.md).

**Credential revocation race test (2026-10-31, opt-in source only):** The shared `JpaActiveDeviceCredentialGuard` now requires the same ordered device→credential locks for heartbeats, event batches and inventory. A staged synthetic PostgreSQL 18.6 suite checks revoked, expired and future client credentials cannot change `device_heartbeat_latest`, `devices.last_seen_at` or canonical events. This does **not** prove actual mTLS/Tomcat certificate attributes or real DB execution until the manual workflow succeeds.

**Cross-device and replay checks (2026-10-31, source only):** The staged manual PG18 suite includes two same-group active devices with distinct registered certificates. A forged first-device fingerprint on the second-device heartbeat must not update either device's `last_seen_at` or heartbeat state. After a genuine heartbeat and event have been committed, revoking the certificate must prevent further heartbeat changes **and deny an otherwise-identical event replay ACK**. A `pending` certificate must also fail despite an active device. These are Testcontainers source tests, **not observed PostgreSQL execution or actual Caddy/Tomcat identity proof**.

## Scope and privacy boundary

- `DeviceHeartbeatService` retains an internal, transactional contract, now called by a **default-OFF, trust-gated HTTP source** only when both Agent ingress and heartbeat flags are enabled; the normal runtime has neither production Agent host nor registered device keys. It must be invoked only after an independently verified Agent mTLS identity is mapped to the enrolled device. An arbitrary body `device_id` or forwarded fingerprint header does **not** authenticate the caller.
- The service validates `schema_version=1`, device/path/credential ID equality, version, status, bounded machine-code capability/error states, nonnegative queue counters, and UTC timestamps **before SQL**. Raw free-text error messages, IP/MAC, file paths, process/window activity, employee names or recording status are not accepted.
- Inside **one Spring-managed transaction**, the JPA repository first locks the currently active device row and then the exact registered credential row with `FOR UPDATE`, verifying its fingerprint, validity window and nonrevoked state after the lock (using `clock_timestamp()`); it inserts the accepted per-device `request_id` in the durable V9 ledger before updating `devices.last_seen_at` and upserting one `device_heartbeat_latest` row. A duplicate request aborts before any latest-state write. `received_at` is database-owned. The Agent's clock must not determine online/stale/offline.
- Capabilities `not_collected`, `unsupported`, `permission_denied` and `degraded` retain their distinct meanings. Successful heartbeats do **not** establish collector coverage or authorize future device telemetry.

## Opt-in synthetic PostgreSQL test

Open [manual PostgreSQL 18 integration workflow](../../.github/workflows/server-postgres18-integration.yml) on `main` and dispatch it explicitly; it uses Testcontainers 2.x, PostgreSQL 18.6, JDK 25 and `./gradlew integrationTest`. It is not scheduled, pushed, or part of unit CI.

| Check | Expected |
|---|---|
| HB-DB-01 | Flyway V1–V9 applies, `device_heartbeat_latest` exists empty on first startup |
| HB-DB-02 | Accepted synthetic active-device heartbeat stores one row, with DB receipt, `devices.last_seen_at`, typed queue/capabilities JSONB |
| HB-DB-03 | A later heartbeat **updates the single latest row**, retaining `unsupported` vs `not_collected` without fabricated coverage |
| HB-DB-04 | Unknown, forged, expired or revoked fingerprint cannot change `last_seen_at` or add heartbeat rows |
| HB-DB-05 | Missing, malformed, oversized, untrusted-device, free-form error or unsupported schema input is rejected before database writes |
| HB-DB-06 | A failed write rolls back the complete latest-state transaction, with no successful response before commit |
| HB-HTTP-01 | **2026-11-12 L2 synthetic PASS** [#43210930135](https://github.com/kunlong-luo/lanveriq/actions/runs/43210930135): real Rust `send_activation`/`send_heartbeat` over Caddy→Tomcat TLS1.3, PG18 persistence, exact ACK, replay HTTP 409/no-store/empty and stable latest-state/last-seen; host/SNI/wrong client CA, trusted-CA-unregistered, wrong device, expiry and revocation denied. Disposable runner only, no Agent service or production endpoint. |
| HB-OS-01 | Installed Windows/Pop!_OS Agent schedules authentic heartbeats and UI derives online/stale/offline from DB server receipts — **NOT IMPLEMENTED** |

**Acceptance boundary:** A successful synthetic PostgreSQL run may establish HB-DB evidence only. It does not close Agent transport, certificate issuance, session login, REST/WSS permissions, OS service tests, organizational privacy approval or real-data authorization. Record tested Git SHA, run URL, actual DB version, sanitized JUnit artifacts and image digest; never upload real device data, certificates or passwords.
