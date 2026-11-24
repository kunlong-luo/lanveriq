# DESKTOP-L2B-21：Tauri 图形安装器的动态门禁审查与独立许可说明

**2026-11-24 · main-only · Offline source/GUI stage L1 · v0.1 NOT RELEASE READY**

## 本轮实际源码

- `apps/desktop/src/install-review.ts`：新建基于实际只读 `inspect_host` 状态和现有 Pop!_OS/COSMIC `inspect_popos_preflight` 结果的**安装前提审查视图模型**；明确区分未经核验、状态被拒绝、仅已取得元数据和独立审批尚缺。静态缺口包含正式发行者/Signed-By 密钥、逐次 Polkit broker、合法许可/具名 COSMIC L3；**模型只能返回全部安装/升级/卸载/服务启动/开机自启/联网/采集不可授权**，没有成功授权枚举或命令参数。
- `apps/desktop/src/InstallReviewPanel.tsx`：现有 Tauri/React 安装向导第 4 步展示七项具体阻塞状态与四条**分别独立、默认未勾选、仅本组件内存中存在**的权限说明：签名软件包来源、管理员逐次授权、安装和服务启动/开机自启分别审批、联网/采集/远控仍 OFF。勾选**不是安装意图/授权票据**；刷新状态或离开组件会清理此前临时说明状态。现有 “安装 Agent（尚未开放）” 按钮继续受 `RELEASE_GATE.installEnabled=false` 锁定。
- `apps/desktop/src/DesktopApp.tsx`：接入新面板，没有新增 Tauri IPC、Shell/HTTP/FS 插件，两个原有只读命令 `inspect_host` / `inspect_popos_preflight` 保持不变。
- `apps/desktop/src/install-review.test.ts` 与 `InstallReviewPanel.test.tsx`：覆盖无主机证据/伪造批准标志、旧版/正在运行/覆盖/未知包、跨架构、全部勾选仍不可安装、隐私说明、静态页面不可执行等。
- `apps/desktop/tools/verify_desktop_readonly.py` 及负向变更测试：在现有源码保护基础上，要求真实向导始终挂载受审面板；约束所有 install/update/remove/start/boot/collection/network/broker 字段**固定为 false**；拒绝该面板出现未授权 IPC、网络、进程调用。增加权威代码变异负例，防止 UI 被误提升为受信任安装器。

## 已确认证据与仍待验收

- **2026-11-24 源码安全/意图防误授权 CI：** [Desktop agent install intent DENY-ALL #45368181785](https://github.com/kunlong-luo/lanveriq/actions/runs/45368181785) **SUCCESS**，精确该工作流源码 SHA `752ed8c11d4d1799b65de620a62d4d3920318376`：原安装意图 39/39 离线拒绝通过，已更新的 `verify_desktop_readonly.py` PASS，`verify_desktop_release_evidence_boundary.py` PASS；没有执行 dpkg、apt、pkexec、Polkit、systemd start、签名发布或联网安装。
- **首次桌面预览失败与修复：** [Desktop Tauri preview #45368132407 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45368132407)，第一个只读源码安全门禁跑 26 项，25 项 PASS、1 项 FAIL；原因是新增 `verify_desktop_readonly.py` 的授权字段正则匹配到了 `type ... installAllowed: false` 类型声明，即使测试故意将实际 `return { installAllowed: true }` 伪造为批准仍可能误过。**这是测试守卫错误而不是正式安装被开启**。已在 [功能修复 SHA fe92d11a](https://github.com/kunlong-luo/lanveriq/commit/fe92d11aae820b4523a91f70de5ba8c4f3140615) 将检查改为**要求实际返回对象的 `field: false,` 且全文禁止 `field: true`**；变异测试保留。该次失败的 native 阶段被正确 SKIPPED，不能冒充成功。
- **修复后的完整 UI / 原生 Tauri 验收**：将以更新过的源码触发的 Desktop Tauri preview 与 GUI-only 候选归档 CI 为准，未完成之前**不能声称真实前端/原生编译或软件包审计通过**。暂不增加正式发行权限。
- 本项目仍没有可部署的正式受限 broker、polkit action 安装策略和真实签名 Agent 发行源，也没有 Pop!_OS24 COSMIC 真实安装/升级/卸载 L3。不得把安装界面出现、四条说明勾选或现有 signed metadata LAB 变成正式包可安装事实。

## 下一项工程及退出标准

**G05-L2B-22（尚未执行）**：优先复用系统原生 PackageKit / APT Signed-By，不在 GUI 中实现 root/shell/路径执行接口；设计并安全审查一个**独立安装事务 broker**（固定包名、固定可信发行者、固定安装/升级/卸载动作、受当前管理员身份强绑定的 Polkit 逐次授权、签名/撤销/可信时间、抵御符号链接/TOCTOU 的包文件打开与执行身份）。需先以暂时无发行密钥/授权的失败优先路线做 Ubuntu24 隔离攻击用例，取得独立合法发行批准后才能运行真实包管理安装 L2。任何服务启用必须独立获批准，未授权时 Agent 维持 inactive/no network/no collection/no remote；真实 Pop!_OS24 COSMIC GUI/Polkit L3 还需另行签字。

**发布门禁：G-02/G-05/G-07/G-08/G-09、正式业务 G-06 均 OPEN。v0.1 NOT RELEASE READY。生产网络/采集/自动恢复/远控/证书签发均 OFF。**
