# DESKTOP-L2B-24 — 真正 polkitd/D-Bus + Rust 固定动作身份绑定拒绝 L2

**2026-11-24 · ACTUAL OS POLKIT DENIAL L2 SUCCESS · 功能源码 [ba04f55c3c63314e43d45179e5a574224a334e29](https://github.com/kunlong-luo/lanveriq/commit/ba04f55c3c63314e43d45179e5a574224a334e29) · [Ubuntu24 OCI CI #45379398181 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181) · v0.1 NOT RELEASE READY · no signed issuer or privileged broker**

## 相比 L2B-22/23 的真实增量

L2B-22 只解析未安装的 Polkit XML，L2B-23 用临时签名 APT 源在 OCI root 下安装真实 Agent，却**没有经过 Polkit 用户授权**。本轮让**真实 upstream Polkit 124、DBus system bus 与调用者自身的 Rust 进程**在一个无网络 Ubuntu24 OCI 内真正完成身份/固定操作的拒绝判定；不靠 Python 解析 XML 来冒充 OS 已判断。

- [`polkit-runtime-oci.Dockerfile`](../../apps/desktop/tools/polkit-runtime-oci.Dockerfile) 从与 L2B-23 一致的固定 Ubuntu24 OCI digest 构建，仅在 Docker build 时安装发行版自带的 `polkitd`、`dbus`、`util-linux`。本产品**没有新守护进程、供应商 SDK 或独立权限引擎**。
- [`test_polkit_runtime_oci.sh`](../../apps/desktop/tools/test_polkit_runtime_oci.sh) 仅在 `docker run --network none` 的临时容器内将本仓库已有的 `apps/desktop/polkit/org.lanveriq.agent.package.policy` 复制到**容器 overlay 内的** `/usr/share/polkit-1/actions`，启动容器私有系统 D-Bus；`pkaction --verbose` 亲自确认三个动作 `implicit any/inactive/active: no`。
- 真正 `pkcheck` 以两个不同非 root Unix 用户 `nobody` 和 `daemon`，分别检查 install/upgrade/remove **6 次真实系统授权**。要求确切退出码 **1（显式拒绝）**；错误/daemon 不可达退出码 127/126/2 **不可冒充拒绝 PASS**；还核对没有泛用 exec/start/enable/network/collection/remote-control 动作。
- [`polkit-fixed-action-gate.rs`](../../apps/desktop/tools/polkit-fixed-action-gate.rs) 是新的**独立、不提权的 Rust 1.100 可执行源码**，不加入 Tauri、Agent、正式发行包或 root helper。命令行只接受一个字面 `install|upgrade|remove`，拒绝所有额外参数、URL、路径、包名和服务名。它从 **Linux /proc/self/status 和 /proc/self/stat** 读取真实本进程 UID、PID 与 start-time，使用官方建议的完整 `pid,start-time,uid` 格式，调用**固定绝对路径** `/usr/bin/pkcheck`；无 shell、无动态程序名、无任意 `Command`。任何 Polkit 明确拒绝返回独立非零码 **21**；身份/daemon 错误返回 **23**；非法操作 **22**；即使意外被 Polkit 允许，也返回 **24**，**永远不执行 apt-get/dpkg/systemctl**，因为发行者/工件/受限 root-side broker 根本未批准。[Ubuntu 官方 pkcheck 手册](https://manpages.ubuntu.com/manpages/noble/man1/pkcheck.1.html) 强调三字段身份绑定与 0/1/2/3/126/127 退出码语义。
- [`desktop-polkit-runtime-oci-l2.yml`](../../.github/workflows/desktop-polkit-runtime-oci-l2.yml) 先运行原静态 Polkit XML **30/30** 变异负测，真实 Rust 1.100 `rustc --test` **3/3** 单测，然后把**仅 CI 编译**的 Rust 可执行文件以只读挂载送入 OCI；两种身份分别用真实 `pkcheck` 和 Rust gate 重复验证 6 组拒绝。另拒绝 5 类非法 CLI 参数、伪造 `PKEXEC_UID=0` / `LANVERIQ_RELEASE_APPROVED=true` 环境、根身份运行、以及与既有 Tauri 只读权限偏离。文件绝不分发给用户。

**本轮已实现的事实仅是“真实 OS 权限拒绝和 Rust 调用方绑定”**：它不是经组织授权的成功安装事务，不代表 OS 会对所有实机用户/活跃 COSMIC 会话作相同判断（本轮没有 systemd-logind 实际用户会话），也不能证明不同本机 `.rules` 无法覆盖默认策略。

## 真实 CI 记录及失败/修复

| 实际提交/CI | 原始结果与精确含义 |
| --- | --- |
| [cd4c188f / #45378688100](https://github.com/kunlong-luo/lanveriq/actions/runs/45378688100) | **FAIL**：容器启用 `no-new-privileges` 阻止 stock DBus setuid activation helper，`pkaction` 不能连接 polkitd；**没有**拿 daemon 失败当授权拒绝成功 |
| [ceff8ec9 / #45378847923](https://github.com/kunlong-luo/lanveriq/actions/runs/45378847923) | **FAIL**：YAML 改动中的注释出现字面 `\\n`，破坏多行 `docker run` 命令，最终 shell 报 `--network: command not found`；未执行 OS policy 评价 |
| [8847eccc / #45379004317](https://github.com/kunlong-luo/lanveriq/actions/runs/45379004317) | **SUCCESS**：真实 Polkit/DBus `pkaction` 读取 all-no 策略，`pkcheck` 原生 3 操作 × 2 非特权账号 **6/6 显式拒绝**，Tauri 源码默认拒绝继续通过 |
| [7ebb27d7 / #45379357031](https://github.com/kunlong-luo/lanveriq/actions/runs/45379357031) | **FAIL**：加入 Rust gate 的 CI 检查前，脚本开始引用尚未编译/只读挂载的程序；此阶段不能声称完成 Rust 集成 |
| [ba04f55c / #45379398181](https://github.com/kunlong-luo/lanveriq/actions/runs/45379398181) | **SUCCESS（最终功能 SHA）**：30/30 XML 负测、Rust 单元 3/3，真实 Polkit/DBus+Rust 固定 action 6/6 非 root 明确拒绝、5 个注入及伪造环境拒绝，Tauri readonly guard 均 PASS |

**隔离例外需透明记录：** 因 DBus 系统服务启动依赖其发行版 setuid helper，最终 `docker run` 没有启用 `--security-opt no-new-privileges`，否则得到上述第一轮错误而非有效测试；但仍使用 `--network none`、一次性私有 OCI 根文件系统、严格 pids/内存/CPU 限制，**没有挂载宿主机系统 DBus socket、`/etc/polkit-1`、`/usr/share/polkit-1/actions` 或任何用户生产密钥**；挂载源 policy 与本轮编译的 Rust 二进制均只读。此安全权衡**仅限隔离测试容器**，不是未来产品 root broker 的权限方案。

## Gate 严格边界 / 后续真正可安装的路径

**未交付**：root-side D-Bus/OS peercred 可信 caller 校验与安全代理、每次真实管理员交互、可批准且可撤销的受限安装策略、独立组织管理的实际 APT Signed-By 发行根及工件来源、真正的 signed Agent、异步取消/中断安装恢复、跨版本升级/回滚、Pop!_OS24 COSMIC 活跃会话和具名实机 L3。**用户界面仍只有两个只读 Tauri IPC，Install 按钮锁定。** 当前 Rust 程序是非特权 OS 请求侧 gate，不能自称为能以 root 执行事务的 broker。

**L2B-25 下一阶段：** 在正式根/管理域及法务审批之前，只能进一步在**隔离授权模拟域**实现并攻击测试 root-side 狭义固定包名/版本/来源、OS D-Bus peer credentials、独立 per-operation Polkit 交互、可中止/取消的 APT 状态事务，证明无任意命令与系统服务自动启动。**任何测试授权不得复制到真实用户主机**。待独立发行根、组织和用户签字后，再进行真实 GUI→Polkit→APT 授权成功与 Pop!_OS COSMIC L3。

[CURRENT_STATUS](../CURRENT_STATUS.md) · [发行阻塞表](../planning/release-gap-register.md) · [工程评分](../planning/project-scorecard.md) · [L2B-22 源审](desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md) · [L2B-23 真实 APT 包安装](desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

**G-01–G-10 保持 OPEN。v0.1 NOT RELEASE READY，生产联网/采集/自动恢复/远控/CA OFF。**
