# Lanveriq Desktop — Tauri 2 安装准备/状态预览（不可安装）

**ACTIVE SOURCE / NONINSTALLING PREVIEW · Tauri 2 / Rust 1.100 / React 19 / Vite 8 / Tailwind 4 · v0.1 NOT RELEASE READY。**

这是本机窗口工具，不是第二个 Web 控制台（唯一后台 Web 位于 `apps/web`）。未来面向 Pop!_OS 24.04 / COSMIC 的用户目标是“一次经授权的图形安装 + 独立 systemd 后台 Agent + 可发现的停止/卸载入口”，**不是现在已经可以一键安装**。

## 已实现的界面和真实边界

- 四步中文安装准备、系统兼容性**只读**检测、查看本机候选 Agent/systemd 状态、发行签名/受限 Polkit/物理机 L3 缺口审查。
- 当前只有 `inspect_host` 与 `inspect_popos_preflight` 两个 Tauri 只读 IPC。安装、升级、卸载、服务启动、开机自启、生产联网、采集和远控均不可达。
- 四条隐私/权限说明的勾选仅是瞬时**阅读记录**，不是管理员授权、签名包或真正 Polkit grant。
- [L2B-21 GUI 审核 CI #45368842676](https://github.com/kunlong-luo/lanveriq/actions/runs/45368842676) 双 job SUCCESS（独立审核测试 10/10、前端 29/29、原生 Rust 6/6）；[GUI-only unsigned .deb 隔离归档 #45368638490](https://github.com/kunlong-luo/lanveriq/actions/runs/45368638490) SUCCESS，但**该包未安装、发布或包含 Agent**，签名仅临时 LAB、不是正式根。详情 [验收报告](../../docs/development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md)。

**L2B-22 源码安全基线（非实际 Polkit 安装）：** [`polkit/`](polkit/README.md) 现含**未安装**的系统 Polkit XML 策略草案，固定安装/升级/卸载三个 action，每个场景全为 `no`。 [Ubuntu24 安装意图 39 项 + Polkit 30 项负测 #45375272477 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45375272477)；[实际报告](../../docs/development/desktop-tauri-l2b22-polkit-inert-action-review-2026-11-24.md)。**没有 polkitd/pkexec/实际 broker 运行，也没有让 GUI 获得任何权限。**

## 目录责任

| 目录 | 用途 |
| --- | --- |
| `src/` | React 只读向导、安装前条件审查/声明和纯逻辑单元测试 |
| `src-tauri/` | Tauri2 Rust 只读主机核验、最小 IPC/capabilities、**禁用**正式 bundle 的配置 |
| `tools/` | Debian 归档/ABI/APT 签名缓存/拒绝优先源码检查，含大量实验室负测；不是安装服务或生产提权 helper |
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
