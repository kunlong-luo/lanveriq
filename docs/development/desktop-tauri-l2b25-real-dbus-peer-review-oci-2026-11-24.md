# DESKTOP-L2B-25 — 真正系统 D-Bus 服务端 OS 同行身份核验、Polkit 全拒绝与重放边界

**2026-11-24 · REAL DBUS/OS PEER L2 PASS · [功能 SHA 91c4669d8b045b5cd67cf94a71101fa7beaa9821](https://github.com/kunlong-luo/lanveriq/commit/91c4669d8b045b5cd67cf94a71101fa7beaa9821) · [Ubuntu24 #45382618806 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) · no actual privileged installation/production trust · v0.1 NOT RELEASE READY**

## 为什么这一项不是又一个静态拒绝 JSON

L2B-24 验证了**普通进程自己**从 Linux /proc 获得 PID/start-time/UID 并请求真实 `pkcheck`。但若日后用户 GUI 调用 root-side 安装服务，**root 侧不能相信 UI 自报的 PID、UID、管理员确认或包路径**。本轮实际在 networkless Ubuntu24 OCI 中运行一个仅审查的 root-side **系统 D-Bus 服务**，服务端由 system bus 根据唯一发送方连接名查询**真实 Unix UID、进程 PID**，核对 Linux 内核 `/proc/<pid>/status` 的 real/effective/saved/fs UID 四值与 `/proc/<pid>/stat` 的进程启动时刻，再调用上游 Polkit；强制只返回拒绝，无包安装。**这不是具备安装权限的可部署 broker**。

- [`polkit-oci-dbus-broker-lab.py`](../../apps/desktop/tools/polkit-oci-dbus-broker-lab.py)：使用发行版维护的 `python3-dbus` 和 GLib/GI，而不是实现自己的 IPC、认证或加密。根服务**仅一个** `org.lanveriq.Agent.ReviewLab.Review(ss) → s` 测试方法和三个固定 `install/upgrade/remove` 动作，不接收/转发用户声明的 UID/PID、包、URL、脚本、服务名、shell 或任意 argv。Linux 实际 sender 同行 UID/PID 始终来自 `org.freedesktop.DBus.GetConnectionUnixUser/GetConnectionUnixProcessID`，服务端通过 /proc 核验并重复检查 bus sender 以拒绝进程退出/身份更替。调用真实 `/usr/bin/pkcheck --process PID,START,UID`，明确退出码 **1** 才返回 `POLKIT_DENIED`；其他包括返回 **0** 的意外允许也只返回 `NOT_APPROVED`，因为尚无正式发行者/信任根/可批准安装 broker；daemon 缺失或超时则 `UNVERIFIED_POLICY`。
- [`polkit-oci-dbus-review.conf`](../../apps/desktop/tools/polkit-oci-dbus-review.conf)：只在实验 OCI overlay 复制到 D-Bus 系统配置；仅允许 root 拥有 **ReviewLab** 这个测试 bus name 和发送此服务的固定接口，不是用户电脑的实际 root 服务定义。没有主动启动服务/安装 Agent 的 systemd 单元、policy 授权缓存或 Polkit `.rules`。
- **重放/资源拒绝**：API 严格接收 GVariant/D-Bus 两个字符串，操作名来自三项 allowlist，请求 ID 严格 **32 个小写十六进制字符**；已消费请求以 `(真实 UID,nonce)` 缓存，**同 UID 跨进程重复提交拒绝**，不同 UID 不借用对方的缓存；缓存达到 256 就 fail-closed。这些记录**仅在本次 OCI 进程内存**，服务重启后不保留，**不是正式防回放数据库、可信时间或 durable 高水位证明**。
- [`test_polkit_dbus_broker_oci.sh`](../../apps/desktop/tools/test_polkit_dbus_broker_oci.sh) 将测试 policy 与 D-Bus conf 仅写入**一次性容器文件系统**，启动真实 distro `dbus-daemon --system` + `polkitd` 和上述 review-only 服务；真正的 `gdbus` 通过系统 D-Bus 连接发起请求。真实 `nobody` 和 `daemon` 两种非 root UID × 三项固定动作得到 **6/6** `POLKIT_DENIED`，再验证相同 nonce 跨进程重放被拒、不同 UID 同 nonce 独立经 Polkit 拒绝、伪造 `PKEXEC_UID` 和 `LANVERIQ_RELEASE_APPROVED` 不能授权，共 **8 次根侧真实 Polkit 拒绝**。未知动作、shell 文字、路径穿越、非标准 nonce、未标注的 GVariant `Int32` 请求、额外方法参数都被拒；**0 次** APT/dpkg/systemctl/Agent 启动。服务返回不包含可执行路径或凭据。
- [`test_polkit_oci_dbus_broker_lab.py`](../../apps/desktop/tools/test_polkit_oci_dbus_broker_lab.py) 通过 distro `dbus-python` 对**同一个实际 service 方法**补 **9/9** 决策单测：包括即使 Polkit 被外部规则放行（模拟返回 0）也必须无安装地返回 `NOT_APPROVED`、127/126/2 不能伪装成明确拒绝、非法操作不触发 Polkit、身份不一致、重复 nonce、内存上限、超时或可执行文件缺失、root caller 默认拒绝。
- [`polkit-runtime-oci.Dockerfile`](../../apps/desktop/tools/polkit-runtime-oci.Dockerfile) 和 [`desktop-polkit-runtime-oci-l2.yml`](../../.github/workflows/desktop-polkit-runtime-oci-l2.yml) 只从 Ubuntu24 发行包安装 `dbus/polkitd/python3-dbus/python3-gi/libglib2.0-bin` 到一次性 OCI image；整个运行用 `docker run --network none`，源代码/策略文件只读挂载，无宿主系统 bus、真实 Agent 或密钥挂载。先跑前一阶段 30/30 Polkit XML 攻击测试、Rust 3/3 固定动作单测和 6/6 用户侧原生 Polkit 拒绝，再运行新 9 单测 + 8 根侧真实拒绝。**源码、Tauri GUI 只有既有两个只读 IPC、所有 Agent 生产开关均未变更。**

## 失败与修复完整证据（不删改）

| 实际 GitHub Actions | 结果 / 原因 |
| --- | --- |
| [#45381206233](https://github.com/kunlong-luo/lanveriq/actions/runs/45381206233)、[#45381349246](https://github.com/kunlong-luo/lanveriq/actions/runs/45381349246)、[#45381527314](https://github.com/kunlong-luo/lanveriq/actions/runs/45381527314) | **FAIL**：`gdbus` 将纯数字 32 位 nonce 解析为 GVariant `Int32`，服务端正确拒绝；测试输入未按 D-Bus 的 `ss` 协议显式标注为字符串。通过使用正确的 GVariant 字符串字面值修复正例，同时**保留未标注 Int32 的恶意类型拒绝**。 |
| [#45381714080](https://github.com/kunlong-luo/lanveriq/actions/runs/45381714080)、[#45381902682](https://github.com/kunlong-luo/lanveriq/actions/runs/45381902682)、[#45382075531](https://github.com/kunlong-luo/lanveriq/actions/runs/45382075531) | **FAIL**：其中六次真实 Polkit root-side 拒绝和重放已 PASS；额外第三个 DBus 参数确实被拒绝，但 `dbus-python` 返回 `TypeError`，测试仅接受 `InvalidArgs`，属于负测错误分类。后续将 TypeError 也视为**请求失败而非授权**，未修改服务端拒绝边界。当前 lab 对方法多余入参仍可能暴露框架错误信息，**尚未完成生产级错误脱敏/接口审查**。 |
| [#45382252693](https://github.com/kunlong-luo/lanveriq/actions/runs/45382252693) | **SUCCESS**：首个完整实时 system D-Bus sender/nonce/replay/恶意输入 L2，8 次实际 Polkit 拒绝；此前 30/30、Rust 3/3、六组用户侧检查均 PASS。 |
| [#45382618806](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | **SUCCESS，最终功能源码 91c4669d**：再补同一实际 root review service 的 **9/9** 决策分支单测，特别证明“模拟 Polkit YES 不执行任何安装”，且全部实时 L2 继续 PASS。 |

## 未通过的生产级根侧安装器 Gate

**不等于能够给用户安装。** 此服务虽然以 root 在一次性 OCI 中运行，但其名字就限定为 **ReviewLab**，无 D-Bus 安装事务、APT 控制、PackageKit 权限、可信仓库、签名发行工件、操作员真实活动登录会话、Polkit 身份验证交互或取消/回滚。当前更进一步的限制包括：临时 D-Bus bus policy 仅用于隔离实验、没有 systemd-logind 活动/非活动用户会话、nonce cache 不持久、未验证本机额外 `.rules` 覆盖、`pkcheck` 的调用和发起者进程退出时序仍需专门事务级 TOCTOU 审查、多余 DBus 参数触发框架 `TypeError` 的错误脱敏不足。它**不能用生产权限或真实 Agent 数据运行**。

**下一阶段 L2B-26 的可批准方向：** 独立安全/发布责任主体审批固定 **Lanveriq Agent package、APT Signed-By 根、可撤销版本和安装/升级/卸载三动作**之后，以成熟 OS Polkit + PackageKit/APT 设计真正 D-Bus peercred root broker 的签名源核查、逐次管理员会话授权与事务原子性、防链接/路径/argv 注入、锁/取消/中断、升级/回滚状态；先一次性隔离 L2，再在设备所有人明确许可下做 Pop!_OS24 COSMIC L3。**未经审批前不得将 ReviewLab service、其测试 bus config、CI 公钥或测试策略复制进任何发布包或用户主机。**

[当前状态](../CURRENT_STATUS.md) · [发行 Gate](../planning/release-gap-register.md) · [L2B-24 真实用户侧 Polkit 绑定](desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md) · [L2B-23 真实 Agent APT 包生命周期](desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)。

**G-01–G-10 OPEN，v0.1 NOT RELEASE READY；生产联网、采集、自动恢复、远控、CA 签发 OFF。**
