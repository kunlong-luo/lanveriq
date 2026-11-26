import { ClipboardCheck, ShieldOff } from "lucide-react";
import type { HostSnapshot } from "./safety";
import type { PoposDisplay } from "./preflight";
import { createOfflineDiagnostics } from "./offline-diagnostics";

type Inspection = "idle" | "reading" | "loaded" | "error" | "web-preview";

export function OfflineDiagnosticsPanel({
  host, inspection, popos,
}: Readonly<{ host: HostSnapshot | null; inspection: Inspection; popos: PoposDisplay }>) {
  const report = createOfflineDiagnostics(host, inspection, popos);
  return (
    <section aria-label="本机脱敏诊断报告" className="mt-6 surface-card">
      <div className="flex flex-wrap items-start gap-3">
        <div className="section-icon"><ClipboardCheck size={20} /></div>
        <div className="min-w-0 flex-1">
          <h2 className="text-lg font-semibold text-slate-900">本机诊断与安装阻塞报告</h2>
          <p className="mt-2 text-xs leading-6 text-slate-500">
            将已读取的本机状态转成不含用户名、路径、软件源、凭据及设备标识的短报告。
            内容只在本窗口生成，默认不保存、不联网、不上传。
          </p>
        </div>
      </div>
      <label htmlFor="lanveriq-offline-diagnostics" className="mt-5 block text-xs font-semibold text-slate-700">
        只读诊断文本 · 选中后手动复制
      </label>
      <textarea
        id="lanveriq-offline-diagnostics"
        aria-label="脱敏诊断文本（非安装授权）"
        className="mt-2 block min-h-64 w-full resize-y rounded-xl border border-slate-200 bg-slate-50 p-4 font-mono text-xs leading-6 text-slate-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
        value={report}
        readOnly
        spellCheck={false}
        onFocus={event => event.currentTarget.select()}
      />
      <div className="mt-4 flex items-start gap-2 rounded-xl border border-amber-200 bg-amber-50 p-3 text-xs leading-6 text-amber-900">
        <ShieldOff className="mt-1 shrink-0" size={16} />
        <span>该文本未签名、未经独立审核；不代表发行包真实可信、Pop!_OS 实机 L3 或批准安装。
          向第三方分享前请自行确认文本内容。安装、服务启动、远控、采集、联网始终不可用。</span>
      </div>
    </section>
  );
}
