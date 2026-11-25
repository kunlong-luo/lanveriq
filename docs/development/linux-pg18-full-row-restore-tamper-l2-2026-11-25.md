# G-08：PG18 全量库存/Flyway 行内容恢复与等数量篡改拒绝（Linux L2）

**2026-11-25 · L2 PASS · v0.1 NOT RELEASE READY。**

## 背景与精确依赖

已有的 [真实 WAL→新 PostgreSQL18 恢复](linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) 使用设备当前快照的 `snapshot_id/inventory_revision/received_at/content_digest` 四字段和 interfaces、volumes、Flyway 数量核对；[损坏/截断归档原生拒绝](linux-pg18-corrupt-backup-atomic-reject-l2-2026-11-25.md) 已在功能 `3687edd7` [Linux #45588502849 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)；[本地清单及恶意授权证据](../operations/backup.md) 在功能 `94f21974` [Linux #45589325627 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45589325627)。这些保证无法排除**数量未变、snapshot 四字段未变，但库存 JSONB 或其他详情行被修改**。

## 本阶段原生可执行断言

- 功能提交 [`8e52d9ab`](https://github.com/kunlong-luo/lanveriq/commit/8e52d9ab53cc820183305ef021b7d709fa076156)，[Linux Batch A #45612605286 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45612605286)，[Server unit #45612605023 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45611916035)。
- 代码复用现有唯一的 [Rust→mTLS→Spring→PG18→Next.js Linux L2](../../apps/server/scripts/verify-agent-upstream-tls.sh)。从真实 Rust SQLite ACK 未见的已提交 PostgreSQL18 在 SIGKILL/WAL 恢复之后导出原生 custom-format `pg_dump`，先核验原档与本地证据，再恢复至独立、`--network none` 的 PG18.7 容器。
- 除原有快照四字段、子表/Flyway 数量断言，新增 PostgreSQL 原生 **JSONB 按主键排序的完整行内容序列化**，对 `device_inventory_current`、`device_inventory_interfaces`、`device_inventory_addresses`、`device_inventory_volumes` 和 `flyway_schema_history` 做一次性本地 SHA-256 比对；源与新库必须一致。使用 psql、sha256sum、已有 PostgreSQL 能力，不引入 pgcrypto/云 SDK/私有协议。
- **必须实际识别等数量篡改：** 在正常恢复以及坏档拒绝成功后，*只修改一次性恢复副本*的一项 `capabilities_json` 测试字段；行数和原 snapshot 四字段必须保持不变，但全行内容指纹必须不同。再检查原始 WAL 数据库指纹不变，然后销毁恢复副本。后续原 Rust SQLite pending 继续通过真实 mTLS duplicate ACK 验收。
- **安全边界：** 同 GitHub CI runner 内的比较，不上传行数据或摘要，不接入员工设备或真实生产 CA；摘要不是独立管理员见证，不可替代可信时间、独立 WORM、外部备份签名、跨机演练、恢复授权或 RPO/RTO 证明。没有 UI/Agent 自动恢复开关。

## 首次真实失败与修复

初版 [`d6259ba2`](https://github.com/kunlong-luo/lanveriq/commit/d6259ba2621853fc95a3d142481164053322d46a) 的 [Linux Batch A #45611916004 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45611916004)：PG18 transaction job SUCCESS；真实 Rust/PG18 业务链在新库恢复后已经输出 `PASS: full-row canonical content matches WAL source across current inventory, interfaces, addresses, volumes and Flyway`；接下来在合成恢复副本单行 JSONB 篡改结果断言处输出 `FAIL: synthetic one-row restore-copy tamper did not execute`，未到达篡改检测 PASS。不能将其视为负例已通过。

[修复功能 `8e52d9ab`](https://github.com/kunlong-luo/lanveriq/commit/8e52d9ab53cc820183305ef021b7d709fa076156) 将易受 `UPDATE ... RETURNING` 输出格式影响的字符串断言改为 `WITH changed AS (UPDATE ... RETURNING device_id) SELECT count(*)`，要求精确更新一行且继续比较完整行指纹、源库不变和 Rust ACK。修复后的 [Linux #45612605286](https://github.com/kunlong-luo/lanveriq/actions/runs/45612605286) **PENDING**，不可借用先前功能 SHA 的 CI PASS。

## 完成条件

功能 `8e52d9ab` 的 [Linux #45612605286 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45612605286)，原生日志分别打印 `PASS: full-row canonical content matches WAL source` 和 `PASS: full-row restore verification rejects equal-count, unchanged-snapshot-tuple JSONB tamper; source unaffected`；Rust mTLS exact ACK 继续通过。首次 `d6259ba2` [#45611916004 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45611916004) 仍保留。下一阶段 [恢复后重启与实验时间](linux-pg18-restored-restart-measured-drill-l2-2026-11-25.md)。**G-08 OPEN**。

[当前事实](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [备份操作边界](../operations/backup.md)。**G-08 OPEN；v0.1 NOT RELEASE READY。**
