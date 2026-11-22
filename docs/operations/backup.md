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

## D4-C18a 受控数据库回退安全负例（2026-11-22）

- 代码参考：[D4-C18a](../security/fido-mds-db-owner-rewind-d4-c18a-2026-11-22.md)。新增的 PostgreSQL 18.6 Testcontainers 负例用具有所有者权限的合成测试角色绕过 **V30/审计表 USER triggers** 删除最新收据，并把高水位保存在 PostgreSQL 容器外的宿主临时文件中；C17 必须拒绝与外部高水位不一致的数据库尾。它**不是**真实 `pg_dump`、`pg_restore`、WAL/PITR 或外部 WORM 演练。
- 此文件/摘要只是测试中的隔离存储，并非可信不可变源。测试还证明：若整条签名链、高水位与数据库一起回退，旧状态可能匹配；这必须由真实独立的 WORM Object Lock/KMS、外部高水位及管理域分离补齐。
- **正式 G-02/G-08 准入要求未关闭：** 合规批准的独立存储账户与保留策略、对象版本的真实不可删除/read-back 验收；受控的 `pg_dump`/`pg_restore` 或 PITR 隔离恢复；旧库拒绝和新库一致性正例；不可跨域修改的高水位、审计、可信时间和密钥轮换，记录准确 SHA、存储版本 ID、独立审核签字，且不得把秘密、真实员工数据上传公共 CI。
