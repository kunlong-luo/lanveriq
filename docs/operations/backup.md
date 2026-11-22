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
- 此文件/摘要只是测试中的隔离存储，并非可信不可变源。测试还证明：若整条签名链、高水位与数据库一起回退，旧状态可能匹配；这必须由真实独立 WORM/密钥托管（无论云厂商还是物理介质）、外部高水位及管理域分离补齐。
- **正式 G-02/G-08 准入要求未关闭：** 合规批准的独立安全存储管理域与保留策略、证据版本/物理介质 ID 的真实不可删除/read-back 验收；受控的 `pg_dump`/`pg_restore` 或 PITR 隔离恢复；旧库拒绝和新库一致性正例；不可跨域修改的高水位、审计、可信时间和密钥轮换，记录准确 SHA、存储版本 ID、独立审核签字，且不得把秘密、真实员工数据上传公共 CI。

- **2026-11-22 C18a 真实 PG18 合成回退子任务通过：** [功能 SHA `5afbee8a`](https://github.com/kunlong-luo/lanveriq/commit/5afbee8aa3f35a2ee916a34f4968d23242790aba) · [真实 PG18 149+ JUnit #45018857143](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857143)。这不是完整备份/恢复；生产前仍须实际 `pg_dump`/`pg_restore` 或 PITR 恢复演练和独立保留证据。见 [ADR-0012 候选外部对象保留方案](../architecture/adrs/0012-independent-fido-witness-object-lock.md)。

## D4-C18b 双备份真实恢复演练（2026-11-22 · 7/7 同 SHA PASS）

[真实 PG18 备份恢复隔离验收](../security/fido-mds-real-pg-restore-d4-c18b-2026-11-22.md)：先后运行 **`pg_dump -Fc`** 与 **`pg_restore --exit-on-error`**，创建独立 `old`、`fresh` 恢复库；要求旧恢复库在宿主保留的新高水位下拒绝，当前恢复库在相同见证历史下通过，旧外部高水位替换依然可以欺骗校验。数据库和所有 dump 文件均为一次性 PG18 容器内合成数据；此验证不证明异地备份/外部 WORM、可信时间、独立密钥托管、生产 PITR 或既定 RPO/RTO。G-02 与 G-08 都仍 OPEN。

- **精确功能证据：** [`4e9dcdac`](https://github.com/kunlong-luo/lanveriq/commit/4e9dcdac074200548317669e78cf8f66e405c3f3) / [PG18.6 Testcontainers ≥150 JUnit #45019585915](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585915) / [Linux Batch A #45019585916](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585916)，均 SUCCESS。实际两份合成数据 `pg_dump -Fc` / `pg_restore --exit-on-error` 在一次性 PostgreSQL18 容器内执行，**非生产异地存储/PITR/云 WORM**。

## D4-C18f 外部见证选型原则（2026-11-22 · 未真实部署）

按 [G-02 云中立选型与攻击矩阵](../security/independent-worm-provider-evaluation-d4-c18f-2026-11-22.md) 区分：离线合成证据校验、PG18 真实事务及备份恢复、独立安全域实际 WORM、独立钥匙及可信时间。不得把本机 readonly/chattr、同管理员 Ceph/MinIO Object Lock、Linux 容器或 PostgreSQL trigger 当作真正外部防回退。具体媒介/供应商还未批准；G-02/G-08 OPEN。

## D4-C18g 外部 WORM/恢复联动验收（仅协议，2026-11-22）

执行前必须获得独立安全受托方、法务及 SRE 的书面批准，在专用可销毁的**非生产**环境按 [C18g 真实管理域与 WORM-01–11 验收协议](../security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md)证明底层管理员、KMS、签名密钥、高水位与备份权限分离，真实数据库 N-1 恢复拒绝、N 读回、一份存储受损时跨域恢复。此章是计划，不是已发生的演练；所有真实 WORM/介质测试仍 NOT RUN，G-02/G-08 OPEN。
