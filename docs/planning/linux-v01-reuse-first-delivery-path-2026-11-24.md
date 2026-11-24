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

## P0 业务闭环：固定合成及真实 CI Linux 库存 L2 已通过（2026-11-24）

**阶段 1 · 同设备心跳→Web：** [2380a6a7 / Ubuntu24 #45360530843](https://github.com/kunlong-luo/lanveriq/actions/runs/45360530843) attempt 1 overall SUCCESS，真实 Rust Reqwest、Caddy/Tomcat 双跳 TLS1.3、PG18.7 的激活/心跳、严格 ACK 和授权 Spring API 经 Chromium/Next.js 展示唯一设备。详见 [FULL-CHAIN-L2](../testing/agent-to-nextjs-single-device-l2-2026-11-24.md)。

**阶段 2 · 库存 outbox→Web：** [**addd7685 / Ubuntu24 #45362450357**](https://github.com/kunlong-luo/lanveriq/actions/runs/45362450357) attempt 1 **overall SUCCESS，两个 jobs 均 SUCCESS**。同一隔离 PostgreSQL18.7 实例中，Rust SQLx SQLite 库存 snapshot/outbox 重启 → 真实 Reqwest/mTLS POST → Spring JPA 当前设备凭据事务与严格 ACK → PG18 `device_inventory_current` → 当前授权管理员的设备列表/详情 API → Chromium 的 Next.js 页面真实显示**合成** OS/CPU/8 GB 内存；重复相同库存不修改 revision/snapshot/received_at/digest，未注册但同 CA 签发的另一叶证书 403，跨组 404、注销 401。浏览器数据同源，不是另行填充的 SQL 数据或静态 React 行。完整 [INV-FULL-CHAIN-L2 证据](../testing/agent-inventory-to-nextjs-full-chain-l2-2026-11-24.md)。

**阶段 3 · 真实隔离 Linux 主机 OS/CPU/RAM → Web：** [**e6b56c88 / Ubuntu24 #45365942781 attempt 1 overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781)（两个 jobs SUCCESS；同源码 [Rust Agent Unit #45365942721 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942721)）。在**一次性 GitHub Ubuntu 24.04 runner** 上，用现有 `sysinfo` 只读取真实 Linux OS、CPU、内存，明确不读 NIC/IP、挂载、进程、用户、设备指纹；由 runner 自己的 `/etc/os-release`、`uname`、`/proc/cpuinfo`、`/proc/meminfo` 独立对照真实来源。数据经真实 Rust SQLite durable outbox/重启 → Caddy/Tomcat 双跳 mTLS → Spring/JPA/PG18.7 → 授权用户当前组 JSON → Chromium/Next.js DOM，来源一致且全部 PASS。新增实际停止 Caddy 导致 Reqwest 断线后 pending 仍保留、重放 duplicate 无改写、相同 revision 伪造 UUID 返回 `409`、同 CA 未注册叶 `403`、过期/吊销证书**Rust 库存 POST 拒绝** 并保持 SQLite pending；跨组 `404`、登出 `401`。此前失败 [#45364454533](https://github.com/kunlong-luo/lanveriq/actions/runs/45364454533)、[#45365163380](https://github.com/kunlong-luo/lanveriq/actions/runs/45365163380) 保留：`os_info` 语义版本归一化未保留 `24.04` 的规范展示，已使用现有 `sysinfo::System::name/os_version` 修复，严格来源比较未放宽。完整 [REAL-HOST-INVENTORY-L2 证据](../testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。

三个业务 L2 子项证明**固定合成上报与实际 CI 主机最小采样均可走同一链路**，但仍不等于真实用户设备联网/采集或普通用户可安装的产品：实际 CPU/内存来自 **GitHub disposable runner**，身份/证书/账户均合成；Next.js 只在 CI 临时挂载受控 `StagedConsoleWorkspace`。没有真实 Pop!_OS 24.04 COSMIC 物理机、已批准的用户会话、独立生产发行 CA 或常驻 Agent 采集。G-02/G-05/G-07/G-08/G-09、正式业务 G-06 均 OPEN；v0.1 NOT RELEASE READY。

**下一项 P0（待批准/未验收）：** 优先实现**Tauri 普通用户可操作的 GUI Agent 下载安装/升级/卸载**，基于发行方独立签名、已固定且合法的包来源和不允许任意命令行参数的**独立 Polkit 受限 broker**，每次管理员动作均复核来源/包名/校验和与用户明确授权，拒绝/取消/撤销/断网/恢复/卸载路径完整验收；默认后台 systemd 服务可无托盘图标但状态须可发现，未授权时必须保持无外联和无采集。随后进入**具名 Pop!_OS 24.04 COSMIC 实机 L3**，必须另经法律、独立信任根和权限审批。不要在此阶段引入 osquery/Alloy 的第二个守护程序或把 CI-only 测试开关移入生产。

## DESKTOP-L2B-21：安装前条件审查 UI 已接入（仅 L1，尚非安装器）

`apps/desktop/src/DesktopApp.tsx` 已调用新增 `InstallReviewPanel`，读取既有 Tauri `inspect_host` 与 `inspect_popos_preflight`，显示七项安装资格状态。普通用户可以逐项审阅签名来源、系统管理员授权、分离的启动/开机权限及采集隐私说明，**这些勾选全部是组件会话内的阅读状态，既不能生成 Polkit token，也不会安装、升级、卸载或开启 Agent**；本轮无新 IPC/依赖/容器权限。原 39 项安装意图拒绝 [#45368181785 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368181785)。完整桌面 [#45368132407](https://github.com/kunlong-luo/lanveriq/actions/runs/45368132407) 因变异门禁的类型声明误匹配 FAIL，已在 [fe92d11a](https://github.com/kunlong-luo/lanveriq/commit/fe92d11aae820b4523a91f70de5ba8c4f3140615) 修复；同源码 [桌面 UI + 原生 Tauri CI #45368638618 **overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638618)，React 29、Python 只读门禁 26、Pop!_OS 元数据 22、原生 Rust 6 和 Clippy 全通过（**仅只读不安装的 UI L1**）。[**新版独立 GUI 审核专项 CI #45368842676，功能 SHA a42094bb，attempt 1 overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676)：新增 install-review Vitest **10/10**、React 总计 **29/29**、源码安全变异 **26/26**、Pop!_OS 元数据检查 **22/22**、原生 Tauri Rust **6/6** 及 Clippy 全通过。原生未签名 GUI-only Debian 归档与隔离 Ubuntu OCI APT 模拟另由 [#45368638490 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638490) 在 fe92d11a 验证，**不构成发行签名和系统安装批准**。[L2B-21 实施与测试](../development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md)。

**实际下一项 P0（尚未实现/无发布许可）：** 在独立批准发行身份和软件源之后，以成熟**APT Signed-By/PackageKit + 系统 Polkit** 实现固定包名、来源和操作集合的受限安装事务 broker。验证 root 侧的程序绝不接受 GUI 自传 shell、路径、URL 或任意服务名，验证同一安装事务的预览/执行一致性、管理员身份、逐次用户同意、撤销/取消、异常关机及重复安装/卸载/保留配置。**安装并不隐含开机启动或生产采集许可**；签名包+broker 先在 Ubuntu24 一次性隔离机证明严格负例，再由具名 Pop!_OS24 COSMIC L3 真实机器/责任人独立验收。G-02/G-05/G-07/G-08/G-09/正式 G-06 全部 OPEN，v0.1 NOT RELEASE READY。

## DESKTOP-L2B-22：Polkit 三动作的未安装、拒绝优先策略草案

本阶段明确采用原生 Polkit 动作模型（而非 GUI 直接运行 root shell），仓库在 `apps/desktop/polkit/org.lanveriq.agent.package.policy` 新增 **install/upgrade/remove** 三个不相互隐式授权的 action，所有 `allow_any`/`allow_inactive`/`allow_active` 均设为 `no`，没有任何 pkexec 绑定或 Javascript rules；静态策略**并未安装到系统，不能当作真实 OS 授权测试**。[功能源码 efb7a558 / Ubuntu24 CI #45375272477 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477) 原 39 项安装意图拒绝+新增 30 项 Polkit XML/注解/缓存授权/助手注入负测全部 PASS；先前 [#45375193990](https://github.com/kunlong-luo/lanveriq/actions/runs/45375193990) 的一个畸形 root XML 负例失败已修复并保留记录。[本轮详细证明](../development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。

**L2B-22 阶段已结束；后续 L2B-23 优先复用真实签名 APT 包管理体系，在隔离 OCI 验证 Agent install/reinstall/remove，已通过下述真实 CI。** 真正的每次会话 Polkit broker 仍需独立发行者签名和管理员权限审批，不因 XML 自身存在而授权启动、boot enable、联网或采集。**L2B-24 转为 fixed-action OS broker 安全审核/实现，Pop!_OS24 COSMIC 具名真实 L3 必须另行批准。**

## DESKTOP-L2B-23：真实 Agent 临时签名 APT 仓库安装/重装/卸载 L2 已通过

在当期 Rust1.100 --locked 源码上真实编译 Agent ELF，按已有严格 allowlist 构建原生 idle-only .deb，随后通过 CI 内临时 Ed25519 OpenPGP 签名的 file: APT 仓库（明确定义唯一 Signed-By keyring、InRelease、Packages 的实际 size/SHA256）在**只读挂载软件源、networkless/cap-drop-all/no-new-privileges 的 Ubuntu24 OCI** 中执行真实 apt-get install、同版本 --reinstall、remove，核对 dpkg 文件归属/版本、模拟配置与状态留存、无 systemd 自启；八个恶意签名/索引/包修改/过期/未来/包名拒绝均由系统原生 apt-get 确认。**功能 SHA [2af1cf34 / #45376989866 attempt 1 overall SUCCESS、9/9](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866)**。首次工作流 [#45376908241 FAIL/0 jobs](https://github.com/kunlong-luo/lanveriq/actions/runs/45376908241) 是 step YAML 冒号解析错误，已修复但保留。[精确安全范围与 CI](../development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

**后续真正 G-05 关键任务归属 L2B-27 及更后阶段的经审批受限 Polkit/PackageKit or APT root-side 安装事务 broker**：不能让当前 CI 临时 signer 成为正式 root，也不能用不可信 GUI 同意框调用任意 apt；必须先有独立发行者/根的审批，再由可信 OS 权限控制逐次授权固定 Agent 包 install/upgrade/remove、禁止动态 argv/URI/脚本/服务，并覆盖取消、重放、跨版本升级/回滚/卸载。L2B-23 当前只证明 **OCI 内真实签名源包管理 L2 子项**，没有用户可点击 GUI、真实授权、正式签名发行或 Pop!_OS/COSMIC L3。**G-05/G-09/正式 G-06 OPEN，v0.1 NOT RELEASE READY。**

## DESKTOP-L2B-24：原生 polkitd/D-Bus 及真实 Unix 身份绑定的 Rust 固定动作 L2 已通过

上一阶段 OCI 内可以用临时 signed-APT 安装真实 Agent，但没有任何 OS 用户授权证据。本轮在固定 Ubuntu24 OCI 里安装**发行版原生 polkitd 124 + DBus**，只在容器 overlay 暂时载入源码中的 install/upgrade/remove 三动作全 no XML。官方 `pkaction` 读出三项 all-no，`pkcheck` 对 `nobody`、`daemon` **3×2=6 次实际 OS 拒绝**。新增独立编译的 **Rust1.100 非提权 gate**，只接受三个固定 action，并从本进程 Linux /proc 读取 PID、start-time、UID，使用 `pkcheck --process PID,START,UID`；非法/多余 argv、伪造环境和 root 调用拒绝，任何意外允许结果仍不能绕过正式发行审批。 [**功能 SHA ba04f55c / #45379398181 SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)，原先 policy 30/30 负测、Rust 3/3 单测、OS 6/6 + 注入均 PASS。[报告和真实 CI 失败史](../development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)。

**不能升级为 G-05 已验收安装器：** 这只是用户侧固定 action 的 OS 原生**拒绝**，不是已获许可的 root-side D-Bus broker、不是逐次管理员成功授权或交易/取消与回滚；实验容器没有 systemd-logind/真实 COSMIC 用户会话。**L2B-25 已把前一部分 OS system D-Bus sender peercred 验证落为 OCI 内真实拒绝证据**，但真正发行来源审查、成功的逐次管理员授权、固定 Agent 安装事务的拒绝/成功/取消与回滚仍归 **L2B-26 及以后的独立审批工作**；Pop!_OS24 COSMIC L3 尚未通过。正式发行者、生产密钥、用户法律许可、生产 Agent 启动/联网/采集保持 OPEN/OFF。**v0.1 NOT RELEASE READY**。

## DESKTOP-L2B-25：真实 root-side D-Bus 发送方内核身份核验与 Polkit 拒绝 L2

前阶段 Rust 非特权 CLI 自查 Linux PID/UID，不能证明未来 root 服务端真的从 IPC transport 获得 caller 身份。本轮在 **Ubuntu24 networkless/disposable OCI** 中用发行版 `python3-dbus` + GLib 真正建立一个 root-side system D-Bus **ReviewLab**，通过 DBus 自身的 `GetConnectionUnixUser/GetConnectionUnixProcessID` 取得真实 Unix sender，结合 /proc 四 UID/start-time 和 `pkcheck` 进行验证。三动作 × 两 Unix 用户 **6 次**+不同 UID 同 nonce 及恶意环境测试合计 **8 次**真实 root-side Polkit 拒绝；同 UID 跨进程 nonce 重放拒绝、GVariant Int32 及额外参数、未知 shell/path 拒绝。额外 **9/9** 同服务端单元测试确认**模拟 Polkit 允许 = NOT_APPROVED，而不是安装**。以前的 Polkit XML 30/30、Rust 3/3 和 Tauri readonly 继续通过。[**功能 SHA 91c4669d / CI #45382618806 overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806)，原始失败/修复见 [L2B-25 完整报告](../development/desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md)。

**真正具备安装权限的事务 broker 尚未执行：** ReviewLab **不能安装任何包**，没有用户可用 Tauri 安装动作、独立发行人根、公钥预置/撤销、管理员 logind 活跃会话、受审可执行事务 broker、durable nonce/可信时间、取消和回滚能力。只有当独立发行者、管理人/安全责任主体审批实际签名包/操作边界之后，才可在隔离 L2 研发**固定包名/版本/来源、session 和 D-Bus peercred 与 Polkit 逐次授权在同一事务核验**的安装器，随后经具名 Pop!_OS24 COSMIC L3。任何实验 root policy、测试密钥和 ReviewLab 都不得打进产品分发。**G-05/G-09 OPEN，v0.1 NOT RELEASE READY**。

## DESKTOP-L2B-26：恶意本机规则让 OS 真正 YES，仍被 root ReviewLab 独立拒绝（OCI L2 PASS）

**[功能 SHA f062f144 / Ubuntu24 native OS CI #45385719766 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766)。** L2B-25 的 root-side ReviewLab 之前实测了 Polkit DENIED 和 Python 模拟 YES，但从未让 OS 真正放行。本阶段在 networkless disposable Ubuntu24 OCI 的 overlay 内**临时注入恶意 JavaScript .rules**，让原生 polkitd 对 nobody/install 返回 YES（pkcheck exit 0），即使原 Polkit XML 三项仍全 no。root-side D-Bus ReviewLab 仍返回 **NOT_APPROVED**，实际安装事务 0 次；nonce 重放拒绝，系统总线不允许非 root 用户抢占服务名称，其他身份和操作仍 POLKIT_DENIED。[本阶段真实 CI 与范围](../development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md)。

**绝不是已获授权的一键安装器。** 恶意 JS 是 CI 攻击数据，只在一次性容器中产生，绝不复制到主机。正式发行根、签名工件、可执行受限 broker、逐次真实管理员确认、Agent 事务/取消/回滚及 Pop!_OS COSMIC L3 仍 OPEN。自评保持 70/100。L2B-27 的必要验收是独立签名根/组织批准和真实受限事务方案；未经许可不得开启 Agent 或 Tauri 安装。

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
