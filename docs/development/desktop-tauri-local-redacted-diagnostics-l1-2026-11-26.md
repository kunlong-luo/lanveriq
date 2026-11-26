# G-05/G-09 — Tauri 本机脱敏诊断与安装阻断报告（非安装功能）

**2026-11-26 · Web/React L1 PASS，Tauri 原生构建 FAIL（APT 闭包漂移） · `main` · v0.1 NOT RELEASE READY。**

## 目的与实际交付

面向 Pop!_OS 24.04 COSMIC 用户，上一阶段 Tauri 窗口只能列出只读系统/Agent 状态，但缺少能给支持工程师或自查使用、且避免暴露个人信息的精简报告。本次直接在**现有桌面 UI** 的“安全与诊断”区增加“本机诊断与安装阻塞报告”，并在“本机 Agent”区明确诊断可用。**不是 GUI 安装器，不会创建 `.deb`、修改主机或授予 Polkit。**

- [纯函数](../../apps/desktop/src/offline-diagnostics.ts)将已有 `inspect_host` 和 `inspect_popos_preflight` 结果转换为**固定白名单枚举值**，不回显 `osName` 原文、主机名、用户、私有路径、APT URL、版本细节、密钥或任意错误字符串。对非法枚举值、陈旧/丢失主机证据默认 `unverified`。
- [React 只读组件](../../apps/desktop/src/OfflineDiagnosticsPanel.tsx)在既有安全页面提供可选中、手动复制的文本框。**没有自动联网、保存、剪贴板插件、文件或 HTTP 权限**，用户手动选择复制之前不向任何地方传送。
- 每份报告固定声明 `install_authorized=false`、`agent_start_authorized=false`、`release_gates_closed=0/10`、`release_status=NOT_RELEASE_READY`、`popos_physical_l3=NOT_RUN`。浏览器预览明确表示 `web_preview_no_host_access`，从不冒充已连接终端。
- 点击重新检测主机时同时清空此前 COSMIC 预检证据，防止把**前一个系统状态**误显示成当前已验证结果。预检仍必须由用户独立点击执行。
- 没有新增第三个 Rust/Tauri IPC、发行依赖、网络权限、systemd 命令、授权 broker 或设备采集。

## 功能 SHA 与验收

- 功能完成提交：[`dfaf97b5`](https://github.com/kunlong-luo/lanveriq/commit/dfaf97b537ac2e8a7a3b302eb4281918a630ada8)；新增 [报告内容负测](../../apps/desktop/src/offline-diagnostics.test.ts) 和 [渲染测试](../../apps/desktop/src/OfflineDiagnosticsPanel.test.tsx)；测试覆盖恶意路径/口令、伪造可安装字段、浏览器空状态、未检查/陈旧状态和禁用 UI 操作。
- [Tauri Preview CI #45744173020](https://github.com/kunlong-luo/lanveriq/actions/runs/45744173020)：**React/L1 job SUCCESS**（包含前端单测、严格安全拒绝测试、TypeScript 类型检查和 Vite 构建），但 **Native Tauri/Rust job FAIL**：签名 Ubuntu 24.04 APT 索引刷新后，当前依赖求解闭包数量不同于已审的 175 包缓存，原生日志明确 `FAIL: APT closure package count differs`；因此尚未运行本功能 SHA 的原生 Rust compile/test。原始失败必须保留，不放宽校验或假称原生通过。
- [桌面 GUI-only .deb 候选 CI #45744172969 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45744172969) 在同样的 `APT closure package count differs` 前置条件阻断。必须另行审核当前仓库索引、真实包版本与完整依赖闭包，重新生成固定且有来源验证的缓存证据，再在相同代码 SHA 上完成原生 Tauri/Rust 构建；**不能改大接受数量/跳过 apt 身份检查来伪造 PASS**。这不是新的可分发包。

## 后续原生构建回归已复验（不追溯改写本 SHA 的失败）

[后续 APT 缓存与原生构建专项报告](desktop-ubuntu24-apt-native-cache-rotation-l2-2026-11-26.md)：原生修复 `a6db3afd` 的 [Tauri 两 job #45745776008 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745776008) 与[GUI-only `.deb` #45745775902 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775902)；`c411d3ea` 的[签名 APT 双 runner #45746180961 两 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180961)。早期 `dfaf97b5` 原生 FAIL 仍是历史事实。非正式 Agent 安装器、无 L3/发行批准。

## 未通过的上线门禁

**没有经正式签名、可供普通用户安装的 Agent 包；没有独立已批准的 Signed-By 发行根及可信时间/抗回滚高水位、最小权限真实 Polkit broker、实际用户安装/取消/卸载或 Pop!_OS 24.04 COSMIC 物理机 L3。** 其他官方 FIDO、管理员 MFA、生产设备 CA、外部 WORM、法务与 SRE 签收同样 OPEN。新诊断界面降低排障阻力，不改变正式发行就绪度或授权状态。[当前评分](../planning/project-scorecard.md)维持**工程 71/100，发布 Gate 0/10 已关闭，生产 NO-GO**。

[桌面技术入口](../../apps/desktop/README.md) · [唯一发布门禁](../planning/release-gap-register.md) · [当前事实](../CURRENT_STATUS.md)。**v0.1 NOT RELEASE READY；生产采集、联网、自动恢复、远控、CA 和未经授权安装 OFF。**
