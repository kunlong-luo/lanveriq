# DESKTOP-L2B-27 — 原生 APT 有效签名旧 Release 仍被接受（单调高水位缺口真实 L2 验证）

**ACTIVE TEST EVIDENCE · 2026-11-24 · exact functional SHA [09a4e3b386db603a530b271b7f779481c1dd7890](https://github.com/kunlong-luo/lanveriq/commit/09a4e3b386db603a530b271b7f779481c1dd7890) · [Ubuntu24 actual signed-APT Agent OCI #45395513243 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243) · 10/10 native APT cases PASS · NO APPROVED PUBLISHER · G-05 OPEN · v0.1 NOT RELEASE READY。**

## 真正增加的技术验收

[L2B-23 的 9/9 原生 Agent APT](desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md) 验证了**当前 Rust Agent .deb**在仅临时签名的 file: 仓库中安装、重装、卸载及八类错误签名、归档篡改、错误日期/包名的拒绝。但是“错误签名、过期元数据被拒绝”不证明**正常有效签名的旧元数据不能被回放**。

新增 `signed-valid-old-release-acceptance` 第十项 [Python 构造与容器执行器](../../apps/desktop/tools/verify_desktop_agent_signed_apt_oci_lab.py)、[容器内系统 APT 脚本](../../apps/desktop/tools/agent-signed-apt-oci-inner.sh) 和 [独立 CI](../../.github/workflows/desktop-agent-signed-apt-oci-l2.yml)，只在**固定 digest 的 disposable Ubuntu24 OCI**、`--network none --cap-drop ALL --security-opt no-new-privileges` 中运行：

1. CI 普通 runner 真正编译 **Rust 1.100 `--release --locked` Agent ELF**，经过原严格 native .deb 归档审计。创建**临时 GnuPG Ed25519** publisher/attacker，公钥挂载至 OCI 只读目录、私钥不挂载。正式发行 signer **不存在**。
2. 生成相同包 `lanveriq-agent-offline-preview_0.1.0~dev1_amd64.deb` 对应的精确 Size/SHA256 `Packages`，`Release` 及 `InRelease`，`Date` 约为当前 -1 分钟，`Valid-Until` 约当前 +2 天；另用**同一临时发布者签名**生成第二份 `InRelease`，其中 `Date` 比第一份早约 **1 天**，但 `Valid-Until` 同样有效。两个 InRelease 字节和 SHA256 **确实不同**，各自有效签名。
3. 在隔离 OCI overlay 复制 file: 仓库，用仅该仓库专用的 **deb822 `.sources` + 显式 `Signed-By`** 运行系统原生 `apt-get update`。先装载**日期较新**的有效签名 Release；保持同一 APT lists 状态，将当前仓库的 `InRelease` 替换为**日期较旧但仍合法签名**的副本，再次执行原生命令。强制校验 old Date 秒数小于 new Date、签名文件 hash 不相同，两次原生 `apt-get update` 都成功。
4. 最终确认**没有执行任何 `apt-get install`、`dpkg -i` 或 systemctl**，OCI 中没有 Agent ELF、正式配置或 systemd enable。原先 9 项场景另各自独立运行，**9/9 继续 PASS**；合并 **10/10 PASS**。桌面 Tauri 两个 IPC 仍只读、Polkit XML 仍 all-no/uninstalled。

**可被证实的结论仅限：** 此版本原生 APT 对**同一临时有效签名者、未过期、`Packages` 完全相同**的更旧 `Release Date` 没有提供可依赖的、独立持久单调高水位阻断。在此 OCI 版本中，较旧 Date 被真正接受；所以**不能将 APT 的签名和 `Valid-Until` 直接认定为我们的生产回滚保护**。

**本测试没有完成真正包版本回退/降级，也没有模拟不同内容的旧 Packages、长期持久高水位、可信外部时钟、真实仓库服务器、生产 signed Agent 或一个成功授权的 root broker。** 不能以“APT 接受旧日期”声称它接受所有旧索引或可以安装恶意包；仍要由独立发行/安全责任主体设计发行版本单调性、抗回放高水位、有效签名元数据与存储管理员权限的分离，并进行**真实旧版本/断网/重装/降级**攻击验收。

## 确切 CI 失败/修复历史，不删掉错误证据

| 来源 | 真实结果 | 分析 |
| --- | --- | --- |
| [#45394090982](https://github.com/kunlong-luo/lanveriq/actions/runs/45394090982) | **FAIL**：旧九项全部 PASS，第十项未启动真正 APT | `cp -a` 尝试保留文件属主，但容器已经移除 `CAP_CHOWN`，导致 `Operation not permitted`。改为不会改属主的 `cp -R`，**没有**补回 capability |
| [#45394639416](https://github.com/kunlong-luo/lanveriq/actions/runs/45394639416) | **FAIL**：九项继续 PASS，第十项旧仓库第一轮更新失败 | OCI 内复制出的 deb822 源临时文件后缀为 `sources` 而不是 `.sources`；APT 当成传统单行源，提示 `Type 'Types:' is not known`；非时间回放拒绝。增加真实 APT 错误输出以防误归因 |
| [#45395050531](https://github.com/kunlong-luo/lanveriq/actions/runs/45395050531) | **FAIL**：同一 `.sources` 配置失效显性诊断 | 通过原生日志确认后修改源文件名为 `lab.sources`，以 deb822 正式格式让 APT 解析 |
| [#45395513243](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243) | **SUCCESS · 功能 SHA `09a4e3b386db603a530b271b7f779481c1dd7890`** | 原有安装/重装/卸载和 8 拒绝场景**9/9 PASS**；第十项 **signed-valid-old-release-acceptance PASS**。最终报告 `10 real APT Agent package L2 OCI cases`、Tauri readonly 与 Polkit inert guard 均 PASS |

**不能将前三次失败写成“APT 已拒绝回放”**；它们属于实验夹具/源文件解析错误，最后一次确实进入两轮原生 APT 并观察到了更旧有效 Date 的接受。

## 后续 G-05/L2B-28 必须回答的问题

- **谁批准正式发行密钥、`Signed-By` 公钥分发与撤销？** CI 临时签名者无法冒充实际发行者，密钥托管、可信时间、高水位应位于独立管理域。
- **如何在可信组件保存并比对版本高水位？** 包名、版本、Release 日期或经审批的不可回退序列、真实旧签名索引/包与路径替换、clock skew/断网/恢复、重装与跨版本回滚应全部覆盖；只存业务本机可重写 JSON 不够。
- **如何严格拆开安装与行为授权？** 即使未来 root-side 固定包事务 broker 获批准，`install` 也不等于 `systemctl start/enable`、联网、采集和远控权限。实机 Pop!_OS24 COSMIC L3 须独立取得书面批准。

文档总入口：[当前事实](../CURRENT_STATUS.md) · [G-05 发布 Gate](../planning/release-gap-register.md) · [Linux 交付路线](../planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [历史可追溯归档](../governance/archive/README.md)。

**G-01–G-10 OPEN · v0.1 NOT RELEASE READY · 生产联网/采集/远控/自动恢复/CA OFF。**
