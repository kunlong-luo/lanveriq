# G-08 PostgreSQL 18 恢复库独立重启与实验耗时 L2

**2026-11-25 · L2 PASS · v0.1 NOT RELEASE READY。**

## 已通过的基线

功能 `8e52d9ab` 的 [Linux #45612605286 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45612605286) 已证明真实 Rust SQLite→mTLS→Spring→PG18 SIGKILL/WAL→备份→全新无网络 PG18 恢复→Next.js 链，完整库存/子表/Flyway 行内容一致，且只修改恢复副本 JSONB 内容但不改变行数和 snapshot 四字段时会被拒绝。[前次失败与修复](linux-pg18-full-row-restore-tamper-l2-2026-11-25.md) 均保留。

## 本轮实现与验证

- 功能 [`e6a7ae12`](https://github.com/kunlong-luo/lanveriq/commit/e6a7ae12cebb42f3f0f29f2cd24147afb00bb640)，[真实 Linux #45615090308 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45615090308)；[Server #45615090336 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45615090336)。
- 在恢复后的 `--network none` 临时 PostgreSQL 18.7 容器执行 `docker restart --time 3`，重新 `pg_isready`，并与原 WAL 库全量比较库存当前行、网卡、地址、磁盘与 Flyway；必须仍一致。继续执行原有坏档拒绝、等数量篡改检测及 Rust mTLS exact ACK。
- 使用 Python 标准库 `time.monotonic_ns()` 测量真实 CI 内 `pg_dump` 用时及新库启动→恢复→重启→验证用时。生成仅存于该 CI runner 私有临时目录的 `pg18-recovery-drill-metrics.json`；只含功能 SHA、实验秒数、明确标注同域/不可信时间与 `production_rpo_measured=false`、`production_rto_measured=false`、`external_custody_verified=false`、`production_restore_authorized=false`；不得上传备份、真实业务数据或凭据。
- **实际验收 PASS：** 功能 `e6a7ae12` 的 [Linux #45615090308 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45615090308)，真实日志显示 `PASS: isolated PG18 recovery/restart drill measured; dump=0.125s, fresh-restore/restart/verify=4.052s`、`PASS: independently restarted recovered PG18 remains queryable with unchanged full inventory and Flyway content`，随后原生 pg_restore 坏档拒绝、JSONB 等数量篡改检测、Rust mTLS exact ACK 均 PASS。下一步参见 [真实 Spring Boot 对恢复库的无 Web 启动](linux-pg18-restored-spring-jpa-context-l2-2026-11-25.md)，不能把本项 PASS 计为 Spring/JPA 恢复验收。

## 严格限制

CI 冷重启和实验时间**不是**跨主机、跨灾难故障域的恢复；不证明生产 RPO≤24h / RTO≤4h。尚无独立备份管理域、可信时间、外部密钥托管、实际故障演练、书面 SRE/安全/法务签收。G-02/G-08 OPEN，v0.1 NOT RELEASE READY。

[备份操作](../operations/backup.md) · [当前事实](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md)。
