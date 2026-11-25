# DESKTOP-L2B-33 — 冷缓存双回放：旧 APT Packages + 有效旧 witness 同时回放、分离只读锚点拒绝

**ACTIVE / PENDING L2（2026-11-25）**。实现及首次失败：[`cd8b3d85`](https://github.com/kunlong-luo/lanveriq/commit/cd8b3d85ed3e5cf7a7b98c4b6dc27947f1d744fe)、[CI #45568690884 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45568690884)；修复驱动缩进：[`8621510f`](https://github.com/kunlong-luo/lanveriq/commit/8621510f3b1b6bd345624d3b3a62e6fdae11582f)、[原生 OCI CI #45568837764 PENDING](https://github.com/kunlong-luo/lanveriq/actions/runs/45568837764)。**尚未通过本项原生 OCI，不可提前声称 20/20。** G-05 OPEN、v0.1 NOT RELEASE READY。

## 目的与现有差距

[L2B-32 17/17](desktop-tauri-l2b32-separated-gpgv-witness-signed-apt-replay-oci-2026-11-25.md) 验证了 CI 中两把不同 GPG 签名者的原生 APT 旧 Release 与 gpgv 见证拒绝；但原检验器固定 `sequence=2`，并未测试 **APT Release、不同的旧 Packages、同样声明 `sequence=2` 的有效旧 witness 同时回放**。这类组合可以通过单纯的签名与序列一致性校验，尤其在重装/空 APT 缓存时不能依靠先前已有的新版 Packages 缓存来保留安全状态。

本项构造 **独立于仓库目录的实验批准锚点**：在 GitHub runner 的另一临时目录写入“批准 witness 文件 SHA256”，通过 **另一个只读 OCI bind mount** 显式交给前置校验器，要求它与 **gpgv 签名验证和当前 signed `InRelease` SHA256** 同时一致。它只演示“不同信任来源的输入要相互约束”，**不证明外部独立管理员/云域/可信时间/硬件反回退高水位**：所有夹具、锚点、实验签名者仍由同一 CI 管理域生成和销毁，管理员可控制其来源。

## 严格实测（新增 3 项，保留前 17 项）

1. **批准材料、冷缓存成功**：全新 networkless Ubuntu24 OCI 自定义 `Dir::State::lists`/`Dir::Cache::archives`，要求旧 APT 列表为空；真实 `apt-get update` 验证当前一次性 publisher Signed-By 仓库和 Packages；不同 CI witness 公钥的 `gpgv` 验签、`sequence=2`、当前 `InRelease` 摘要、独立只读批准 witness SHA256 均匹配；**只有预检、不安装**。
2. **两个旧签名共同回放，冷缓存拒绝**：把仍在 `Valid-Until` 范围内的 **旧 `InRelease` + 内容不同、签名索引摘要匹配的旧 `Packages`** 一起提供给空 APT 列表，必须证明原生 APT **确实采用旧 Packages 描述**，不能沿用 [L2B-29](desktop-tauri-l2b29-native-apt-different-valid-signed-index-2026-11-25.md) 的热缓存现象。旧 `InRelease` 是一次性 publisher 真签名；旧 witness 也是不同 CI witness 真签名且**仍为 sequence=2**，其内容哈希与旧 Release 正确匹配。原生 `gpgv`、序列与 Release 摘要三者独立自检通过之后，单独挂载的批准 witness SHA256 必须因不同而拒绝。
3. **独立锚点缺失时拒绝**：其他 APT/见证数据正确，但容器**不挂载**批准锚点，需明确因锚点缺失拒绝；不得用参数错误、APT 失败或缺失公钥冒充证明。

三项全部继续检查 **没有在宿主机或 OCI 内安装 Agent、没有 service start/enable、没有调用 Polkit root broker、没有生产网络**。完整原生测试目标 **20/20**。固定 Ubuntu24 OCI digest、`--network none --cap-drop ALL --security-opt no-new-privileges`，一次性 GPG 私钥只存在 runner 临时目录、所有 repo/witness 和单独批准锚点只读挂载。仅用成熟 **APT、gpgv、GnuPG、sha256sum、Docker**，没有新产品签名协议、发行 SDK 或实际用户安装器。

## 失败历史与代码入口

- 实验见证逻辑：[OCI only 校验脚本](../../apps/desktop/tools/verify_signed_apt_witness_oci.sh)；
- 试验仓库、不同签名见证、独立 host 临时锚点：[Python 夹具驱动](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py)；
- 冷缓存的原生 APT 实验与安装前拒绝：[OCI 内 Bash](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh)；
- [5+ 定向负测](../../apps/desktop/tools/test_signed_apt_index_replay.py) 与 [真实 OCI workflow](../../.github/workflows/desktop-agent-signed-apt-oci-l2.yml)。
- [#45568609482 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45568609482) 和 [#45568690884 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45568690884)：`run_cases` 代码挪入单独函数时缩进不一致，`python3 -m py_compile` **在 Rust 编译前拒绝**。这是测试驱动源代码错误，不是独立锚点的实际安全证据。修复提交 `8621510f` 仍需等待真实原生 CI。不得抹去或改写 FAIL。

## 下一项真实发行退出条件

真正的 G-05 仍必须由**具名、独立于发行软件仓库及应用运维权限的安全主体**提供可信 signer、硬件/隔离密钥与撤销、可信时间、不可回退的外部版本/摘要高水位和审计，以及在重装/冷缓存/离线/双回放场景中的真实受控恢复授权。没有正式可信工件、按操作的真实 Polkit root 事务 broker、Pop!_OS24 COSMIC 实机 L3、用户/法务/SRE 签收，**全部 Gate OPEN**。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [唯一发布缺口](../planning/release-gap-register.md) · [Linux 路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [发行运行手册](../operations/release.md)。
