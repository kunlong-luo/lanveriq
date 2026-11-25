# G-06/G-08 — 真实 Spring Boot/Hibernate 接管已重启的恢复 PG18（无 Web L2）

**2026-11-25 · L2 PENDING · main · v0.1 NOT RELEASE READY。**

## 已验收基线（不能冒充本阶段）

[先前 PG18 恢复→独立重启](linux-pg18-restored-restart-measured-drill-l2-2026-11-25.md) 在功能 SHA [`e6a7ae12`](https://github.com/kunlong-luo/lanveriq/commit/e6a7ae12cebb42f3f0f29f2cd24147afb00bb640) 的 [Linux #45615090308 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45615090308) 实测：原生 `pg_dump` 约 0.125 s、新 PG18 恢复/重启/复查约 4.052 s，重启后全量库存和 Flyway 内容未变。秒数只是**一次 CI 合成实验**，不是生产 RPO/RTO。先前只通过 PostgreSQL 原生命令查询，尚未证明 Spring/JPA 能使用恢复库。

## 本轮实际功能和严格边界

- 功能提交 [`dcde55e1`](https://github.com/kunlong-luo/lanveriq/commit/dcde55e18e8249fbfc4eaf6399cc5ce59f4d100d)，[Linux Batch A #45616194271 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45616194271) / [Server unit #45616194360 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45616194360)。
- 在现有 Rust SQLite→Caddy TLS1.3 mTLS→Spring Boot/JPA→PG18 SIGKILL/WAL→`pg_dump`→独立 PostgreSQL18.7 容器 `pg_restore --single-transaction`→重启复核的唯一 Linux CI 脚本中，临时创建**仅内部 Docker bridge**，不给恢复容器发布主机 TCP 端口。
- 将已重启并核对内容的恢复库接入该一次性内部网络，获取私网 IPv4；用专用 `LANVERIQ_DB_URL` 启动**实际构建出的 Spring Boot 4.2 可执行 JAR**，传入 `--spring.main.web-application-type=none`、`--spring.flyway.enabled=false`、`--lanveriq.bootstrap.enabled=false`。没有 Web 端口、首次管理员注册、Agent 入站或生产 CA/设备授权；只验证当前 Spring 容器/JPA/Hibernate 能正确启动、识别已恢复模式。
- 应用返回成功后，使用真实 PostgreSQL 原生完整行指纹再次证明恢复库未被修改，**马上断开/销毁私有桥接网络**，继续此前坏归档拒绝、等数量 JSONB 篡改负测以及原始 Rust SQLite pending 经实际 mTLS exact ACK 的全链验收。
- 出错时失败关闭并清理临时容器、网络和 Spring 启动日志；不公开原始 SQL、凭据或真实设备信息。**此阶段没有把恢复的数据库重新接入真正 Web 用户会话，也未执行实际切流。**

## 验收和仍未完成的外部工作

只有与功能 SHA `dcde55e1` 完全一致的 [Linux #45616194271](https://github.com/kunlong-luo/lanveriq/actions/runs/45616194271) 两个 job SUCCESS，且原生日志出现 `PASS: actual non-web Spring Boot/Hibernate started on recovered PG18 after independent restart`，才能将此阶段从 PENDING 改成 PASS；失败必须原样保留并修复。

这不是生产恢复能力。**G-02/G-08** 的真实跨主机备份保管、独立受托方、加密密钥、可信时间/高水位、生产 RPO/RTO、真实 SRE/安全审批，以及 **G-06** 的合法生产用户/RBAC、CA/MFA、完整界面切换仍未完成。

[当前项目状态](../CURRENT_STATUS.md) · [备份操作边界](../operations/backup.md) · [唯一发布门禁](../planning/release-gap-register.md)。**v0.1 NOT RELEASE READY。**
