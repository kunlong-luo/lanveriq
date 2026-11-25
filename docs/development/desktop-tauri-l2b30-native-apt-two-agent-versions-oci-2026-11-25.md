# DESKTOP-L2B-30 — 两个真实 Debian 包版本的原生 APT 降级边界（隔离 OCI）

**ACTIVE L2 EXPERIMENT · 2026-11-25 · 首轮功能 SHA [`30723a8f`](https://github.com/kunlong-luo/lanveriq/commit/30723a8f4baff26bfd724dfab1bd0b9ab4e2ffe6) / [OCI #45560934991 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45560934991)；修复 SHA [`a7899ed1`](https://github.com/kunlong-luo/lanveriq/commit/a7899ed167ccc22b675bdffc07a3f396c456c7f7) / [OCI #45561370502 13/13 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45561370502)。** 13/13 真实原生 APT OCI L2 PASS；G-05 OPEN · v0.1 NOT RELEASE READY。**

## 目标：从签名索引观测推进到真实 dpkg 安装版本

[L2B-29](desktop-tauri-l2b29-native-apt-different-valid-signed-index-2026-11-25.md) 验证旧有效签名 `InRelease` 可被原生 APT 接受，但不同的旧 `Packages` **未被下载**、较新缓存保留。其两个 `Packages` 仍描述**同一版本同一 .deb**，不能推出实际安装版本降级。L2B-30 转而在原生 APT 中真正操作**两个 Debian 包版本**，并把默认降级拒绝与显式覆盖分开验证。

## 使用现有成熟组件，不把 CI 试验钥匙升级为正式根

1. 在 Ubuntu24 GitHub runner 使用现有 Rust 1.100 `cargo --release --locked` 真实编译 Agent、运行严格 `verify-offline-agent-deb.py` 归档审计。原合法实验包为 `lanveriq-agent-offline-preview_0.1.0~dev1_amd64.deb`。
2. [隔离 OCI 驱动](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py) 用原生 `dpkg-deb --raw-extract/--build` 从**已审计的 dev1 包**派生 `0.1.0~dev0`。仅替换 control `Version`，并再次用原生 `dpkg-deb --field`、`dpkg --compare-versions` 和解包后 SHA-256 比较，确保两版实际包版本不同、Rust ELF 和 systemd 单元字节**完全相同**，没有新增安装/启动钩子。因此这是**真实 .deb/dpkg 版本测试，不是两个独立的历史源码构建或不同二进制版本升级**。
3. 将两份真正的 `.deb` 放入同一专用临时 `Signed-By` 文件仓库，`Packages` 中各自包含正确 Version、Size、SHA-256 和 filename，由**一次性 CI signer** 生成 `InRelease`；公钥只读挂载，私钥从不进入容器。依旧使用固定 Ubuntu 24.04 OCI digest、`--network none --cap-drop ALL --security-opt no-new-privileges`，APT/dpkg **只在一次性容器 overlay** 操作。
4. [容器内真实原生 APT](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh) 独立执行两项新增场景：
   - **无人值守拒绝：** 在容器内先真实安装 dev1，再以 `-y` 请求 dev0、**不授予覆盖标志**。要求原生 APT 返回非零、日志明确与 downgrade 有关，并核对 `dpkg-query` 仍为 dev1。拒绝由 APT 行为证明，不把包不存在/仓库不可信误当降级保护。
   - **显式例外反证：** 仅 OCI 测试内显式覆盖降级拒绝，要求原生 APT 确实把已安装版本变为 dev0，再升级回 dev1；核查私有操作员配置/状态不丢，未启动、未 enable，最后原生卸载仍保留操作员数据。这证明**APT 的无人值守默认拒绝不是独立、不可回退的发行高水位**；该命令/能力不可进入用户安装器或被解释成生产回滚授权。
5. 现有 [11 项原生正负例](desktop-tauri-l2b29-native-apt-different-valid-signed-index-2026-11-25.md) 继续运行，目标总计 **13 项**。CI 守卫要求非 root runner、显式 `GITHUB_ACTIONS`/实验环境标记、`bash -n`、原生 deb 归档审计和只读 Tauri/Polkit DENY-ALL。

## 真实失败记录（不得归因成安全拒绝）

- [#45560934991 FAIL / 功能 SHA `30723a8f`](https://github.com/kunlong-luo/lanveriq/actions/runs/45560934991)：真实 Rust Agent 编译、`.deb` 审计、3 个源级负测和旧 11 个原生 OCI 场景全部通过；第 12 项在 `apt-get update` 后以退出码 141 中止，尚未触发降级交易。该退出码来自 `apt-cache policy | grep -q` 与 `set -o pipefail` 的提前关闭管道风险；**不是 APT 成功拒绝降级的证据**。
- [`a7899ed1` / #45561370502 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45561370502)：消除 SIGPIPE 假失败后，**13/13 原生 OCI 全部 PASS**；实际 `apt-get`/dpkg 证明：无覆盖参数时 `-y` 降级被拒绝、已安装版本仍 dev1；仅隔离实验使用显式覆盖后，实际安装版本变为 dev0，再升级 dev1。两个真实包的 ELF/systemd 字节相同，配置/state 不丢，未启用 Agent。只读 Tauri 与 Polkit all-no 继续 PASS。

## 验收等级及未解决阻塞

- **L1：** 源码语法与三项索引/版本约束测试均 PASS。**L2：** 经修复 [#45561370502](https://github.com/kunlong-luo/lanveriq/actions/runs/45561370502) **13/13 SUCCESS**，原生 `.deb`审计、临时 signed-APT、真实 OCI 安装/降级/升级/卸载及 no-autostart 通过。runner 主机无安装，实验私钥销毁。**L3 仍 NOT RUN**。
- **不能从本项推导：** 独立历史 Agent ELF、真实外部软件源、正式发行签名/HSM、独立管理域可信时间及高水位、防重装/断网回退、已授权安装 broker、Pop!_OS24 COSMIC 真实硬件 L3。特别是包 Version 变化**不代表代码版本不同**。
- **后续真实退出：** 由具名发行/安全主体批准 signer、固定 Signed-By 密钥与更新链、可信单调高水位和时钟，再以不同**源代码/ELF**及旧签名索引、冷缓存、断网/重装/取消/升级失败做真实隔离负测，随后再申请 Polkit 授权 broker 和真实 L3。此时任何生产安装/联网/采集/自动恢复/远控/CA 依然 OFF。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [唯一 G-05 门禁](../planning/release-gap-register.md) · [首发路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [发行 runbook](../operations/release.md)。
