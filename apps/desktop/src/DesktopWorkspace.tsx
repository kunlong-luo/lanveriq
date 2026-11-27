import { ArrowRight, CircleHelp, ClipboardCheck, Laptop2, LayoutDashboard, LockKeyhole, MonitorCog, RefreshCw, Settings2, ShieldCheck, Unplug } from "lucide-react";
import { describeReadiness, nativeDetails, type HostSnapshot } from "./safety";

export type Section = "overview" | "setup" | "config" | "enrollment" | "status" | "safety" | "about";
export const sectionLabels: Record<Section, string> = {
  overview: "概览", setup: "安装向导", config: "服务器与配置", enrollment: "设备注册",
  status: "Agent 服务", safety: "安全与诊断", about: "关于",
};

const navigation = [
  ["overview", LayoutDashboard], ["setup", MonitorCog], ["config", Settings2],
  ["enrollment", ClipboardCheck], ["status", Laptop2], ["safety", ShieldCheck], ["about", CircleHelp],
] as const;

export function DesktopNavigation({ section, onNavigate }: { section: Section; onNavigate: (section: Section) => void }) {
  return <nav aria-label="桌面应用导航" className="workspace-nav">
    <p className="workspace-nav-label">工作空间</p>
    {navigation.map(([name, Icon]) => <button key={name} type="button"
      aria-current={section === name ? "page" : undefined} aria-label={sectionLabels[name]}
      className={"nav-item " + (section === name ? "nav-item-active" : "")}
      onClick={() => onNavigate(name)}>
      <Icon size={18} strokeWidth={1.8} aria-hidden="true" /><span>{sectionLabels[name]}</span>
    </button>)}
  </nav>;
}

export function DesktopOverview({ host, inspection, onNavigate, onInspect }: {
  host: HostSnapshot | null; inspection: string; onNavigate: (section: Section) => void; onInspect: () => void;
}) {
  // Reject stale or synthetic display values whenever the last read is not loaded.
  const checkedHost = inspection === "loaded" ? host : null;
  const details = nativeDetails(checkedHost);
  const status = describeReadiness(checkedHost);
  const cards = [
    ["本机操作系统", details[0][1], "仅只读检查"],
    ["Agent 后台服务", details[3][1], "不代表授权或在线"],
    ["服务器连接", "尚未检测", "联网未开放"],
    ["设备注册", "尚未核验", "签发未开放"],
  ];
  return <>
    <div className="workspace-intro">
      <p className="workspace-kicker">LANVERIQ DESKTOP / LOCAL</p>
      <h1>设备概览</h1>
      <p>在本机准备安装、配置与诊断。当前为离线开发预览，不执行安装、注册、联网或后台服务操作。</p>
    </div>
    <section className="workspace-hero" aria-label="当前准备状态">
      <div className="workspace-hero-copy">
        <span className="workspace-lock"><LockKeyhole size={14} aria-hidden="true" /> 安全锁定</span>
        <h2>从本机准备开始</h2>
        <p role="status">{status.label}。{inspection === "web-preview" ? "浏览器预览无法读取本机状态。" : inspection === "reading" ? "正在只读检测。" : inspection === "error" ? "检测失败，未保留旧状态。" : "正式安装尚未批准。"}</p>
        <button type="button" className="workspace-primary" onClick={() => onNavigate("setup")}>打开安装向导 <ArrowRight size={16} aria-hidden="true" /></button>
      </div>
      <div className="workspace-hero-art" aria-hidden="true"><Laptop2 size={76} strokeWidth={1.2} /><ShieldCheck size={30} /></div>
    </section>
    <section className="workspace-metrics" aria-label="本机状态摘要">
      {cards.map(([label, value, note]) => <article className="workspace-metric" key={label}><p>{label}</p><strong>{value}</strong><small>{note}</small></article>)}
    </section>
    <section className="surface-card workspace-actions" aria-label="本机操作入口">
      <div><h2 className="text-lg font-semibold">下一步</h2><p className="mt-1 text-sm text-slate-500">只有导航和原有只读检测可用。</p></div>
      <button type="button" className="workspace-secondary" disabled={inspection === "reading"} onClick={onInspect}><RefreshCw size={16} aria-hidden="true" /> 重新检测（只读）</button>
      <button type="button" className="workspace-secondary" onClick={() => onNavigate("config")}><Settings2 size={16} aria-hidden="true" /> 查看配置草稿</button>
      <button type="button" className="workspace-secondary" onClick={() => onNavigate("safety")}><ShieldCheck size={16} aria-hidden="true" /> 安全与诊断</button>
    </section>
  </>;
}

const planned = {
  config: { heading: "服务器连接 · 尚未开放", description: "当前只能预览非机密的空闲周期配置；服务器地址、CA 配置和连接检测待单独验收。", actions: ["连接检测（尚未开放）", "保存配置（尚未开放）"] },
  enrollment: { heading: "设备注册 · 尚未开放", description: "未来需逐次明确授权、受信服务器及证书生命周期验证。当前不接收任何凭据或注册材料。", actions: ["注册设备（尚未开放）", "导入凭据（尚未开放）"] },
  about: { heading: "Lanveriq Desktop · v0.1 开发预览", description: "Tauri 2 / React / Rust。独立 Agent 与唯一 Next.js Web 管理后台不依赖本窗口。当前没有获批签名发行件、安装授权或生产许可。", actions: [] },
} as const;

export function PlannedSection({ kind }: { kind: keyof typeof planned }) {
  const item = planned[kind];
  return <section className="surface-card mb-6" aria-label={item.heading}>
    <div className="flex items-start gap-3"><div className="section-icon"><Unplug size={22} aria-hidden="true" /></div>
      <div><h2 className="text-lg font-semibold">{item.heading}</h2><p className="mt-2 text-sm leading-7 text-slate-600">{item.description}</p></div>
    </div>
    {item.actions.length > 0 && <div className="mt-5 flex flex-wrap gap-2">{item.actions.map(label => <button type="button" disabled key={label} className="workspace-secondary"><LockKeyhole size={15} aria-hidden="true" />{label}</button>)}</div>}
  </section>;
}
