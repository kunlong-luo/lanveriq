# G-05/G-09 — Tauri 本机脱敏诊断与安装阻断报告（非安装功能）

**2026-11-26 · Web/React L1 PASS，Tauri 原生 L1 PENDING · `main` · v0.1 NOT RELEASE READY。**

## 目的与实际交付

面向 Pop!_OS 24.04 COSMIC 用户，上一阶段 Tauri 窗口只能列出只读系统/Agent 状态，但缺少能给支持工程师或自查使用、且避免暴露个人信息的精简报告。本次直接在**现有桌面 UI** 的“安全与诊断”区增加“本机诊断与安装阻塞报告”，并在“本机 Agent”区明确诊断可用。**不是 GUI 安装器，不会创建 `.deb`、修改主机或授予 Polkit。**

- [纯函数](../../apps/desktop/src/offline-diagnostics.ts)将已有 `inspect_host` 和 `inspect_popos_preflight` 结果转换为**固定白名单枚举值**，不回显 `osName` 原文、主机名、用户、私有路径、APT URL、版本细节、密钥或任意错误字符串。对非法枚举值、陈旧/丢失主机证据默认 `unverified`。
- [React 只读组件](../../apps/desktop/src/OfflineDiagnosticsPanel.tsx)在既有安全页面提供可选中、手动复制的文本框。**没有自动联网、保存、剪贴板插件、文件或 HTTP 权限**，用户手动选择复制之前不向任何地方传送。
- 每份报告固定声明 `install_authorized=false`、`agent_start_authorized=false`、`release_gates_closed=0/10`、`release_status=NOT_RELEASE_READY`、`popos_physical_l3=NOT_RUN`。浏览器预览明确表示 `web_preview_no_host_access`，从不冒充已连接终端。
- 点击重新检测主机时同时清空此前 COSMIC 预检证据，防止把**前一个系统状态**误显示成当前已验证结果。预检仍必须由用户独立点击执行。
- 没有新增第三个 Rust/Tauri IPC、发行依赖、网络权限、systemd 命令、授权 broker 或设备采集。

## 功能 SHA 与验收

- 功能完成提交：[`dfaf97b5`](https://github.com/kunlong-luo/lanveriq/commit/dfaf97b537ac2e8a7a3b302eb4281918a630ada8)；新增 [报告内容负测](../../apps/desktop/src/offline-diagnostics.test.ts) 和 [渲染测试](../../apps/desktop/src/OfflineDiagnosticsPanel.test.tsx)；测试覆盖恶意路径/口令、伪造可安装字段、浏览器空状态、未检查/陈旧状态和禁用 UI 操作。
- [Tauri Preview CI #45744173020](https://github.com/kunlong-luo/lanveriq/actions/runs/45744173020)：**React/L1 job SUCCESS**，Native Tauri/Rust job **PENDING**（以实际 GitHub 结果为准，不得提前标两 job PASS）。
- [桌面 GUI-only .deb 候选 CI](https://github.com/kunlong-luo/lanveriq/actions/workflows/desktop-deb-candidate.yml) 仍属于未安装的 CI 合成候选；与本次界面报告无安装授权关系。

## 未通过的上线门禁

**没有经正式签名、可供普通用户安装的 Agent 包；没有独立已批准的 Signed-By 发行根及可信时间/抗回滚高水位、最小权限真实 Polkit broker、实际用户安装/取消/卸载或 Pop!_OS 24.04 COSMIC 物理机 L3。** 其他官方 FIDO、管理员 MFA、生产设备 CA、外部 WORM、法务与 SRE 签收同样 OPEN。新诊断界面降低排障阻力，不改变正式发行就绪度或授权状态。[当前评分](../planning/project-scorecard.md)维持**工程 71/100，发布 Gate 0/10 已关闭，生产 NO-GO**。

[桌面技术入口](../../apps/desktop/README.md) · [唯一发布门禁](../planning/release-gap-register.md) · [当前事实](../CURRENT_STATUS.md)。**v0.1 NOT RELEASE READY；生产采集、联网、自动恢复、远控、CA 和未经授权安装 OFF。**
