# Linux 隔离清单 ACK 未交付给 Rust → PG18 已提交 → 原 SQLite 请求重送

**2026-11-25 · 功能已提交，原生验收 PENDING · v0.1 NOT RELEASE READY。**

## 真实缺口与此次实验边界

之前两轮有 [断网后 ACK 恢复](linux-inventory-reconnect-exact-ack-nextjs-l2-2026-11-25.md) 和 [恢复后迟到重复、旧版及冲突不变性](linux-inventory-post-reconnect-idempotency-l2-2026-11-25.md) 的隔离 Linux L2 PASS，但二者都没有覆盖**服务端已应用了一个仍在 Rust SQLite outbox 中的请求、而 Rust 尚未收到该请求的 ACK**的临界窗口。

本轮功能 SHA [`f83aeab61199fa59256e3422c84a34b34383ab91`](https://github.com/kunlong-luo/lanveriq/commit/f83aeab61199fa59256e3422c84a34b34383ab91) 只修改现成 [Rust 一次性 CI 示例](../../apps/agent/examples/linux_batch_a_http_synthetic.rs) 与 [真实 Caddy/PG18 Bash 联调脚本](../../apps/server/scripts/verify-agent-upstream-tls.sh)。不新增守护进程、重试定时器、后台采集、root 安装 broker 或面向产品的业务路由。

**必须区分：** 该实验由测试驱动单独发起首次真实 HTTPS POST、故意**不向 Rust 交付** HTTP ACK，属于**合成的 ACK withheld**，**不是**在实际网络层证明“PostgreSQL commit 后到 ACK 传输中途 TCP 被断开”。仅用于验证客户端“未见 ACK”的 durable outbox 语义。

## 断言顺序（同一 disposable Ubuntu 24.04 runner）

1. 真实 Caddy 停止时，Rust Reqwest 发送 revision 2 失败；SQLx SQLite 重新打开后，pending 的 JSON/UUID/revision 不变；CI 仅把**原本持久化的同一段请求字节**写入 runner 私有临时文件。
2. Caddy 恢复后，CI 驱动携带与 Rust 相同合成客户端证书，向真实 TLS1.3/mTLS → Spring → PostgreSQL18 发送那段原始 JSON；断言首次 HTTP `200 / applied` 且不缓存、PG18 current revision 确实由 1→2；响应 ACK **不回传给 Rust**。
3. Rust 重新从 SQLite 打开原 pending，执行一次真实 Reqwest POST；Spring 应按同 snapshot/revision/content 返回幂等 `duplicate`，Rust 现有 strict ACK 校验才能清理 SQLite；PG18 snapshot_id/revision/received_at/digest 不因第二次 POST 改变。
4. Rust SQLite 再次打开后仅 revision 2 已结清，新 revision 3 仍待发；保持过期/撤销叶拒绝、Web Chromium 授权读取和迟到/冲突断言，均沿用既有 CI 测试。
5. 只有同一功能 SHA 的 **Linux Batch A 两 jobs SUCCESS**、Agent format/test 与 server unit 的实际结果齐备，才将本轮实验标记 `L2 PASS`；任何 queued/failed/cancelled 不得借历史通过冒充。

## 待验证 CI

- [Linux Batch A #45581861955](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861955)：**PENDING**（包括真实 PostgreSQL/Testcontainers 与真实 Rust→Caddy→PG18→Chromium 两 jobs）。
- [Agent unit / rustfmt #45581861744](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861744)：**PENDING**。
- [Server unit #45581861736](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861736)：**PENDING**。
- [RD-2 #45581861814](https://github.com/kunlong-luo/lanveriq/actions/runs/45581861814)：**SUCCESS**；防远控回归，不等于上述业务链验收。

## 不能推导的授权

不是真实 Agent 安装后的自动恢复，也没有正式设备认证、可信发行身份、受限 Polkit、Pop!_OS COSMIC L3、合法员工采集授权、生产 CA/MFA 或独立 WORM。无证据更改 G-01～G-10 的 OPEN 状态。**v0.1 NOT RELEASE READY；生产联网、自动采集、远控、恢复、CA 签发 OFF。**

[唯一事实](../CURRENT_STATUS.md) · [唯一发布门禁](../planning/release-gap-register.md) · [Linux 路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)。
