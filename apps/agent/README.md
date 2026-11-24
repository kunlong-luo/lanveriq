# Lanveriq Agent — 独立 Rust 终端服务（生产仍 idle）

**ACTIVE SOURCE · Rust 1.100 · Tokio / SQLx SQLite / reqwest-rustls · v0.1 NOT RELEASE READY。**

此目录是真实 Agent 源码，不是可供普通用户直接安装的产品。编译好的程序、系统 `systemd` unit、发行包与 Tauri GUI **职责分离**：未来 Agent 可独立后台运行，不要求常驻托盘/Dock 图标，但必须有清晰的查看、停止和卸载入口及设备所有者授权。

## 目录用途

| 路径 | 内容 |
| --- | --- |
| `src/` | Rust 业务/身份、SQLite 持久待发送队列、ACK 校验、TLS 安全边界、平台适配 |
| `examples/` | **测试专用**的合成请求、只读 Linux 最小采样、注册/心跳等一次性实验，不是产品常驻采集入口 |
| `deploy/systemd/` | 只读审查过的 idle 系统服务模板；安装/启用依然需要正式授权 |
| `tools/linux/`、`tools/windows/` | systemd、包归档、PKI、ACL、OS 负测和隔离 CI；不是用户安装器 |
| `Cargo.toml`、`Cargo.lock`、`rust-toolchain.toml` | 唯一依赖/版本事实，使用已审成熟 Rust crates |

## 现有真实证据与界限

在一次性 GitHub Ubuntu 24.04 runner 上已证明 Rust 只读采样 OS/CPU/RAM（不含网卡、磁盘、进程、个人数据）→ SQLite outbox/重启 → Reqwest 双跳 mTLS → Spring/JPA/PG18 → 授权 Next.js Chromium 显示；断网保持 pending、过期/吊销/版本冲突拒绝均经隔离验收。[完整真实 Linux L2](../../docs/testing/linux-readonly-os-cpu-ram-to-nextjs-l2-2026-11-24.md)。

**正式 Agent daemon 并未默认启动定时采集或联网。** CI 环境变量仅可在一次性合成试验中使用，不能挪入生产开关。无受信发布包、正式 CA、设备授权或 Pop!_OS24 COSMIC L3，**没有可安装给员工的 Agent**。

## 维护者开发测试（不是普通用户操作）

```bash
cd apps/agent
cargo fmt --all -- --check
cargo test --workspace --locked
cargo clippy --workspace --all-targets --locked -- -D warnings
cargo build --release --locked -p lanveriq-agent
```

真实包发行、版本/依赖、受限 Polkit 安装和服务控制将按 [Tauri/Agent 安装 ADR](../../docs/architecture/adrs/0013-tauri-desktop-installer-idle-agent.md)、[发行阻塞 Gate](../../docs/planning/release-gap-register.md)、[Linux 实机 L3 计划](../../docs/testing/linux-popos24-agent-acceptance.md) 另行验收；不要自行执行 `dpkg -i` 或 `systemctl enable` 认为完成产品安装。[当前状态](../../docs/CURRENT_STATUS.md)。
