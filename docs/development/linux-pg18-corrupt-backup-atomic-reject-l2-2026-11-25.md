# G-08 PostgreSQL 18 损坏/截断备份拒绝与事务原子性

**2026-11-25 · L2 PENDING · v0.1 NOT RELEASE READY。**

## 基线及本次功能 SHA

上一阶段 [SIGKILL/WAL → pg_dump → 新 PG18 数据库恢复](linux-pg18-wal-backup-fresh-restore-l2-2026-11-25.md) 已在功能 SHA `b64a14ad` 的 [Linux #45586283694（两 jobs SUCCESS）](https://github.com/kunlong-luo/lanveriq/actions/runs/45586283694) 验证；真实日志显示 `custom pg_dump from WAL recovered PG18 -> fresh no-network PG18 restore has identical inventory, child counts and Flyway history`。

**本次功能 SHA：** [`3687edd7`](https://github.com/kunlong-luo/lanveriq/commit/3687edd70df087d7b69e7d061407de49fb941688)；[真实 Linux L2 #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)；[Server unit #45587202694 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45587202694)。

## 在现有全链脚本中新增的实际负测

入口：[verify-agent-upstream-tls.sh](../../apps/server/scripts/verify-agent-upstream-tls.sh)。仅在一次性 Ubuntu Linux 合成环境，复用 PostgreSQL 原生 `pg_dump`/`pg_restore`/`psql`、`sha256sum` 和 Python 标准库。

1. 从**已经成功恢复验证**的 PostgreSQL 18 自定义备份复制两份 0600 私有坏夹具：`PGDMP` 头被破坏；以及截取原档的一半。原归档哈希保持不变，坏归档与原哈希必须不同。
2. 对坏头调用原生 `pg_restore --list`，必须由真实格式错误拒绝，不能拿参数错误/容器不可用冒充拒绝。
3. 在恢复容器内另建全新空库 `lanveriq_corrupt_probe`，用原生 `pg_restore --exit-on-error --single-transaction` 尝试导入截断归档，必须非零退出并显示真实 `pg_restore` 错误。
4. 验证失败恢复的库不存在 `public.device_inventory_current`，且之前成功恢复的数据库仍保留正确 `snapshot_id / revision / received_at / digest`。
5. 现有 Rust SQLite 待发请求→真实 mTLS exact ACK、凭据撤销/过期拒绝、Next.js Chromium 手动读刷新仍须完整通过。

**安全边界：** 不上传归档或数据，不运行真实设备、不开放生产网络/自动采集/远控、不执行安装/Polkit；所有归档、密钥与摘要仅由**同一 CI 管理域**生成，不能称独立备份、WORM、防特权篡改或生产灾备。

## 首轮失败与修复

- [初始功能 `f77d8454`](https://github.com/kunlong-luo/lanveriq/commit/f77d84549a738cc012080971518ce5aa64e15b37) 的 [真实 Linux L2 #45587202877 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45587202877)：PG18 transaction job SUCCESS，但完整链在夹具生成时报 `FAIL: corrupt archive fixture not distinct`。原因是测试错误地要求**修改归档头必须缩短长度**；等长内容变化是正确场景。本次没有到达真正的 pg_restore 拒绝测试，不得计 PASS。
- [修复功能 `3687edd7`](https://github.com/kunlong-luo/lanveriq/commit/3687edd70df087d7b69e7d061407de49fb941688) 改为比较真实字节是否不同，并保留两种损坏类型；[Linux #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)，须重新验收两项真实 jobs。

## 第二轮失败与原生错误措辞修正

- 修复版 `b6957916` 的 [Linux #45587871391 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45587871391)：PG18 事务 job SUCCESS，Rust/Linux 全链已到达原生损坏文件 `pg_restore --list`，**命令按预期拒绝**，但脚本仅接受固定英文关键词而输出 `FAIL: invalid backup header rejection was not a format failure`；没有完成后续截断/原子性验证，不能标 PASS。
- [新修正功能 `3687edd7`](https://github.com/kunlong-luo/lanveriq/commit/3687edd70df087d7b69e7d061407de49fb941688) 改为只接收 `pg_restore: error:` 真实原生诊断，不限制错误正文措辞，且诊断缺失时打印前 10 行以便排错。[Linux #45588502849 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849)。原先两次真实失败及其功能 SHA 均保留。

## 退出与剩余阻塞

只有 SHA `3687edd7` 对应的 [Linux #45588502849](https://github.com/kunlong-luo/lanveriq/actions/runs/45588502849) 两个 jobs 均 SUCCESS，且原生日志有损坏头、截断归档、原子失败恢复以及正常库内容保持不变的明确 PASS，才能将本阶段标 PASS。失败/取消均须保留原始证据。后续 G-08 需跨主机保管、加密/独立密钥、备份定期演练、RPO/RTO 与 SRE 签收。

[当前项目事实](../CURRENT_STATUS.md) · [G-08 发布 Gate](../planning/release-gap-register.md) · [唯一 Linux 路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)。**G-08 OPEN · v0.1 NOT RELEASE READY。**
