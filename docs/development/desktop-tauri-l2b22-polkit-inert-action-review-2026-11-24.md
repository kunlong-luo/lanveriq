# DESKTOP-L2B-22：Polkit 最小动作集的独立默认拒绝安全基线

**2026-11-24 · SOURCE REVIEW ONLY / L1 OFFLINE PASS · NOT INSTALLED · v0.1 NOT RELEASE READY**

## 本轮确切实现

功能源码 [`efb7a558711436ae76df8082322b14c7346f249b`](https://github.com/kunlong-luo/lanveriq/commit/efb7a558711436ae76df8082322b14c7346f249b)；[同 SHA Ubuntu 24.04 CI #45375272477 attempt 1 **overall SUCCESS**](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)。该测试是离线源码/策略逻辑验收，**没有启动真实 polkitd/pkexec、没有安装政策文件、没有以 root 运行 broker、没有执行 APT 安装/升级/卸载，也不构成 Pop!_OS 实机验收**。

- [`apps/desktop/polkit/org.lanveriq.agent.package.policy`](../../apps/desktop/polkit/org.lanveriq.agent.package.policy)：采用系统 Polkit 原生 XML 动作结构，**仅定义**三个不同 ID：`org.lanveriq.agent.package.install`、`upgrade`、`remove`。每个动作的 `allow_any`、`allow_inactive`、`allow_active` 都是字面值 `no`。没有 `org.freedesktop.policykit.exec.path/argv1`、`imply`、`allow_gui`、其他任意执行动作或 Javascript `.rules`。
- [`apps/desktop/tools/verify_desktop_polkit_review.py`](../../apps/desktop/tools/verify_desktop_polkit_review.py)：Python 标准库 `xml.etree.ElementTree` **解析实际 XML 字节**，严格固定文件名/大小/内容类型、三种动作/显示词、默认权限、无缓存/额外注解/DTD/ENTITY、文件不为 symlink/可执行、同目录禁止任何 helper/rules/service 文件；同时检查 Tauri `bundle.active=false`、只有两个原只读 IPC、没有 polkit/pkexec 运行路径。成功只返回 `PASS: inert Polkit action XML is DENY-ALL; NOT INSTALLED, NO BROKER OR INSTALL AUTHORITY`。
- [`apps/desktop/tools/test_verify_desktop_polkit_review.py`](../../apps/desktop/tools/test_verify_desktop_polkit_review.py)：**30/30** 离线测试，向真实政策字节注入 `yes`、`auth_admin`、`auth_admin_keep`、`auth_self_keep`、meta action `imply`、pkexec exec.path / argv1、动态 start、新动作、同名动作、身份/提示篡改、DTD/ENTITY、额外 helper/rules、Symlink、执行位和桌面 bundle 提权，**全部拒绝**。改动不能仅在文档里声称策略默认拒绝。
- [`.github/workflows/desktop-agent-install-intent-offline.yml`](../../.github/workflows/desktop-agent-install-intent-offline.yml)：路径过滤包括新政策/源码/负测，继续联合运行之前 **39/39** 安装意图 DENY-ALL、已部署 Tauri 只读源码门禁和发行证据只读护栏；其中 `39/39 + 30/30` 是不同测试集，不把一次 CI 虚报成“真实 Polkit 权限测试”。

## CI 失败与修复（不可抹掉）

[首次同逻辑 CI #45375193990 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45375193990)：原安装意图 **39/39** PASS，新政策负测 30 项中 **29 PASS、1 FAIL**。失败是 `test_reject_wrong_policy_root` 只替换打开标签而未同时替换结束标签，导致通用“无效 XML”先于“错误 root 身份”拒绝；不是任何权限被放开。之后在 [`efb7a558`](https://github.com/kunlong-luo/lanveriq/commit/efb7a558711436ae76df8082322b14c7346f249b) 将恶意样例改为**形式良好但身份错误**的 XML，保留该攻击用例，复跑 [#45375272477 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)：**39/39 + 30/30 + 既有 Tauri/read-only 与发布拒绝护栏全 PASS**。

## 与真实受限安装器的严格区别

**本轮仅是未安装的 Polkit XML 策略审查，不代表“系统已经禁止/允许了这个动作”。** Polkit 可有其它本机 Javascript `.rules` 覆盖 XML 默认判断，因此必须针对最终目标主机的整个规则集和用户身份做实机/隔离检查。Polkit 的 `auth_admin_keep` 会在短期内缓存同一 action+subject 的授权，即使附加变量变化，故本政策主动禁止任何 KEEP 行为，真实 broker 仍要每次结合权限来源、行为、会话、工件、时钟重新鉴别。[Polkit 官方参考](https://polkit.pages.freedesktop.org/polkit/polkit.8.html) · [pkexec 安全边界](https://polkit.pages.freedesktop.org/polkit/pkexec.1.html)。

**本仓库没有把政策复制到 `/usr/share/polkit-1/actions/`，没有配置 `pkexec` 可执行文件或受限 broker，没有授予 Tauri/Agent 管理员权限。** 没有真实受信发行根、签名 Agent 包、正式可用 APT `Signed-By` 源、合法用户签收、服务启停/自启授权或实际 Pop!_OS COSMIC L3。安装/升级/卸载与**启动后台服务、开机自启、联网、采集、远控、CA 签发**是不同能力，此政策即使未来审批也不能授予它们。

## 真正的 L2B-23 退出标准（尚未执行）

1. 确认独立发行者的**受信签名根和实际 Agent 版本/工件、发行源、撤销/可信时间/防回退**；产品不得以 CI 临时 Ed25519 key、GUI JSON 或 unsigned GUI-only `.deb` 冒充受信。
2. 不新增独立常驻守护程序，优先复用 OS 原生 APT/PackageKit 与 Polkit。设计可审的**固定作用域 root-side broker**：严格绑定调用方真实活动会话、包名、受信来源、签名索引/固定仓库、操作种类、快照/授权的同事务一致性，防 symlink、任意路径/URL/argv、环境变量、TOCTOU、重放；**绝不提供泛用 root shell**。
3. 先在隔离 Ubuntu24 使用**真实已验的 Agent `.deb`**，执行受限安装/升级/卸载的正负向 L2，明确失败/取消/重装/保存状态、默认 inactive/disabled 与不触发隐式采集/联网。
4. 另由设备所有人、法务/安全与操作员对具名 Pop!_OS 24.04 COSMIC 实机 L3 签收；运行中不得无告知安装或隐蔽采集。

[已有桌面 GUI L2B-21](desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) · [L2B-17 原威胁模型](desktop-tauri-l2b17-agent-install-intent-polkit-threat-contract-2026-11-24.md) · [当前状态](../CURRENT_STATUS.md) · [发布缺口](../planning/release-gap-register.md)。

**G-02/G-05/G-07/G-08/G-09 及正式 G-06 等全部 OPEN；v0.1 NOT RELEASE READY。**
