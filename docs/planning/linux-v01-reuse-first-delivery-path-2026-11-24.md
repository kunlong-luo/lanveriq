# Lanveriq Linux v0.1：优先复用的真实业务闭环交付路线（2026-11-24）

**ACTIVE DELIVERY PLAN · main-only · Pop!_OS 24.04/COSMIC priority · v0.1 NOT RELEASE READY**

这份计划替换“每一轮继续新增一组独立密码学实验”作为默认开发方向。安全默认拒绝、外部法务/密钥/WORM 审批、隐私授权及 Pop!_OS 物理验收仍是硬门禁；但工程产出的首要评价指标改为：**普通管理员能否以 GUI 理解安装权限，得到一台可核实的测试 Agent 状态，并在 Web 控制台看到实际的已授权合成设备记录**。只有真实证据才允许提升验收等级。

## 优先级 P0：先把已经写好的零件真正连接起来

| 当前代码/成熟能力 | 直接复用而不造轮子 | 本项目最小集成工作 | 单独成功判据与不可混淆的 Gate |
| --- | --- | --- | --- |
| Rust Agent 二进制、systemd 单元、原生 Agent `.deb` 候选 | **Cargo/Rust、dpkg、APT、systemd** | [G05-LNX4](../development/agent-linux-real-dpkg-disposable-lnx4-2026-11-24.md) 在固定 Ubuntu OCI 内执行**真正 dpkg install/同版 reinstall/remove**；收据必须证明包归属和配置/状态保留 | 仅 OCI L2、Agent inactive；**不等于发行签名/Polkit/Pop!_OS L3** |
| 普通用户 GUI 目前 Tauri 2 只读 | **Tauri 2 + Base UI + 系统 Polkit/PackageKit/受限包管理能力**（候选） | 先评审产品如何限定“唯一受信任 Agent 包/固定来源/固定动作”，由独立可审计 broker 每次重验，不允许 Tauri root 或任意 argv/URI；授权后才实现 GUI 安装 | 具名安全审查、拒绝/取消/撤销和受限执行 L2，**用户可用 GUI 安装/卸载**，真实 L3 后再关闭 G-05/G-09 |
| 已有 Rust TLS1.3/reqwest、SQLite durable outbox、Spring/JPA/PG18、Next.js/Query | **现有成熟 HTTP/TLS/JPA/SQLite/React 组件** | 不换协议；用具名实验设备和合成消息跑注册→激活→心跳 ACK→清单 outbox→数据库查询→Web 真实读模型，严禁虚构设备 | **同一次合成授权执行链**，断网/吊销/重复 ACK/越权/跨组全部拒绝或安全恢复；真正设备/生产联网需独立授权 |
| 已有硬件/OS/NIC 采集源代码 | **sysinfo/os_info/network-interface**，探索 [osquery](https://github.com/osquery/osquery) 在精确白名单读取 OS/软件库存的适配 | 先核对 osquery 的 Linux 安装/运行方式、许可、字段范围、性能和隐私；如可复用仅提供固定只读查询表、版本 pin、禁止任意 SQL 和进程/用户信息 | 真实隔离 OS 采集与静态固定字段对照，**无生产/员工采集**；不需引入第二个未经证据批准的 Agent 常驻服务 |
| 软件更新/发行 | **官方 `apt-secure` + deb822 `Signed-By`**、按需评估 Aptly 的仓库管理 | 生产仓库根由独立发行方审批/分发；固定来源/包名、最小权限安装事务，版本、撤销、合法回滚策略；绝不直接粘贴测试私钥 | 仓库身份独立证明 + 隔离真实 Agent 包安装 + 真实 Pop!_OS24 L3；L2B-20 的合成元数据 9/9 不能替代 |
| 非生产授权运维/审计 | **现有 PG18/Flyway/备份工具**，独立 WORM 只在选型签字后接入 | 最少具名审阅者、真实撤销、外部托管密钥与不可回退介质恢复 | G-02/G-07/G-08 的行政域、合法性、恢复都需要**实物与人员签署**，CI 不能自证 |

## 已锁定下一项集成验收：FULL-CHAIN-L2（2026-11-24）

**优先级 P0；SPEC 已提交，执行未通过。** [单设备端到端验收契约](../testing/agent-to-nextjs-single-device-l2-2026-11-24.md) 规定真实 Rust 请求经**同一** Caddy/Tomcat mTLS、**同一** PostgreSQL 18 事务后，由 Spring 当前管理员/设备组授权 API 返回到 Chromium 中的 Next.js 合成候选页面。当前 [Linux Batch A](../../.github/workflows/linux-batch-a-caddy-pg18-acceptance.yml) 写入模式明确关闭 Console；既有 Next.js/Chromium 合成读使用另行初始化的数据，因此**不得把两条成功证据相加当作一条通过**。

具体下一次源码改动先做：在现有隔离脚本下通过具名 CI-only 开关联动 Rust 业务与 Console，复用首管理员 bootstrap/设备组 scope，而不是放开正式后端；然后把同一设备 UUID、实际心跳 request ID、PG 行、Spring 受权响应和 Next.js DOM 做强关联验收。真实库存发送与展示列为第二个完整链路里程碑，既不将静态 fixture 当真实提交，也不增加默认密码学测试。所有结果标注精确功能 SHA、运行链接及未执行项。

**本次文档决策不改变发布 Gate，不批准正常 Next.js 页面访问业务、生产 Agent 网络、采集、自动恢复、远控、正式 CA 或普通用户 GUI 安装。G-02/G-05/G-07/G-08/G-09 OPEN，v0.1 NOT RELEASE READY。**

## P1：避免重复引入功能引擎

- **RustDesk**：沿用现有隔离服务端试验与成熟客户端/NAT/Relay 技术；远控仍默认关闭，独立许可（AGPL-3.0）、管理员实时审批、设备侧授权、会话审计及 Pop!_OS COSMIC/Wayland 实测。**不另造远控音视频/输入协议**。
- **Alloy/OpenTelemetry**：仅在真的需要跨机器资源指标时再作候选；Linux v0.1 的库存与心跳不要求同时部署 osquery、Alloy、另一套 Agent 和重复可用性监测。先定义数据最小化、采样资源消耗和保留期限。
- **Fleet**：作为“是否整体替代现有资产管理底座”的单独 PoC 比较，检查数据模型/API、许可证的 OSS/商业功能边界、Pop!_OS Agent 兼容、迁移成本；没有替代决策就不把 Fleet 和 Lanveriq 两套设备控制平面并行部署。
- **复杂报表、长期录屏/应用活动**：不是首个授权 Linux 设备闭环的前置。远控/录制必须在相应隐私、法律和专用安全 Gate 完成后独立推进。

## 执行顺序及合并规则

1. 完成 **G05-LNX4 原生 dpkg 实装隔离 L2** 并保存同源码、真实 Ubuntu OCI、dpkg status/ownership、reinstall/remove、operator state 保留证据；失败就修复，不能仅新增模拟检查。
2. 将业务关注点从多轮独立元数据小实验转到 **Agent→TLS→Spring/JPA/PG18→Next.js** 的具名合成端到端真实数据流；沿用现有拒绝优先规则，仍不许生产开关或真实采集。
3. 并行仅做**必要的外部责任人决策**：真实发行者/仓库专用 root、合法签字、WORM 独立管理域、最小权限包管理 broker 审批及 Pop!_OS24 具名试验机器。任何批准前，代码仍保持 OFF。
4. 获批准后，完成受限 GUI 安装与被审 Agent 包升级/卸载的 L2 → Pop!_OS/COSMIC L3；再考虑可选的 osquery 资产库存、指标与远程协助。

**定量验收层次：** L1 = 实际源码/契约测试；L2 = 真正 Ubuntu24 OCI + 真实 dpkg/PG18/Caddy/浏览器等隔离服务；L3 = 具名授权的 Pop!_OS24 实机、生产级发行身份和独立责任主体。**任何一级 PASS 不能自动推动下一层，也不能自动打开生产联网、采集、自动恢复、远控或 CA 签发。**

[当前事实](../CURRENT_STATUS.md) · [发行缺口](release-gap-register.md) · [工程成熟生态优先规则](../development/reuse-first-policy.md) · [设备协议](../architecture/device-communication-stack.md) · [设备 Agent L3 验收](../testing/linux-popos24-agent-acceptance.md)。
