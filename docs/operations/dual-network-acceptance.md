# Lanveriq LAN + WAN 安全访问验收手册（待实施）

**日期：2026-11-08。状态：网络设计与只读验收工具；不是已上线证明。**

关联 [ADR-0010](../architecture/adrs/0010-rustdesk-dual-network-access.md)、[部署基线](../architecture/deployment.md)、[设备通信协议](../architecture/device-communication-stack.md) 和 [v0.1 交付计划](../planning/v0.1-batched-delivery-plan-2026-11-08.md)。

## 1. 入口和域名约定

建议由部署所有者准备**自己实际持有**的域名（下列域名是示例，不可直接发布）：

| 入口 | 用途 | LAN | WAN（第一阶段） | WAN（将来获批的公网方案） |
| --- | --- | --- | --- | --- |
| `console.example.org:443` | Next.js HTML/RSC + Spring Session REST/WSS | 私网 Caddy IP | 安全 VPN 私网 Caddy IP | 公网安全入口 443 → 私网 Caddy |
| `enroll.example.org:443` | 审批配对和 CSR | **默认 OFF** | **默认 OFF** | **默认 OFF，独立批准** |
| `agent.example.org:443` | 设备心跳/库存/事件；设备证书 mTLS | **默认 OFF** | **默认 OFF** | **默认 OFF，先完成 Batch A/TLS1.3** |
| `rustdesk.example.org` | RustDesk hbbs/hbbr 信令/中继 | 内网直连或路径协商 | VPN 可达或受控公网 hbbs/hbbr | 仅开放所需原生端口和受控 Relay |

**单一 Console origin，不做双站点登录。** LAN 内 DNS 对 Console 指向 Caddy 内网 IP；获授权的 WAN 用户先连 VPN，VPN DNS 将同一 FQDN 指回内网 IP。未来真的要求直接公网浏览器访问，再使用同一域名的公网 A/AAAA，前置公网入口严格只允许 443，Caddy 必须保留原始 Host/SNI/Origin 和服务端 RBAC/CSRF 验证，且要分别证明内外网实际 DNS 及证书链。不要用 IP 字面量访问登录，不要依赖路由器 NAT loopback/hairpin。

推荐拥有域名使用可信 CA（DNS-01 可用于私网入口），配合正式的证书续期及私钥保护；如果仅有私有 CA，WAN/VPN 管理员设备也必须显式且受控安装该 CA。**禁止 `curl -k`、跳过主机名或证书校验。** `*.lanveriq.internal` 仅历史合成测试域名，不可充当公网有效证书名。

## 2. 防火墙／网络隔离边界

- Console: 仅 Caddy TCP 443 面向获授权 LAN/VPN；如果将来获批直接公网访问，也只允许经硬化的公网 443 入口。强制 HTTPS、Session、CSRF、实时 RBAC/分组授权、审计、最小化限流、MFA/SSO 策略；未完成真实授权登录前**不能开公网入口**。
- Next.js 私有 Node 端口 3100、Spring 本地 TLS 8443、PostgreSQL 5432、Redis 6379、RustDesk 管理 API 必须对不可信网络不可达。Docker 的 `127.0.0.1` 不跨容器共享，实际容器私网及安全组必须验证直接访问失败。
- Agent ingress 是**独立的** Caddy TLS vhost，要求真实设备客户端证书，再经单独 Caddy→Spring mTLS 校验；未完成证书生命周期和全链路 TLS1.3 负向测试前不开放。WAN Agent 可通过 VPN 或单独经批准的公网 443/mTLS 接入，绝不借 Console Cookie、RustDesk ID 或 IP 充当身份。
- RustDesk 原生服务独立：依据确切版本/功能检查官方端口矩阵。常见原生路径 hbbs 21115/TCP、21116/TCP+UDP，hbbr 21117/TCP。生产部署必须以版本说明和抓包/网络验收为准；不是说所有网络都要永久暴露这些端口。Web Client 可能还涉及 21118/21119 WebSocket 代理；**不要直接暴露这些端口到 WAN**，官方提示转发 IP Header 可被伪造。若无 Web Client 授权/需求则保持关闭。
- 设备与管理界面需验证断网后的 LAN 本地继续访问；WAN 依赖 VPN/ISP/Relay 可达，不承诺断网仍可跨互联网远程控制。断线重连、超时以及远程会话撤权后必须可观察且安全失败。

参考：RustDesk OSS 官方部署 https://rustdesk.com/docs/en/self-host/rustdesk-server-oss/install/ ，RustDesk 1.5.0 WebRTC https://rustdesk.com/blog/rustdesk-1-5-0-webrtc-qos/?lang=en ，Caddy HTTPS https://caddyserver.com/docs/automatic-https 。

## 3. 只读网络探针（代码已提交，不会修改服务）

`deploy/network/verify_dual_access.py` **不接受用户名/密码，不开启采集/远程桌面，也不连接数据库**。它用系统信任链校验真实 TLS/SNI，以只读 GET 检查 `/login` 是否正常可达/明确保护、匿名 `/api/v1/devices` 是否返回 401/403，选填检查 RustDesk TCP 21116/21117 端口可达。它不验证 UDP、ICE、RustDesk 登录、Agent mTLS、受保护真实 UI 或外网物理位置。

仅在**已有批准的测试服务与真实 DNS**以后，从两个独立位置分别运行（替换成自己持有的域名/网络段；这些是操作模板，不要用于尚未上线的生产环境）：

```bash
# LAN: 从指定内部网络的机器执行
python3 deploy/network/verify_dual_access.py \
  --vantage lan \
  --console-origin https://console.example.org \
  --expect-peer-cidr 10.42.0.0/16 \
  --rustdesk-host rustdesk.example.org

# WAN: 从真正位于外部网络的设备上、接入经批准 VPN 后执行
python3 deploy/network/verify_dual_access.py \
  --vantage wan \
  --console-origin https://console.example.org \
  --expect-peer-cidr 10.42.0.0/16 \
  --rustdesk-host rustdesk.example.org
```

若将来采用**公网 Console**，WAN 探针应改用经审查的公网入口 IP/CIDR，不能复用内部 CIDR 要求。输出 `PASS_NETWORK_ONLY` 是**单地点 TLS/HTTP 和选填 TCP 观察**，不代表 WAN 身份真实已验证；必须由独立网络的部署者保留来源/网络/时间/配置变更记录，才算两地点验收。若现有 Caddy 仍返回 503，该检查会**按设计失败**，不得为刷绿修改判定。

本地无网络单元测试：

```bash
python3 -m unittest discover -s deploy/network -p 'test_verify_dual_access.py' -v
python3 -m py_compile deploy/network/verify_dual_access.py
```

## 4. 真正的双网络功能验收清单（需环境与证据）

| 验收范围 | 在 LAN 测 | 在独立 WAN / VPN 测 | 必须保留的负例 |
| --- | --- | --- | --- |
| 同一 FQDN + 可信 TLS | 是 | 是 | 错 CA、错 SNI、降级 TLS、错 Host |
| Next.js HTML/RSC + 登录/退出 | 是 | 是 | 未登录 HTML/RSC、Cookie/CSRF、撤权、缓存泄露 |
| Spring REST/WSS | 是 | 是 | 匿名 GET 403、跨组 403、非法 Origin、无 Session |
| RustDesk 原生会话 | LAN P2P/直连 | WAN NAT/ICE/中继 | 禁止访问、伪造身份、断开/重连/撤权 |
| RustDesk Relay | 人为阻断直连后验证 | 人为阻断直连后验证 | Relay 不可达时有明确故障，不返回假成功 |
| Agent mTLS ingress | Batch A 准备后 | 独立批准后 | 无/错/撤销证书、伪造指纹、TLS1.2 |
| 私有接口不能直连 | 是 | 是 | 3100、8443、5432、6379 和管理 API 不可达 |
| 运行和恢复 | 内网断互联网仍能工作 | VPN/公网断连后明确失败 | 服务重启、DNS 失效、证书过期、日志泄密 |

**硬性结论规则：**不能用同一台 CI Runner 调用两次 `--vantage` 来代替 LAN/WAN；不能用普通 TCP connect 证明 RustDesk ICE/UDP/Relay；不能把只有静态 HTML 200 当作真实登录、授权或生产可用。对真正的公网开放，在网络和密码学验收外，还需合法授权设备/人员、隐私告知、发布回滚、备份恢复、授权许可和管理人员审查。

## 5. 分批交付与当前事实

- **现在已可提供**：架构决议、网络边界清单、只读探针与合成单元测试；无实际 DNS、证书、VPN、端口映射或 RustDesk Server 部署。
- **下一步合成 L2**：使用自持域名/专用测试 CA/隔离测试主机，部署 RustDesk pin 版本的 OSS hbbs/hbbr，完成 LAN + WAN (独立地点) 双路线及可强制中继验证；在许可和 API 评估前，只允许独立客户端会话，不把远程控制嵌入正式 Lanveriq Web。
- **后续 Batch A–E**：在既有业务安全环完整通过以后才启用真实授权 Console 和 Agent 入口，最终经过 L3 批准才开放公网管理入口。对已有 `spark-postgres`、Redis、应用开关、GitHub Pages 预览不做任何自动变更。
