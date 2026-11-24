# Lanveriq（澜维巡）

[English](README.md) · **简体中文** · [文档导航](docs/README.md)

**Linux-first、自托管的终端资产、健康状态和审计平台**，仅面向已告知且依法获得授权的设备。主要目标操作系统为 **Pop!_OS 24.04 / COSMIC**。

> **当前版本：v0.1 NOT RELEASE READY（不可正式分发或上线）。** 现有 CI 使用隔离 Linux runner、合成设备及临时身份。尚无可信签名、受限 Polkit 安装器、真实 Pop!_OS 实机 L3、生产 CA/MFA/独立 WORM/法律与运维批准。**真实设备联网、自动采集、远控、自动恢复及证书签发默认关闭**。下载源码并不等于取得设备管理权限。

[当前真实进度及 CI](docs/CURRENT_STATUS.md) · [发布阻塞清单 G-01–G-10](docs/planning/release-gap-register.md) · [项目评分与整改优先级](docs/planning/project-scorecard.md) · [代码/文档目录审计](docs/governance/repository-layout-audit-2026-11-24.md)

## 哪些目录有用？不要运行哪一套旧项目？

| 目录 | 功能 | 现在是什么状态 | 处理决定 |
| --- | --- | --- | --- |
| [`apps/web/`](apps/web/README.md) | **唯一 Web 管理控制台**；Next.js 16.4.5、React 19、Tailwind 4、Base UI | 公开 [Pages](https://kunlong-luo.github.io/lanveriq/) 和本地开发是**断开真实业务的展示**；仅隔离 CI 已将真实测试链路接入临时页面 | **保留** |
| [`apps/server/`](apps/server/README.md) | Java 25 / Spring Boot 4.2.0 / Gradle / Hibernate JPA / Flyway / PostgreSQL 18 的业务与安全后端 | 默认拒绝的受控业务入口；PG18.7/mTLS 合成链路经 CI 测试 | **保留** |
| [`apps/agent/`](apps/agent/README.md) | Rust 1.100 / Tokio / SQLx SQLite / reqwest-rustls 的终端 Agent 与 durable outbox | Linux 隔离 CI 已验证只读 OS/CPU/RAM→后台；常驻产品 Agent 仍无生产采集/联网授权 | **保留** |
| [`apps/desktop/`](apps/desktop/README.md) | Tauri 2 / React 的本机安装准备、兼容性预检和状态界面 | **只读 GUI**，显示签名/Polkit/L3 缺口；没有可用安装事务或 Agent 开机启动 | **保留** |
| [`deploy/caddy/`](deploy/caddy/Caddyfile.reference) | Caddy TLS1.3、双跳 mTLS 参考配置和隔离验收 | 测试基础设施；不是可直接公开部署的生产配置 | **保留** |
| [`deploy/network/`](deploy/network/verify_dual_access.py) | LAN/WAN 接入边界的只读及拒绝测试 | 仅安全合同/实验 | **保留** |
| [`deploy/rustdesk/`](deploy/rustdesk/client-release-lock.json) 与 [`deploy/rustdesk-synthetic/`](deploy/rustdesk-synthetic/verify_rd2_release_boundary.py) | RustDesk 远控候选与 RD-2 默认拒绝、隔离协议测试 | **远控关闭**，许可证、真实权限和安全批准未完成 | **保留，仅实验** |
| [`docs/`](docs/README.md) | 唯一文档目录：规范、架构、测试证据、历史、发布缺口 | 按[文档生命周期](docs/governance/document-lifecycle.md)区分当前规范与历史记录 | **保留并精简索引** |
| [`.github/workflows/`](.github/workflows/docs-governance.yml) | 后端、Agent、Web、Tauri 和安全发布门禁自动 CI | 自动化测试不等于生产授权；历史失败保留在 Actions | **保留** |

**不存在需要重新启动的 `apps/web-next/`、旧版 React/Vite Web 项目、独立 Maven 后端或第二套可执行控制台。** 没有这些目录是整理后的正确状态；不要重新创建它们。根目录不是 Gradle 项目。

**文档只有一套现行权威入口，按领域归档**；历史失败报告放原领域或 Git 历史，而不是复制到每份 README：

```text
docs/
├── README.md                  # 唯一文档导航
├── CURRENT_STATUS.md          # 唯一功能/CI 现状
├── architecture/             # 规范、协议、系统架构
│   └── adrs/                 # 当前/历史架构决策
├── security/                  # 身份、隐私、FIDO、WORM
├── development/               # 技术标准 + 日期 L2B/NXT 历史验收
├── testing/                   # L1/L2/L3 验收、真实 CI 与失败证据
├── planning/                  # 唯一发布 Gate、现行路线/评分
├── governance/               # 文档所有权、审计规则
│   └── archive/              # 历史固定 Git SHA 索引，不重复保留全文
├── operations/  product/      # 操作手册与产品交互行为
└── diagrams/    visuals/      # Mermaid、SVG 概念图（非截图）
```

历史全文可从 [归档索引](docs/governance/archive/README.md) 中的固定 Git SHA 恢复。[目录保留/删除审计](docs/governance/repository-layout-audit-2026-11-24.md)列出被移除的重复入口和保留原因；历史文档快照用**固定 Git SHA 链接**追溯，不保留第二份活动索引。

## 我现在能做什么？

**普通 Pop!_OS 用户：** 目前不能通过签名安装器完成一键 Agent 安装。 虽然[真实 Agent .deb 经临时 Signed-By APT 仓库完成 Ubuntu24 OCI 安装/重装/卸载](docs/development/desktop-tauri-l2b23-real-agent-signed-apt-oci-2026-11-24.md)，并且[真实恶意 Polkit 授权 YES 仍被 root D-Bus 审查阻止](docs/development/desktop-tauri-l2b26-malicious-polkit-rule-override-real-os-2026-11-24.md)，**它们都不是用户可以部署的 root 安装器**，后者没有执行任何包安装。无需按照历史报告安装 Rust、Node 或手动执行 `dpkg -i`；这些是**开发者**命令，不是最终产品安装过程。[安装器真实进度](docs/development/desktop-tauri-l2b21-install-gate-review-ui-2026-11-24.md) · [未来 GUI 用户流程](docs/product/linux-desktop-installer-v01.md)。

**开发者：** 请先阅读 [AGENTS.md](AGENTS.md)、[贡献规范](CONTRIBUTING.md)、[工程手册](docs/governance/engineering-playbook.md)。只在独立开发环境运行：

```bash
# 唯一 Web 前端：本地只读/断开业务预览
cd apps/web
corepack enable
corepack prepare pnpm@12.0.0 --activate
pnpm install --frozen-lockfile
pnpm test
pnpm dev
# http://127.0.0.1:3100
```

后端使用 `cd apps/server && ./gradlew test`；Agent 使用 `cd apps/agent && cargo test --workspace --locked`；Tauri UI 使用 `cd apps/desktop && pnpm test`，这些**都是开发测试，不是安装或激活终端**。后端启动需要经验证的独立 PostgreSQL 凭据和 loopback 限制，按 [开发/安装操作指南](docs/operations/installation.md) 执行；不要把本地数据库操作脚本当成终端安装器。

## 上线之前还缺什么？

依照 [G-01–G-10 发布门禁](docs/planning/release-gap-register.md)，优先完成可信签名发行与受限 Polkit 安装、**经授权的 Pop!_OS 24.04 COSMIC 实机**、正式 CA/管理员 MFA、独立 WORM/密钥/可信时间、法务授权、备份恢复/回滚和正式业务登录。用户勾选一份 GUI 说明并不能代替这些批准。

项目的工程进展评分只用于决定研发优先级，**不是发布认证**。[评分与改进记录](docs/planning/project-scorecard.md) · [路线](docs/planning/linux-v01-reuse-first-delivery-path-2026-11-24.md) · [漏洞报告](SECURITY.md) · [变更历史](CHANGELOG.md)。
