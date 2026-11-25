# Linux 清单：PG18 已提交但 ACK 未交付 → SIGKILL/WAL 恢复 → Rust SQLite 精确重送

**2026-11-25 · 功能提交，Linux L2 验收 PENDING · v0.1 NOT RELEASE READY。**

## 前序证据与这次新增的问题

[ACK 未交付给 Rust 而 PostgreSQL 已成功提交的 L2 实验](linux-inventory-ack-withheld-replay-l2-2026-11-25.md) 在功能 SHA f83aeab6、[Linux #45581861955 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)、[Agent #45581861744 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861744)、[Server #45581861736 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861736) 已经通过：CI 经真实 mTLS 发送 Rust SQLite 原始 revision 2 JSON，PG18 应用后不把 ACK 交给 Rust；Rust 重新打开原待发请求取得 duplicate ACK，PG18 行不变。这仍是合成 ACK withheld，不是真实 TCP 断线。

新的 G-06 增量验证另一个临界窗口：**数据库已经提交，但 Rust 未见 ACK 期间 PostgreSQL 实际崩溃，然后从 WAL 恢复，原请求才被重送。**

## 代码与严格退出条件

- 功能 SHA：[9ee4681ec4b7689fd0e7b2f0ef1251889c196cc3](https://github.com/kunlong-luo/lanveriq/commit/9ee4681ec4b7689fd0e7b2f0ef1251889c196cc3)。只修改现有 [真实隔离 Linux 链路脚本](../../apps/server/scripts/verify-agent-upstream-tls.sh)，不改 Java/Rust 产品逻辑、不增加守护进程、自动重试、采集或新协议。
- Linux L2：[GitHub Actions #45583252985](https://github.com/kunlong-luo/lanveriq/actions/runs/45583252985) **PENDING**；同功能 SHA [Server unit #45583252967 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583252967)，[RD-2 #45583252958 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45583252958)。
- 仅 disposable Linux CI + 合成业务链 opt-in 可以执行故障注入。PG18 容器临时只绑定回环地址，保留可重启的容器 ID，但在脚本退出时强制删除；生产数据库绝不可作为目标。
- 真实 Caddy/TLS1.3/mTLS POST 首次拿到 200/applied，且通过 PG18 直接查询 snapshot_id、inventory_revision=2、received_at、content_digest，确保 **commit 先于故障**。
- 用 docker kill --signal=KILL 让同一真实 PostgreSQL18.7 容器非优雅退出，检查其 Running=false；docker start 同一容器，限定 120 秒内恢复 pg_isready，保留原 Docker 映射端口。恢复后用真实 psql 逐字比较当前行四字段，严禁数据消失或改变。
- 原 Rust SQLite outbox 仍保持同 snapshot/revision/JSON；由真实 Rust Reqwest 重新发送取得 duplicate strict ACK 后才允许清理本次 pending，PG18 当前行保持不变。继续原有过期/撤销拒绝、迟到旧版本/冲突拒绝、Chromium 授权显示与 Testcontainers PG18 工作流。
- **本增量只有相同功能 SHA 的 Linux Batch A 两个 jobs 实际 SUCCESS 才可标记 L2 PASS**，失败/取消或文档 CI 不能冒充验收；若失败保留原 run 和修复提交历史。

## 不能推导的授权

该试验仅证明同一 Docker 容器数据在 SIGKILL 后重新启动；不证明跨机灾备、磁盘完全丢失、外部备份、真实网络 ACK 线路丢包、RPO/RTO、生产 WORM、独立高水位或真实 Pop!_OS 24.04 COSMIC 实机。没有正式 Agent 安装包或合法生产数据采集授权。**G-06/G-08 仍 OPEN，v0.1 NOT RELEASE READY，生产联网、采集、自动恢复、远控和 CA 签发 OFF。**

[当前状态](../CURRENT_STATUS.md) · [实施路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [发布 Gate](../planning/release-gap-register.md)。
