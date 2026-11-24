# INV-FULL-CHAIN-L2：Rust 合成库存经 mTLS、Spring、PG18 到 Next.js 的单次实链验收

**VERIFIED SYNTHETIC L2 · 2026-11-24 · main-only · NO PRODUCTION APPROVAL · v0.1 NOT RELEASE READY**

## 实际验收证据

- **精确功能源码 SHA**：[addd768515b26432f270760adef6807670175226](https://github.com/kunlong-luo/lanveriq/commit/addd768515b26432f270760adef6807670175226)
- **真实 GitHub Actions**：[Linux Batch A #45362450357](https://github.com/kunlong-luo/lanveriq/actions/runs/45362450357)，**attempt 1 overall SUCCESS；两个 jobs 全部 SUCCESS**（Ubuntu 24.04 一次性 runner；Rust 1.100、Java 25、Spring Boot 4.2、PostgreSQL 18.7、Caddy 2.11.7、Node 24/Next.js 16、真实 Chromium）。
- **一条同设备链**：Rust 单次显式合成设备将固定的、非个人数据的 `RawInventory` 按既有严格 V0.1 模型归一化，并写入 SQLx/SQLite durable outbox；关闭/重新打开 SQLite 后，经已有 Rust Reqwest/rustls HTTPS TLS1.3 → Caddy Agent mTLS → Caddy 到 Spring/Tomcat mTLS → `DeviceInventoryService` / JPA 的真实 PostgreSQL 事务存入 `device_inventory_current`，收到匹配 device/snapshot/revision 的 HTTP 200 ACK 后才删除本地待发送条目。再次打开 SQLite，同语义快照标记 `Unchanged`，不会自动重发。
- **后端和网络核实**：同一 PostgreSQL 实例断言 `inventory_revision=1`、`snapshot_id`、`received_at`、`os_family=linux`、`os_name=Synthetic Inventory Linux`、`cpu_model=Synthetic Inventory CPU`、`memory_total_bytes=8000000000`；使用真实客户端证书和相同库存 JSON 再次 POST，Spring 返回 `duplicate`，并验证 `snapshot_id/revision/received_at/digest` 均未改变。使用同 CA 但未注册的 Agent 叶证书 POST 返回空正文的 403。
- **真实授权展示**：此前通过的同设备心跳/授权读链继续在此运行成功。测试中的 Spring Console Cookie/CSRF 登录、当前组 scope 受限设备列表/详情/监控 API 返回同一设备及库存字段；真实 Chromium 导入临时 NSS 根、校验 TLS CA，在 Caddy 同源 HTTPS 上打开**仅在 CI 内临时生成**的 Next.js `StagedConsoleWorkspace`，验证唯一设备行实际显示 `Synthetic Inventory Linux`、`Synthetic Inventory CPU`、`8 GB`；无权设备详情 404，管理员登出后 DOM 清空且读 API 401。无伪造 HTTP 响应，也不把另一个 SQL fixture 或固定 React 数组冒充同一设备。
- **已复用安全负例**：测试整体仍执行 Agent TLS1.2-only/无或错误证书、错误代理上游证书、请求重放和凭据过期/吊销的**心跳**拒绝；这些现成负例不能夸称为库存的每一种专用吊销边界都已覆盖。

## 本轮源码变化与拒绝原则

1. Spring 新增独立默认关闭的 `lanveriq.agent.inventory.http-enabled`；`AgentInventoryRestController` 与精确路径无状态安全链，必须同时通过已存在的 `lanveriq.agent.ingress.assertions-enabled`、实际 Caddy/Tomcat TLS 断言及 JPA 当前有效设备凭证锁；没有改为直接 JDBC，也未新增另一套传输协议或存储。
2. Rust `linux_batch_a_http_synthetic.rs` 仅在 `LANVERIQ_SYNTHETIC_INVENTORY_CHAIN=1` 且明确的 GitHub/Linux/full-chain 隔离环境中使用现有 `OutboxStore::stage_inventory_snapshot`、`verified_pending_inventory`、`AgentTlsTransport::send_next_inventory`。不在正式 Agent 中安排定时上传、采集循环或开机自动连接。
3. Next.js 只扩展已有受权设备行的 `os_name/cpu_model/memory_total_bytes` 有界投影与非个人信息显示；没有启用正式 `/login`、`/devices` 的生产数据入口。新增字段继续严守非负、JS 安全整数、字符串长度和不透传 NIC/IP/用户信息。
4. CI 扩展现有 [Linux Batch A](../../.github/workflows/linux-batch-a-caddy-pg18-acceptance.yml)，而不是复制另一条孤立密码学实验流水线；运行前仍先执行真实默认关闭/错误 TLS 拒绝，随后用 CI-only 独立开关启动库存路由。

## 严格的仍未完成项

- **真实 Linux 系统信息采集 NOT RUN**：OS/CPU/内存值均为硬编码**合成实验材料**，没有读取员工/用户设备、网络接口、MAC/IP、进程、软件列表或实际文件系统。源码测试中的 `hardware_inventory` capability 不是对生产采集能力的授权。
- **非合成 Agent 守护进程与首次注册 NOT RUN**：没有 Pop!_OS 24.04 COSMIC 物理设备、受限 Polkit 授权、正式签名 GUI 包或受信任生产 CA；没有生产用户登录或证书签发。
- **进一步 L2 负向边界待补**：库存路径专属的过期/吊销后 POST、变更版本冲突/旧版本忽略、服务失败后 outbox 待决不清除、不同管理员切换后的 CPU/内存缓存清理，均不得由心跳或其他子测试代替；在引入真实设备采集前补上。
- **下一阶段 P0**：在管理员/隐私范围批准的独立 Ubuntu 24.04 实验 runner 上，以现有成熟 Rust/Linux 系统 API 做极小白名单的**只读实际 OS/CPU/内存采样**，对照系统事实，经明确手动触发的同一 outbox 机制做独立验收；不要默认部署 osquery、第二个守护程序或开放任意采集查询。另行推进 GUI 安装 Polkit/发行签名/真实 Pop!_OS COSMIC L3 的独立发布门禁。

**结论：INV-FULL-CHAIN-L2 固定合成数据端到端 PASS @ addd7685 / #45362450357。生产设备采集、管理员正式登录、用户 GUI Agent 安装和 Pop!_OS L3 均 NOT APPROVED。G-02/G-05/G-07/G-08/G-09 和 G-06 正式业务发布门禁仍 OPEN；v0.1 NOT RELEASE READY。**
