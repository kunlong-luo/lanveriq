import { useState } from "react";
import { ClipboardCheck, LockKeyhole } from "lucide-react";
import { buildOfflineIdleConfig, IDLE_INTERVAL_OPTIONS, type IdleInterval } from "./idle-config";

export function IdleConfigPanel() {
  const [interval, setInterval] = useState<IdleInterval>(30);
  const preview = buildOfflineIdleConfig(interval);

  return (
    <section aria-label="离线 Agent 空闲配置预览"
      className="mb-6 rounded-2xl border border-slate-200 bg-white p-5">
      <div className="flex items-start gap-3">
        <div className="section-icon"><ClipboardCheck size={20} /></div>
        <div>
          <h3 className="text-sm font-semibold text-slate-900">可视化准备空闲 Agent 配置</h3>
          <p className="mt-2 text-xs leading-6 text-slate-500">
            与 Rust Agent 配置 v1 完全相同的字段：只包含周期，不包含证书、服务端地址、
            设备身份、采集或联网开关。此处仅生成预览，不会写入文件或更改后台服务。
          </p>
        </div>
      </div>
      <fieldset className="mt-5">
        <legend className="text-xs font-semibold text-slate-700">选择空闲运行周期（不授权启动）</legend>
        <div className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-5">
          {IDLE_INTERVAL_OPTIONS.map(option => (
            <label key={option.seconds} className="cursor-pointer">
              <input type="radio" name="lanveriq-idle-interval" className="peer sr-only"
                value={option.seconds} checked={interval === option.seconds}
                onChange={() => setInterval(option.seconds)} />
              <span className="block rounded-xl border border-slate-200 bg-slate-50 px-2 py-3 text-center text-xs font-semibold text-slate-600 transition hover:border-slate-300 peer-checked:border-sky-500 peer-checked:bg-sky-50 peer-checked:text-sky-800 peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-sky-600">
                {option.label}
              </span>
            </label>
          ))}
        </div>
      </fieldset>
      <label htmlFor="lanveriq-idle-config-preview"
        className="mt-5 block text-xs font-semibold text-slate-700">
        本机配置 TOML 草稿 · 点击后选择全部文本
      </label>
      <textarea id="lanveriq-idle-config-preview"
        aria-label="只读离线 Agent 配置草稿"
        className="mt-2 block min-h-32 w-full resize-y rounded-xl border border-slate-200 bg-slate-50 p-4 font-mono text-xs leading-6 text-slate-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-600"
        value={preview} readOnly spellCheck={false}
        onFocus={event => event.currentTarget.select()} />
      <p className="mt-3 flex items-start gap-2 text-xs leading-6 text-amber-800">
        <LockKeyhole size={16} className="mt-1 shrink-0" />
        仅为非机密配置草稿。未签名、未保存、未下载，也不是 Polkit 授权。
        正式配置须由通过发行身份核验的受限安装服务写入；
        Agent 安装、开机自启、联网、采集及远控仍全部关闭。
      </p>
    </section>
  );
}
