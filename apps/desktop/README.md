# Lanveriq Desktop — Tauri 2 安装准备/状态预览（不可安装）

**ACTIVE SOURCE / NONINSTALLING PREVIEW · Tauri 2 / Rust 1.100 / React 19 / Vite 8 / Tailwind 4 · v0.1 NOT RELEASE READY。**

这是本机窗口工具，不是第二个 Web 控制台（唯一后台 Web 位于 `apps/web`）。未来面向 Pop!_OS 24.04 / COSMIC 的用户目标是“一次经授权的图形安装 + 独立 systemd 后台 Agent + 可发现的停止/卸载入口”，**不是现在已经可以一键安装**。

## 已实现的界面和真实边界

- 四步中文安装准备，新增[AgentConfig v1 的离线图形化空闲配置预览](../../docs/development/desktop-tauri-agent-idle-config-ui-2026-11-26.md)：选择 15/30/60/120/300 秒并生成只读 TOML，不写入 `/etc`、不持有机密；已有系统兼容性、候选 Agent/systemd 状态及发行/Polkit/L3 缺口审查保持。
- 当前只有 `inspect_host` 与 `inspect_popos_preflight` 两个 Tauri 只读 IPC。安全页新增[本机脱敏诊断与安装阻断报告](../../docs/development/desktop-tauri-local-redacted-diagnostics-l1-2026-11-26.md)，仅白名单状态、可手动选中复制，浏览器预览明确标记未连接本机，不传输、不保存、不添加权限；重新检测自动清除旧 COSMIC 结果。安装、升级、卸载、服务启动、开机自启、生产联网、采集和远控均不可达。
- 四条隐私/权限说明的勾选仅是瞬时**阅读记录**，不是管理员授权、签名包或真正 Polkit grant。
- **2026-11-26 原生 CI 恢复：** [本机脱敏诊断报告](../../docs/development/desktop-tauri-local-redacted-diagnostics-l1-2026-11-26.md) 的旧[React PASS/Native FAIL #45744173020](https://github.com/kunlong-luo/lanveriq/actions/runs/45744173020) 原始证据保留。现已[重新审核 176 包签名索引/二 runner #45746180961 PASS](../../docs/development/desktop-ubuntu24-apt-native-cache-rotation-l2-2026-11-26.md)，[Tauri 原生 Rust/Clippy #45745776008 两 job PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745776008)，[未签名 GUI-only `.deb` #45745775902 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775902)。**仍不能安装真实 Agent、Polkit broker 不存在、COSMIC L3 未验。**
- [L2B-21 GUI 审核 CI #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) 双 job SUCCESS（独立审核测试 10/10、前端 29/29、原生 Rust 6/6）；[GUI-only unsigned .deb 隔离归档 #45368638490](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638490) SUCCESS，但**该包未安装、发布或包含 Agent**，签名仅临时 LAB、不是正式根。详情 [验收报告](../../docs/development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md)。

## 系统级验收（仅 Ubuntu24 disposable OCI，不是发行安装器）

| 能力 | 真实证明 | 产品权限边界 |
| --- | --- | --- |
| **APT Signed-By 真实 Agent 包** | [L2B-27 10/10 实际 APT #45395513243](https://github.com/kunlong-luo/lanveriq/actions/runs/45395513243)；真实安装/同版重装/卸载、8 个签名/哈希/身份拒绝，另见[旧签名 Release Date 仍被接受的回放风险](../../docs/development/desktop-tauri-l2b27-native-apt-signed-release-date-replay-oci-2026-11-24.md) | CI 临时密钥；**无独立发行根/可信高水位、没有实际 Agent 降级或真实用户安装** |
| **Polkit 与请求者身份** | [L2B-24 原生 `polkitd` 6/6](../../docs/development/desktop-tauri-l2b24-native-polkitd-rust-subject-authorization-l2-2026-11-24.md)、[L2B-25 root-side D-Bus UID/PID/nonce #45382618806](https://github.com/kunlong-luo/lanveriq/actions/runs/45382618806) | [`polkit/`](polkit/README.md) 全 no/uninstalled；[`tools/polkit-oci-dbus-broker-lab.py`](tools/polkit-oci-dbus-broker-lab.py) 只在 OCI 拒绝请求，**不执行安装** |
| **真实本机规则覆盖攻击** | [L2B-26 恶意 .rules 令 OS 真 YES，root ReviewLab 仍 NOT_APPROVED #45385719766](https://github.com/kunlong-luo/lanveriq/actions/runs/45385719766) | 规则只在无网络临时容器生成，**不是可授权用户安装的 broker 或可部署策略** |

以上是受控 L2 技术验收，而非正式管理员授权、经批准的生产签名源或 Pop!_OS24 COSMIC 实机 L3。每次 CI 的失败与确切功能 SHA 保存在[专项验收](../../docs/README.md)，**不要向用户分发 CI 内的实验公钥、恶意 .rules 或未经审查的 root 服务**。

## 目录责任

| 目录 | 用途 |
| --- | --- |
| `src/` | React 只读向导、AgentConfig v1 配置草稿、安装条件审查、本机脱敏诊断与单元测试 |
| `src-tauri/` | Tauri2 Rust 只读主机核验、最小 IPC/capabilities、**禁用**正式 bundle 的配置 |
| `tools/` | Debian/ABI/APT 隔离实装、**polkitd/D-Bus 容器内真实 root-side ReviewLab 与 Unix peer 检验**、非提权 Rust gate 和攻击负测；**不是可部署安装 broker** |
| `polkit/` | **未安装的**三动作默认拒绝 XML 策略审查与说明；没有部署到系统、没有 helper 或授权规则 |
| `package.json`、`pnpm-lock.yaml` | 桌面前端构建依赖，与唯一 Next.js Web 分离 |

## 开发者离线预览

要求 Node.js 24 / pnpm 12；在本目录运行：

```bash
cd apps/desktop
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
pnpm dev
```

`pnpm dev` 是**浏览器静态窗口预览**，不能读取系统受限状态，绝不意味着 Pop!_OS 兼容性通过。真正 Tauri 原生构建还需要 Linux WebKitGTK/GTK 原生系统依赖，已在隔离 CI 验证；普通终端用户**不需要自行安装 Rust、Node 或运行上述命令**。

下一实际阶段是独立批准的可信 APT `Signed-By` 发行源、受审狭义 Polkit broker、签名 Agent 包、拒绝/取消/卸载测试，再进行真实 Pop!_OS24 COSMIC L3。[安装产品方案](../../docs/product/linux-desktop-installer-v01.md) · [当前状态](../../docs/CURRENT_STATUS.md) · [发布门禁](../../docs/planning/release-gap-register.md)。

**v0.1 NOT RELEASE READY；禁止 GUI root、任意 shell、自动安装/启动或伪造发布批准。**


**L2B-28 发布证据边界：** 新的 [v2 离线合约](../../docs/development/desktop-tauri-l2b28-apt-replay-evidence-boundary-2026-11-24.md)将已观测到的有效旧签名 Release 接受风险与**独立高水位/可信时钟 NOT_APPROVED** 固定到回归测试；这不是安装授权或实际 rollback 拒绝器。**v0.1 NOT RELEASE READY。**
