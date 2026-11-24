# Linux CI 主机真实 OS/CPU/内存的只读库存到 Next.js L2 验收

**PASS — ISOLATED CI HOST ONLY · 2026-11-24 · main-only · v0.1 NOT RELEASE READY**

## 可信来源与复验定位

- **精确功能源码 SHA**：[e6b56c88de18a8d751e5736483bd90e0296cab06](https://github.com/kunlong-luo/lanveriq/commit/e6b56c88de18a8d751e5736483bd90e0296cab06)。
- **一次性 Ubuntu 24.04 runner 真实同运行**：[Linux Batch A #45365942781](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)，attempt 1、**overall SUCCESS，两个 jobs SUCCESS**。同源码 [Agent Unit #45365942721](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942721) **SUCCESS**；[RD-2 fail-closed #45365942726](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942726) **SUCCESS**。
- 此次不是固定 `Synthetic Inventory Linux / Synthetic Inventory CPU / 8 GB` 的上一阶段测试：真实运行的 Linux runner 上由现有 Rust `sysinfo 0.37.1` 做**仅 OS/CPU/内存白名单采样**，经同一 Rust Agent 真实库存协议送达。仍使用合成 UUID、测试根和隔离 Caddy、临时 Next.js 页面；**没有真实员工或用户设备**。

## 本阶段真正打通的链路

1. `apps/agent/src/inventory_platform.rs` 新增 `collect_minimal_linux_inventory()`，在仅明确启用的 CI 示例调用：用 `sysinfo` 的窄范围 CPU/memory refresh，以及 `System::name/os_version/kernel_version` 获取发行版、CPU、内存；显式将 `interfaces=[]`、`volumes=[]` 和网卡/挂载/桌面会话/制造商能力标记为 `not_collected`。不读取网卡地址、MAC/IP、磁盘列表、主机名、进程、用户、应用活动或文件。
2. Python 独立来源核验仅在 runner 内将实际 Rust outbox 请求中 OS 的 NAME/VERSION_ID 对照 `/etc/os-release`，内核/arch 对照 `uname`，CPU brand 对照 `/proc/cpuinfo`，总内存对照 `/proc/meminfo`，并确认身份、原始消息边界和禁采能力字段。**PASS**；日志不打印原始采样值或临时请求内容。若有错误只输出源码固定标签，失败即阻断。
3. 同一次 runner 中 Rust `OutboxStore` SQLx SQLite 保存真实样本，重启后通过 `AgentTlsTransport::send_next_inventory` 发往 `agent.lanveriq.internal` 的**仅本机 loopback DNS**，真实 Reqwest/rustls TLS1.3 → Caddy 客户端 mTLS → Caddy 到 Spring/Tomcat 独立上游 mTLS → 激活已注册合成凭据与 JPA 当前身份锁 → PostgreSQL 18.7 `device_inventory_current`。返回严格匹配 device/snapshot/revision 的 ACK 后，本地 pending 精确清除。当前库中只存在同一源设备的最新快照。
4. 独立网络/身份/版本负测均 PASS：相同 POST 请求在真实 Spring 返回 `duplicate` 且不改变 PG snapshot/revision/received_at/digest；同 revision 不同 snapshot UUID 返回空正文/no-store `409` 且不改写；另一个同 CA 但未注册证书返回 `403`；实际**停止隔离 Caddy** 造成 Rust Reqwest 网络连接失败，SQLite revision 2 的未发送待决和重启数据一致；证书分别过期、吊销后，同一 Rust 库存发送均拒绝并保留 exact pending，PG current 完全不变。
5. 同设备的 Spring 当前组管理员会话 + 受限列表/详情 JSON 返回从这条 PostgreSQL 快照获得的 `os_name/cpu_model/memory_total_bytes`；真实 Chromium 在导入专有测试 CA 且**不跳过 TLS 校验**的临时 Next.js `StagedConsoleWorkspace` 中显示由该 Rust 源消息证明的 OS/CPU/内存字段。跨组详情 `404`，登出清空 DOM，随后读取 `401`。无伪造 API、SQL 填充库存或静态模拟 React 字段。

## 历史失败与根因

- [#45364454533 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45364454533)：Rust 首次实际只读库存成功采样并获得 ACK，但独立 host-facts 核验不匹配；此前日志未公开内部字段名称。
- [#45365163380 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45365163380)：固定的安全门禁诊断收敛到 `Rust Linux OS differs from the actual CI host`；确认 `os_info` 对某些 Linux 发行版版本号进行语义数字化处理，会丢失 `24.04` 中带前导零的原始编号。修复：在**新的最小化 Linux 路径**改复用已有 `sysinfo::System::name()` / `os_version()`，获取 OS release 规范字段，独立逐项核对 NAME、VERSION_ID、kernel、arch，不取消任何拒绝条件。保留旧功能及失败历史。
- [#45365942781 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)：修复后全部真实端到端正反例及真实 PG18、浏览器同源闭环通过。

## 不可推论的正式能力与下一安全门禁

- **绝不视作真实客户/员工/Pop!_OS 设备的采集批准**。采样设备是一次性 GitHub Ubuntu 24.04 CI runner，而不是物理 Pop!_OS 24.04 COSMIC。仍缺独立法务/隐私/运营批准、物理机权限和性能验收。
- 正式 Agent daemon **没有接入这段只读采样函数**；它只由在 GitHub Linux runner 下经 `GITHUB_ACTIONS=true`、`RUNNER_OS=Linux`、`LANVERIQ_SYNTHETIC_INVENTORY_CHAIN=1` 与 `LANVERIQ_SYNTHETIC_LINUX_READONLY_SAMPLE=1` 双重 CI-only 条件运行的显式示例调用。未启用生产联网、定时采集、自动恢复、远控、证书签发、WORM 或默认后台数据上报。
- 正式 Next.js 登录/设备入口仍未批准：本次仅隔离 runner 临时路由，未将其合并到产品运行时页面。新 L2 不意味着真实 Pop!_OS 机器上的普通用户 UI 完成。
- **下一优先工程工作**：先评审正式授权的真实 Pop!_OS 24.04 COSMIC 实机 L3 与普通用户 Tauri GUI 安装；依成熟 PackageKit/Polkit 受限 broker、独立发行签名和限定动作完成首次授权、取消/拒绝、卸载及后台无托盘图标的可发现状态。任何真实采样或联网还需独立管理批准，不能由 CI 开关变成部署功能。与 G-02 真正独立 WORM、G-07 法务、G-08 运维验收分开安排。

**结论：isolated real Linux OS/CPU/RAM → SQLite → Reqwest/mTLS → Spring/JPA → PostgreSQL18 → scoped Next.js/Chromium 的子项 L2 PASS。G-02/G-05/G-07/G-08/G-09、正式 G-06 均 OPEN；v0.1 NOT RELEASE READY。**
