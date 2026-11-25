# Linux 断网恢复后延迟重送与当前版本保持 — L2 增量

**2026-11-25 · 验证状态：PENDING（不是 PASS）· v0.1 NOT RELEASE READY。**

## 为什么要做

先前 [断网保留→恢复准确 ACK→PostgreSQL 18→Next.js 真实 Chromium 链路](linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) 在功能 SHA `db570181`、[Linux Batch A #45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 两项真实 L2 jobs **SUCCESS**，但主要证明 revision 1 首次重复请求和 revision 2 恢复成功；尚未在恢复后用**原始网络请求**再次打到实际 mTLS/PG18，验证 ACK 结清后的延迟重复、老版本迟到和同版本不同内容。

本次只增加[隔离 Linux Caddy/PG18 执行脚本](../../apps/server/scripts/verify-agent-upstream-tls.sh)的真实 HTTP/数据库断言，不修改 Java/Rust 产品服务、发行能力、生产开关或自动重试器；生产自动采集、联网、远控及正式证书签发维持 OFF。

## 精确变更与预期退出断言

- **功能 SHA：** [`9ba853991245765eaf313d3b99d9707c01373942`](https://github.com/kunlong-luo/lanveriq/commit/9ba853991245765eaf313d3b99d9707c01373942)；使用真实 Linux runner [#45571463449（L2，当前 PENDING）](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463449)，额外 [server unit #45571463462（PENDING）](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463462) 与 [RD-2 #45571463408（PENDING）](https://github.com/kunlong-luo/lanveriq/actions/runs/45571463408)。
- **恢复成功后相同 revision 2 迟到的原始请求：** Rust outbox 已因准确 ACK 清空后，在同一个真实 Caddy/TLS1.3/mTLS/Spring/PG18 路径重复 POST 原始 revision 2 JSON；要求 HTTP 200、`no-store`、`duplicate`、相同 device/snapshot/revision，且 PG18 当前行的 snapshot、revision、`received_at` 与 digest **逐字不变**。
- **旧 revision 1 晚到：** 再次经同一 mTLS 路径发送原始 revision 1 JSON；要求 `200 / stale_ignored`，receipt 必须指向**当前 revision 2 和其 snapshot_id**，而不是使 PG18 回退。PG18 四字段逐字不变。
- **同 revision 2 不同 snapshot_id：** 只在 CI 临时请求里变更 UUID 形成冲突；要求 `409`、空响应体与 `no-store`，PG18 四字段逐字不变。
- 完成后仍进行现有过期/吊销凭据下 revision 3 SQLite 保留测试和 Chromium 授权手动读取；仅当 **同一功能 SHA 的 Linux Batch A 两 jobs 都是 SUCCESS** 才能把本增量标为 L2 PASS。

**边界：** 该测试在一次性 Ubuntu 24.04 runner 和合成设备身份下运行。没有真实 Pop!_OS/COSMIC 端点安装、正式 CA、合法采集授权、生产 MFA/WORM 或独立高水位；不是设备自动恢复、产品端 UI 新功能或上线许可。若 CI 失败/取消/未运行，按真实结论保留证据，不能复用 `db570181` 的历史 SUCCESS 冒充本轮验收。

[当前唯一事实](../CURRENT_STATUS.md) · [G-06 发布门禁](../planning/release-gap-register.md) · [Linux 实施路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)。
