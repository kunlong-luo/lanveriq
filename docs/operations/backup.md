# Backup and recovery design

## 2026-11-25 现行 G-08 设备清单备份验证与恢复流程（隔离合成，非生产 runbook）

**范围：** 先证明真实数据库事务可在原始 PG18 SIGKILL/WAL 之后提交，再用原生 `pg_dump -Fc` 导出，`pg_restore --list` 核对库存模式，然后恢复到全新、未发布端口、无网络的 PG18.7 OCI 数据库，比较 `device_inventory_current` 的 snapshot UUID、revision、received_at、digest 及 interfaces/volumes/Flyway 行数。仅使用真实 Rust SQLite→mTLS→Spring→PG18→Next.js 的 GitHub disposable synthetic fixtures；**不面向员工设备执行**。

- **已通过：** [真实 PG18 全新实例恢复，功能 `b64a14ad` / Linux #45586283694 两 jobs SUCCESS](../development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md)；此流程不是 PostgreSQL WAL/PITR 的替代，也没有验证附件/系统配置/CA/KMS 的恢复。
- **损坏拒绝正在复验：** [格式头损坏、截断归档及 `--single-transaction` 失败原子性](../development/linux-pg18-corrupt-backup-atomic-reject-l2-2026-11-25.md)，功能 `3687edd7` / [Linux #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)。失败与修复均留在专项报告。
- **恢复前门禁：** 有合法授权的测试范围与隔离数据；校验备份来源、创建者、完整性摘要、版本、权限、保留时间及独立受托方签收；缺失任何独立证据**不得把实验演练标为 G-08 生产 PASS**。成功导入≠备份来自真实可信源。
- **恢复时：** 使用隔离新实例/新库和 `pg_restore --exit-on-error --single-transaction`，拒绝已有业务库原地覆盖，失败要审计和销毁临时恢复环境；核对清单及迁移版本后，才由独立审核人决定是否可用于后续非生产检查。
- **灾备无法闭环的事实：** 尚无获批准的独立备份管理域、加密与密钥封存、可信时间与抗回滚高水位、生产定时备份/告警、跨机保管、真实演练记录、可测量 RPO/RTO、批准的恢复主体、Pop!_OS L3；G-02/G-08 OPEN。不能把同一个 CI 管理员创建的 SHA-256 自证完整性视为安全托管。

## G-08 本地 PG18 归档证据工具（2026-11-25 · L1 PASS；L2 NOT RUN）

[源码](../../apps/server/scripts/pg_backup_evidence.py) · [12 项拒绝测试](../../apps/server/scripts/test_pg_backup_evidence.py) · 功能 [`06897384`](https://github.com/kunlong-luo/lanveriq/commit/06897384adff08fe63d4fe875045890857960a6b) · [Server unit #45588705792 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588705792)。

此 Python 标准库工具**不进行数据库备份或恢复**，仅对明确授权的隔离测试中已有的 `pg_dump --format=custom` 归档建立同 OS 用户域的本地 SHA-256 证据，并在恢复前核对格式头 `PGDMP`、文件字节数、摘要、所有权、只限所有者的访问权限、非软链接及非多重硬链接。JSON 证据严格限定字段及值；默认不可覆盖现有证据；始终标注 `production_restore_authorized=false`、`independent_custody_verified=false`。用法仅针对一次性合成备份文件：

```bash
umask 077
# 在批准的离线合成测试目录事先生成 /private/lab.dump
python3 apps/server/scripts/pg_backup_evidence.py create /private/lab.dump /private/lab.evidence.json
python3 apps/server/scripts/pg_backup_evidence.py verify /private/lab.dump /private/lab.evidence.json
```

如果同一管理员同时改写归档和证据，依然可以生成一致 SHA-256：**这不是签名、高水位、独立 WORM、可信时间、生产灾备或可恢复性证明**。尚未将此工具串入真实 [PG18 WAL→备份→新库恢复](../development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) L2（**NOT RUN**）；后续需独立保管与审批，G-08 OPEN。

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

## D4-C18j 独立高水位发布与灾备（2026-11-23，NOT RUN）

参见 [C18j 实物介质选型、受控实验和八种崩溃/回退真实故障矩阵](../security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md)。分别记录 DB 已提交但未外部发表、keeper 已写但 ACK 丢失、N+1 并发、N-1 数据库及旧签名链重放、外部 H=N 存活、第一介质损毁而异域副本存活、KMS/时间失效。单纯 pg_dump/pg_restore 合成测试、Object Lock API 403 或元数据清单预检成功均不能替代实际异域灾备。仅在书面授权的非生产实验环境测量恢复和失败率；G-02/G-08 OPEN。
