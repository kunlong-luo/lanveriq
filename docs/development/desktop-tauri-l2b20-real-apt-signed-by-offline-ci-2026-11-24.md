# DESKTOP-L2B-20 — 原生 apt-secure Signed-By 软件源隔离验收（真实 Ubuntu24 APT，只有合成索引）

**ENGINEERING L2 SOURCE AUTHENTICATION LAB · 2026-11-24 · exact source SHA `107941e1961bd17e36b3c408f5c5e0be685e97d9` · [CI #45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405) attempt 1 overall SUCCESS / 9 of 9 tests PASS · NO AGENT, NO INSTALL, NO PUBLISHER APPROVAL · v0.1 NOT RELEASE READY**

## 为什么不继续自研发布签名协议

L2B-18/19 已通过实验用 Ed25519 双签、角色、撤销、模拟根 pin/过期/回滚负测，但**密钥、根 pin、时钟和高水位均在一次性 CI 内自给**，没有真正的发行身份。Lanveriq 目标系统为 Pop!_OS 24.04（Debian APT 体系），应优先复用成熟 [APT 仓库认证机制](https://manpages.debian.org/trixie/apt/apt-secure.8.en.html)，而不是开发一个与发行包管理平行的生产验签 SDK。[deb822 `Signed-By`](https://manpages.debian.org/trixie/apt/sources.list.5.en.html) 可以限定指定软件源能使用哪些 keyring，而不是让第三方密钥默认信任所有 APT 软件源；[Debian 第三方仓库指南](https://wiki.debian.org/DebianRepository/UseThirdParty) 也建议限制信任范围。

本阶段将这一**技术选型假设**转成真正的 APT 安全实验，而不是再次仅验证 JSON 声明或模拟器。**仍不创建任何真实产品仓库或可分发 Agent**。

## 已实现的真实 CI 与验收

- [Python 9 项系统测试](../../apps/desktop/tools/test_desktop_apt_signed_by_lab.py) 使用 Ubuntu runner 自带 GnuPG/apt-get，随机创建 publisher 和 attacker **两把一次性 OpenPGP Ed25519 签名密钥**，为一个**完全不存在的合成软件包名称**生成非空 `Packages` 元数据（只包含虚构 Filename、Size 和哈希，**没有 .deb 文件**）。本地 `Release` 给该索引绑定 SHA-256/Size 与 `Date`/`Valid-Until`，GPG 产生真实 clearsigned `InRelease`。
- 每个测试创建独立临时 `file://` 仓库；APT 使用仅指向临时文件的 deb822 `test.sources` 和 `Signed-By` 专用 keyring。**所有** APT `Dir::Etc::sourcelist/sourceparts/trusted/trustedparts`、`Dir::State::lists/status`、`Dir::Cache::archives` 改为临时目录，同时明确 `AllowInsecureRepositories=false`、`AllowUnauthenticated=false`、`Check-Valid-Until=true`、`Check-Date=true`。无外部仓库/网络请求、无 sudo、无 `apt-get install`、无实际包或系统 dpkg 状态修改。
- [独立 Ubuntu24 工作流](../../.github/workflows/desktop-apt-signed-by-lab.yml)：只读 checkout/contents:read，现场调用真实 `apt-get update`，不上传、签发、下载 Agent；再实际执行 [Desktop 只读源码守卫](../../apps/desktop/tools/verify_desktop_readonly.py)、[L2B-16 发行状态拒绝](../../apps/desktop/tools/verify_desktop_release_evidence_boundary.py) 和 [L2B-17 Agent 安装意图 DENIED](../../apps/desktop/tools/verify_desktop_agent_install_intent.py)。

| 真实命令/证据 | 期望 | [run #45334019405](https://github.com/kunlong-luo/lanveriq/actions/runs/45334019405) |
| --- | --- | --- |
| 发布方签名匹配 `Signed-By`，真实 `apt-get update` 读到合成 `Packages` | APT 成功，并且无安装与系统状态修改 | **PASS** |
| 仓库 `InRelease` 用未知 attacker key 签署 | 非零退出，拒绝不可信签名 | **PASS** |
| 发布方签名正确，但 `Signed-By` 指向 attacker keyring | 必须拒绝 | **PASS** |
| 将发布方密钥写入**隔离的全局 trust keyring**，`Signed-By` 仍指向错误密钥 | 仍须拒绝，不能用全局信任绕过指定源 | **PASS** |
| 仅留 `Release`，删去 `InRelease` 且无 `Release.gpg` | 未签名仓库必须拒绝 | **PASS** |
| 已签署的 `InRelease` 原文字节被篡改 | 验签失败 | **PASS** |
| 在已签名元数据下修改真实 `Packages` 索引字节 | APT 通过索引哈希/大小验证拒绝 | **PASS** |
| `Date` 在过去、`Valid-Until` 已过期 | APT 必须拒绝过期仓库元数据 | **PASS** |
| `Date` 处于未来（早于本机合理时间不满足） | APT 必须拒绝尚未生效的元数据 | **PASS** |

**最终实际日志：** `Ran 9 tests in 0.858s — OK`；旧有只读 GUI、安全发布状态、安装意图三个拒绝检查亦全部 **PASS**。[工作流精确 SHA `107941e1961b...`](https://github.com/kunlong-luo/lanveriq/commit/107941e1961bd17e36b3c408f5c5e0be685e97d9)。本阶段只证明系统 APT 原生执行的离线验签与错误拒绝；**不是对真实 Agent 软件源、软件包、设备或管理者的批准**。

## 此次测试明确不能覆盖的安全边界

1. **发行身份/密钥交付**：两把 OpenPGP key 都由同一 disposable CI 创建；`Signed-By` 路径/keyring 由测试 harness 配置，**不是**独立安全方审核与安全预置于用户设备的正式信任根。不能让用户操作或包内脚本将不可信 key 自动加入机器；不允许 `trusted=yes`、`apt-key add` 全局扩大信任、`allow-insecure`。
2. **真实分发网络与更新**：只测试 `file://` 静态仓库的 `apt-get update`，**未**验证 HTTPS/TLS、镜像/仓库同步、错误网络、合法密钥轮换、撤销生效、离线持久状态、`Valid-Until` 到期维护或回滚至以前**尚未过期且有效签名**的索引。APT 日期检查依赖目标机时钟，不能取代独立可信时间与持久化高水位。
3. **软件包和运行时**：合成 `Packages` 的目标是**不存在**的占位包，不生成 `.deb`，不调用 `apt-get install`、`dpkg` 或 `systemctl`；所以真实 Agent .deb 的来源/ABI、Postinst、systemd 默认 disabled/inactive、完整第三方许可证及合法安装路径依旧未测。APT 认证**签名仓库元数据**，并非发行者对每个独立 `.deb` 的 detached signature。
4. **授权与产品 UX**：Tauri 仍仅两个只读 IPC，不提供 Polkit/broker 或下载/包管理按钮；现有 L2B-17 安装意图仍无授权分支。已通过的 GUI-only unsigned Debian L2 不能冒充 Agent 一键安装器；真实 COSMIC/Wayland 用户授权、卸载/升级与 L3 **NOT RUN**。
5. **横向发布门禁**：G-02 独立 WORM、G-05 真实签名 Agent+物理 L3、G-07 法务、G-08 运营恢复及 G-09 真正安装体验都仍 OPEN；联网、采集、自动恢复、远控与 PKI 签发继续 OFF。

## 下一阶段：先做产品发布架构决策与现实验收

**推荐优先方案（设计决策尚待审批）：** 在 Pop!_OS 上使用由已批准的发行主体管理的**签名 APT 仓库及专用 `Signed-By` keyring**，并通过 OS 原生包管理器及独立审核过的最小权限 broker 实施安装与升级。不得让不可信 GUI JSON 任意配置 keyring/URI/包名/脚本。若实际要求离线多版本分发、跨根轮换阈值、防回滚持久性和额外构建身份来源，则再评估**成熟 TUF/Sigstore**，不要把 L2B-19 自研实验 JSON 根作为真实 TUF 元数据。

真实退出证据必须包括发行者法务及密钥审批、可信根由独立安全路径进入设备、签名仓库和合法版本发布/撤销/轮换、受限安装事务与设备本地用户同意、在非生产环境**真实 Agent 包隔离安装 L2**、Pop!_OS 24.04 COSMIC 获授权物理 L3 和软件许可/运营/审计签字。未满足前始终 **v0.1 NOT RELEASE READY**。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [L2B-19 root LAB](desktop-tauri-l2b19-receiver-root-preprovision-lab-2026-11-24.md) · [发行缺口](../planning/release-gap-register.md) · [Agent 安装器验收矩阵](../testing/linux-desktop-installer-acceptance.md)。
