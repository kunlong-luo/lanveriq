# DESKTOP-L2B-17 — 独立 Agent 图形安装意图协议与 Polkit 威胁边界（DENY-ALL）

**DESIGN + OFFLINE SECURITY CONTRACT · 2026-11-24 · 39/39 L1 PASS · 不存在可授权的安装桥或正式发行可信根 · G-05 OPEN · v0.1 NOT RELEASE READY**

## 当前真实进度及目的

已取得指定源码 [c4f04e44 / GUI-only Debian 完整 L2 #45322290248](https://github.com/kunlong-luo/lanveriq/actions/runs/45322290248) 和 [ea62c63c / 常规 Tauri 原生预览 #45322718592](https://github.com/kunlong-luo/lanveriq/actions/runs/45322718592) 的 Ubuntu24 成功证据，但这两个包/预览都没有真实安装器、正式发行签名、Polkit 授权或 Pop!_OS 物理 L3。现有 Tauri 固定 **两个只读 IPC**，bundle.active=false 且不包含 Agent/service/maintainer scripts，故不应直接写入 root/sudo/pkexec/systemctl 能力。

本轮仅新增**设计级、始终拒绝授权的安装意图数据形状**：
- [离线 Python 标准库检查器](../../apps/desktop/tools/verify_desktop_agent_install_intent.py) 解析请求，只接受明确的只读演示结构，任何有效结构也只会返回 **DENIED**，不提供“已授权”的返回分支。拒绝任意 shell 命令、动态 systemd 单元名、包路径/URL、额外参数、伪造官方签名、可信 Agent 包及 Polkit 授权、隐私/安装/启动/开机自启/联网/采集/远控勾选。
- [固定 JSON 测试样例](../planning/desktop-agent-install-intent-deny-all.json) 中真实 Agent 包版本、摘要、来源和发行信任全部为 null，各项同意字段必须为严格布尔 false，目标仅限字符串 pop-24.04-cosmic；该样例**不能成为安装请求**。
- [39 项离线正负测试](../../apps/desktop/tools/test_verify_desktop_agent_install_intent.py) 包括重复 JSON、符号链接、文件大小、包名混淆、试图偷渡可信签名、路径穿越、shell 参数、布尔数值强转、伪装同意/Polkit grant、伪造官方操作、错误系统目标等。
- [独立 Actions 工作流](../../.github/workflows/desktop-agent-install-intent-offline.yml) 在 Ubuntu24 上运行单测、真实静态只读源码检查器及 L2B-16 的历史证据/Release Gate 检查；不装包、不执行 pkexec、不用生产密钥和权限，也不会构建可发布的 Agent 安装器。

**真实结果：** [workflow SHA a969798a / CI #45324617901](https://github.com/kunlong-luo/lanveriq/actions/runs/45324617901) **overall SUCCESS；39/39 测试、样例恒定 DENIED、两道既有只读/发布证据 guard 全部 PASS。** 仅可记为离线 L1 安装意图协议**拒绝优先**验证通过。Agent 安装桥的原生功能、签名、真实系统调用及物理 L3 **均 NOT IMPLEMENTED/NOT RUN**。

## 未来真正的安装事务必须单独完成的审查

1. **可信工件与发行主体（现 NOT APPROVED）**：独立审批发布者身份、公钥根/密钥独立管理、签名格式和证书/公钥撤销与可信时间；绑定完整 Agent 包及源码摘要、确切版本/架构、可追踪发布清单。仅算 SHA-256、使用 CI 生成的 Ed25519 实验密钥或由 GitHub Actions 自己声称 success 均不能构成可信发行者。
2. **系统授权边界（现 NOT IMPLEMENTED）**：GUI 永远不以 root 运行；另行设计最小用途、固定执行文件的受限 broker，后端必须对经过签名/白名单批准的包标识、SHA-256 和发行来源做独立重新验证。所有路径/文件描述符的绑定、目录权限、防 symlink/TOCTOU、更换包竞争与环境变量、禁止任意 shell/动态 argv/systemctl 单元名都要真实负向测试；任何缺失都应拒绝。此阶段**没有编写 broker、Polkit action、D-Bus API 或 pkexec 执行入口**。
3. **最少授权及撤销（现 NOT AUTHORIZED）**：安装、启动、开机自启、网络/采集、卸载和恢复必须分离为独立能力与独立用户可见同意；客户端传来的 consent 字段不能当作 OS 身份或组织审批。提交前拟议策略默认拒绝全部非当前活跃会话和未授权身份，不应使用全局 allow 或保留授权缓存（例如 auth_admin_keep）。必须对授权缓存、角色切换、会话状态变化、撤销、重放与离线失败做专项审计。
4. **安装后状态和卸载（未实测）**：签名验证、受限安装、操作收据需与真正包/来源绑定；默认 systemd inactive/disabled，安装成功不意味着同意运行，GUI 退出也不意味着后台 Agent 应停止。拒绝/取消路径不执行 dpkg、更不启用服务；卸载应保留用户数据/合法保留材料，除非另有独立授权。
5. **Pop!_OS 24.04 COSMIC 实机 L3（NOT RUN）**：Wayland/桌面提示/Polkit 用户身份、真实包安装/升级/卸载/重启及无托盘后台状态，只在获得具名操作员和物理非生产设备使用授权后执行。Ubuntu24 Actions 的真实 GUI-only L2 不能替代。

### 与 Polkit 官方规范对应的明确安全约束

Polkit 官方 [polkit 参考手册](https://polkit.pages.freedesktop.org/polkit/polkit.8.html) 提醒，AUTH_ADMIN_KEEP/持久化短期授权可能使后续使用不同附加变量的同一 action 请求继续被授权；因此 Lanveriq 安装事务**禁止依赖短期缓存授权**作为多动作独立审批。官方 [pkexec 参考](https://polkit.pages.freedesktop.org/polkit/pkexec.1.html) 同样说明请求的 command_line 等信息**不应当作为安全判断依据**。这些是未来设计时必须独立验证的约束，不代表现有产品已拥有或配置任何 Polkit 策略。

**不允许快速绕过：** 不创建泛用 root helper、广泛 DBus 服务、不可审计的 shell 脚本执行器，不直接对 unsigned GUI-only .deb 执行 dpkg --install，不通过 CI 设置 installEnabled=true，不以任意本地 JSON 中的用户勾选/公钥字符串颁发受信任安装授权。未取得独立审批前，真实安装动作始终未实现且禁用。

## 下一阶段退出证据

这一阶段只完成了 **离线 L1 协议拒绝检查**，不是 Polkit 交付/Agent installer 完成。之后须依次提供：独立签名及工件托管选型和真实验签链，受限 broker/Polkit 设计审核和专项攻击测试，真实签名 Agent 包的隔离安装 L2，用户同意/卸载及 systemd 默认关闭，最终具名授权 Pop!_OS24 COSMIC L3。保持 G-02/G-05/G-07/G-08/G-09 OPEN，**v0.1 NOT RELEASE READY**。

[L2B-16 发布事实边界](desktop-tauri-l2b16-release-evidence-denial-contract-2026-11-24.md) · [ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md) · [安装器 L1/L2/L3 验收矩阵](../testing/linux-desktop-installer-acceptance.md) · [当前状态](../CURRENT_STATUS.md)。
