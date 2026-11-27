import { useCallback, useEffect, useState, type ReactNode } from "react";
import {
  Activity, ArrowLeft, ArrowRight, CheckCircle2, ChevronRight, CircleHelp,
  ClipboardCheck, Cpu, FileCheck2, Info, Laptop2, LockKeyhole, MonitorCog,
  PackageCheck, RefreshCw, Shield, ShieldCheck, ShieldOff, TerminalSquare,
  Unplug, XCircle,
} from "lucide-react";
import { invoke, isTauri } from "@tauri-apps/api/core";
import {
  describeReadiness, nativeDetails, RELEASE_GATE, type HostSnapshot,
} from "./safety";
import { parseLocalPreflight, poposSummary, type PoposDisplay } from "./preflight";
import { InstallReviewPanel } from "./InstallReviewPanel";
import { IdleConfigPanel } from "./IdleConfigPanel";
import { OfflineDiagnosticsPanel } from "./OfflineDiagnosticsPanel";
import { DesktopNavigation, DesktopOverview, PlannedSection, sectionLabels, type Section } from "./DesktopWorkspace";
type InspectionState = "idle" | "reading" | "loaded" | "error" | "web-preview";

const stages = [
  { title: "欢迎使用", subtitle: "了解 Lanveriq Desktop" },
  { title: "环境检查", subtitle: "只读检测本机状态" },
  { title: "授权说明", subtitle: "了解安装权限与隐私" },
  { title: "安装准备", subtitle: "预览受控安装步骤" },
] as const;

function FrameTitle({ eyebrow, title, description }: {
  eyebrow: string; title: string; description: string;
}) {
  return (
    <div className="mb-8">
      <p className="mb-2 text-[11px] font-semibold tracking-[.2em] text-sky-700 uppercase">{eyebrow}</p>
      <h1 className="text-3xl font-semibold tracking-tight text-slate-950">{title}</h1>
      <p className="mt-3 max-w-xl text-sm leading-7 text-slate-500">{description}</p>
    </div>
  );
}

function StatePill({ children }: { children: ReactNode }) {
  return <span className="inline-flex items-center gap-1.5 rounded-full border border-amber-200 bg-amber-50 px-3 py-1.5 text-xs font-medium text-amber-800"><span className="size-1.5 rounded-full bg-amber-500" />{children}</span>;
}

function ActionButton({ onClick, disabled, children, secondary = false }: {
  onClick?: () => void; disabled?: boolean; children: ReactNode; secondary?: boolean;
}) {
  return <button type="button" onClick={onClick} disabled={disabled} className={
    "inline-flex items-center justify-center gap-2 rounded-xl px-5 py-3 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600 disabled:cursor-not-allowed disabled:opacity-40 " +
    (secondary ? "border border-slate-200 bg-white text-slate-700 hover:bg-slate-50" : "bg-slate-950 text-white hover:bg-slate-800")
  }>{children}</button>;
}

export function DesktopApp() {
  const [section, setSection] = useState<Section>("overview");
  const [stage, setStage] = useState(0);
  const [noticeRead, setNoticeRead] = useState(false);
  const [host, setHost] = useState<HostSnapshot | null>(null);
  const [inspection, setInspection] = useState<InspectionState>("idle");
  const [popos, setPopos] = useState<PoposDisplay>({
    status: "idle", code: "", data: null,
  });

  const inspect = useCallback(async () => {
    // Rechecking the OS invalidates older compatibility metadata.
    setPopos({ status: "idle", code: "", data: null });
    if (!isTauri()) {
      setHost(null);
      setInspection("web-preview");
      return;
    }
    setHost(null);
    setInspection("reading");
    try {
      const snapshot = await invoke<HostSnapshot>("inspect_host");
      setHost(snapshot);
      setInspection("loaded");
    } catch {
      setHost(null);
      setInspection("error");
    }
  }, []);

  // An explicit operator click, never an automatic host scan or install step.
  const checkPoposCompatibility = useCallback(async () => {
    if (!isTauri()) {
      setPopos({ status: "web-preview", code: "", data: null });
      return;
    }
    setPopos({ status: "reading", code: "", data: null });
    try {
      const raw = await invoke<string>("inspect_popos_preflight");
      const data = parseLocalPreflight(raw);
      setPopos({ status: "checked", code: "", data });
    } catch (error) {
      const code = typeof error === "string"
        ? error : error instanceof Error ? error.message : "PREFLIGHT_UNAVAILABLE";
      setPopos({ status: "blocked", code, data: null });
    }
  }, []);

  useEffect(() => {
    void inspect();
  }, [inspect]);

  const currentStatus = describeReadiness(host);
  const ready = stage === 0 || stage === 1 || noticeRead;
  const currentStage = stages[stage];
  return (
    <div className="app-shell text-slate-900">
      <aside className="sidebar">
        <div className="flex items-center gap-3 px-3 pb-9 pt-1">
          <div className="brand-mark"><Activity size={21} strokeWidth={2.2} /></div>
          <div>
            <div className="text-[17px] font-bold tracking-[-0.045em]">lanveriq<span className="text-sky-600">.</span></div>
            <div className="mt-0.5 text-[10px] font-medium tracking-widest text-slate-400 uppercase">desktop preview</div>
          </div>
        </div>
        <DesktopNavigation section={section} onNavigate={setSection} />
        <div className="mt-auto px-2 pb-3">
          <div className="rounded-2xl border border-slate-200 bg-white/85 p-4">
            <div className="flex items-center gap-2 text-xs font-semibold text-slate-800"><ShieldOff size={15} /> 安全锁定</div>
            <p className="mt-2 text-[12px] leading-6 text-slate-500">生产联网、采集、远控与开机自启均未开放。</p>
          </div>
          <div className="mt-5 flex items-center justify-between px-2 text-[11px] text-slate-400"><span>v0.1 开发预览</span><span>Pop!_OS 首发</span></div>
        </div>
      </aside>

      <main className="main-content">
        <header className="topbar">
          <div className="flex items-center gap-2 text-xs font-medium text-slate-500">
            <span>工作空间</span><ChevronRight size={14} className="text-slate-300" />
            <span className="text-slate-900">{sectionLabels[section]}</span>
          </div>
          <StatePill>离线预览 · 不可安装</StatePill>
        </header>

        <div className="content-scroll" key={section}>
          <div className="mx-auto w-full max-w-[990px] px-8 py-10 lg:px-12 lg:py-12">
            {section === "overview" && <DesktopOverview host={inspection === "loaded" ? host : null} inspection={inspection} onNavigate={setSection} onInspect={() => { void inspect(); }} />}
            {section === "config" && <><FrameTitle eyebrow="configuration" title="服务器与配置" description="仅预览现有 Agent 空闲配置。服务器地址校验与连接检测属于后续任务，本页不发送请求或写入文件。" /><PlannedSection kind="config" /><IdleConfigPanel /></>}
            {section === "enrollment" && <><FrameTitle eyebrow="device identity" title="设备注册" description="注册与证书管理尚未授权。本页仅说明后续流程，不读取密钥、不提交 CSR、不签发证书。" /><PlannedSection kind="enrollment" /></>}
            {section === "about" && <><FrameTitle eyebrow="about" title="关于 Lanveriq" description="本机配置与诊断窗口，与独立 Rust Agent 及 Web 管理后台职责分离。" /><PlannedSection kind="about" /></>}
            {section === "setup" && (
              <>
                <FrameTitle eyebrow="installation experience" title="让设备管理更简单"
                  description="无需自行编译 Rust、输入复杂命令或手动编辑配置。我们正在构建受审核的图形化安装体验；当前版本仅提供离线预览与只读检测。" />
                <div className="mb-7 grid grid-cols-2 gap-3 lg:grid-cols-4" aria-label="安装步骤">
                  {stages.map((item, i) => (
                    <div key={item.title} className={"stage-card " + (i === stage ? "stage-active" : "")}>
                      <div className="mb-3 flex items-center justify-between">
                        <span className="text-[11px] font-bold text-slate-400">{String(i + 1).padStart(2, "0")}</span>
                        {i < stage ? <CheckCircle2 size={16} className="text-emerald-600" /> : <span className={"size-2 rounded-full " + (i === stage ? "bg-sky-600" : "bg-slate-200")} />}
                      </div>
                      <div className="text-[13px] font-semibold text-slate-900">{item.title}</div>
                      <div className="mt-1 text-[11px] text-slate-500">{item.subtitle}</div>
                    </div>
                  ))}
                </div>

                <section className="surface-card min-h-[354px]">
                  <div className="mb-5 flex items-center gap-3">
                    <div className="section-icon">
                      {stage === 0 ? <MonitorCog size={22} /> : stage === 1 ? <Cpu size={22} /> : stage === 2 ? <Shield size={22} /> : <PackageCheck size={22} />}
                    </div>
                    <div>
                      <p className="text-[11px] uppercase tracking-widest text-slate-400">Step {stage + 1} / 4</p>
                      <h2 className="mt-1 text-lg font-semibold text-slate-950">{currentStage.title}</h2>
                    </div>
                  </div>

                  {stage === 0 && (
                    <div>
                      <p className="max-w-2xl text-sm leading-8 text-slate-600">Lanveriq Agent 未来将通过独立的 systemd 系统服务在后台运行。配置窗口关闭后不需要常驻托盘程序，但始终保留清晰的服务查看、停止和卸载入口。</p>
                      <div className="mt-7 grid gap-3 md:grid-cols-3">
                        <Feature icon={<Laptop2 size={19} />} label="图形化配置" value="生成离线 TOML 草稿" />
                        <Feature icon={<ShieldCheck size={19} />} label="明确授权" value="安装与启动分别确认" />
                        <Feature icon={<Unplug size={19} />} label="默认离线" value="不采集、不上传、不远控" />
                      </div>
                    </div>
                  )}
                  {stage === 1 && (
                    <div>
                      <p className="mb-5 text-sm leading-7 text-slate-600">仅在 Tauri 桌面环境读取操作系统标识、候选软件包和 systemd 的公开状态。不读取私钥、个人文件或设备资产；浏览器预览不会伪造检测结果。</p>
                      <div className="grid gap-3 sm:grid-cols-2">
                        <DataItem title="操作系统" value={host ? host.osName : "尚未核验"} />
                        <DataItem title="目标架构" value={host ? host.architecture : "尚未核验"} />
                        <DataItem title="检查来源" value={inspection === "loaded" ? "本机只读检查" : "尚无真实本机证据"} />
                        <DataItem title="安装资格" value={currentStatus.label} />
                      </div>
                      <button type="button" onClick={() => { void inspect(); }} className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-sky-700 hover:text-sky-900"><RefreshCw size={15} /> 重新检测（只读）</button>
                      <section aria-label="Pop!_OS 和 COSMIC 兼容性预检" className="mt-7 rounded-2xl border border-slate-200 bg-slate-50/80 p-5">
                        <div className="flex flex-wrap items-center justify-between gap-3">
                          <div>
                            <h3 className="flex items-center gap-2 text-sm font-semibold text-slate-900"><MonitorCog size={17} /> Pop!_OS / COSMIC 兼容性</h3>
                            <p className="mt-1 text-xs leading-6 text-slate-500">检查本机只读软件包缓存和会话环境。不更新软件源、不安装、不启动 Agent。</p>
                          </div>
                          <ActionButton secondary disabled={popos.status === "reading" || !isTauri()}
                            onClick={() => { void checkPoposCompatibility(); }}>
                            <RefreshCw size={15} /> {popos.status === "reading" ? "检查中…" : "检查兼容性（只读）"}
                          </ActionButton>
                        </div>
                        <p role="status" aria-live="polite" className="mt-4 rounded-xl border border-slate-200 bg-white p-3 text-xs leading-6 text-slate-700">{poposSummary(popos)}</p>
                        {popos.status === "checked" && popos.data && (
                          <div className="mt-4 space-y-4">
                            <div className="grid gap-3 sm:grid-cols-2">
                              <DataItem title="目标发行版 / 架构" value="Pop!_OS 24.04 · amd64（系统标识）" />
                              <DataItem title="桌面会话声明" value="COSMIC / Wayland（尚未验证窗口和合成器）" />
                              <DataItem title="本机 ABI 依赖缓存" value={`${popos.data.reviewed_abi_cache.length} 组可查询 · 不代表安装求解`} />
                              <DataItem title="COSMIC 组件" value={`${popos.data.cosmic_components.length} 个已安装包 · 未测试启动`} />
                            </div>
                            <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white">
                              <table className="w-full text-left text-xs">
                                <caption className="px-3 py-3 text-left font-semibold text-slate-700">系统库候选版本（仅本机 APT 缓存）</caption>
                                <thead className="bg-slate-50 text-slate-500"><tr><th scope="col" className="px-3 py-2">软件包</th><th scope="col" className="px-3 py-2">最低要求</th><th scope="col" className="px-3 py-2">缓存候选</th></tr></thead>
                                <tbody className="divide-y divide-slate-100">
                                  {popos.data.reviewed_abi_cache.map(row => (
                                    <tr key={row.package}><th scope="row" className="px-3 py-2 font-medium text-slate-700">{row.package}</th><td className="px-3 py-2 text-slate-500">{row.minimum ?? "未规定"}</td><td className="px-3 py-2 text-slate-700">{row.cached_candidate}</td></tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                            <p className="text-xs leading-6 text-amber-800">以上不验证源签名、真实 GUI 运行、法务许可或 Pop!_OS L3，安装授权始终关闭。</p>
                          </div>
                        )}
                      </section>
                    </div>
                  )}
                  {stage === 2 && (
                    <div>
                      <p className="text-sm leading-7 text-slate-600">安装前必须清楚说明服务用途、所需管理员权限和隐私影响。当前没有任何可启用的生产联网、采集、远控或证书签发权限。</p>
                      <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 px-5 py-4 text-sm leading-7 text-amber-900">
                        <div className="mb-1 flex items-center gap-2 font-semibold"><Info size={17} /> 当前仅供离线实验</div>
                        阅读说明不构成安装授权、服务启动授权或开机自启许可。正式审批和签名安装功能尚未实现。
                      </div>
                      <label className="mt-6 flex cursor-pointer items-start gap-3 text-sm text-slate-700">
                        <input type="checkbox" checked={noticeRead} onChange={event => setNoticeRead(event.target.checked)} className="mt-1 size-4 accent-sky-700" />
                        <span>我已阅读当前版本限制，并了解此步骤<strong>不会执行安装或授权</strong>。</span>
                      </label>
                    </div>
                  )}
                  {stage === 3 && (
                    <div>
                      <IdleConfigPanel />
                      <InstallReviewPanel host={host} popos={popos} />
                      <ActionButton disabled={!RELEASE_GATE.installEnabled}><PackageCheck size={17} /> 安装 Agent（尚未开放）</ActionButton>
                      <p className="mt-4 text-xs leading-6 text-slate-500">不会调用 dpkg、apt、systemctl、sudo、polkit，也不会下载或安装软件。未来安装必须由发行者签名、独立受限 broker 和管理员逐次授权共同通过。</p>
                    </div>
                  )}
                  <div className="mt-8 flex items-center justify-between border-t border-slate-100 pt-6">
                    <ActionButton secondary disabled={stage === 0} onClick={() => setStage(previous => Math.max(0, previous - 1))}><ArrowLeft size={16} /> 上一步</ActionButton>
                    {stage < stages.length - 1 && <ActionButton disabled={!ready} onClick={() => setStage(previous => Math.min(3, previous + 1))}>下一步 <ArrowRight size={16} /></ActionButton>}
                    {stage === stages.length - 1 && <ActionButton secondary onClick={() => setSection("status")}>查看本机状态 <ArrowRight size={16} /></ActionButton>}
                  </div>
                </section>
              </>
            )}

            {section === "status" && (
              <>
                <FrameTitle eyebrow="local system" title="本机 Agent 状态" description="展示当前设备上可公开读取的安装包和系统服务信息，不含远程业务状态，也不把候选软件包冒充正式安装。" />
                <section className="surface-card">
                  <div className="mb-7 flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-3"><div className="section-icon"><Activity size={22} /></div><div><p className="text-xs text-slate-500">本机检查结果</p><div className="mt-1 font-semibold">{currentStatus.label}</div></div></div>
                    <ActionButton secondary onClick={() => { void inspect(); }}><RefreshCw size={16} /> 重新检测</ActionButton>
                  </div>
                  {inspection === "loaded" && host && describeReadiness(host).kind === "unverified" && <div role="alert" className="mb-6 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">安装前检查发现需要人工处理的状态。桌面预览不会自动修改旧版文件、覆盖配置、systemd 或服务状态。</div>}
                  {inspection !== "loaded" && <div className="mb-6 rounded-xl border border-sky-100 bg-sky-50 p-4 text-sm text-sky-800">{inspection === "web-preview" ? "当前处于浏览器预览，无法读取本机 systemd；这不是检测通过。" : inspection === "error" ? "读取主机状态失败；保持未经核验状态。" : inspection === "reading" ? "正在读取有限的本机公开状态…" : "尚未读取本机状态。"}</div>}
                  <div className="grid gap-3 sm:grid-cols-2">
                    {nativeDetails(host).map(([title, value]) => <DataItem key={title} title={title} value={value} />)}
                  </div>
                </section>
                <div className="mt-6 grid gap-3 md:grid-cols-3">
                  <Feature icon={<LockKeyhole size={19} />} label="安装" value="未授权，不可操作" />
                  <Feature icon={<ShieldOff size={19} />} label="开机自启" value="需要另行审批" />
                  <Feature icon={<TerminalSquare size={19} />} label="日志" value="可选中复制 · 本地脱敏" />
                </div>
              </>
            )}

            {section === "safety" && (
              <>
                <FrameTitle eyebrow="security & diagnostics" title="安全与诊断" description="所有安装、提权和运行时控制均保持关闭。此页面是安全能力说明，不是已经取得生产或设备管理权限的证据。" />
                <div className="surface-card">
                  <div className="mb-6 flex items-center gap-3"><div className="section-icon"><ClipboardCheck size={22} /></div><div><h2 className="text-lg font-semibold">安全门禁</h2><p className="text-xs text-slate-500">开发预览中的实际控制能力</p></div></div>
                  <div className="divide-y divide-slate-100">
                    {[
                      ["原生 Agent 安装与升级", "未开放"],
                      ["systemd 启动与开机自启", "未开放"],
                      ["生产联网与设备采集", "默认关闭"],
                      ["远程控制与屏幕采集", "默认关闭"],
                      ["生产证书签发与管理员 MFA", "尚未批准"],
                      ["图形化查看状态", inspection === "loaded" ? "只读本机状态" : "需本机运行"],
                      ["Pop!_OS/COSMIC 兼容性", popos.status === "checked" ? "只读元数据预检（非 L3）" : popos.status === "blocked" ? "检查受阻 · 未验收" : "尚未核验"],
                    ].map(([name, state]) => (
                      <div key={name} className="flex items-center justify-between gap-4 py-4"><div className="flex items-center gap-2 text-sm text-slate-700"><ShieldCheck size={16} className="shrink-0 text-slate-400" />{name}</div><span className="text-xs font-semibold text-slate-500">{state}</span></div>
                    ))}
                  </div>
                  <div className="mt-5 flex items-start gap-3 rounded-xl border border-slate-200 bg-slate-50 p-4"><CircleHelp size={19} className="mt-0.5 shrink-0 text-slate-500" /><p className="text-xs leading-6 text-slate-600">如需查看真实安装、安全属性和卸载入口，应等待正式签名安装器和授权桥接完成。当前仅有只读检测能力，所有数据与标识均来自本机直接读取或明确显示为“未核验”。</p></div>
                </div>
                <OfflineDiagnosticsPanel host={host} inspection={inspection} popos={popos} />
              </>
            )}
            <footer className="mt-9 flex flex-wrap items-center justify-between gap-2 border-t border-slate-200 pt-6 text-xs text-slate-400"><span>Lanveriq Desktop · 只读离线预览</span><span>v0.1 NOT RELEASE READY</span></footer>
          </div>
        </div>
      </main>
    </div>
  );
}

function Feature({ icon, label, value }: { icon: ReactNode; label: string; value: string }) {
  return <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4"><span className="text-sky-700">{icon}</span><div className="mt-4 text-sm font-semibold">{label}</div><div className="mt-1.5 text-xs text-slate-500">{value}</div></div>;
}

function DataItem({ title, value }: { title: string; value: string }) {
  return <div className="rounded-xl border border-slate-200 bg-white px-4 py-4"><div className="text-[11px] font-medium text-slate-400">{title}</div><div className="mt-2 text-sm font-semibold text-slate-800">{value}</div></div>;
}
