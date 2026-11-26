# G-05/G-09 — Ubuntu 24.04 原生 Tauri / GUI-only .deb APT 缓存闭包变更与双 runner 验收

**2026-11-26 · `main` · 签名 APT 双 runner 预取与独立复核 L2 PASS；原生 Tauri 和 GUI-only .deb 待同功能 SHA 的 CI 结果。** 本报告是 **CI 构建先决条件** 的验收，**不等于** Pop!_OS 24.04 COSMIC 物理 L3、Agent 安装许可或产品发行。

## 现象（原始失败不删除）

- 桌面功能 SHA `dfaf97b5`：[Tauri Preview #45744173020](https://github.com/kunlong-luo/lanveriq/actions/runs/45744173020) 的 React/UI job SUCCESS、Native job **FAIL**；[GUI-only .deb #45744172969](https://github.com/kunlong-luo/lanveriq/actions/runs/45744172969) **FAIL**。两者均在原生 WebKitGTK 依赖安装之前拒绝：旧审核缓存 **175 个**，当前 Ubuntu signed-index 求解闭包计数不同，`FAIL: APT closure package count differs`。
- 首次刷新 SHA [`abd55c61`](https://github.com/kunlong-luo/lanveriq/commit/abd55c61168ec2cb9b1d94ea33212faf2dffabad)：[预取 #45745478152](https://github.com/kunlong-luo/lanveriq/actions/runs/45745478152) 第一个 Ubuntu runner **172 个签名源归档**下载成功；第二 runner 的计划数量不同，严格完整闭包检查 **FAIL**。保留此失败，不把来源验证混同为跨 runner 完成。
- 第二次诊断 SHA [`d0f93b6c`](https://github.com/kunlong-luo/lanveriq/commit/d0f93b6c4567b24504bc4e542b9077428c479749)：[APT 双 runner #45745618560](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560) **两个 jobs SUCCESS**，源与目标各自用正式 Ubuntu 24.04 APT 签名索引独立验收 **176 个归档**，并核对 source artifact SHA256、大小、版本、架构、无多余/缺失包和不修改安装包状态。此轮 GitHub runner `ImageVersion=20261116.376.1`，不证明以后任意镜像都使用同一依赖闭包。

## 已提交的真实修复（功能 SHA `a6db3afd`）

[单个原子提交](https://github.com/kunlong-luo/lanveriq/commit/a6db3afdfdcf75c9ef9690ace18a1240f1a03064) 同时修改以下两个 CI 使用者，不更改产品 Agent、Tauri 权限、安全门禁或正式发布流程：

1. [Tauri 原生构建](../../.github/workflows/desktop-tauri-preview.yml)：审过的包数 **175→176**，归档摘要/缓存 key 更新，严格运行本机签名 APT metadata 和完整闭包校验，安装系统原生 GUI 编译前提时仍 **`--no-download`**。
2. [GUI-only `.deb` 候选审计](../../.github/workflows/desktop-deb-candidate.yml)：**同一**审核缓存 key、176 包总数、归档哈希、无下载安装和独立 `.deb` 完整性/ELF/许可证缺口检查。CI 构建的 `.deb` 只有 GUI；**不含 Agent、不安装、不签发、不发布**。
3. [APT 独立预取生产者](../../.github/workflows/desktop-ubuntu-apt-prefetch.yml) 已在任一原生消费者工作流修改时触发重新预取；要求 Ubuntu 签名索引检查、download-only 不更改 dpkg 安装状态、另一个 runner 审计全部归档。诊断仅记录 APT 依赖闭包差异，**不能静默退回旧缓存或接受不完整/越权归档**。

**当前被批准的临时 CI 加速缓存（仅独立 GitHub CI 管理域）：**

- source/probe run: [#45745618560](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560) 两 jobs SUCCESS，source `d0f93b6c`
- archive count: `176`；producer/independent receiver manifest SHA-256: `0d72c92f41066df4b98f7dd5fa9d101d2508676d771d0a3b4023b076d4d7da43`
- GitHub cache key: `lanveriq-ubuntu24-tauri-apt-v2-7ea38b271819e80608ebc8faedf2e9846799ae777dd881fe0ccd616caf671ca6`
- **注意：** 此 cache 只是独立 CI 的**不可信提速层**，不是正式软件发行签名根、独立物理保管人、合法防回滚高水位或可分发包的验收。Ubuntu runner image/source 或安装基础集变动导致本机 APT 计划不匹配时应 **FAIL / 重新两 runner 审核**，不得放宽预期包数、关闭索引验签或使用未经批准的回退 cache。

## 本轮状态与下一项出口

[Tauri Preview #45745776008](https://github.com/kunlong-luo/lanveriq/actions/runs/45745776008) 与 [GUI-only `.deb` #45745775902](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775902) 使用精确功能 `a6db3afd` 已触发；**若尚未完成，本报告不能将它们计为 PASS**。两者通过也只代表 Ubuntu runner 的原生 GUI 构建与未安装包审计；实体 COSMIC、Polkit/正式发行根、安装与服务授权、真实 Agent 包、CA/MFA/WORM/法务/SRE 均 NOT RUN / OPEN。

[当前评分](../planning/project-scorecard.md)暂仍 **工程 71/100、正式 Gate 0/10 已关闭**。**v0.1 NOT RELEASE READY；生产联网、采集、自动恢复、远控、CA 签发与未授权安装 OFF。**
