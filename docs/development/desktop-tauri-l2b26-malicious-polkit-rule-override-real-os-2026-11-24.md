# DESKTOP-L2B-26 — 真正的本机 Polkit .rules 覆盖拒绝测试（真实 OS L2 PASS）

**2026-11-24 · 功能 SHA [f062f1443fbe1c6144f56b2e2a339b289ac70ee1](https://github.com/kunlong-luo/lanveriq/commit/f062f1443fbe1c6144f56b2e2a339b289ac70ee1) · [Ubuntu24 Polkit CI #45385719766 attempt 1 overall SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) · v0.1 NOT RELEASE READY**

## 新增的工程事实

[L2B-25](desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) 已通过真正 system D-Bus 获取 root-side 发送者 UID/PID，并用内核 /proc 与 Polkit 复核身份；此前实际测过 Polkit DENIED，而模拟 Polkit YES 只存在于单元测试。本阶段用 **真正 upstream polkitd 124** 验证更危险的情况：Polkit 的 .policy XML 仍写 all-no，但本地 JavaScript .rules 可将最终 OS 决定覆盖为 YES。上游 [polkit Reference Manual](https://polkit.pages.freedesktop.org/polkit/polkit.8.html) 说明 addRule/Result.YES 的规则优先机制。

新增 [test_polkit_overriding_rules_oci.sh](../../apps/desktop/tools/test_polkit_overriding_rules_oci.sh)，纳入现有 [.github/workflows/desktop-polkit-runtime-oci-l2.yml](../../.github/workflows/desktop-polkit-runtime-oci-l2.yml)：

- **严格实验范围。** 仅 root、Docker 内 / .dockerenv、CI 显式双环境门禁（disposable-oci-only + ephemeral-only）、无 systemd/Agent 的 disposable Ubuntu24 OCI。运行时 --network none、无宿主系统总线/socket/密钥挂载；原 Polkit policy 与 ReviewLab 源码只读挂载。测试用 .rules 仅动态写到容器 overlay 的 /etc/polkit-1/rules.d，**源码仓库并无可被用户误安装的规则文件**。
- **真实 Polkit 提权反例。** 容器内临时恶意规则仅对 nobody 这个合成 Unix 账号与 org.lanveriq.agent.package.install 返回 polkit.Result.YES。原生 pkaction 仍显示原 XML 三种默认 no，但真正的 pkcheck 以该账号本进程 PID/start-time/UID 检查得到真实 YES（exit 0），而 nobody upgrade/remove、daemon install 仍明确拒绝（exit 1），不是 Python mock。
- **总线名称权限。** 普通账号企图在真实 system D-Bus 上 RequestName(org.lanveriq.Agent.ReviewLab)，返回 AccessDenied，不能假扮只供 root 拥有的临时服务名。
- **独立发行批准阻断。** 原 root-side D-Bus ReviewLab 接收到 nobody 的真正 install 请求（真实 OS Polkit YES），实际返回 **NOT_APPROVED**；服务日志记录 OS_PEER_POLICY_UNVERIFIED operation=install uid=65534。该服务未拥有获批准正式发行者/Agent root 工件或安装 broker，因此绝不调用 APT/dpkg/systemctl。再次使用原 nonce，通过第二次 D-Bus 连接得到 REPLAY_DENIED。
- **继续隔离其他操作与身份。** 对 nobody upgrade/remove、daemon install 的三次独立 root-side D-Bus 请求返回 POLKIT_DENIED；CI 明确要求记录 YES 分支一次、明确拒绝三次、重放一次。验证无 Agent ELF、Agent service、配置或自启链接。

## 真实可核查 CI

[**#45385719766 overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766)，功能源码 SHA **f062f1443fbe1c6144f56b2e2a339b289ac70ee1**。新增专门 CI step **Real hostile local Polkit YES must not bypass root-side independent publisher gate** SUCCESS，原始日志关键结果：

- PASS: real polkitd returned YES despite static XML all-no
- PASS: OS override limited to nobody/install
- PASS: real D-Bus denied unprivileged service-name takeover
- PASS: root-side real bus peer + real Polkit YES remains NOT_APPROVED
- PASS: real hostile Polkit YES cannot authorize ReviewLab install; UID isolation, name ownership and replay remain fail-closed; no Agent, apt or service

既有 L2B-24 Polkit caller 6/6、L2B-25 root-side D-Bus 8 次拒绝与 nonce、XML 30/30、Rust 3/3、ReviewLab 分支 9/9 同一功能 SHA 回归继续通过。此次功能 CI 没有失败 job；历史 [L2B-25 失败与修复](desktop-tauri-l2b25-real-dbus-peer-review-oci-2026-11-24.md) 继续保留。

## 哪些安全结论绝对不能外推

**攻击演练本身故意在 OCI 临时生成一条允许规则，不是正常用户配置教程，绝不复制到真实电脑。** 拥有真实系统 root 的攻击者理论上可以停掉安全服务、修改系统文件，因此不能把此验证说成可对抗完全失陷的 root。本测试只证明现有 OCI ReviewLab 的独立门禁没有在 OS Polkit YES 时执行安装。

**仍缺：** 独立正式发行签名根/密钥托管、经批准的可信源与真实 signed Agent、生产可用 root-side 包管理 broker、可交互管理员/真实 COSMIC 会话、完整事务与安全取消/恢复、跨版本升级、真实 Pop!_OS 24.04 L3、跨重启持久防重放。当前 ReviewLab 没有 APT 安装方法，Tauri 仍只有两个只读 IPC，生产联网/采集/远控/自动恢复/CA 签发 OFF。

**下一阶段 L2B-27：** 优先解决发行信任根与正式签名工件的独立批准，及受限 broker 固定可信来源、独立逐次授权、取消/回滚和服务启动分离。审批前只准隔离 L2 安全验收，不得开启用户一键 Agent 安装。

[当前进度](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [原生安装验收矩阵](../testing/linux-desktop-installer-acceptance.md)。

**G-01–G-10 OPEN · v0.1 NOT RELEASE READY。**
