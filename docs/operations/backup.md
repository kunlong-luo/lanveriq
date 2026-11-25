# Lanveriq G-08 — PostgreSQL 备份与恢复操作边界

## 2026-11-25 现行 G-08 设备清单备份验证与恢复流程（隔离合成，非生产 runbook）

**范围：** 先证明真实数据库事务可在原始 PG18 SIGKILL/WAL 之后提交，再用原生 `pg_dump -Fc` 导出，`pg_restore --list` 核对库存模式，然后恢复到全新、未发布端口、无网络的 PG18.7 OCI 数据库，比较 `device_inventory_current` 的 snapshot UUID、revision、received_at、digest 及 interfaces/volumes/Flyway 行数。仅使用真实 Rust SQLite→mTLS→Spring→PG18→Next.js 的 GitHub disposable synthetic fixtures；**不面向员工设备执行**。

- **已通过：** [真实 PG18 全新实例恢复，功能 `b64a14ad` / Linux #45586283694 两 jobs SUCCESS](../development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md)；此流程不是 PostgreSQL WAL/PITR 的替代，也没有验证附件/系统配置/CA/KMS 的恢复。
- **损坏拒绝 L2 PASS：** [格式头损坏、截断归档及 `--single-transaction` 失败原子性](../development/linux-pg18-corrupt-backup-atomic-reject-l2-2026-11-25.md)，功能 `3687edd7` / [Linux #45588502849 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)。失败与修复均留在专项报告。
- **恢复前门禁：** 有合法授权的测试范围与隔离数据；校验备份来源、创建者、完整性摘要、版本、权限、保留时间及独立受托方签收；缺失任何独立证据**不得把实验演练标为 G-08 生产 PASS**。成功导入≠备份来自真实可信源。
- **恢复时：** 使用隔离新实例/新库和 `pg_restore --exit-on-error --single-transaction`，拒绝已有业务库原地覆盖，失败要审计和销毁临时恢复环境；核对清单及迁移版本后，才由独立审核人决定是否可用于后续非生产检查。
- **灾备无法闭环的事实：** 尚无获批准的独立备份管理域、加密与密钥封存、可信时间与抗回滚高水位、生产定时备份/告警、跨机保管、真实演练记录、可测量 RPO/RTO、批准的恢复主体、Pop!_OS L3；G-02/G-08 OPEN。不能把同一个 CI 管理员创建的 SHA-256 自证完整性视为安全托管。

## G-08 本地 PG18 归档证据工具（2026-11-25 · L1/L2 PASS）

[源码](../../apps/server/scripts/pg_backup_evidence.py) · [12 项拒绝测试](../../apps/server/scripts/test_pg_backup_evidence.py) · 功能 [`06897384`](https://github.com/kunlong-luo/lanveriq/commit/06897384adff08fe63d4fe875045890857960a6b) · [Server unit #45588705792 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588705792)。

此 Python 标准库工具**不进行数据库备份或恢复**，仅对明确授权的隔离测试中已有的 `pg_dump --format=custom` 归档建立同 OS 用户域的本地 SHA-256 证据，并在恢复前核对格式头 `PGDMP`、文件字节数、摘要、所有权、只限所有者的访问权限、非软链接及非多重硬链接。JSON 证据严格限定字段及值；默认不可覆盖现有证据；始终标注 `production_restore_authorized=false`、`independent_custody_verified=false`。用法仅针对一次性合成备份文件：

```bash
umask 077
# 在批准的离线合成测试目录事先生成 /private/lab.dump
python3 apps/server/scripts/pg_backup_evidence.py create /private/lab.dump /private/lab.evidence.json
python3 apps/server/scripts/pg_backup_evidence.py verify /private/lab.dump /private/lab.evidence.json
```

如果同一管理员同时改写归档和证据，依然可以生成一致 SHA-256：**这不是签名、高水位、独立 WORM、可信时间、生产灾备或可恢复性证明**。现已在 [真实 Linux PG18 WAL→备份→新库恢复验收](../development/linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) 中接入 `create/verify`，要求正常归档通过、格式损坏及截断档与冒充生产授权的 JSON 被拒绝，且原有真实 Rust/mTLS/Next.js 链不断。功能 [`94f21974`](https://github.com/kunlong-luo/lanveriq/commit/94f219747d767761f31de036470eb630ec535b89)，[Linux #45589325627 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45589325627)。原生执行日志已验证归档证据 create/verify、损坏档和伪造生产授权拒绝及完整 Rust/mTLS/PG18 恢复；**同一 CI 管理域**不是外部保管。下一项 [全库存/子表/Flyway 内容比较及等数量篡改拒绝](../development/linux-pg18-full-row-restore-tamper-l2-2026-11-25.md) `8e52d9ab` / [Linux #45612605286 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45612605286)。G-08 OPEN。

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

## 不再在运行手册复制历史验收

历史 D4-C18a/b/f/g/j 分别保留为独立报告，运行手册只保留决策和当前有效的检查入口：

- [D4-C18a 数据库拥有者回退反例](../security/fido-mds-db-owner-rewind-d4-c18a-2026-11-22.md)：即使 PG18 触发器及数据库自审计存在，拥有者仍可能回退记录；[原生 PG18 #45018857143 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45018857143)，**非备份恢复**。
- [D4-C18b 旧库/新库实际 `pg_dump/pg_restore`](../security/fido-mds-real-pg-restore-d4-c18b-2026-11-22.md)：`4e9dcdac`，[PG18 #45019585915](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585915) 与 [Linux #45019585916](https://github.com/kunlong-luo/lanveriq/actions/runs/45019585916) SUCCESS；外部高水位同步回退仍能欺骗同域校验。
- [D4-C18f 无供应商锁定的外部 WORM 选型及威胁矩阵](../security/independent-worm-provider-evaluation-d4-c18f-2026-11-22.md)：同一管理域的 Ceph/RADOS root 不能用 Object Lock API 限制来代替。
- [D4-C18g 管理域分离与 WORM-01～11 批准/验收方案](../security/independent-worm-custody-acceptance-d4-c18g-2026-11-22.md) 和 [D4-C18j 介质毁坏、签发/ACK、高水位与恢复故障矩阵](../security/independent-worm-lab-execution-package-d4-c18j-2026-11-23.md) 均是 **NOT RUN** 的实物/跨域实验，不允许以 CI 通过替代签收。

文档优先级：[当前事实](../CURRENT_STATUS.md) → [唯一发布门禁](../planning/release-gap-register.md) → 本操作边界 → 各 dated 证据。**G-02/G-08 OPEN；v0.1 NOT RELEASE READY。**
