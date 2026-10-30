# Lanveriq（澜维巡）

[English](README.md) | 中文

**局域网内、自托管的企业终端活动与安全审计平台。**

> **D0 技术基线已于 2026-10-30 批准，仅允许启动 V0.1 工程开发。** 仓库目前仍无可运行的软件，也没有通过真实操作系统、数据库或安全集成测试。**尚未批准采集真实员工数据、生产部署、V0.2 审计或 V0.3 录像。**

![Lanveriq 系统总览彩图（设计概念）](docs/visuals/assets/01-overview.svg)

[查看全部 8 张统一风格彩图](docs/visuals/README.md) · [查看 20 张 Mermaid 技术图纸](docs/diagrams/README.md)

目标环境：Windows 11 和经过验证的 Linux 桌面；初期 1–10 台公司授权终端；局域网独立部署，不需要互联网或 AI。

## 主要功能（计划）
- 安全设备配对、在线状态、系统信息和心跳。
- 已告知范围内的应用活动时间线、重点系统事件和指定业务目录变更审计。
- 网络断开时在 Agent 的 SQLite 队列暂存，恢复后按幂等协议补传。
- 管理后台按角色查询、导出、审计管理员操作。
- **第二阶段以后**：显式告知、经过系统授权的屏幕录像、时间切片、存储保留与回放。

不提供秘密安装、隐蔽录像、键盘记录、密码抓取、绕过 Windows/Linux 录屏授权或隐藏式远程控制。

## 技术栈
- 前端 REST 访问：**Orval 根据 Springdoc OpenAPI 自动生成 TypeScript API 类型及 TanStack Query Hooks**，避免人工维护重复接口代码。
- Agent：Rust + Tokio + **SQLx 0.9 / bundled SQLite（WAL）**。
- 可选桌面端：Tauri 2 + React。
- 服务端：Java 25 / Spring Boot 4 模块化单体 + PostgreSQL 18 + Flyway。
- V0.1 通信：Rust Agent 通过 HTTPS REST 上报；React 后台使用 HTTPS REST + WSS WebSocket 实时推送，断线后 REST 重新同步。
- 后台：React + Vite + TypeScript + Tailwind + Zustand（有限使用）。
- 局域网 HTTPS、WebSocket；视频留在本地或 NAS。
- 单仓库、直接 main 开发；早期 CI 只做单元测试，不做 E2E。

## 文档入口
从 [文档总览](docs/README.md) 开始。关键文件：[产品需求](docs/product/requirements.md)、
[系统架构](docs/architecture/system-overview.md)、
[全部 Mermaid 架构图](docs/diagrams/README.md)、
[WebSocket 消息协议](docs/architecture/websocket-contract.md)、
[Agent 数据库 ADR](docs/architecture/adrs/0002-postgres-sqlite.md)、
[Pagevela 借鉴](docs/development/pagevela-reference.md)、
[分阶段计划](docs/planning/roadmap.md)、
[事件补传协议](docs/architecture/event-delivery.md)、
[局域网证书与设备配对](docs/architecture/enrollment-bootstrap.md)、
[已批准的 D0 技术基线与验收边界](docs/governance/d0-decision-package-2026-10-30.md)、
[D0 开发准入门槛](docs/governance/documentation-gate.md)。

## 许可证
公开仓库不自动授予代码使用和再分发许可。正式开源前需要单独决策并提交 LICENSE。
