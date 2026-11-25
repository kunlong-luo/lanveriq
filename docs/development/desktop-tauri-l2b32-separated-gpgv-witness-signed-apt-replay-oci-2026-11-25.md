# DESKTOP-L2B-32 — 两种实验签名身份：原生 APT 可接受旧 Release，但独立见证前置拒绝

**L2 17/17 SUCCESS。** 首轮 [#45566159779 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45566159779) 因见证清单 LF 格式错误未运行 APT，保留失败；修正功能 SHA [55de6e714bde3bb8a3c2f5a0050b413ff38f70c9](https://github.com/kunlong-luo/lanveriq/commit/55de6e714bde3bb8a3c2f5a0050b413ff38f70c9)，[原生 OCI #45566961126 17/17 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126)。**G-05 OPEN · v0.1 NOT RELEASE READY。**

## 从 L2B-31 再推进一个具体的发行安全边界

[L2B-31](desktop-tauri-l2b31-pinned-historical-rust-elf-apt-oci-2026-11-25.md) 已验收两份不同历史 Git 源码编译的 ELF，在隔离 Ubuntu24 原生 APT 中确认无人值守降级拒绝、**显式例外可安装旧版 ELF**，所以 APT 自身的签名与默认提示不是独立高水位。[L2B-27](desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) 还证明，同签名者较旧但仍有效的签名 `InRelease` 可以再次被 APT 接受。不能把两者组合成已经有防回退保证。

本项增加 **OCI 内的第二签名身份与见证预检**，验证真正的拒绝先于产品安装，而不是重新写协议栈或增加自制密码学。沿用成熟 **GnuPG/gpgv、APT/dpkg、系统 SHA-256、只读 Docker 文件挂载**。**这是可观察的 CI 负测链，不是获独立法人/设备管理域托管的生产见证权威。**

## 代码入口与严格限定

- [原生 APT OCI 驱动](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py)：在原有 15 个用例基础上新增 2 个。GnuPG 分别生成 **publisher / attacker / witness 三把一次性钥匙**；单独的 witness 公钥不能与 publisher 公钥相同。APT 软件源继续由 publisher `Signed-By` 签名。
- [OCI-only 见证检查](../../apps/desktop/tools/verify_signed_apt_witness_oci.sh)：在网络关闭的原生 Ubuntu24 容器，使用 `gpgv --keyring ...` 验证独立 witness 的**分离签名**，然后严格检查 `LANVERIQ-CI-ONLY-APT-WITNESS-V1`、`sequence=2` 和当前文件的 `InRelease` SHA-256。所有脚本/见证/公钥只读挂载，不含正式密钥、服务进程、安装器或 Agent 代码。**固定 sequence=2 是实验测试夹具，不是生产高水位的可信持久托管。**
- [容器内真实 APT 交易](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh)：
  1. 两种检查先接受当前有效签名的 `InRelease`；
  2. 更换为**日期更旧仍未到期、由同一 publisher 签名**的 InRelease，要求原生 `apt-get update` 仍成功，而见证因 **Release SHA-256 不同**必须拒绝，不能把 APT 自身拒绝误算为见证成功；
  3. 再验证另一份**由真正 witness 签过的旧 sequence=1**：原生 `gpgv` 验签应成功，但固定实验序列要求拒绝；publisher 冒充 witness 的签名及改动过的见证内容都必须因 **真实 GPG 签名验证失败**而拒绝。
- [L1 单元/安全负测](../../apps/desktop/tools/test_signed_apt_index_replay.py) 约束所见证的摘要绑定、序列、旧日期和总场景数；[CI workflow](../../.github/workflows/desktop-agent-signed-apt-oci-l2.yml) 同时要求非 CI 与缺少实验授权均因真实环境门禁被拒绝，继续从固定 Git 历史源码重新编译不同 ELF 并完成原有 15 个真实 APT 用例。

**隔离环境：** `ubuntu@sha256:562111b...`、`docker --network none --cap-drop ALL --security-opt no-new-privileges`、签名私钥只保留在一次性 GitHub runner 临时目录、不挂载进入 OCI、仓库/公钥/见证签名只读；Agent 不安装到宿主机，也不调用正式 root broker 或 Polkit 授权。

## 保留真实失败和修复记录

- **[#45566159779 FAIL / 功能 SHA `10f97066`](https://github.com/kunlong-luo/lanveriq/actions/runs/45566159779)：** 真实当前 Rust Agent 构建、`.deb` 归档审计、固定旧源码第二次 Rust 编译均成功，但随后五项源级测试中的 witness 清单格式测试正确发现了**字面量反斜线-n（`\\n`），而不是实际 LF 换行**。因此从未进入新增的原生 gpgv/APT 见证实验，**不是见证拒绝通过**。
- **[修正 SHA `55de6e71` / #45566961126 17/17 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45566961126)：** 改由 `bytes([10]).join(...)` 生成真实 LF，并将 **5 项 L1 负测提前到 Rust 编译前，全部通过**。真实两个 Rust ELF 构建/归档审计后，原有 15 项 native OCI APT PASS，新增 2 项均 PASS：**APT 接受旧有效签名 Release，但独立实验见证因哈希不同拒绝**；**真正的旧 witness GPG 签名通过原生 gpgv，但 sequence=1 被固定实验序列拒绝**，发布者伪造与篡改见证同样被原生 gpgv 拒绝。Tauri 只读与 Polkit DENY-ALL 继续 PASS。首轮失败和原始日志保留。

## 必须分开记录的三个层次

| 层次 | 本项可证明 | 本项不能证明 |
| --- | --- | --- |
| APT 签名 | 当前及旧仍有效 signed InRelease 的实际 `apt-get update` 行为 | 系统 APT 自身保证不可回退 |
| 独立实验签名 | 与 publisher 不同的 GnuPG witness，用原生 `gpgv` 和固定 sequence/digest 使旧 Release 和旧签名见证**前置失败** | 真正独立管理域的密钥、高水位托管、可信外部时间、签名/版本链 |
| 用户发行与安装 | 不安装、不启动、不 enable，无网络 OCI；只读 Tauri/Polkit all-no 继续回归 | 用户可信安装器、每次 Polkit 授权、Pop!_OS24 COSMIC 实机 L3、生产信任与合规 |

**不能提高发布状态或把它接入生产安装逻辑。** CI 验证器可被拥有主机管理权的人改动，见证文件/固定值也没有外部托管；旧见证若被同管理域回放，独立防回退仍不成立。后续需具名组织批准**独立发行根与见证管理主体、不可回退高水位/可信时间、密钥分离与撤销、外部可信存储**，再通过受权的实际 broker、冷缓存/断网/重装/旧源/被控 signer 等 L2 与 Pop!_OS 实机 L3。

[当前状态](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [发行操作规范](../operations/release.md)。**G-01～G-10 全 OPEN，生产联网/采集/恢复/远控/CA 签发 OFF。**
