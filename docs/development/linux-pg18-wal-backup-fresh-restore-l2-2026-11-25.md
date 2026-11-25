# G-08 Linux PostgreSQL 18 — SIGKILL/WAL 后真实逻辑备份恢复至全新 OCI

**2026-11-25 · L2 验证状态：PENDING · v0.1 NOT RELEASE READY。**

## 原因及目标

原 [PostgreSQL SIGKILL/WAL 恢复→Rust SQLite 同请求重送 L2](linux-inventory-pg18-crash-wal-replay-l2-2026-11-25.md) 已在功能 SHA `3e5b7009`、[Linux #45583968475 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583968475) 验证**原容器原数据目录** WAL 恢复。这与可用备份是不同的保证：WAL 成功不代表 `pg_dump` 归档可以在**新数据库实例**恢复且能读到相同设备数据。

## 本轮新增的隔离验证

- **功能提交：** 初始 [`abdebe66`](https://github.com/kunlong-luo/lanveriq/commit/abdebe664431227fee3993ca93af423174c88cdd)，修正 `pg_restore --list` 的 stdin Docker 传递后以 [`b64a14ad`](https://github.com/kunlong-luo/lanveriq/commit/b64a14ad2c0eeeeb2f5db135c0f6f19dbab77d6f) 为验收功能 SHA。
- **真实 L2：** [Linux Batch A #45586283694](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283694) **PENDING**（需两 jobs SUCCESS）；[Server unit #45586283622](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283622) **PENDING**。不借用较早 `3e5b7009` 的 CI PASS。
- **代码入口：** [verify-agent-upstream-tls.sh](../../apps/server/scripts/verify-agent-upstream-tls.sh)。在一次性 Ubuntu runner 的合成 Rust Agent/mTLS/Spring/PG18 链路中，让原 PG18 容器发生真正 SIGKILL 并完成 WAL 恢复，再通过原生 `pg_dump --format=custom` 生成只在私有临时目录存在的归档；`pg_restore --list` 确认至少含 inventory 表。
- **真正新数据库：** 启动第二个 `postgres:18.7` 容器，`--network none`，不映射任何端口，不借用原容器数据卷；原生 `pg_restore --exit-on-error --single-transaction` 恢复完整归档，失败立即拒绝。
- **一致性断言：** 使用真正的 `psql` 比较源和新库的 `device_inventory_current` 中 snapshot UUID、revision、PostgreSQL `received_at` 与内容 digest 完全一致，另比较 inventory/current、interfaces、volumes 与 Flyway history 记录数。隔离恢复实例检测后强制清理；原 Rust SQLite 未收到的 ACK 仍继续在旧数据库上做真实重复送达验收。
- **安全边界：** 测试数据、一次性密码和归档全部在临时 CI 环境；归档不发布、不上传 artifacts、不注入真实员工数据。**不验证**实际跨机器/跨管理员存储、备份加密、签名、定期调度、恢复演练审批、保留周期、RPO/RTO，也不是独立 WORM。

## 验收与限制

只有功能 SHA `b64a14ad` 对应的 Linux L2 两 jobs **SUCCESS**，且同一次日志明确出现归档导出、全新 PostgreSQL 恢复与四字段一致 PASS，才可将本阶段改为 PASS。若出现 FAIL/CANCELLED，保留真实失败，进一步修复并绑定新的功能 SHA。

[现行状态](../CURRENT_STATUS.md) · [G-08 Gate](../planning/release-gap-register.md) · [Linux 实施路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)。**G-08 OPEN；v0.1 NOT RELEASE READY。**
