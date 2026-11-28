# GPUI Linux 发行许可审批执行卡

**REVIEW ONLY · 2026-11-28 · NOT A DISTRIBUTION NOTICE · v0.1 NOT RELEASE READY**

仅为 MVP-08/G-07 准备审批材料，不替代 [ADR-0014](../architecture/adrs/0014-gpui-desktop-candidate.md)、[任务账本](../planning/local-mvp-tasks.md) 或[唯一发布门禁](../planning/release-gap-register.md)。本卡逐包队列已按新版 f4bd6fde 的实际归档独立复核。所有许可、安装与发布批准均未取得。

## 1. 已核实材料及使用顺序

| 证据 | 核实结果 | 限制 |
| --- | --- | --- |
| [发行材料归档 15250515162](https://github.com/kunlong-luo/lanveriq/actions/runs/46112005437/artifacts/15250515162) | ZIP SHA256 be953c4fb5b35c9b6f46369c8e6aa56e66fded7e713a3cbcbb3ec217d9147ca9；327/327 文本实际字节数、SHA256 均与 index 一致；原始文本合计 963848 bytes | 7日留存；不是可分发 NOTICE |
| 归档绑定 | source 39db84ef39ac70414e7d3b1a1821d5ec14699f45；ELF SHA256 2f2a13853a9259d223d575b29ee8aa12d1587d6df078a6632c6076d347163b5c；lock SHA256 0f77a9e9df9f3ff03612f22f4777de7d7ebf2cd65b69fc79d18a2ed05e37dad9 | 不是新版 ELF；重新绑定前不得将此材料称作新版正式 NOTICE |
| 522 package rows | 499 有 registry 材料、22 缺项有辅助材料、1 本地产品无许可；890 registry material files、1835 resource candidates | 全部仍待审，材料存在不等于许可充分 |
| [源码范围复核 #46119833647](https://github.com/kunlong-luo/lanveriq/actions/runs/46119833647) | 3/3 fixture PASS；5个 GPUI 示例资源无外部字面引用；EXIF 另有 test-only 源码引用，registry platform.rs Git blob 与固定上游相同 | 源码范围审查完成；不证明最终 ELF 不含转换资源 |
| [新版 f4bd6fde #46119431583](https://github.com/kunlong-luo/lanveriq/actions/runs/46119431583) | 4/4 jobs SUCCESS；96 selected 原始 SVG 存在、12 retained 原始 payload 未检出；Release/Wayland/association PASS | ELF SHA256 83b51857cfc422f81e3457df11f6c5c0e6ad2cb31fe305b1aa52020930118b3b；新版 linked notice [#46127559956](https://github.com/kunlong-luo/lanveriq/actions/runs/46127559956) SUCCESS；[归档15256242540](https://github.com/kunlong-luo/lanveriq/actions/runs/46127559956/artifacts/15256242540) SHA256 3a4ddfaea50c878d6a8201cc3062c41897d5da8168b269e733a68895ad9a506c；新版522行/246 observed/13+9缺项/15 external，[新版实际文本重绑 #46127631310](https://github.com/kunlong-luo/lanveriq/actions/runs/46127631310) SUCCESS，522/890/327/1835，0 gaps closed；[归档15255729897](https://github.com/kunlong-luo/lanveriq/actions/runs/46127631310/artifacts/15255729897) SHA256 c354a403494da4066d95dd0468e13f1b326c3ce365acb3de3272e0d3704f8615（本轮独立下载并复核两个 ZIP；327/327 文本实际字节数及 SHA256 一致，总计963848 bytes；522/522 逐包名称/版本/声明/registry checksum/链接状态/辅助摘要/原始路径与摘要一致，890条原始材料路径及字节数一致；所有文本均被引用；source/lock/ELF/SBOM 摘要两归档一致，SBOM SHA256 7c3f9beb3dc72a4d7cf9d83d265b48dcb1ef9bcdb68ef82769f2161fd2ea7dd6） |

保留原始 CycloneDX、全部 registry material、辅助文本及 SHA；审批人必须能获取原始内容。CI 归档到期前由获授权责任人选择受控证据保管位置，记录接收摘要、访问控制、保留期与审查人；本次没有迁移、长期保管或外部签收。到期后不得凭摘要声称仍能取得原文，必要时用既有工具重建并核对差异。

## 2. 22 个 registry 材料缺项审批队列

以下来自新版 15255729897 index 与 15256242540 linked notice 的逐项独立交叉核验，22个名称/版本/声明/辅助材料数/链接分类与历史队列一致，按新版 LLD 观测优先级排列；**每行均 OPEN**。不以未观察 rlib 为排除许可义务的依据。

| 优先级/新版观测 | 锁定包 | 原始声明 | 辅助材料数 |
| --- | --- | --- | ---: |
| P1 / 非零段 | accesskit 0.24.1 | MIT OR Apache-2.0 | 3 |
| P1 / 非零段 | accesskit_atspi_common 0.19.1 | MIT OR Apache-2.0 | 3 |
| P1 / 非零段 | accesskit_consumer 0.38.0 | MIT OR Apache-2.0 | 3 |
| P1 / 非零段 | accesskit_unix 0.22.1 | MIT OR Apache-2.0 | 3 |
| P1 / 非零段 | gpui-kit 0.11.0 | Apache-2.0 | 2 |
| P1 / 非零段 | harfrust 0.5.2 | MIT | 1 |
| P1 / 非零段 | lyon_path 1.0.19 | MIT OR Apache-2.0 | 2 |
| P1 / 非零段 | rust-i18n-support 4.2.4 | MIT | 1 |
| P1 / 非零段 | svg_fmt 0.4.5 | MIT/Apache-2.0 | 1 |
| P1 / 非零段 | taffy 0.15.1 | MIT | 1 |
| P1 / 非零段 | xim-ctext 0.3.0 | MIT | 1 |
| P1 / 非零段 | xim-parser 0.2.2 | MIT | 1 |
| P1 / 非零段 | zune-inflate 0.2.54 | MIT OR Apache-2.0 OR Zlib | 2 |
| P2 / 未观察 rlib | lyon_geom 1.0.20 | MIT OR Apache-2.0 | 2 |
| P2 / 未观察 rlib | mac 0.1.1 | MIT/Apache-2.0 | 1 |
| P2 / 未观察 rlib | pathfinder_geometry 0.5.1 | MIT/Apache-2.0 | 1 |
| P2 / 未观察 rlib | pathfinder_simd 0.5.7 | MIT OR Apache-2.0 | 2 |
| P2 / 未观察 rlib | profiling 1.0.18 | MIT OR Apache-2.0 | 2 |
| P2 / 未观察 rlib | seahash 4.1.0 | MIT | 1 |
| P2 / 未观察 rlib | spirv 0.4.0+sdk-1.4.341.0 | Apache-2.0 | 1 |
| P2 / 无 rlib 观测 | profiling-procmacros 1.0.18 | MIT OR Apache-2.0 | 2 |
| P2 / 无 rlib 观测 | rust-i18n-macro 4.2.4 | MIT | 1 |

先审13项，再审9项（7未观察、2无观测）。每项必须核对原始版权、完整许可文本、适用分支与额外义务。辅助来源见 [固定证据索引](../../tools/gpui_notice_gap_index.json)。AccessKit 的 Chromium 通知、GPUI Kit 的 docs 许可须分别核对作用范围；lyon 两项只有 Apache-notice-only 辅助材料，不当作完整 Apache 许可文本；zune-inflate 的 MIT/Apache 声明不代替完整文本。mac/pathfinder_geometry/seahash 的 Debian 版权仅为下游佐证，3项上游 Git 精确绑定仍 OPEN。任何兼容性/替代决定必须经实际维护者与适用法务审核。

## 3. 其他有实质义务的材料

新版 499 个有材料记录中，优先审 encoding_rs 0.8.44 的 BSD-3-Clause 附加义务，unicode-ident 1.0.27 和 unicode-properties 0.1.5 的 Unicode-3.0 附加义务（AND 不可当作 OR）。rustix 1.1.6、linux-raw-sys 0.12.1 涉及 LLVM exception 的备选声明；self_cell 1.3.0 为 Apache-2.0 OR GPL-2.0-only，须明确审批适用分支，不因 GPL 字样直接判定禁止。option-ext 0.2.0 是 MPL-2.0，旧 rlib 未观察，仍核对实际分发与相应义务；ryu 1.0.23 有 Apache-2.0 OR BSL-1.0 声明。保留全部原始文本，禁止自动选择 SPDX OR 分支、自动批准或删去 AND 通知。上述只为有限风险排序，不是整个项目许可审查。

图标：新版使用96个已核对 Lucide 图像与12个兼容映射，精确新版 ELF 原始字节检查 PASS（96存在、12未检出；不证明转换资源不存在）；保留 Lucide ISC/Feather MIT 通知候选、先前12个原始图标来源与权利记录，另6示例资源的最终范围仍待审。OS/native 库、编译器运行时、生成代码、其他平台依赖、完整发行 SBOM 和9维护警告分别审，不用522行替代全范围。

## 4. 产品许可方向已选；权属与实际授权仍待审

**2026-11-28 维护者明确选择专有软件作为拟定发行方向（DIRECTION SELECTED）**。不对 Lanveriq 自有代码授予 MIT/Apache-2.0 等开源许可；第三方原许可与义务保持不变。当前未取得可核实权利人签署的产品许可/客户 EULA，不提交根 LICENSE 或 Cargo license metadata，不声称所有版权已归属。RustDesk 保持 OFF。

实际基线 ef2377e2：GitHub repo metadata public / fork=false / forks_count=0 / license=null；仓库所有者账户权限 admin。完整 Git tree 非截断，未发现根 LICENSE/COPYING/NOTICE、CLA/DCO 或 AUTHORS；CONTRIBUTING.md 先前已提醒公开不授予发行许可，外部贡献政策待定。候选 Cargo.toml publish=false、无 license 字段。抽查 GitHub 原始提交 27ce8af7、6d4ee762、ef2377e2，三处 author/committer 均关联仓库所有者账户。贡献者 REST 端点不在连接器许可范围；搜索最近100条/另一排序9条的作者映射为空，不构成完整作者清单。样本无权属证明；forks_count=0 也不证明无历史复制。仓库可见性未更改。

### 4.1 待完成权属与商业发行检查

| 事项 | 当前判定 | 后续证据 |
| --- | --- | --- |
| 专有发行方向 | SELECTED | 本次维护者选择，记录日期与范围 |
| 权利主体及签署权限 | OPEN | 真实自然人/法人、授权关系；在受控位置核验 |
| 全部历史作者及贡献权属 | OPEN | 完整 Git 历史/合并/外部贡献，必要时取得许可或转让文件；提交邮箱及身份不进入公开文档 |
| 自研/复制/生成/外部素材 | OPEN | 核对来源、合同、许可、适用范围，不以 Git 作者或生成工具替代权属证明 |
| 仓库可见性 | PUBLIC，未改变 | 单独批准 Private 及 CI/Pages/协作者影响；既有复制不消失 |
| 客户授权条款与正式发布 | OPEN | 权利人及法务确认 EULA、渠道、支持与适用法；独立发行签署 |

### 4.2 专有产品的第三方审查规则

- MIT/BSD/ISC 等宽松许可：逐组件保留适用许可及版权文本；Apache-2.0 按第4条保留许可证、适用 NOTICE、修改说明等，不把自有代码许可替代原许可。
- MPL-2.0：按实际分发覆盖文件履行源码可获得性及通知义务；可与专有较大作品共存，但不直接判定本项目 option-ext 合规。
- GPL/AGPL/LGPL 或多选 SPDX：按真实链接、分发、修改、交互、适用许可分支逐项审核，不能自动批准或一概禁止；RustDesk 未授权功能继续关闭。
- 22项 registry 材料缺项仍 OPEN；按现有逐包队列及固定上游来源审查。最终设计 ELF/SBOM/原始许可文本统一绑定，不能使用旧版本 NOTICE 代替。安装、分发、生产联网、远控与签名门禁不变。
- 参考官方 [GitHub 仓库许可](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)、[Apache-2.0 原文](https://www.apache.org/licenses/LICENSE-2.0)、[Mozilla MPL FAQ](https://www.mozilla.org/en-US/MPL/2.0/FAQ/)；本卡为工程审查记录，不是法务结论。

## 5. 退出审批卡（全部未签收）

| 责任人 | 必须交付 | 状态 |
| --- | --- | --- |
| 权利人/维护者 | 专有方向已选；本地产品权属、授权范围、贡献权属、发行渠道仍须核验 | DIRECTION SELECTED / APPROVAL OPEN |
| 法务/许可 | 522行及其他发行范围的审查结论、22缺项处理、SPDX 分支、原始文本与附加义务、资源来源及许可 | OPEN |
| 工程/发行 | 同功能 SHA+lock+ELF、包摘要、精确资源检查、发行 SBOM、适用 NOTICE/版权材料与受控证据保管 | OPEN |
| 设备所有人/UX | 首要本机 KDE/X11：真实刷新、四字对齐、IME、DPI、完整键盘/关闭及受限操作；COSMIC/Wayland 与 KDE/Wayland 独立兼容验收；独立 L3 不由合成 CI 替代 | OPEN |
| 安全/发行 | 真实授权、签名、清洁 ABI、回退及其余 G-01–G-10 的批准 | OPEN |

完成后在真实审批记录写入责任人、日期、范围、确切发行 SHA/包摘要、证据摘要与保管位置、结论（批准/拒绝/待补证）。未获批准不得改写 OPEN，不构建发布用 NOTICE，不发包，不删除 Tauri，不开启 Agent 权限。该卡是审批入口，不是发布许可或法律意见。

## 6. 单次物理验收与移交（2026-11-28 更新：适配维护者 KDE/X11）

维护者明确不切换桌面。本机 MVP 首选 Pop!_OS 24.04 KDE/X11，原七项结果保留；只补缺失的本机观察及精确源码/ELF证据。COSMIC/Wayland 与 KDE/Wayland 分开验收，不再是本机 MVP 强制前提，正式发行 Gate 不变。普通用户在可信仓库执行：

```bash
git pull --ff-only origin main
python3 apps/desktop/tools/owner_kde_x11_review.py
```

十二项工具仅在当前本地活动 KDE/X11 会话运行；已有观察不作废，不虚填未测项目。以下 COSMIC 指令仅供未来独立兼容环境使用，不要求当前维护者执行。

当前所有人历史 KDE/X11 七项功能通过；不要求重复原七项 KDE 演示，不当作 COSMIC。自动端以修正后 [#46127948779](https://github.com/kunlong-luo/lanveriq/actions/runs/46127948779) 的实际 clean OCI 结果为准：四jobs全 SUCCESS，clean APT 2/2、92基础包、9计划安装/配置、0移除、dpkg状态不变、五项直接floor满足；新KDE适配源码2ab63895另行CI，勿混用旧ELF。若失败只修复具体失败；若通过仍仅为五项直接 ABI 解析，不以其代替全运行时/真实系统。历史先前 Release、图标和新版材料已验，不因文档改动重复运行。

若登录管理器已经提供 COSMIC，保存工作后正常注销并选择该真实本地 Wayland 会话；未提供则记录 NOT_RUN，不安装/切换桌面软件或伪造 XDG 环境。普通用户在已信任、候选目录无本地改动的仓库根目录运行：

```bash
git pull --ff-only origin main
python3 apps/desktop/gpui-candidate/tools/owner_cosmic_review.py
```

此工具固定 Rust 1.100.0、locked/offline Release 构建，校验源码/锁/ELF及Wayland socket，九项实际观察并 Ctrl+Q；缺少本地依赖或不是真实 COSMIC Wayland 时停止。不改服务/安装包/上传报告。保持独立 L3 NOT_APPROVED。现有 [KDE 工具](../../apps/desktop/tools/owner_kde_x11_review.py) 有十二项，但其报告不得用于 COSMIC。COSMIC 九项工具尚未自动采集以下补充结果，同一窗口观察、附于同一 SHA/ELF 的报告即可，不新增独立运行：

| 补充观察 | 通过条件 |
| --- | --- |
| 主题 | 浅色/深色变化，导航后保持，回到跟随系统；真实系统外观事件未测单列 |
| 紧凑导航 | 窄窗图标、七项仍可达、宽窗标签恢复且当前页不变 |
| 受限操作 | 安装/配置/注册/服务分别3/2/2/3项且禁用；其他页不重复 |
| DPI/GPU/可访问性 | 记录实际显示缩放、渲染表现与辅助技术观察；未测试明确 NOT_RUN，视觉正常不等于 GPU 或语义树证据 |
| 窗口管理器关闭 | Ctrl+Q 与 WM close 是不同路径；只有同ELF实际单独关闭观察才记 PASS，未执行则 NOT_RUN |

设备所有人交回脱敏 JSON 与附加 PASS/FAIL/NOT_RUN；独立审核人另行确认身份、授权范围、实机证据，不能由所有人报告代签。不索取机器序列号、密钥、完整环境或真实员工数据。

维护者已选择 **专有发行方向**，审批前仍须由权利人确认实际主体、贡献权属和具体发行授权；由法务确认适用第三方分支、附加义务及22缺项、由工程/安全审查9维护警告与其余运行时范围、最终包/SBOM/NOTICE/证据保管及回退。仅在所有 ADR-0014 迁移条件及相应授权实际满足后执行 Tauri cutover。当前保留 Tauri，禁止生产/安装权限，MVP-08 BLOCKED。若外部批准未取得，本项进入明确阻塞交接，不以新外围 CI 制造进度。

## 7. 当前设计基线与历史材料边界

2026-11-28：实际 KDE 适配 [#46128826401](https://github.com/kunlong-luo/lanveriq/actions/runs/46128826401)、实际本机 OS/CPU/RAM 功能 [#46129584167](https://github.com/kunlong-luo/lanveriq/actions/runs/46129584167) 四jobs全 SUCCESS；新锁 747f0bf406a772c1253f7ffd67452c8d9cc5ac7cdd9c584541c5110ad1eed887，后者 ELF 85bbe31034bcaab42fdbdf0870f7a4a3050932a68780e2524be1131b1e4ef1b9。Release association 实际仍22缺项；归档 [15257710474](https://github.com/kunlong-luo/lanveriq/actions/runs/46129584167/artifacts/15257710474) SHA256 940b99668609f4dc343544ec6dbf78226813e7ff7da710545d5e040dff215e03（仅日志与metadata已核）。当前设计 [26714637](https://github.com/kunlong-luo/lanveriq/commit/267146374dcb67f0e5801f8e9fc29b2d708da2dc) [#46130309195](https://github.com/kunlong-luo/lanveriq/actions/runs/46130309195) supply SUCCESS（569 SBOM pairs/46 extras/0 graph missing/22 gaps），native仍运行。第1、2节f4bd6fde独立解包材料仅作历史佐证，不能作为新锁或ELF已审批材料；最终设计功能CI完成后集中绑定新产物，法律22缺项、产品许可、正式NOTICE及独立审批不自动关闭。当前本机仅KDE/X11，不强制切换；无需重复旧七项演示，最终报告补真实刷新、对齐、未测项和精确SHA/ELF。

四字菜单修正 [#46129951995](https://github.com/kunlong-luo/lanveriq/actions/runs/46129951995) 四jobs SUCCESS，确切 ELF 111db67b1d3978dbb58326fd0a010b106a17be3726c4fdbd47b8d97770a11861；22材料缺项保持OPEN。设计初版 #46130309195 原生失败 E0599，修正8bd35b22仅导入公开 trait，[#46130643220](https://github.com/kunlong-luo/lanveriq/actions/runs/46130643220) 供链已成功，最终原生验收仍待。无需为中间版重新组装材料；仅最终功能ELF绑定后交审批。未取得产品权属许可、第三方材料确认或独立签署；不以新测试替代批准。
