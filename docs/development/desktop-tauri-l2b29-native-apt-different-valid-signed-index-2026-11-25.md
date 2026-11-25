# DESKTOP-L2B-29 — 原生 APT 接受旧签名 Release，但保留不同的较新 Packages 索引

**功能源码：** [`ef9954610622c4ff8a09e1548a1418bdf5c10ce0`](https://github.com/kunlong-luo/lanveriq/commit/ef9954610622c4ff8a09e1548a1418bdf5c10ce0)。**隔离原生 APT L2：[运行 #45559034203](https://github.com/kunlong-luo/lanveriq/actions/runs/45559034203) 11/11 SUCCESS。** 先前三次 FAIL 均保留原始证据；仅证明本固定 OCI 中较新版 Packages 被保留。**G-05 OPEN · v0.1 NOT RELEASE READY。**

## 为什么在 L2B-27 / L2B-28 之后仍需此测试

[L2B-27](desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) 在真实系统 APT 中观察到「新 Date 有效签名 Release → 旧 Date 仍有效签名 Release」，但两个 Release 所引用的 `Packages` 完全相同；旧日期被接受并不足以证明**不同包索引**被实际采用。随后 [L2B-28](desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md) 仅在源码/离线 JSON 层拒绝错误的可信高水位声明，并没有阻断 APT 原生回放。

本项复用同一成熟 `apt-get`/deb822 `Signed-By`/GnuPG 与真实已经审计过的 Rust `.deb`，而不是开发自制 APT 协议或签名组件。

## 实际代码与真实环境

- [Python 原生 OCI 驱动](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py)：在现有十项上增加 `signed-old-different-packages-retained` 第十一项；持有 **一次性**签名私钥的一侧构造签名索引，只有公钥与真实 Agent `.deb` 暴露给 OCI；新旧 `Packages` 使用**同一包名/版本/arch/原真实 .deb SHA256**，只改变可识别的 `Description` 文字，因此新旧 `Packages` SHA256 与签名 `Release` 中绑定的摘要必定不同。
- [容器内系统 APT 脚本](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh)：在 `ubuntu@sha256:562111...` 固定镜像的 disposable OCI overlay 内，先 `apt-get update` 新签名索引并用 `apt-cache show` 核对新描述，再在同一个 APT lists 状态下替换为**日期较旧、仍未到期、相同 signer、重新签名且指向旧 Packages 哈希**的 `InRelease` 和包索引；第二次执行真正 `apt-get update`；已经实测**它接受旧有效签名 `InRelease`、却没有再下载旧 `Packages`，`apt-cache` 保留较新描述**。新拒绝优先检查固定此行为：如果改为下载旧 Packages 或旧描述被采用，则 CI 必须 FAIL 供安全复核。另核对两索引 SHA256 不同、旧 Date 更早、Agent 未安装、未启动、未 enable。
- [定向源级负测](../../apps/desktop/tools/test_signed_apt_index_replay.py)及[路径过滤 L2 CI](../../.github/workflows/desktop-agent-signed-apt-oci-l2.yml)：保证旧索引构造只改变描述字段、不冒充实际新旧版本/软件降级、遇到重复或无标记则拒绝。**实际 APT 接受与否必须由原生 OCI job 结果决定**。

**强制环境：** `GITHUB_ACTIONS=true`、`RUNNER_OS=Linux`、显式 `LANVERIQ_SIGNED_AGENT_APT_L2=1`、rootless runner 的容器调用、`docker --network none --cap-drop ALL --security-opt no-new-privileges`、仅读挂载测试仓库、固定 Ubuntu 24 digest、临时实验公私钥。无外部网络、正式发行者/CA、用户主机事务、用户/员工数据或本地 Polkit 授权。

## 本阶段原始失败与保留证据

| 功能 SHA | 真实运行 | 已经观察到的事实 |
| --- | --- | --- |
| [`bd6d4664`](https://github.com/kunlong-luo/lanveriq/commit/bd6d46646b6bad9fb7578a0097921084af059650) | [#45558040197 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45558040197) | 原有 10 个原生 APT 场景通过；第 11 项未通过。最初仅有新描述输出，不可据此判定具体缺口 |
| [`d29da39a`](https://github.com/kunlong-luo/lanveriq/commit/d29da39ab00ef352585c3593022a7fa314982bf6) | [#45558342030 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45558342030) | 旧 Release 日期确实更早、Packages SHA256 确实不同、第二次原生 `apt-get update` 接受旧 `InRelease`，但 `apt-cache` **未显示旧 Packages 的描述**。因此不能宣称不同内容旧索引被实际采用 |
| [`607e7b17`](https://github.com/kunlong-luo/lanveriq/commit/607e7b171feecc74361e5ff7da3c1908d9688b47) | [#45558662699 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45558662699) | 第二次 APT `update` 接受旧签名 InRelease，**没有下载旧 Packages、`apt-cache` 留在新版**；原测试期待旧内容被采用，因此依设计失败 |
| [`ef995461`](https://github.com/kunlong-luo/lanveriq/commit/ef9954610622c4ff8a09e1548a1418bdf5c10ce0) | [#45559034203 SUCCESS：11/11](https://github.com/kunlong-luo/lanveriq/actions/runs/45559034203) | 实际 `apt-get update` 接受较旧的有效签名 InRelease；未下载不同旧 Packages、`apt-cache` 保留较新描述；正式签名高水位仍不存在。若将来下载/采用旧 Packages，回归 CI fail-closed |

## 同一 SHA 原生验收结果

[Linux APT L2 #45559034203](https://github.com/kunlong-luo/lanveriq/actions/runs/45559034203) 的原始日志确认真实 Rust Agent `--release --locked` 编译、严格 `.deb` 归档审计、三项定向 Python 负测均 PASS；十项既有原生 APT 正反例继续 PASS；新增第 11 项 `signed-old-different-packages-retained` PASS。测试脚本和原生 APT 同时确认：两份 Packages SHA256 不同、较旧的签名 Release Date、更旧 `InRelease` 被接受、无旧 Packages 下载、`apt-cache` 保持新描述、无 Agent 安装。只读 Tauri/Polkit DENY-ALL 继续 PASS。完整历史失败 #45558040197 / #45558342030 / #45558662699 不删除。

## 精确允许的结论与不能推论的能力

本测试**即使最终通过**也只证明当前固定 OCI 里 APT 在接受旧有效签名 `InRelease` 后**仍保留较新 Packages 元数据**，且包索引不同。此行为不等于获独立保证的持久高水位：测试没有卸载/重装后的独立托管单调状态、没有可信外部时钟、也**没有实际 Agent 版本降级或不同真实包版本交易**。不能把此次缓存保留外推为 APT 保证拒绝所有旧仓库或已发行受信任 Agent。

**后续安全工作**：独立批准发行根与保管、高水位单调版本/摘要托管、可信时间和实际包版本变化的旧索引负测；再做被授权的 root-side 固定安装事务、取消/中断/重装/回滚以及真实 Pop!_OS24 COSMIC L3。每个都要独立证据和真实责任人签字。**G-01～G-10 全 OPEN，正式 Agent/联网/采集/自动恢复/远控/签发 OFF。**
