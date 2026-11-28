# Lanveriq v0.1 发行、升级与回滚操作门禁

> **2026-11-28 现行桌面迁移边界：** 唯一桌面源码为 `apps/desktop` GPUI 原生 Rust；旧 Tauri CI 和相关包证据仅为历史（下方固定 Git 链接），不是当前构建或发布路径。迁移后验收通过，本机 MVP-08 DONE；G-01～G-10 保持 OPEN，v0.1 NOT RELEASE READY。

**ACTIVE RUNBOOK · 2026-11-26 · `main` · v0.1 NOT RELEASE READY · NO CUSTOMER INSTALLATION AUTHORIZED。** 这里是**候选发行与验收流程**，不是已发布安装说明。**2026-11-21 现行发布边界：** 唯一 Web 前端 `apps/web` 为 Next.js，官方 Pages 是断开业务的演示；Rust Agent/桌面 Tauri/Java 后端均没有获批的生产发行与部署路径。最新 CI/功能 SHA 请查 [CURRENT_STATUS.md](../CURRENT_STATUS.md)，Gate 以[唯一发布缺口登记](../planning/release-gap-register.md)为准，不在本文件复制全部旧日志。

## 发布状态和权限顺序

1. **固定范围：** v0.1 Linux-first（Pop!_OS 24.04 COSMIC），Windows/RustDesk 另行书面决定。生产联网、自动采集、远控 RD-2、自动恢复、CA 签发等开关保持 OFF，**不因系统安装、图形界面勾选或 GitHub CI 成功而开启**。
2. **独立发行主体与根：** 获取具名组织、法务/安全批准的发行签名者、硬件/隔离管理域的密钥托管、预置信任根及撤销/轮换流程；只认固定包名、版本/架构、来源和已批准的签名策略。CI 一次性实验密钥绝不能当正式根。
3. **独立高水位/可信时间：** 不能只依赖 `apt-secure` 的有效签名和 `Valid-Until`；[L2B-27 原生 APT 10/10](../development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) 实测接受**同 signer、同 Packages、仍有效但日期更旧的签名 Release**。必须由独立可信主体审批准入序列和持久版本高水位，并对真实旧仓库索引/旧软件包、回退、断网、错时钟、重装、版本降级做负测。
**原生 APT 新观察（L2B-29）：** [不同 Packages 的旧有效签名源观察](../development/desktop-tauri-l2b29-native-apt-different-valid-signed-index-2026-11-25.md) 在功能 SHA `ef995461` 的 [OCI #45559034203 11/11 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45559034203)：旧签名 `InRelease` 仍被接受，但 APT **未下载不同旧 Packages、保留新版包元数据**。三次失败/诊断原始记录保留。**不是**生产高水位防回退保证，更非 Agent 实际版本降级。

**L2B-30 真实包版本 L2：** [13/13 原生 OCI #45561370502 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45561370502)，使用[两份真实 `.deb`（同一审计 ELF，控制版本 dev1/dev0）](../development/desktop-tauri-l2b30-native-apt-two-agent-versions-oci-2026-11-25.md)：APT 无人值守降级被拒，但只在隔离环境中显式覆盖后，真实 dpkg 已安装版本成功降为 dev0、再恢复 dev1，操作员状态保留。这说明**系统 APT 的降级提示不能替代独立单调高水位**；绝不可把 OCI 例外参数移入用户安装流程。

**L2B-31 不同 Rust ELF 原生交易：** [473f90d9 / #45563578245 15/15 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45563578245) 使用[历史钉住 Git 源与当前独立编译](../development/desktop-tauri-l2b31-pinned-historical-rust-elf-apt-oci-2026-11-25.md)，证明 OCI 默认降级拒绝、显式覆盖旧版 ELF 确实装入并恢复新版、操作员状态保留；CI-only 权限拒绝为真实环境门禁而非参数错误。**不产生正式高水位、发行身份或安装授权，禁把实验覆盖开关放入产品。**

**L2B-32 原生 APT + 单独见证 signer：** [55de6e71 / #45566961126 17/17 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126)；[专项报告](../development/desktop-tauri-l2b32-separated-gpgv-witness-signed-apt-replay-oci-2026-11-25.md) 展示 APT 真正接受旧有效签名 Release，随后独立**CI 实验签名者**的 gpgv 序列/摘要校验将其拒绝，旧见证签名、发布者伪造和改动内容也拒绝。**没有外部高水位、可信时间/正式签发或产品强制执行路径**，不能作为客户升级/回滚权限。

**L2B-33 冷缓存双重回放 L2：** [功能 SHA c177f281 / 原生 OCI #45569177575 20/20 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45569177575)。[专项报告](../development/desktop-tauri-l2b33-cold-cache-double-replay-anchored-oci-2026-11-25.md) 证明原生 APT 在空缓存真实采用不同旧 Packages，旧 Release 与旧 witness（同 sequence=2）都签名有效，但另行 RO 挂载的 CI 批准摘要**拒绝**这组共同回放；缺锚点拒绝、批准材料通过。**CI 锚点不是独立管理域高水位，不能作为产品安装许可或正式根。**

4. **最小权限 GUI 安装：** Tauri 只能请求固定动作，不持有 root shell/可自选 URI/任意安装路径；必须经原生 D-Bus 实际发送者身份、真正 logind 活跃会话和 Polkit 每操作一次授权、root-side 固定工件/事务证据核验。安装/升级/卸载、服务启动、boot enable、联网、采集须分别审批和审计。当前只有 [root-side ReviewLab 拒绝与恶意 OS YES 纵深负测](../development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md)，**没有能执行安装的获批 broker**。
5. **具名 Linux L3：** 先在 Ubuntu24 disposable L2 验证安全交易及拒绝/取消，再经用户/设备所有人真实书面许可，在 Pop!_OS24 COSMIC/Wayland 实机验证 GUI、背景服务可发现性、停止/卸载、升级/回滚、隐私告知、权限/性能。实机通过前不可分发。
6. **生产信任/合规：** 官方 FIDO、真人管理员 WebAuthn MFA、CA/KMS、独立管理域 WORM、PG18/备份恢复、法律/许可证、SRE/应急/运营签收及正式 Agent→Spring/PG18→Next.js 全链，全部必须独立满足。[G-01～G-10](../planning/release-gap-register.md) 仍 **OPEN**。

## G-05 CI 原生 APT 依赖缓存轮换（Ubuntu 构建环境，不是产品发行）

- [Tauri/GUI-only `.deb` 原生 APT 轮换专项](../development/desktop-ubuntu24-apt-native-cache-rotation-l2-2026-11-26.md) 记录旧 **175** 包 cache 因新版 Ubuntu24 runner 软件源/预装状态漂移而被严格拒绝。由[已有 Ubuntu 签名索引预取工作流](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-ubuntu-apt-prefetch.yml) 使用发行版 apt-get 验签后 **download-only**，要求第二个独立 GitHub runner 逐包原生索引核验/原计划闭包/哈希/版本/大小/架构全部 PASS，才能审查 cache key/count/manifest。
- 2026-11-26 有效独立证据仅 [#45745618560 两 jobs SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560)，对应该次 Ubuntu24 runner **176** 个原生依赖。两个**非发行用途**消费者 [Tauri 预览](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-tauri-preview.yml)与 [GUI-only 候选审计](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-deb-candidate.yml) 均已在提交 [`a6db3afd`](https://github.com/kunlong-luo/lanveriq/commit/a6db3afdfdcf75c9ef9690ace18a1240f1a03064) 更新审核 key/数量/哈希；自己的运行器仍须重新刷新签名索引且仅可 `--no-download` 安装编译头文件。
- **运行器镜像变更不能自动视为可信**：不同 GitHub runner 镜像/预装库导致 apt `Inst` 数量不同是实际风险；接受上一步的源归档不能代替当前 builder 的独立闭包检验。签名源不可用、当前需要的依赖缺包/版本不同、固定缓存本身的数量/哈希改变均 **FAIL**。仅当宿主机**已安装**部分依赖使真实 `Inst` 计划成为已审缓存的子集，才允许 [`--allow-signed-superset`](../../apps/desktop/tools/verify_desktop_apt_archive_cache.py)；额外归档仍必须全部经当前签名索引 SHA256/大小/版本验明且不能被安装计划选择；不得使用 `--allow-unauthenticated`、松散 cache 前缀恢复、跳过检查或把实验 `.deb` 发布。更长期的确定性 runner 基础环境需要单独审核并验证。
- 即使 native Rust、Tauri `.deb` 归档审计完全通过，**也不代表 Agent 安装包或正式发布**：Tauri 正式 `bundle.active` 仍关闭，CI 单次无签名 GUI-only 构建未获发行身份/Polkit broker/Pop!_OS COSMIC L3。G-05/G-09 OPEN。

## 源码开发预览候选：不是用户安装版本

- **唯一当前预览候选：** [`v0.1.0-dev.1` 来源与待发布说明](source-preview-v0.1.0-dev.1.md)、[严格 JSON 元数据](../planning/source-preview-v0.1.0-dev.1.json)。状态为 `NOT_PUBLISHED`，尚无 Git Tag / GitHub Release，也**不代表 G-05 获得批准**。
- **自动化 L1 验收：** [`tools/release/verify_source_preview.py`](../../tools/release/verify_source_preview.py) 与[负测](../../tools/release/test_verify_source_preview.py) 经 [#45557337790 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45557337790) 检验：只能准备 GitHub 自动生成的源代码归档，附件/Agent 安装包为空；独立发行根、APT 高水位、可信时钟、Polkit 事务、Pop!_OS 实机、生产网络/采集/远控/恢复/CA 全部仍为未批准或 OFF。
- **GitHub Actions 仅有读取权限：** [源码预览 preflight 工作流](../../.github/workflows/source-preview-preflight.yml) 在 main 与手动触发时做本地源代码一致性检查，**没有创建 tag、release、签名或上传工件的能力**。即使 PASS 也只是候选审核，不自动发布。
- **独立发布决策：** 具名维护人必须另行判断源代码公开许可、安全披露、第三方源分发与最终 release notes，并在明确审批后才可创建**标注 NOT RELEASE READY 的 source-only GitHub prerelease**；不得上传 `.deb`、AppImage、RPM、执行二进制、临时签名源或可安装 Agent 工件。正式生产发布仍要求下面全部 G Gate。

## 候选工件与发布记录最低字段

| 必填字段 | 审核内容 / 缺失时的动作 |
| --- | --- |
| source SHA / lockfiles / 架构 | exact source commit + Cargo/Gradle/pnpm lock、target arch、真实 Agent ELF/.deb、静态/动态依赖及产物 SHA256；**缺任一项则 DENY** |
| signer/root/source approval | 有效可信发行签名、公钥分发与独立保管、真实被允许的固定 Signed-By 仓库、可信时间/撤销/高水位；CI 临时签名不算 |
| 安装授权链 | 真实用户身份/RBAC、活动 session、每次 Polkit consent、root broker 绑定固定唯一包与可信版本、最小 D-Bus ABI、取消/重放/TOCTOU 拒绝 |
| 可逆生命周期 | 安装时 idle/disabled；跨版本升级/重装、配置/状态不丢、失败中断重试、取消、卸载和备份/回滚/恢复的**实际 L2+实机 L3** |
| 数据及生产控制 | 数据所有人用途告知/合法许可、访问日志/限额、无未经许可的采集/远控、可撤销/导出/删除说明 |
| 独立签字与运行证据 | 真实 GitHub run ID/JUnit/OCI/Pop!_OS L3、失败历史、责任人及签收日期；未满足时状态保持 **NOT RELEASE READY** |

## 发布异常和回滚边界

- **代码或 CI 失败：** 记录失败的 exact SHA/run/job、实际原因和复验 SHA；不 force-push、不删失败历史、不把 YAML/容器夹具问题写成真实安全拒绝。
- **APT/发行身份与回放异常：** 停止发行交易，检查正式 signer/root/keyring/证书吊销、镜像归属、Release 日期与独立高水位；不能依赖 `--allow-unauthenticated`、允许全局密钥、临时跳过日期检查或回退到旧版未知签名。
- **Agent/后台服务失败：** 优先停用生产联网/采集、维持 Agent idle；不自动 enable/restart/恢复或删除用户/操作员配置和状态。按审查过的最小权限 broker 事务计划回滚，由具名 SRE 和数据所有人核对结果。
- **数据库/证据失配：** PostgreSQL/Flyway、Rust SQLite WAL/outbox 与审计/WORM 版本必须分开核验；先备份并实际完成独立介质恢复演练，不得通过替换正在运行的 WAL/SQLite 文件“回滚”。
- **真实硬件、合规或管理员链失效：** 立即停止候选发布，维持所有生产 Gate OPEN；申请书面重新授权后才能重跑真实 L3。

## 原有历史和开发者参考

当前 [Linux 首发路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [L1/L2/L3 验收规则](../testing/strategy.md) · [桌面安装检查](../testing/linux-desktop-installer-acceptance.md) · [仅开发环境的操作手册](installation.md) · [备份](backup.md) · [GitHub Actions](https://github.com/kunlong-luo/lanveriq/actions)。

旧 [2026-11-08~24 release.md 全文原始版本](https://github.com/kunlong-luo/lanveriq/blob/80a743739b32333881b926c0f63e953299ad1ba7/docs/operations/release.md) 包含当时 Batch A、TLS1.2 预检、原生 dpkg 和 L2B-20 等历史进度，按其日期理解；相关准确 CI 仍在对应专题和 Git 历史。**删去活跃运行手册中的重复旧时间线，不等于删掉独立安全证据。**

**当前没有可下载的正式用户 Agent 一键安装器；v0.1 NOT RELEASE READY。**
