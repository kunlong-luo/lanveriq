# Linux 清单断网恢复 → 精确 ACK → PG18 → Next.js 手动刷新 L2

**验收状态：PASS（2026-11-25，真实 Ubuntu 24.04 disposable runner L2；两个 jobs SUCCESS）· v0.1 NOT RELEASE READY。**

## 任务与真实源码范围

这轮只填补现有[真实 Linux OS/CPU/RAM 全链 L2](../testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)的**断网后恢复成功路径**。原链已经证明 Rust/SQLite 将 revision 2 保留、过期/撤销身份拒绝，却还没有证明恢复 Caddy 之后 **revision 2 原始 outbox** 可以成功重新入库、精确 ACK、浏览器再读取。

- **功能源码 SHA：** [`db570181d603a7f069a85a23c009658e9a8bed4c`](https://github.com/kunlong-luo/lanveriq/commit/db570181d603a7f069a85a23c009658e9a8bed4c)。Rust 的早期实现 [`e46722a8`](https://github.com/kunlong-luo/lanveriq/commit/e46722a81446e33d768a8d2e54b3454ee9ae015f) 因 rustfmt 失败 [Agent CI #45554071256 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45554071256)；已经按原生检查差异修正到功能 SHA，**失败记录保留**。
- **本轮真实运行：** [Linux Batch A #45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) **overall SUCCESS，实际 PG18 事务 job 和 Rust→Caddy/mTLS→Spring→PG18→Chromium job 均 SUCCESS**；[Agent 单元/格式/Clippy #45554279741](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279741) **SUCCESS**。以上均取自相同功能源码 `db570181`；文档提交不改变功能 SHA。

## 代码实现与可复验门禁

| 顺序 | 实际新增范围 | 退出条件 |
| --- | --- | --- |
| 1. 本机只读采样 | [Rust CI-only 示例](../../apps/agent/examples/linux_batch_a_http_synthetic.rs) 保留仅 Ubuntu runner 的 OS/CPU/RAM 白名单采样；无 NIC、MAC、IP、盘、用户/员工、进程 | 非 CI/非显式双重 opt-in 无法执行、没有自动定时 |
| 2. 首次真实入库 | 同一临时 Rust UUID，真实 Caddy/Tomcat mTLS、Spring JPA PG18；SQLite revision 1 在精确 ACK 后清空并持久化 checkpoint | same-request 不重写、冲突 409、未注册叶 403 |
| 3. 真实断网 | [Linux Batch A 驱动](../../apps/server/scripts/verify-agent-upstream-tls.sh) 实际停止 Caddy，Rust revision 2 发送网络失败；SQLite pending 跨重启逐字节相同 | 无伪 ACK、无自动重试、PG18 current 仍为 revision 1 |
| 4. 明确恢复发送 | 驱动恢复本机 Caddy，额外显式 `recovered` Rust 测试阶段读取 **原有 SQLite revision 2**、发真实 mTLS POST；只接受准确 device/snapshot/revision 的 Spring ACK | Rust pending 清空、last revision=2，重启一致；原请求私有 JSON 可与 PG18 snapshot UUID 和 revision 精确匹配 |
| 5. 防误放后续队列 | 只在隔离环境新增未提交的 revision 3；之后分别令**同一凭据**在 PG18 过期、吊销，尝试发送 | 真实 HTTP 拒绝、revision 3 在 SQLite 重启后仍待发送；PG18 current 仍精确为 revision 2 |
| 6. 真后台读刷新 | [Chromium/Playwright L2](../../apps/server/scripts/verify-synthetic-rust-chain-browser.py) 不跳过 CA/TLS；先核对同一 source revision 2 的 DB 快照和白名单字段，经原 Spring 当前管理员组角色读，再按真实页面**「手动刷新」**并等待真实 HTTP 响应 | React DOM 只显示一个 authorized device，跨组 404、登出清空 DOM 和 API 401；刷新不是自动轮询 |

**强制隔离边界：** `GITHUB_ACTIONS=true`、`RUNNER_OS=Linux`、`LANVERIQ_SYNTHETIC_INVENTORY_CHAIN=1`、`LANVERIQ_SYNTHETIC_LINUX_READONLY_SAMPLE=1`；固定合成 UUID、仅本地 Docker PG18/Caddy、`/tmp` 私有请求和 NSS 临时信任根。正式 Agent 不接入此示例、后台采样或可恢复的定时器；Next.js 页面仅由 CI 在 build 前临时安装，工作树不出现正式身份/采集路由。

## 同一真实 L2 运行输出与范围

[Linux Batch A #45554279736](https://github.com/kunlong-luo/lanveriq/actions/runs/45554279736) 的原生 job 日志含独立、连续的固定 PASS：断开 HTTPS 保留 exact SQLite revision 2、恢复 Caddy 后旧 revision 2 收到真实 Spring ACK、SQLite 重启后 revision 2 已结清但 revision 3 待发、PG18 revision 2/current/snapshot ID 与 Rust 私有请求吻合、凭据过期与吊销都拒绝发送 revision 3 并保持 PG18 不变；Chromium 真实 HTTPS 登录后 Spring 当前组授权查询、Next.js DOM 和操作者手动刷新均 PASS，跨组 404、登出 401。没有跳过 TLS、没有伪造 API、没有设备真实数据、没有正常产品入口改动。

## 不能推导的权限

即使上述 CI 最终 PASS，只证明**同一隔离 Linux 主机源数据在断网/恢复/身份撤销下的事务行为**；不是消费者 Pop!_OS 24.04 COSMIC 真机、正式信任根、管理员 MFA、合法员工采集、真实 root Polkit broker、自动恢复机制、官方 WORM 或产品联网。更新的清单内容因只增加测试标记而 OS/CPU/RAM 值保持相同；浏览器检验新的 PG18 版本来源及显式手动 reread，不能宣称 UI 显示了不同的 CPU/RAM 数值。

**发布：G-01～G-10 仍 OPEN；v0.1 NOT RELEASE READY；生产联网、采集、远控、自动恢复、签发 OFF。**
