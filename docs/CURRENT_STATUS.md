# Lanveriq 当前状态 / Current status

**LIVE-FACT · 2026-11-24 · `main` · v0.1 NOT RELEASE READY。** 这里仅记录**可用于下一步决策的最新事实**。完整旧 SHA、CI 失败/修复与日期细节保存在 [历史验收账本](planning/release-readiness-2026-11-13.md)、[里程碑报告](README.md)、[GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions) 和 Git 历史；**旧 CI 并非当前生产审批**。

> **发布绝对边界：** 默认**生产联网、设备/员工采集、远控、自动恢复、官方 FIDO 可信来源及 CA 签发全部关闭**。没有正式受信签名 Agent 安装包/Polkit broker、授权 Pop!_OS 24.04 COSMIC 实机 L3、生产 CA、真人 WebAuthn MFA、外部独立 WORM/可信时间或法务/SRE 认可。公开 Pages 和本地 Web 是断开真实业务的预览。**G-01–G-10 尚有 OPEN；v0.1 NOT RELEASE READY。**

## 现行代码和目录（禁止第二套旧栈）

| 组件 | 唯一实现 | 当前可证明的边界 |
| --- | --- | --- |
| [Web](../apps/web/README.md) | `apps/web` Next.js 16.4.5 / React19 / Tailwind4 / Base UI | GitHub Pages 公开静态展示；真实 Spring/PG18/设备源数据仅在**临时 CI 受控路由**经过浏览器验证。NXT-28 紧凑导航/键盘/滚动为设计/测试工作 |
| [Server](../apps/server/README.md) | Java25 / Spring Boot4.2.0 / Gradle / Hibernate JPA / Flyway / PostgreSQL18.7 | 默认拒绝身份、业务、证书签发；隔离 PG18 事务/双跳 TLS1.3 mTLS 验证通过 |
| [Agent](../apps/agent/README.md) | Rust1.100 / SQLx SQLite durable outbox / Reqwest/rustls mTLS | 库存/心跳严格 ACK/离线重启已测；真实 CI Linux OS/CPU/RAM 最小样本通过；**常驻产品 Agent 不自动采集/上传** |
| [Desktop](../apps/desktop/README.md) | Tauri2 / Rust / React / Vite（仅桌面窗体） | 本机只读预检、七项门禁、四条独立非授权说明；**安装/升级/卸载/启动按钮和可信 broker 不存在或禁用**；真实 Agent APT 包生命周期只在隔离 OCI 的临时签名源 L2 验收 |
| [Caddy/RustDesk](../deploy/caddy/Caddyfile.reference) | Caddy 参考/双跳 mTLS；RustDesk 实验隔离 | 不支持未批准公网生产路由、远控 RD-2 或员工屏幕采集 |

完整“哪些文件/目录保留与删除”的实际清单：[仓库结构审计](governance/repository-layout-audit-2026-11-24.md)。历史 React/Vite `apps/web` 以及 `apps/web-next` 已废止，不要重新建立。

## L2B-24：OS 原生 polkitd/D-Bus + Rust 固定身份授权拒绝 L2 PASS（2026-11-24）

**[功能 SHA ba04f55c / Ubuntu24 native Polkit OCI #45379398181 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181)。** 实际 Ubuntu24 Polkit 124 + D-Bus 读取现有三个 install/upgrade/remove 全 no policy（策略**只装入 OCI overlay**），官方 `pkaction` 确认 `any/inactive/active=no`；官方 `pkcheck` 对 **nobody、daemon 两个真实 Unix UID × 三动作共六次**均返回 **exit 1**，不能让退出 127 的服务故障冒充授权拒绝。新增独立编译的 **Rust 1.100 固定动作用户侧 gate**，只接受三个字面操作，读取自身 Linux PID/start-time/UID，使用原生 `pkcheck --process PID,START,UID` 检查；Rust 单元 **3/3**、原有 policy 负测 **30/30**、六组原生拒绝与额外参数/环境伪造负测 PASS。gate 即使 OS 回答 yes 仍因缺**发行签名与 root-side broker**而拒绝；**没有连接 Tauri IPC、没有运行 apt-get/systemctl 或安装 Agent**。[精确报告与首次失败/修复历史](development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)。第一轮 `no-new-privileges` 阻断 D-Bus setuid helper、另一次 YAML 换行错误及未挂载新 Rust gate 的失败均如实保留。

**仅代表隔离 OCI 的真实 Polkit 拒绝 L2 子项**，不是对真实管理域、获得批准的发行者、系统活动登录会话/管理员弹窗、Polkit `.rules`、成功授权安装事务、Pop!_OS24 COSMIC L3 的结论。**G-05/G-09、正式 G-06 与全部发行 Gate 仍 OPEN；v0.1 NOT RELEASE READY**。

## L2B-23：真实 Agent 包经原生 APT 临时签名仓库完成隔离生命周期 L2 PASS（2026-11-24）

**[功能源码 2af1cf344afa1506d596f183f465c03248ae55ac / Ubuntu24 #45376989866 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) · 9/9 实际 APT 测试 PASS。** 已用真实 Rust 1.100 --locked release ELF 构建严格无 hook 原生 Agent .deb，放入临时 Ed25519 实验密钥生成的 file: 签名仓库；OCI 内系统 apt-get 用唯一 Signed-By，**真实 install、同版本 reinstall、remove**，验证 dpkg 归属/版本、ELF --help、模拟操作员文件跨重装/卸载保留、systemd 服务未启动/自启。另八类源签名错误、错误 key、未签名、索引篡改、过期/未来元数据、.deb 字节篡改与包名混淆**原生 APT 正确拒绝**。Ubuntu24 OCI **network none/cap drop all/no-new-privileges**；私钥不挂载，runner/真实电脑从未安装。最初 [工作流 YAML #45376908241 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45376908241) 无 jobs，修正冒号后才有上述实际 CI；失败保留。[完整报告](development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

**严格范围：** 这是**正式发行者未批准的临时 CI 签名源 + 真实 APT 包生命周期 L2**，不是独立密钥托管/发布签名，不是实际 GUI/Polkit broker，也**没有跨版本升级、真实用户/Pop!_OS24 COSMIC L3、生产联网/采集**。因此 G-05/G-09 和全部正式发布 Gate 继续 OPEN；v0.1 NOT RELEASE READY。

## L2B-22：未安装的 Polkit 最小权限动作安全基线（2026-11-24）

**实际代码与 [功能 SHA efb7a558 / Ubuntu24 CI #45375272477 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)：** 新增仓库内未安装的 Polkit XML `org.lanveriq.agent.package.policy`，仅有安装/升级/卸载三个独立动作，`allow_any/inactive/active=no`，**无** `exec.path/argv1`、隐式 action、root helper、Polkit Javascript rules 或 Tauri 新 IPC。新增严格源检查及 **30/30** XML/缓存授权/任意执行与文件注入负测；原有安装意图 **39/39** 和桌面默认拒绝守卫同时 PASS。首次 [#45375193990 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45375193990) 因一个畸形负例无法触达 root 身份检查，修正后保留并通过。[完整报告](development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。**不是正式可安装的 Polkit policy、已经运行的 broker、签名 Agent 安装包或真正 OS 层授权；G-05/G-09 仍 OPEN。**

## 已核实的最新关键里程碑

| 工作 | 精确功能 SHA 和真实 CI | 严格结论 |
| --- | --- | --- |
| **DESKTOP-L2B-21 受控 GUI 安装准备 L1** | [a42094bb · Desktop Tauri #45368842676 attempt 1](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) **overall SUCCESS，两 jobs SUCCESS** | 动态只读安装门禁、10/10 独立组件 Vitest、29/29 全量 React、26/26 只读源码变异、22/22 COSMIC 元数据单测、6/6 原生 Tauri Rust、Clippy/构建均 PASS。**并未启用安装、Polkit、Agent 服务或联网**。[L2B-21 报告](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) |
| **GUI-only 原生 Debian 候选归档 L2（不含 Agent）** | [fe92d11a · #45368638490](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638490) **SUCCESS** | 真正 Tauri GUI-only .deb 归档审计、Ubuntu 依赖闭包模拟及临时签名 LAB PASS；**不含 Agent/服务/hooks，未上传、未发布、未安装或正式签名** |
| **真实一次性 Linux runner 的库存端到端 L2** | [e6b56c88 · Linux Batch A #45365942781 attempt 1](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942781) **overall SUCCESS，两 jobs SUCCESS**，同 SHA [Agent Unit #45365942721 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45365942721) | 只读 OS/CPU/RAM 与 `/etc/os-release`、`uname`、`/proc/cpuinfo`、`/proc/meminfo` 实时对照；Rust SQLite 重启→Reqwest TLS1.3/Caddy/Tomcat 双跳 mTLS→Spring/JPA/PG18.7→授权 Next.js 真 Chromium DOM 全链一致。真实断开 Caddy、过期/吊销叶、版本冲突 409、未登记 403、跨组 404、登出 401 均拒绝优先。[L2 证据](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md) |
| **固定无个人资料库存与合成心跳 L2** | [addd7685 · #45362450357](https://github.com/kunlong-luo/lanveriq/actions/runs/45362450357)、[2380a6a7 · #45360530843](https://github.com/kunlong-luo/lanveriq/actions/runs/45360530843) **SUCCESS** | 固定非真实设备记录先证明 ACK/授权 API/CI Chromium；已被上方真实 CI Linux 样本里程碑推进，仍不是生产设备/登录 |
| **Agent 实际原生 dpkg 生命周期仅 CI L2** | [eeec5f38 · #45339799209](https://github.com/kunlong-luo/lanveriq/actions/runs/45339799209) **SUCCESS** | 一次性 networkless Ubuntu OCI 中真实 `dpkg -i` / 重装 / 删除 idle Agent；**并非 Pop!_OS 实机、GUI 安装器、签名发行或常驻联网服务**。[验收](development/agent-linux-real-dpkg-disposable-lnx4-2026-11-24.md) |
| **APT 官方 Signed-By 独立签名机制 LAB** | [#45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405) **9/9 PASS** | 只认证合成 file:// APT 索引和临时 GPG signer；无正式发行根或真实 Agent 安装。[详细来源实验](development/desktop-tauri-l2b20-real-apt-signed-by-offline-ci-2026-11-24.md) |
| **安全默认拒绝 RD-2** | [本轮桌面 #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) + 独立 RD-2 workflow | UI/系统不允许未授权远控；工作流成功不是生产远控功能已经存在 |

**保留关键失败历史：** [桌面 #45368132407 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45368132407) 的源码拒绝守卫最初仅识别类型中的 `false`，未拒绝实际返回 `true` 的变异，修复在 [fe92d11a](https://github.com/kunlong-luo/lanveriq/commit/fe92d11aae820b4523a91f70de5ba8c4f3140615)；Linux [#45364454533 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45364454533) 与 [#45365163380 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45365163380) 揭示 Ubuntu `24.04` 版本规范化差异，改用既有 `sysinfo::System::os_version/name` 才真正通过。[两类报告](development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md)／[Linux 失败说明](testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。**不得删除失败运行或回写成一直成功**。

## 仍未完成的生产发布路径

| 优先级 | 必须补齐的实证 | 状态 |
| --- | --- | --- |
| P0 | **受信签名 Agent** + 独立批准的发行根与源、固定作用域的受限 Polkit broker、受控安装/升级/卸载、拒绝/取消/回滚；默认 systemd inactive | **G-05 OPEN** |
| P0 | 经合法书面许可的**具名 Pop!_OS 24.04 COSMIC 实机 L3**，真正 GUI 启动/授权/设备密钥/服务控制/性能和无界面图标的可发现管理 | **G-05/G-09 OPEN** |
| P0 | 真正官方 FIDO MDS 来源链/撤销/可信时间、管理员 MFA 双审、生产 CA/KMS/轮换 | **G-01/G-03/G-04 OPEN** |
| P0 | 真实外部独立管理域 WORM、不靠业务 DB 托管高水位及合法业务保留/删除、外部安全/运维/许可证批准 | **G-02/G-07/G-08 OPEN** |
| P0 | 已批准真实设备的 Agent→Caddy→Spring→PG18→正式授权 Next.js 业务、离线恢复/权限/失败回滚 | **正式 G-06 OPEN** |
| P1 | Windows 和 RustDesk 是否正式纳入 v0.1，单独书面决定 | **G-10 OPEN / OUT OF SCOPE 待审批** |

详细 Gate 正文：[release-gap register](planning/release-gap-register.md)；[优先复用交付计划](planning/linux-v01-reuse-first-delivery-path-2026-11-24.md)；[工程评分](planning/project-scorecard.md) 只衡量成熟度，不产生授权。

## 历史记录与维护办法

- 历史 FIDO D4-C10、C16–C18 报告、D0 审批、NXT-28 及 PostgreSQL18 版本升级细节继续保留于 [专题索引](README.md) 和 [完整历史验收账本](planning/release-readiness-2026-11-13.md)。旧合成 PG18 事务功能 SHA `2368163e588e6e94f74a748bdc6e162b9c4c6b9a` 是**历史来源标识**，不是今日 Git HEAD 或生产批准。
- **要运行/开发：** [中文根 README](../README.zh.md) 和 [开发运行指南](operations/installation.md)，不要使用历史 Vite/老端口/旧 Gradle 或把实验脚本用于生产用户机器。
- **要更新本页：** 只在**确切功能 SHA、实际 CI 运行 PASS**后更新表格；未跑就标 NOT RUN；docs-only CI 只证明文档链接/安全声明，不证明新功能。
- 旧详细运行日志、SHA/尝试次数、失败/修复仍可通过仓库 Git 历史和 GitHub Actions 核对；新增历史资料写对应专题，不再次向本文件顶部复制 5 KB 里程碑段落。

**v0.1 NOT RELEASE READY · `main` · 生产联网、采集、自动恢复、远控、CA 签发 OFF。**
