# R2：RustDesk 真实客户端双端会话验收（部分执行，不得以模拟替代）

- **状态：2026-11-08 R2-01 已在隔离 Docker 中通过“两份真实 Linux 客户端向官方 hbbs 注册”的验收；R2-02 合成 GUI 远程画面/输入已尝试但验收失败；LAN 两台物理主机、WAN/Relay 仍未通过，不得称已通过。**
- 依赖：[ADR-0011](../architecture/adrs/0011-rustdesk-dual-network-access.md) · [OSS Pilot](../operations/rustdesk-pilot.md) · [内外网验收](../operations/dual-network-acceptance.md)。
- 客户端候选：RustDesk **1.5.1 稳定版**，以官方 [Release 1.5.1](https://github.com/rustdesk/rustdesk/releases/tag/1.5.1) 的受信下载、签名/哈希和各目标平台版本为准。明确不是 nightly；Linux 和 Windows 都要固定安装器/平台哈希后才实施测试。服务端继续使用已锁定的 OSS s6 **1.1.16** 镜像直至升级评审。
- 不采集真实个人屏幕、键盘、文件和 OS 账号。**所有实际远程操作仅发生在受控且获授权的合成测试桌面。** 不向 RustDesk 公共服务器注册：客户端必须先完成自建服务器和公钥配置，并在隔离网段检查出站规则。

## 按优先级复用现成能力

直接使用 RustDesk 原生客户端 GUI、官方服务端、系统图形栈和既有发行包；不自行实现 Rendezvous/ICE/WebRTC/视频编码。官方 RustDesk Linux 客户端无头运行并不保证自动可控：Wayland 门户需要用户授权，X11/虚拟显示方案也要证明能够捕获正确帧；不要把 `--connect` 出现命令行参数当作完整无头 API。仅使用官方允许的配置导入/受控脚本；不要使用 `--password <plaintext>` 在 CI 参数/日志中传递凭据。

## 自动化验收最小环境

- 两台**独立**的一次性合成桌面 VM/Runner（控制端 A 和被控端 B），至少包含 Linux X11/Xvfb；后续追加 Windows 11 真实 OS。
- 一台独立的 RustDesk OSS 1.1.16 ID/Relay 服务节点，**测试服务器使用独立的 `id_ed25519` 密钥对和完全隔离数据卷**，同时保证仅预定测试网络内可达。
- 仅由受控测试环境的 Secret Manager 提供一次性随机强口令/测试证书（如需要），无真实人员账户或公司网络凭据；首次连接必须验证客户端已固定使用正确服务端公钥和 ID Server。
- 使用双网络拓扑：LAN 直连及 VPN/WAN 分别从真正不同网络发起，不能用同一 Runner 自报 `--vantage wan` 当成外网证据。
- 日志、截图、会话元数据只保存**合成标志和最小测试信息**，密钥、口令、身份证书不入构建日志或公共 Actions Artifact。

## 准入用例及真正的成功标准

| 用例 | 必须观测到 | 不可接受的假阳性 |
| --- | --- | --- |
| R2-01 两端注册 | 两个不同客户端向**自建** hbbs 完成注册，分别得到独立 peer ID，服务器公钥校验通过 | 仅显示本地 ID、TCP 21116 通 |
| R2-02 正确会话 | A 经明确授权连接 B；B 的合成测试桌面指定窗口内容被 A 收到；只有合成输入被执行 | 截图由本地应用生成、连接窗体打开但未验证远端帧 |
| R2-03 客户端安全 | 错公钥、错/过期密码和无授权会话全部失败，不创建活动远控会话 | 仅 TCP 拒绝、前端隐藏按钮 |
| R2-04 LAN | 连接诊断/服务器统计能区分直连路径，包捕获确认设备间直连路径 | 仅 P2P 开关为真 |
| R2-05 Relay | 有意识阻断 A-B 直连或通过**已批准的隔离测试配置**强制 Relay，仍完成远程帧+输入，能确认实际经过 hbbr | hbbs `ALWAYS_USE_RELAY=true` 但无真实会话 |
| R2-06 WAN/VPN | 独立外网视角用自建 ID/Relay 与受控 VPN 后端保持会话，断开后有明确错误/重连状态 | 云 CI 单节点环回或公网端口连通 |
| R2-07 生命周期 | B 重启、短断网、Relay 宕机后的行为可恢复或明确失败；会话操作、延迟和错误有脱敏记录 | 仅重启 Docker 容器后 TCP 通 |
| R2-08 产品权限 | 将来 Spring/Next.js 的登录态、实时 RBAC 和设备/人绑定在启动远控前验证，撤权后禁止新会话并有审计；客户端原生密码仍生效 | 根据 RustDesk peer ID 或 IP 直接授予操作权 |

所有真会话用例均须独立保留测试节点版本、服务器版本/摘要、Docker/主机网络边界、操作时间、测试 ID、可证明的会话级结果，并由批准的测试环境按需上传结构化摘要。**L2 仅覆盖测试路径，不替代 Windows/Linux 试点和 L3 上线验收。**

## 当前可自动复用的 R1 证据

GitHub Actions [#42475221417](https://github.com/kunlong-luo/lanveriq/actions/runs/42475221417) 已在真实 RustDesk OSS 服务端容器验证：关闭外部 WebSocket 映射、真实 hbbs 原生 relay 模式读回 `false → true → false`、宿主机未获得 loopback 管理权限、重启后模式复位且公钥摘要不变、私钥权限 `0600`，以及 30 项离线单元测试。这是**原生服务端行为与密钥安全的证明**，尚未有两台 RustDesk 客户端成功握手或远程画面的证据。

## R2-01 实际自动验收证据（2026-11-08）

GitHub Actions [#42480430470](https://github.com/kunlong-luo/lanveriq/actions/runs/42480430470)：**Success**。工作流独立启动 RustDesk OSS Server 1.1.16 官方摘要锁定镜像，及两个独立 Xvfb 容器中运行的 RustDesk 1.5.1 官方 Linux 客户端二进制（安装包 SHA-256 已独立匹配 GitHub Release）。两端以真实自建服务端公钥及独立容器配置注册，通过 `docker compose cp` 读取 **本次隔离服务端原生 SQLite `peer` 表**，经 SQL 检查确认为 **2 条、2 个不同 peer ID**。隔离网络使用 Docker `internal:true`，没有任何宿主机端口发布，CI 结束自动清理本次临时 Compose 项目、数据库与密钥。5 项原有 R2 静态防伪测试通过。

这里发现并修复了一个纯验收工具错误：`docker compose cp` 并不接受 `-q` 参数，旧脚本复制数据库失败，却被错归因为“客户端未注册”。修复后取得成功；新增独立回归用例确保复制错误不再被当成设备缺席。

**证据边界：R2-01 证明的是两份真实客户端对自托管 hbbs 的注册，且发生在同一台 GitHub Runner 内部 Docker 网络。** 未证实客户端间完整的密码握手、屏幕远端帧、键盘鼠标输入、直接 P2P/ICE、Relay 的实际承载、真实独立 LAN/WAN 网络、Windows 客户端、Spring 授权与商业化 Web Client 许可。这些项目保持 OPEN。

## 实施前置差距

要完成 R2-02 至 R2-08，还必须具有**可运行完整客户端图形会话及可验证远端帧/输入的两台受控测试 VM/Runner、正式确认的授权范围，以及真正独立 LAN/WAN 的网络**。这些资源在当前 GitHub hosted 单 Runner 的配置里没有绑定。**没有这些前提，不得生成“P2P PASS / Relay PASS / WAN PASS”或自动把业务端口默认打开。** 后续可以为独立 Runner 添加由 CI 调度的完整用例，无需用户在每次提交后手工点屏幕；但必须先有可用的受控测试基础设施。

## R2-02 CI failed evidence (2026-11-08)

[GitHub Actions #42485980575](https://github.com/kunlong-luo/lanveriq/actions/runs/42485980575) returned **FAIL_R2_02**, because the native client GUI session did not produce independently verified remote pixels, target-side consent and input. The subsequent main-only source integration corrects a PID-file shell typo in the test harness, **but has NOT rerun/passed an actual full remote session**. Keep this case FAIL/NOT_ACCEPTED, not PASS; do not enable RustDesk remote access until separate authenticated data-plane admission/revocation and operator consent are independently demonstrated.
