# G-05/G-09 — Tauri 图形化生成真实 AgentConfig v1 离线空闲配置

**2026-11-26 · 功能 `a70c4243` · 仅开发预览 · React/安装拒绝及 Tauri 原生两 job PASS，GUI-only `.deb` CI 以最终结果为准。v0.1 NOT RELEASE READY。**

## 用户目标与实现

普通 Pop!_OS 24.04 COSMIC 用户不应为了准备最基本的 Agent 配置就先学习 Rust、编译软件或手写 TOML。此前 Tauri 安装向导只做离线兼容性预检、签名和 Polkit 门禁说明，**没有配置编辑/生成界面**；真实 Rust Agent 离线候选包与 OCI `dpkg` 安装/卸载已存在，不再复制一套包管理器。

- [Tauri 新增 `IdleConfigPanel`](../../apps/desktop/src/IdleConfigPanel.tsx)：安装向导第 4 步可直接选择受审的 **15/30/60/120/300 秒**空闲周期；默认 30 秒；标准浏览器 radio 键盘可操作、焦点可见；生成对应只读 TOML 文本框，用户可选中文本。
- [纯函数模板](../../apps/desktop/src/idle-config.ts)只输出 `schema_version = 1`、`[runtime]` 与 `cycle_seconds = N`。这些正是 [Rust `AgentConfig`/严格拒绝未知字段实现](../../apps/agent/src/config.rs) 的真实两项配置；Rust 支持 5～300 秒，本 GUI 仅开放五个离线预审档位。**不加密钥、令牌、URL、设备身份、联网/采集/远控、服务授权字段**；拒绝任意数值、字符串强制转换或注入式参数。
- [单元测试](../../apps/desktop/src/idle-config.test.ts)将默认值与实际 [Agent 配置示例](../../apps/agent/config/agent.example.toml)去掉注释后逐字对齐，覆盖每个预设与非法输入；[界面渲染测试](../../apps/desktop/src/IdleConfigPanel.test.tsx)检查五个单选项、只读文本及非安装边界。现有 [Tauri 只读源码审计](../../apps/desktop/tools/verify_desktop_readonly.py) 同时确保配置面板已嵌入向导且无文件写入/原生 IPC/网络/安装调用。
- **实际运行边界：** 仅浏览器/桌面内存中的预览，不写入 `/etc`，无自动保存、下载、剪贴板插件或 root 请求；没有第三个 Tauri IPC，`bundle.active=false`、Agent idle/offline、生产联网/采集/远控/自动恢复/CA 都不变。只读文本**不是**签名配置、管理员批准、真实 Agent 安装或硬件 L3。

## 精确 CI 和外部缺口

- 功能 SHA：[`a70c4243e7a9b6ade62465e65924735718e76dce`](https://github.com/kunlong-luo/lanveriq/commit/a70c4243e7a9b6ade62465e65924735718e76dce)。
- [桌面 Tauri CI #45748162585](https://github.com/kunlong-luo/lanveriq/actions/runs/45748162585)：**两个 jobs 全部 SUCCESS**：React L1 包含新配置 Vitest、严格 TypeScript/Vite 构建和只读安全边界；原生 Tauri/Rust compile/test 与 Clippy 在同功能 SHA 实际通过。
- [安装意图与 Polkit 默认拒绝 #45748162734](https://github.com/kunlong-luo/lanveriq/actions/runs/45748162734)：**SUCCESS**，实际未放开安装授权。
- [未安装的 GUI-only `.deb` #45748162574](https://github.com/kunlong-luo/lanveriq/actions/runs/45748162574)：**PENDING**，即使通过也只证明隔离的 GUI-only 候选完整性，仍不含正式 Agent。
- 上轮 [更正后的原生 APT 两 runner #45746180961](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180961)、[Tauri #45746181071](https://github.com/kunlong-luo/lanveriq/actions/runs/45746181071)、[GUI-only `.deb` #45746180976](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180976) 均已在各自 `c411d3ea` SHA **SUCCESS**；**不能替代本轮功能 SHA 的原生验收**。

**下一真实产品出口**：独立审批的 Agent 包发行者、可信的 `Signed-By` 发行根及跨重装版本高水位、真正最小权限且可执行的 Polkit/D-Bus 安装事务，由该事务在用户授权后验证并安全写入配置文件，明确安装/启动/开机自启分别授权，支持取消/卸载/升级，最后完成合法授权的真实 Pop!_OS 24.04 COSMIC 硬件 L3。当前均未获批。

**工程自评 71/100，生产 Gate 0/10 已关闭，NO-GO。** 新增的是有真实 Rust schema 对齐的用户配置预览，不是已能给普通人安装的产品；不因 GUI 只读增强而虚增正式发行评分。[唯一当前状态](../CURRENT_STATUS.md) · [发布门禁](../planning/release-gap-register.md) · [安装 ADR](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md)。
