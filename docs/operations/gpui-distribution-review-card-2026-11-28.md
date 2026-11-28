# GPUI Linux 发行许可审批执行卡

**REVIEW ONLY · 2026-11-28 · NOT A DISTRIBUTION NOTICE · v0.1 NOT RELEASE READY**

仅为 MVP-08/G-07 准备审批材料，不替代 [ADR-0014](../architecture/adrs/0014-gpui-desktop-candidate.md)、[任务账本](../planning/local-mvp-tasks.md) 或[唯一发布门禁](../planning/release-gap-register.md)。以下旧版链接观测不是新版 f4bd6fde 的链接结论。所有许可、安装与发布批准均未取得。

## 1. 已核实材料及使用顺序

| 证据 | 核实结果 | 限制 |
| --- | --- | --- |
| [发行材料归档 15250515162](https://github.com/kunlong-luo/lanveriq/actions/runs/46112005437/artifacts/15250515162) | ZIP SHA256 be953c4fb5b35c9b6f46369c8e6aa56e66fded7e713a3cbcbb3ec217d9147ca9；327/327 文本实际字节数、SHA256 均与 index 一致；原始文本合计 963848 bytes | 7日留存；不是可分发 NOTICE |
| 归档绑定 | source 39db84ef39ac70414e7d3b1a1821d5ec14699f45；ELF SHA256 2f2a13853a9259d223d575b29ee8aa12d1587d6df078a6632c6076d347163b5c；lock SHA256 0f77a9e9df9f3ff03612f22f4777de7d7ebf2cd65b69fc79d18a2ed05e37dad9 | 不是新版 ELF；重新绑定前不得将此材料称作新版正式 NOTICE |
| 522 package rows | 499 有 registry 材料、22 缺项有辅助材料、1 本地产品无许可；890 registry material files、1835 resource candidates | 全部仍待审，材料存在不等于许可充分 |
| [源码范围复核 #46119833647](https://github.com/kunlong-luo/lanveriq/actions/runs/46119833647) | 3/3 fixture PASS；5个 GPUI 示例资源无外部字面引用；EXIF 另有 test-only 源码引用，registry platform.rs Git blob 与固定上游相同 | 源码范围审查完成；不证明最终 ELF 不含转换资源 |
| [新版 f4bd6fde #46119431583](https://github.com/kunlong-luo/lanveriq/actions/runs/46119431583) | 4/4 jobs SUCCESS；96 selected 原始 SVG 存在、12 retained 原始 payload 未检出；Release/Wayland/association PASS | ELF SHA256 83b51857cfc422f81e3457df11f6c5c0e6ad2cb31fe305b1aa52020930118b3b；新版 linked notice [#46127559956](https://github.com/kunlong-luo/lanveriq/actions/runs/46127559956) SUCCESS（job 结论；逐项数据待核）；新版材料重新绑定待完成 |

保留原始 CycloneDX、全部 registry material、辅助文本及 SHA；审批人必须能获取原始内容。CI 归档到期前由获授权责任人选择受控证据保管位置，记录接收摘要、访问控制、保留期与审查人；本次没有迁移、长期保管或外部签收。到期后不得凭摘要声称仍能取得原文，必要时用既有工具重建并核对差异。

## 2. 22 个 registry 材料缺项审批队列

以下来自实际解包核验的 15250515162 index，按旧版 LLD 观测优先级排列；**每行均 OPEN**。不以未观察 rlib 为排除许可义务的依据。

| 优先级/旧版观测 | 锁定包 | 原始声明 | 辅助材料数 |
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

旧版 499 个有材料记录中，优先审 encoding_rs 0.8.44 的 BSD-3-Clause 附加义务，unicode-ident 1.0.27 和 unicode-properties 0.1.5 的 Unicode-3.0 附加义务（AND 不可当作 OR）。rustix 1.1.6、linux-raw-sys 0.12.1 涉及 LLVM exception 的备选声明；self_cell 1.3.0 为 Apache-2.0 OR GPL-2.0-only，须明确审批适用分支，不因 GPL 字样直接判定禁止。option-ext 0.2.0 是 MPL-2.0，旧 rlib 未观察，仍核对实际分发与相应义务；ryu 1.0.23 有 Apache-2.0 OR BSL-1.0 声明。保留全部原始文本，禁止自动选择 SPDX OR 分支、自动批准或删去 AND 通知。上述只为有限风险排序，不是整个项目许可审查。

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
