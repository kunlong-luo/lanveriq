# DESKTOP-L2B-23：真实 Agent .deb 通过签名 APT 完成隔离安装/重装/卸载

**REAL NATIVE APT L2 PASS · 2026-11-24 · exact functional SHA 2af1cf344afa1506d596f183f465c03248ae55ac · [Ubuntu24 #45376989866 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) · 9/9 real APT scenarios PASS · NO APPROVED SIGNER OR PRIVILEGED BROKER · v0.1 NOT RELEASE READY**

## 本轮实际交付的真实能力

与只验签名 JSON、只运行 apt-get update 或将 Debian 包用 dpkg 直接安装不同，本阶段**把实际 Rust Agent .deb 放进了一座受系统原生 apt-secure 验证的临时签名仓库，并执行 apt-get install / --reinstall / remove**。二者使用同一真实包管理器的事务状态和文件归属校验，取代手工拆包模拟。

- [CI 工作流](../../.github/workflows/desktop-agent-signed-apt-oci-l2.yml) 在 ubuntu-24.04 一次性 runner，以 Rust 1.100 运行 Cargo --locked --release，实际编译 lanveriq-agent（CI 日志显示 release 1m33s），随后使用既有 [Agent 候选包构建](../../apps/agent/tools/linux/build-offline-agent-deb.sh) 生成真实 lanveriq-agent-offline-preview_0.1.0~dev1_amd64.deb；[严格原生归档审计](../../apps/agent/tools/linux/verify-offline-agent-deb.py) 强制只允许 Agent ELF/指定 systemd 单元、固定版本/架构、无 postinst/prerm、无配置/凭据/自启 hook，PASS。
- [独立签名仓库/OCI 测试组织器](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py) 使用真实 .deb 大小和 SHA256 填入 Packages；Release 锚定 Packages SHA256/字节数及 Date/Valid-Until，GnuPG 用仅本次 GitHub CI 生成的 publisher 和 attacker 临时 OpenPGP Ed25519 key 产生 InRelease。私钥从不挂到容器，测试完成即销毁。
- [容器内实际 APT 脚本](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh) 按 deb822 Signed-By 独立指定公钥，隔离唯一 file: 源，关闭所有其它系统软件源/全局 trusted keyring、未经授权签名/不安全仓库回退，保持 APT 原生 Date/Valid-Until 检查。OCI 是前次验收已固定 digest 的 Ubuntu24 镜像，--network none / --cap-drop ALL / no-new-privileges / pids 与内存限额，唯一挂载是本次源、公钥的**只读目录**。**所有真实 apt-get install/remove 都在容器内 root 执行，绝不运行在 runner 或真实终端的宿主 OS**；用户无需 Rust/命令行完成本实验，这仍不是用户安装入口。
- 仓库继续复用 L2B-22 [Polkit 三动作全拒绝源审查](desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md) 和 Tauri 两个只读 IPC，没有增加 root GUI、shell plugin、pkexec、正式 broker、产品密钥、自动采集/联网。

## 真实九项场景和 CI 准确结果

| 操作 | 使用的真实系统能力 | [#45376989866](https://github.com/kunlong-luo/lanveriq/actions/runs/45376989866) |
| --- | --- | --- |
| 正确已签名仓库 + 真实 Agent 包 | apt-get update → install 精确版本 → dpkg-query Status/Version/归属，执行真实 ELF --help → 同版本 apt-get --reinstall install → apt-get remove；模拟操作员配置/state 保留，无 systemd manager/启用链接 | **PASS** |
| Signed-By 指向错误 attacker key | APT 对签名的仓库认证拒绝，无安装 | **PASS** |
| InRelease 被未知 attacker key 签署 | APT 拒绝冒充发布者签名，无安装 | **PASS** |
| 删除 InRelease 且无 Release.gpg | APT 拒绝未签名源，禁止 fallback | **PASS** |
| Release 已签，但 Packages 被篡改 | APT 检测索引 SHA256/长度不符，拒绝 | **PASS** |
| 已签 Release 过期 | APT 检查 Valid-Until 拒绝 | **PASS** |
| 已签 Release 时间在未来 | APT 检查 Date 拒绝 | **PASS** |
| 索引合法但 .deb 字节被追加 | apt-get update 成功，但 apt-get install 验真实包与 Packages 的 Size/SHA256 不符并拒绝，无安装 | **PASS** |
| 合法签名索引把包改名为另一个包 | 固定 Agent 包名/版本不存在，APT 拒绝安装混淆对象 | **PASS** |

**CI 实际日志：** 9 个逐项 PASS + 测试总报告“9 real APT Agent package L2 OCI cases”，原有 Desktop readonly 与 inert Polkit DENY-ALL 源审查也均 PASS。CI 输出只有发行源与包管理实验性质，不是对真实 Pop!_OS、可信发布者或许可证的审批。

## 保留首次工作流错误

[首次 #45376908241](https://github.com/kunlong-luo/lanveriq/actions/runs/45376908241) **FAIL，0 jobs 运行**：新增 workflow 的 step name 中出现未加引号的冒号，GitHub YAML 解析失败；修复 [2af1cf34](https://github.com/kunlong-luo/lanveriq/commit/2af1cf344afa1506d596f183f465c03248ae55ac) 将 step name 改为不含冒号，之后 **#45376989866 attempt 1 SUCCESS**。该失败是工作流定义错误，不曾安装或放开权限，也不能当成一次 APT 负测试成功；保留历史证据而非改写为最初就通过。

## 技术依据与明确的未覆盖范围

系统 APT 的 [apt-secure](https://manpages.debian.org/trixie/apt/apt-secure.8.en.html)、[Debian 第三方源限制](https://wiki.debian.org/DebianRepository/UseThirdParty)支持 Signed-By 受限根、签名 Release、Packages 索引和真正包字节的校验。**这只证明包来自本次预置受信测试密钥，不证明背后维护者的正式身份，亦不自动证明包无恶意代码或组织有合法授权。** 本次测试密钥全部由同一 CI 控制，不构成独立托管；没有 HTTPS 产品仓库、正式根分发/轮换/撤销、可靠可信时间或持久防回滚高水位，无法阻止对仍处有效期内的旧签名索引重放。

- **尚无 Polkit broker。** 本阶段从 CLI 脚本在 isolated OCI 内执行实际 APT，**不是**桌面 GUI→真实活动会话→Polkit→受审 root broker→APT 的逐次授权链。L2B-22 的 XML 仍只是仓库内 inert fixture、所有 permission=no，未安装到目标系统。
- **同版本重装 != 跨版本升级。** 无两个具备实际迁移工件的不同 Agent 版本，升级/降级/取消/中断后恢复仍 **NOT RUN**。
- **安装成功 != 后台 Agent 自动运行。** 服务无 systemd manager、自启链接和配置；不运行 Agent --config，不联网、不采集、不远控。未来需把安装、服务启动、开机自启、联网与采集授权完全分离。
- 目标 Pop!_OS24 COSMIC 实机 L3、Wayland、实际管理员认证、密钥/法务/SRE/产品/用户授权、真实生产业务登录、独立 WORM 仍 **NOT RUN/OPEN**。即使本次 OCI L2 全 PASS，G-05/G-09 与正式 G-06 也不能关闭。

**下一项 L2B-24 实质目标：** 在独立批准发行主体/信任根的前提下，审查具有**固定包名、固定来源、独立每操作授权**的真正 root-side OS Polkit/PackageKit 或 APT broker，先实现逐次用户权限、攻击拒绝、取消/升级/卸载的隔离 L2。禁止 GUI 动态 root shell、供应商 SDK 自研验签、自动注册外部密钥。随后才在具名合法授权 Pop!_OS COSMIC 实机做 L3。

[当前状态](../CURRENT_STATUS.md) · [发布缺口](../planning/release-gap-register.md) · [真实 idle Agent dpkg LNX4](agent-linux-real-dpkg-disposable-lnx4-2026-11-24.md) · [L2B-22 Polkit source review](desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。

**v0.1 NOT RELEASE READY。生产联网、采集、自动恢复、远控、CA 签发全部 OFF。**
