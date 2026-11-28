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

## 4. 维护者必须作出的产品许可决定

当前仓库没有已确定的本地产品发行许可，候选 package 无 license 声明。项目权利人须先核对贡献权属和既有第三方代码；可选内部/专有分发（仍履行第三方义务）或明确开源许可（例如 Apache-2.0 或 MIT，需权利人授权且核对依赖兼容）。不预设是否公开源码，不代选、不生成假版权或签字、不提交根 LICENSE/Cargo metadata。若选择全项目开源，按各组件和实际发行范围独立核对，不能据本卡自动将 RustDesk 或其他第三方改许可。RustDesk 保持 OFF。

## 5. 退出审批卡（全部未签收）

| 责任人 | 必须交付 | 状态 |
| --- | --- | --- |
| 权利人/维护者 | 本地产品许可与适用范围、贡献权属、发行渠道 | OPEN |
| 法务/许可 | 522行及其他发行范围的审查结论、22缺项处理、SPDX 分支、原始文本与附加义务、资源来源及许可 | OPEN |
| 工程/发行 | 同功能 SHA+lock+ELF、包摘要、精确资源检查、发行 SBOM、适用 NOTICE/版权材料与受控证据保管 | OPEN |
| 设备所有人/UX | 实际 COSMIC/Wayland、IME、DPI、完整键盘/关闭及受限操作；独立 L3 不由合成 CI 替代 | OPEN |
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

审批前必须由权利人明确 **仅内部/专有发行** 或 **开源及具体许可**，并核对贡献权属；由法务确认适用第三方分支、附加义务及22缺项、由工程/安全审查9维护警告与其余运行时范围、最终包/SBOM/NOTICE/证据保管及回退。仅在所有 ADR-0014 迁移条件及相应授权实际满足后执行 Tauri cutover。当前保留 Tauri，禁止生产/安装权限，MVP-08 BLOCKED。若外部批准未取得，本项进入明确阻塞交接，不以新外围 CI 制造进度。
