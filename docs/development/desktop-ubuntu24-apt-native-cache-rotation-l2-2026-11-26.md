# G-05/G-09 — Ubuntu 24.04 原生 Tauri / GUI-only .deb APT 缓存闭包变更与双 runner 验收

**2026-11-26 · `main` · 签名 APT 双 runner L2 PASS，真实 Tauri 原生 CI PASS，未签名 GUI-only .deb 归档 CI PASS；不是 Agent 发行。** 本报告是 **CI 构建先决条件** 的验收，**不等于** Pop!_OS 24.04 COSMIC 物理 L3、Agent 安装许可或产品发行。

## 现象（原始失败不删除）

- 桌面功能 SHA `dfaf97b5`：[Tauri Preview #45744173020](https://github.com/kunlong-luo/lanveriq/actions/runs/45744173020) 的 React/UI job SUCCESS、Native job **FAIL**；[GUI-only .deb #45744172969](https://github.com/kunlong-luo/lanveriq/actions/runs/45744172969) **FAIL**。两者均在原生 WebKitGTK 依赖安装之前拒绝：旧审核缓存 **175 个**，当前 Ubuntu signed-index 求解闭包计数不同，`FAIL: APT closure package count differs`。
- 首次刷新 SHA [`abd55c61`](https://github.com/kunlong-luo/lanveriq/commit/abd55c61168ec2cb9b1d94ea33212faf2dffabad)：[预取 #45745478152](https://github.com/kunlong-luo/lanveriq/actions/runs/45745478152) 第一个 Ubuntu runner **172 个签名源归档**下载成功；第二 runner 的计划数量不同，严格完整闭包检查 **FAIL**。保留此失败，不把来源验证混同为跨 runner 完成。
- 第二次诊断 SHA [`d0f93b6c`](https://github.com/kunlong-luo/lanveriq/commit/d0f93b6c4567b24504bc4e542b9077428c479749)：[APT 双 runner #45745618560](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560) **两个 jobs SUCCESS**，源与目标各自用正式 Ubuntu 24.04 APT 签名索引独立验收 **176 个归档**，并核对 source artifact SHA256、大小、版本、架构、无多余/缺失包和不修改安装包状态。此轮 GitHub runner `ImageVersion=20261116.376.1`，不证明以后任意镜像都使用同一依赖闭包。

## 已提交的真实修复（功能 SHA `a6db3afd`）

[单个原子提交](https://github.com/kunlong-luo/lanveriq/commit/a6db3afdfdcf75c9ef9690ace18a1240f1a03064) 同时修改以下两个 CI 使用者，不更改产品 Agent、Tauri 权限、安全门禁或正式发布流程：

1. [Tauri 原生构建](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-tauri-preview.yml)：审过的包数 **175→176**，归档摘要/缓存 key 更新，严格运行本机签名 APT metadata 和完整闭包校验，安装系统原生 GUI 编译前提时仍 **`--no-download`**。
2. [GUI-only `.deb` 候选审计](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-deb-candidate.yml)：**同一**审核缓存 key、176 包总数、归档哈希、无下载安装和独立 `.deb` 完整性/ELF/许可证缺口检查。CI 构建的 `.deb` 只有 GUI；**不含 Agent、不安装、不签发、不发布**。
3. [APT 独立预取生产者](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-ubuntu-apt-prefetch.yml) 已在任一原生消费者工作流修改时触发重新预取；要求 Ubuntu 签名索引检查、download-only 不更改 dpkg 安装状态、另一个 runner 审计全部归档。诊断仅记录 APT 依赖闭包差异，**不能静默退回旧缓存或接受不完整/越权归档**。

**当前被批准的临时 CI 加速缓存（仅独立 GitHub CI 管理域）：**

- source/probe run: [#45745618560](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560) 两 jobs SUCCESS，source `d0f93b6c`
- archive count: `176`；producer/independent receiver manifest SHA-256: `0d72c92f41066df4b98f7dd5fa9d101d2508676d771d0a3b4023b076d4d7da43`
- GitHub cache key: `lanveriq-ubuntu24-tauri-apt-v2-7ea38b271819e80608ebc8faedf2e9846799ae777dd881fe0ccd616caf671ca6`
- **注意：** 此 cache 只是独立 CI 的**不可信提速层**，不是正式软件发行签名根、独立物理保管人、合法防回滚高水位或可分发包的验收。Ubuntu runner image/source 或安装基础集变动导致本机 APT 计划不匹配时应 **FAIL / 重新两 runner 审核**，不得放宽预期包数、关闭索引验签或使用未经批准的回退 cache。

## 不同 runner 已安装依赖不一致的边界修复

- [旧 #45745478152 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45745478152) 的源 runner 只有 172 个待安装包；[批准 #45745618560 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745618560) 的源/目标都需要 176；紧接着 [#45745775904 FAIL](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775904) 的源 runner 需要 **176**、独立目标仅需 **172**，其缓存中多出的 **4** 个已由目标宿主机满足，**所需包/版本不缺失**。原先将“两个不同 runner 待安装包数必须相同”当成不变条件，会误拒绝受审的真实缓存。
- [验证器](../../apps/desktop/tools/verify_desktop_apt_archive_cache.py) 在提交 [`61cedcd3`](https://github.com/kunlong-luo/lanveriq/commit/61cedcd382eebec5ed98c2b74a4f2f84e013c75c) 新增 **显式开启** 的 `--allow-signed-superset`，默认严格模式**仍保持不变**。无论当前 runner 需要多少安装包，仍逐个核查缓存内全部 **176** 件的索引签名链间接信任、确切名字/版本/架构、SHA-256、文件大小及独立生产者 manifest；`Inst` 计划的每一条必需依赖**必须存在且版本一致**。只有“不需要再安装、但仍逐包签名验证”的归档允许额外存在。
- [离线负测](../../apps/desktop/tools/test_verify_desktop_apt_archive_cache.py) 明确覆盖**缺少必需包、必需版本变更、额外包的哈希篡改、当前计划超过缓存、默认严格模式不允许额外包**。[验证器单元 CI #45746132753 PASS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746132753)（功能 `e9b85a5d`）。
- 三条[预取](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-ubuntu-apt-prefetch.yml)、[原生 Tauri](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-tauri-preview.yml)、[GUI-only `.deb`](https://github.com/kunlong-luo/lanveriq/blob/d04d3e01ca97afd0c4a2ae31d5df19f5791c5e09/.github/workflows/desktop-deb-candidate.yml) 工作流已由功能 [`c411d3ea`](https://github.com/kunlong-luo/lanveriq/commit/c411d3ead8f7b79c9951fbf31a0a9f1712b72e0d) 显式调用该模式。**若生产者缺少任何目标必需的版本、签名索引不可验证或来源清单不同，仍 FAIL**；不会下载未经审核的替代包。此模式仅适用于一次性 Ubuntu CI GUI 编译依赖，不是 Agent/安装器的发行高水位。

## 本轮状态与下一项出口

[Tauri Preview #45745776008 两 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745776008) 在 `a6db3afd` 完成 React、原生 Rust 编译/测试与 Clippy；[GUI-only `.deb` #45745775902 SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45745775902) 在同一功能 SHA 实际构建未签名 UI-only 归档并经过包/ELF/依赖/只读 OCI 审计，**不安装、不发布、不包含 Agent**；[新的 signed-superset 双 runner #45746180961 两 job SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180961) 对应 `c411d3ea`。新的 [Tauri #45746181071](https://github.com/kunlong-luo/lanveriq/actions/runs/45746181071) 与 [GUI-only `.deb` #45746180976](https://github.com/kunlong-luo/lanveriq/actions/runs/45746180976) 需以其**各自最终结果**验收，不能由先前 SHA 的 PASS 替代。

[当前评分](../planning/project-scorecard.md)暂仍 **工程 71/100、正式 Gate 0/10 已关闭**。**v0.1 NOT RELEASE READY；生产联网、采集、自动恢复、远控、CA 签发与未授权安装 OFF。**
