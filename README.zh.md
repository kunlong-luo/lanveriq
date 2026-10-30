# Lanveriq（澜维巡）

[English](README.md) | 中文

**局域网内、自托管的企业终端活动与安全审计平台。**

> **设计阶段：尚无可运行的软件。** 先完成产品、架构、安全、接口、数据和验收文档，再编写业务代码。

目标环境：Windows 11 和经过验证的 Linux 桌面；初期 1–10 台公司授权终端；局域网独立部署，不需要互联网或 AI。

## 主要功能（计划）
- 安全设备配对、在线状态、系统信息和心跳。
- 已告知范围内的应用活动时间线、重点系统事件和指定业务目录变更审计。
- 网络断开时在 Agent 的 SQLite 队列暂存，恢复后按幂等协议补传。
- 管理后台按角色查询、导出、审计管理员操作。
- **第二阶段以后**：显式告知、经过系统授权的屏幕录像、时间切片、存储保留与回放。

不提供秘密安装、隐蔽录像、键盘记录、密码抓取、绕过 Windows/Linux 录屏授权或隐藏式远程控制。

## 技术栈
- Agent：Rust + Tokio + rusqlite / bundled SQLite（WAL）。
- 可选桌面端：Tauri 2 + React。
- 服务端：Java 25 / Spring Boot 4 模块化单体 + PostgreSQL 18 + Flyway。
- 后台：React + Vite + TypeScript + Tailwind + Zustand（有限使用）。
- 局域网 HTTPS、WebSocket；视频留在本地或 NAS。
- 单仓库、直接 main 开发；早期 CI 只做单元测试，不做 E2E。

## 文档入口
从 [文档总览](docs/README.md) 开始。关键文件：[产品需求](docs/product/requirements.md)、
[系统架构](docs/architecture/system-overview.md)、
[Agent 数据库 ADR](docs/architecture/adrs/0002-postgres-sqlite.md)、
[Pagevela 借鉴](docs/development/pagevela-reference.md)、
[分阶段计划](docs/planning/roadmap.md)、
[文档完成门槛](docs/governance/documentation-gate.md)。

## 许可证
公开仓库不自动授予代码使用和再分发许可。正式开源前需要单独决策并提交 LICENSE。
