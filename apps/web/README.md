# Lanveriq Web — 唯一 Next.js 管理控制台

**ACTIVE SOURCE · Next.js 16.4.5 / React 19 / TypeScript 7 / Tailwind 4 / Base UI 1.9 · v0.1 NOT RELEASE READY.**

这个目录是**唯一的 Web 管理后台**。`apps/web-next` 与历史 Vite Web 代码已经移除；不要重新建第二个 UI。Tauri 桌面端使用 Vite 仅因为它是另一个**桌面窗口**，不是额外 Web 控制台。详细 [NXT-24 历史迁移](../../docs/development/nextjs-path-rename-nxt24-2026-11-21.md)。

## 当前确切能力

- 开发机和公开 [GitHub Pages](https://kunlong-luo.github.io/lanveriq/) 只能浏览**断开真实服务的静态展示**；不要输入真实账号密码。
- CI 专用临时 Next.js 路由在受控 Linux runner 上已通过 Rust Agent→Caddy/mTLS→Spring/JPA→PostgreSQL18→授权 React DOM，**这不是正式产品登录和可开启的生产业务入口**。[最近真实隔离链路 L2](../../docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。
- 保留 TypeScript 类型、API 契约和受控浏览器安全负测。不开启真实 Agent 采集、生产联网、远控、FIDO 信任或证书签发。

## 开发者本地预览（不是终端安装）

要求 Node.js 24、Corepack/pnpm 12；在本目录运行：

```bash
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm run verify:routes
pnpm test
pnpm typecheck
pnpm build
pnpm dev
# http://127.0.0.1:3100
```

此开发服务只监听 127.0.0.1；成功打开页面并不意味着 Spring 后端、数据库、实际设备或登录已经连通。不要从旧 Vite/5173 工作目录运行。

## 目录职责和规范

| 目录 | 功能 |
| --- | --- |
| `src/app/` | Next.js App Router 页面/路由和局部布局；业务路由安全放行另需授权 |
| `src/components/` | React 管理台与受控组件展示 |
| `src/lib/` | 客户端投影/安全逻辑、测试和共用约束 |
| `scripts/` | 源码/路由/文档/浏览器安全验证与 CI fixtures；不要当生产初始化脚本 |
| `public/`（若存在） | 只读公开静态资源，不包含私钥或生产终端数据 |

请以[当前 UI 规范](../../docs/development/frontend-design-system.md)、[工程规范](../../docs/development/standards.md)、[协议定义](../../docs/architecture/api-contract.md)、[唯一事实入口](../../docs/CURRENT_STATUS.md)、[发布门禁](../../docs/planning/release-gap-register.md)为准。历史 NXT-02～24 的运行记录按日期保留在 `docs/testing/`、`docs/development/`，不是现行部署说明。

**生产 Web 登录/设备管理 OPEN；v0.1 NOT RELEASE READY。**
