# G05-LNX4 — 复用原生 dpkg 的 Agent 实际安装/重装/卸载隔离 L2 验收

**IN IMPLEMENTATION · 2026-11-24 · source SHA 7ef4310e · CI #45338818298 RUNNING AT INITIAL RECORD · Ubuntu24 OCI ONLY · G-05 OPEN · v0.1 NOT RELEASE READY**

> **CI 首次运行纠错（2026-11-24）：** [功能 SHA 7ef4310e / #45338818298](https://github.com/kunlong-luo/lanveriq/actions/runs/45338818298) **overall FAIL**。实际 Rust release、原生 `.deb` 构建与严格解包审计均 PASS，紧接着新 LNX4 脚本因为 Bash 变量被误写成转义 `\${GITHUB_ACTIONS:-}` 等导致**合法 GitHub CI 也被 fail-closed 拒绝**，未进入 Docker，因此这次**没有真实 dpkg 安装验证**。源码修复 SHA [f0cea27f](https://github.com/kunlong-luo/lanveriq/commit/f0cea27f77ee416ad3bb70bff198339e8789faef) 仅恢复 Bash 环境变量和 `dpkg-query` 字段的正确展开、不放宽权限；[重跑 CI #45339314490](https://github.com/kunlong-luo/lanveriq/actions/runs/45339314490) 尚待最终结果。历史 FAIL 不得更名 PASS。

## 本阶段为什么不另造安装器

Lanveriq 已有真正的 Rust Agent release 编译、一个受控的 `lanveriq-agent-offline-preview_0.1.0~dev1_amd64.deb` 生成器、严格归档和 systemd 单元检查，但此前 [verify-offline-agent-deb.py](../../apps/agent/tools/linux/verify-offline-agent-deb.py) 的安装/删除检验属于 **`dpkg-deb --extract` 演练**，**并未运行 Linux 包管理器对 Agent 的真实安装事务**。本轮不再创建另一个自研包管理协议，而直接使用已在目标 OS 生态存在的 **Debian dpkg**，只在无网络的一次性 Ubuntu24 OCI 容器中调用。

## 真实代码与安全边界

- 新增 [verify-disposable-agent-dpkg-lifecycle.sh](../../apps/agent/tools/linux/verify-disposable-agent-dpkg-lifecycle.sh)。仅在 `GITHUB_ACTIONS=true`、`RUNNER_OS=Linux` 且 CI 步骤显式 `LANVERIQ_DISPOSABLE_DPKG_L2=1` 时可被调用；宿主必须非 root；无参数或未授权环境均**拒绝而非试装**。
- 只接受**已经由现存严格源码级验收工具**确认过的 Agent `.deb`：仅 `/usr/bin/lanveriq-agent`、`/usr/lib/systemd/system/lanveriq-agent.service` 和合法目录；无 preinst/postinst/prerm/postrm、无配置/秘密/状态、无其它文件。检查失败时禁止 Docker 容器运行。
- 使用此前已审固定 `ubuntu@sha256:562111b88caa79c71391c0c4d3c2114a7d5c35915c20561fd893cb1e07929e6b` 容器镜像；容器 `--network none`、`--cap-drop ALL`、`no-new-privileges`、资源限制，只挂载**本次候选包只读文件**；绝不将 host 根、Docker socket、系统软件源或工作数据挂入容器。
- 在容器内运行真正的 `dpkg --install` → `dpkg-query` 精确包名、版本、二进制和 unit 归属 → **Agent 仅运行 `--help`** → 添加**合成操作员自有配置/状态** → `dpkg --install` **同版本重装** → `dpkg --remove`。验证二进制/unit 删除，合成独立配置/状态保留，系统没有服务启用 symlink，PID 1 非 systemd、`/run/systemd/system` 不存在。
- 已将容器真实 `dpkg` 验收作为 [现有 Linux Pop!_OS custody CI](../../.github/workflows/agent-linux-popos24-custody.yml) 的真实 Rust `cargo build --release --locked`、严格归档检查之后的下一步；加上脚本 CI 环境守卫拒绝测试，未修改 Agent 运行时代码或生产开关。**没有向 GitHub 上传/分发任何候选安装包**。

## 验收等级须精确区分

| 检查 | 本阶段目标 | 不可误认的边界 |
| --- | --- | --- |
| 真实 Rust release 二进制编译和原生 Debian 打包 | 原有 L2 流程 | 这个预览包没有正式签名、完整法律许可审查或信任根 |
| 容器内真实 `dpkg -i` 和包所有权/版本状态 | 本轮新增 L2 | 容器不是用户设备，更不是实际 Pop!_OS COSMIC |
| 容器内二进制 `--help` | 不进入 Agent runtime | **没有**启动真实后台服务或连接后端 |
| 容器内同版本重装、`dpkg -r`、用户状态保留 | 本轮新增 L2 | **不是**跨版本升级、受限 Polkit 事务或真实系统d的升级/卸载 |
| 无 systemd、无 enable/start、无网络/采集/远控 | 必须保持 | 仅可证明这一容器实验没有激活；真实 Pop!_OS L3 另行授权执行 |

CI 必须在同功能 SHA 上整体 SUCCESS 才能将本轮“真实 `dpkg` L2”标成 PASS；**代码提交和步骤排队不等于验收成功**。现有 [Agent 宿主安装只读审查器](../../apps/agent/tools/linux/verify-installed-agent-idle.py) 仍必须在未安装 runner 上返回 NOT_APPROVED，而不是把容器已装过的 Agent 当作真实主机已安装。

## 下一阶段：产品而不是继续单独造密码学原语

当本阶段真实 CI 通过后，应转入**真正可用的用户功能**：给受审原生 Agent 包定义正式发布渠道/身份与许可证；最小用途 Polkit/PackageKit 方案的独立安全评审；一次实际合法授权的 Pop!_OS24 COSMIC 非生产设备上检查安装、默认 disabled/inactive、显式启动、升级/卸载、撤销和退出。随后打通已有 **Agent mTLS/ACK → Spring/PG18 → Next.js** 的可观察测试设备闭环。通用资产采集优先评估 osquery 的正式生命周期、许可和可限制字段的集成，不应一开始重写整套数据引擎。

**本轮不会创建正式分发软件包、真实生产信任根、远控、隐蔽部署或自动设备采集。G-02/G-05/G-07/G-08/G-09 OPEN；v0.1 NOT RELEASE READY。**

[当前状态](../CURRENT_STATUS.md) · [Agent/Pop!_OS 验收](../testing/linux-popos24-agent-acceptance.md) · [发布缺口](../planning/release-gap-register.md) · [桌面安装器 ADR-0013](../architecture/adrs/0013-tauri-desktop-installer-idle-agent.md)。
