# Operational signals and incident response

## Essential signals
Server: API uptime, DB connectivity, storage free %, event ingestion requests/latency, ingestion rejects/duplicates, retention queue, backup age, admin login failures.
Agent: last heartbeat, OS/capability, assigned policy digest, SQLite queue rows/bytes, oldest pending event, upload error class, clock drift, collector health, dropped-event/gap count.
Recording (later): OS permission/capture status, encoder failure, segment-close delay, pending upload bytes, rejected checksum, missing manifest and storage quota.

## Alerts and thresholds (initial examples)
- Device stale >90 seconds, offline >300 seconds; operator can distinguish powered off from network failure only with external evidence.
- Queue >70% early warning, >90% urgent; disk free <15%; expired/revoked credentials; repeated ingestion denial.
- Recording unavailability is **not** a reason to bypass a portal or system permission.
- Backup older than policy target; restore testing overdue.

## Incident response
1. Determine affected devices and exact interval without attributing motives to employees.
2. Preserve necessary server evidence with role-limited access and case reference.
3. Contain compromised credentials or endpoints, revoke/rotate identities.
4. Restore collection when lawful/authorized, record outage and data gaps.
5. Follow privacy incident notification procedures where applicable.
6. Verify recovery and document root cause, corrective action and real user impact.

## Auditability
Record admin data search/export/playback, denied access, enrollment, policy changes and retention deletion. Deny unaudited raw file access to video.
