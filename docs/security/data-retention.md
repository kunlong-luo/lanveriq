# Retention, deletion and data minimization

**Proposed defaults only; final policy requires employer/legal review before deployment.**

| Category | Draft default | Storage |
|---|---|---|
| Heartbeat history | 7 days | PostgreSQL |
| Current authorized device inventory (OS/CPU/RAM/NIC/IP/MAC/volumes) | until replaced or device retired, per approved asset-retention policy | PostgreSQL current child tables |
| Network/IP/MAC change observations | **7 days proposed** where operational history is justified | PostgreSQL; separate from current NIC details |
| Host CPU/RAM/disk performance samples | **7 days proposed** | PostgreSQL bounded time-series |
| Application activity and OS events | 30 days | PostgreSQL |
| Collection gaps | 90 days | PostgreSQL |
| Recordings (later) | 14 days | local/NAS volume; indexed in PostgreSQL |
| Security admin access audit | 90 days | PostgreSQL |
| Agent unacknowledged outbox | bounded by 1 GiB or 7 days; report overflow | local SQLite |

## Deletion workflow
Policy is per device group/data category. A scheduled server job deletes in bounded transactions; video deletion removes media and corresponding index state with error/retry tracking. Queue messages and caches follow policy. Authorized legal hold needs case reason and expiry, cannot be a silent indefinite retention bypass. Backups require an explicit expiry and restore-time re-application of deletion policy.

## Capacity and quotas
Keep video on separate volume; alert at configurable 70/85/95% usage levels (draft). Prevent capture when safe free-space reserve is exhausted and report gap. Verify database purge reduces storage by appropriate vacuum/index housekeeping without blocking ingest. Use UTC server receive timestamps for retention; client clock cannot extend retention indefinitely.

## Export policy
Require reason and device scope; redact when possible, encrypt output at rest, log download and expiry. No unrestricted browse of raw employee screen recordings.

## Open decisions
Actual retention days, legitimate purpose, working-hours limit, request/appeal process, backup expiry and file redaction must be signed off before real-data pilot.
