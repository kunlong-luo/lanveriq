# G-08 — PG18 合成备份跨 GitHub 运行器交接与新实例恢复（L2）

**2026-11-26 · PENDING · main · v0.1 NOT RELEASE READY。** 本报告记录下一项功能的实际证据，不复制历史 CI 时间线；不能替代 [独立 WORM 与跨实体机器验收](../operations/backup.md)。

## 验收基线与新增内容

- 前置功能 [`45683e64`](https://github.com/kunlong-luo/lanveriq/commit/45683e64e88e6f23a50eaa98ce2c4423f32a22ef) 在 [Linux #45618414247 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45618414247) 已验证同一 runner 中 PG18 恢复、冷重启、真实 Spring/JPA no-web 启动和完整内容不变。首轮 Docker 网络失败仍记录在[前置报告](linux-pg18-restored-spring-jpa-context-l2-2026-11-25.md)。
- 本阶段功能 SHA [`b31f45ef`](https://github.com/kunlong-luo/lanveriq/commit/b31f45efe1972b3bfedb9961bc39cbc5f7a11e4f)；[Linux Batch A #45742025955 **PENDING**](https://github.com/kunlong-luo/lanveriq/actions/runs/45742025955)。**只有精确功能 SHA 的源任务、PG18 事务任务、独立接收任务均 SUCCESS 且原生日志实际出现接收端 PASS，才能把 L2 改成 PASS。**
- 源任务沿用真实 Rust SQLite → TLS1.3 双 mTLS → Spring/JPA → PG18 WAL/SIGKILL → 原生 `pg_dump -Fc` → 本 runner `pg_restore` → 无 Web Spring/JPA。源库验收之后，显式 GitHub CI-only 开关将**仅合成**备份、同域证据 JSON、源码 SHA、run ID、源 runner hostname、完整库存与 Flyway 指纹交给 Actions artifact（保留 1 日）；没有真实员工数据、私有 CA 或生产数据授权。
- 接收任务是**另一个 GitHub 托管 Ubuntu runner 任务**，通过 `actions/download-artifact` 取得该 run 的备份，并调用 [专用接收脚本](../../apps/server/scripts/verify-pg18-cross-runner-recovery.sh)，在全新、无网络、无端口发布的 PostgreSQL 18.7 OCI 中使用 `pg_restore --exit-on-error --single-transaction`。严格匹配来源 SHA/run、不同 runner hostname、文件集合、同域 JSON/字节完整性；使用[共享的完整行 SQL](../../apps/server/scripts/pg18-inventory-canonical-fingerprint.sql) 比对 current/interfaces/addresses/volumes/Flyway，并要求实际库存及迁移历史非空。再重启数据库并再次对比；失败时不切流、销毁临时实例。
- 本增量没有新增同类型加密单测或云厂商 SDK；复用原生 PostgreSQL、Docker 与 Actions artifact。源脚本 [`verify-agent-upstream-tls.sh`](../../apps/server/scripts/verify-agent-upstream-tls.sh) 的默认运行不导出归档；所有新交接仅在明确 CI 开关下启用。

## 不能由这次 CI 证明的事情

两个 runner 属于**同一 GitHub 管理面**，artifact 可由掌握该管理面的主体控制；同域 SHA-256 只能发现一般传输损坏，不能阻止源管理员一起替换备份及证据。runner hostname 自证不等于独立物理机器证明。此项也**没有**独立 WORM、不同组织保管权、外部 KMS、可信时间/高水位、正式密钥恢复、生产全量对象与 WAL/PITR、真实 RPO/RTO、法律授权、SRE 现场签收或切流。不得把它记录为生产灾备 PASS。

## 下一项真实退出条件（所有 NOT RUN）

依照[唯一备份运行手册](../operations/backup.md)与 [Issue #6](https://github.com/kunlong-luo/lanveriq/issues/6)，由安全/法务事先批准隔离实验环境及数据，在实际**不同物理主机/独立运维域**上完成备份保管、外部回执/高水位验证、完整恢复和故障注入；独立 SRE/安全/法务签收权限矩阵、介质/钥匙托管、源/目标主机证据、原始恢复日志、RPO/RTO 与回滚记录。**G-01–G-10 全部 OPEN，生产联网、采集、自动恢复、远控、安装和 CA 签发继续 OFF；v0.1 NOT RELEASE READY。**
