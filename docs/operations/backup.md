# Backup and recovery design

## Scope
Back up PostgreSQL database, application configuration and policy versions, internal CA/required certificates and secrets through a protected separate channel, media manifests and video segments according to retention. Agent SQLite outbox is endpoint-local; it is **not** the central audit authority until accepted by server.

## PostgreSQL
Use a consistent PostgreSQL-aware backup (`pg_dump` for small pilot, verified WAL/PITR tooling if warranted) and restore to an isolated test instance. Don't back up PostgreSQL by casually copying live data directory. Record PostgreSQL version, migration version, backup timestamp and integrity. Encryption at rest and limited access are mandatory.

## Media
Independent video disk/NAS backup only where policy permits. Ensure restore reconciles media files to PostgreSQL manifest; missing video must surface as lost/unavailable, not valid recording. Separate retention/backup expiry and legal hold.

## Recovery objectives
Proposed pilot targets RPO ≤24 hours and RTO ≤4 hours; these are goals, not guaranteed until a restore drill passes. Credential compromise requires reissue/rotation and audit, not restoration of a previously stolen credential.

## Test scenarios
Corrupt DB, incomplete backup, migration failure, missing volume, lost media file, agent local disk failure, and expired certificates. Record restoration steps, actual completion and gaps. Do not copy employee data into public issue attachments.
