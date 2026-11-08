# RustDesk OSS 实际进程试验：隔离 Compose + LAN/WAN 分阶段验证

**2026-11-08；状态：已通过真实隔离容器的本地原生控制平面验收；尚未部署到真实 LAN/WAN，非生产配置。**
前置阅读：[ADR-0010](../architecture/adrs/0010-rustdesk-dual-network-access.md) · [双网络验收清单](dual-network-acceptance.md)。

## 选择与边界

- 使用官方 **RustDesk Server OSS s6 1.1.16**（2026-11-08 可验证的已发布容器，而不是源代码 `Cargo.toml` 中尚未同步发布的 1.1.17），多架构索引精确锁定为 `sha256:5974cd791a5aca6fbf01a167b37e7427db1d136e95b2710c33a34fdc9fea724c`；从 [GitHub 官方 Package](https://github.com/rustdesk/rustdesk-server/pkgs/container/rustdesk-server-s6) 交叉确认。**禁止随意替换 `latest`；更新时重做许可证、发布来源、平台和 CI 验收。**
- s6 镜像统一启动 `hbbs` 与 `hbbr`；`ENCRYPTED_ONLY=1` 要求二者使用共享密钥验证模式，`/data` 存于**独立命名 Docker 卷**。首次启动前调用**原镜像内的 `rustdesk-utils genkeypair`** 生成密钥（密钥不输出到日志），设定 `umask 077` 并在 `hbbs` 启动前收紧为文件权限 `0600`；之后由上游 s6 初始化校验已存在的密钥对。CI 发现旧版 RustDesk 首次由 `hbbs` 自行生成的文件权限未满足我们的 `0600` 要求，因此不能去掉这段启动安全措施。私钥 `id_ed25519` 不进入 Git、CI 日志、前端或 Spring API；远端客户端仅配置相应公钥。
- 单服务、独立项目 `lanveriq-rustdesk-pilot`，不连接 `spark-db` / Lanveriq 业务 Docker 网络。仅原生 `21115/TCP`、`21116/TCP+UDP`、`21117/TCP`；**不发布** 21114、21118、21119。Docker 容器内部启动 WebSocket 监听不等于对宿主机开放，仍需验证 Docker 规则和可达性。
- `restart: "no"`、`profiles: [pilot]`，没有任何自动启动的生产服务。默认绑定 `127.0.0.1`。仅获批准的隔离主机在完成网络/端口审查后才可以指定私网或 VPN 专用绑定 IP（包括某些 mesh VPN 使用的 RFC 6598 共享地址 `100.64.0.0/10`；必须确认该 IP 实际存在于本机接口、路由和 ACL 均已批准，不能因为地址不是公网就默认安全）。不要在主机修改公共 DNS、VPN、NAT、防火墙或真实设备。
- **产品集成暂不启动**：RustDesk Client/Server 使用 AGPL-3.0；Pro 管理 API、Web Client、商业再分发需独立许可审查。无合法的会话启动授权之前，不给 Next.js 添加可以访问实际设备的“远程控制”链接。

## A. 当前 CI 的自动化进程检查（无需用户手动操作）

`.github/workflows/dual-network-contract.yml` 在有关路径的 PR/合并构建中执行：
1. 校验 Compose 经官方 `docker compose config --format json` 渲染后仍为审核过的固定镜像/版本、单服务、单网络、强制 profile、共享加密模式、独立数据卷与仅 4 个宿主机本地端口；负例包括 0.0.0.0、公网 IP、镜像漂移、WebSocket 21118/21119、额外能力、共享现有网络/卷等。
2. 从 GHCR 拉取官方锁定镜像，在 **GitHub Runner 的 127.0.0.1** 临时启动真实 RustDesk 二进制，检查 21115/21116/21117 TCP 实际可连接，21114/21118/21119 TCP 在宿主机不可连接，`/data/id_ed25519` 和 `/data/id_ed25519.pub` 已生成且非空。
3. **原生服务端行为验证（新增）**：从容器内 loopback 读取 RustDesk 自带的 hbbs `ALWAYS_USE_RELAY` 控制状态，验证 `false → true → false`；从宿主机映射端口尝试向管理控制台注入指令时，状态不得变更；随后重启并检查控制状态恢复、公开密钥摘要不变、私钥保持 `0600`、端口仍受限。这里验证的是**实际进程的模式切换控制**，不代表存在真实数据经 Relay 转发。
4. 在 job 结束时只删除**本次试验 Compose 项目容器/网络**，不加 `-v`、不接触其他容器/数据库。**TCP 联通、模式切换均不等于 RustDesk 端到端握手、ICE/UDP 流量或真正的远控会话；CI Runner 不等于外网独立视角。** [本次最终成功的 GitHub Actions #42475221417](https://github.com/kunlong-luo/lanveriq/actions/runs/42475221417) 已运行 9 项基础网络 + 21 项 RustDesk/原生控制脚本单测、Docker Compose 配置验证及实际 hbbs 控制行为与密钥持久化检查。

## B. 人工批准后，才在独立测试主机启动（仅供实验）

要求已验证 Docker Engine/Compose 可运行、无现有 21115/21116/21117 端口占用、能隔离 Docker 卷与网络。以下命令**只做本机回环测试**，不提供内外网远程访问：

```bash
# 在单独的实验主机、本仓库根目录执行；不会修改当前 spark-db
export RUSTDESK_PILOT_BIND_IP=127.0.0.1
export RUSTDESK_PILOT_RELAY_HOST=localhost
docker compose -f deploy/rustdesk/compose.pilot.yml --profile pilot config --format json |
  python3 deploy/rustdesk/verify_pilot_config.py \
    --bind-ip "$RUSTDESK_PILOT_BIND_IP" \
    --relay-host "$RUSTDESK_PILOT_RELAY_HOST"

# 只有审查、确认了端口和本机实验范围，才运行此明确的启动操作：
docker compose -f deploy/rustdesk/compose.pilot.yml --profile pilot up -d --pull always
python3 deploy/rustdesk/smoke_local.py
docker compose -f deploy/rustdesk/compose.pilot.yml --profile pilot exec -T rustdesk-pilot sh -ec 'test -s /data/id_ed25519 && test -s /data/id_ed25519.pub'

# 停止本次试验容器和专属网络；保留私钥及数据库所在独立卷，不使用 down -v：
docker compose -f deploy/rustdesk/compose.pilot.yml --profile pilot down
```

经批准的局域网试点，在持有设备和服务端合法访问权限、并确认容器宿主机防火墙（Docker publish 规则可能绕过常规 UFW 直觉）后，可把 `RUSTDESK_PILOT_BIND_IP` 设为该主机实际私网 IP，把 `RUSTDESK_PILOT_RELAY_HOST` 设为已配置 DNS 且客户端可达的真实 FQDN。重跑 `config | verify_pilot_config.py` 并进行独立网络扫描后才启动。**不得设为 0.0.0.0、公网地址或演示占位 DNS**。

LAN 客户端使用经批准的 RustDesk 客户端，配置自建 ID/Relay 主机名与从 `/data/id_ed25519.pub` 读取的**公钥**，并只连接获授权测试设备。私钥要加密备份，禁止上传日志、聊天和公开库，服务重建应验证公钥保持不变。

WAN 阶段**首先经批准的 VPN** 连回内网，要求 VPN DNS 能解析到正确的私网/隧道地址，客户端能使用同一 RustDesk 服务端而不是第三方公共服务器。跨 NAT 的纯 P2P 是否成立，由网络实测决定；不能把能打开 TCP 端口当作已完成 P2P、ICE、TURN 或 Relay fallback 验收。真正公网服务端暴露与公网浏览器入口是**两个独立的审查项**，不能因本地 CI 绿色而直接 NAT 转发。

## C. 后续功能/安全验收（尚未完成）

- **本机运行性（已通过隔离 CI）**：真实容器启动、官方密钥生成、私钥权限、重启公钥稳定、hbbs Relay 模式控制读写、宿主 TCP 端口关闭策略。不是 LAN/WAN 验收。
- **LAN**：两台经授权实验机器远控会话，输入/文件传输/断线/重启、日志脱敏、最小权限，直接连接和强制 Relay 的路径证据。
- **WAN**：真实独立外部网络+批准 VPN，IP/DNS/密钥确认、NAT 类型、直连/中继回退与断连故障；不开放未经审查的 WebSocket。
- **Lanveriq 业务**：Spring 实时 RBAC/会话、设备 ID 映射、会话授权和撤权、审计关联、Next.js 产品 UI、Rust Agent 独立 mTLS 业务链路。
- **许可**：OSS AGPL + Server Pro API/客户端/Web Client 功能许可的逐项法务审核与必要公告。不要把 Server OSS 当成包含 Pro 的设备管理 API。
- **生产**：私钥托管和轮换、系统/容器安全基线、防火墙、VPN/MFA、备份恢复、并发/带宽/成本、隐私授权、L3 系统联调后才批准。

官方资料：[OSS Docker 端口说明](https://rustdesk.com/docs/en/self-host/rustdesk-server-oss/docker/) · [当前 Server 环境选项](https://github.com/rustdesk/rustdesk-server/blob/master/docs/environment-variables.md) · [客户端 WebRTC 功能条件](https://rustdesk.com/docs/en/self-host/client-configuration/advanced-settings/)。
