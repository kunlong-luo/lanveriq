import { useEffect, useState } from "react";
import type { HostSnapshot } from "./safety";
import type { PoposDisplay } from "./preflight";
import {
  EMPTY_REVIEW_CONSENT, REVIEW_CONSENT_FIELDS, reviewOnlyInstallPlan,
  type ReviewConsent,
} from "./install-review";

const explanations: Record<keyof ReviewConsent, string> = {
  understoodPackageSource: "Agent 必须来自独立批准的签名仓库，不能选择任意本地 .deb 或网址。",
  understoodAdminAuthorization: "安装、升级、卸载分别需要系统独立验证的管理员身份与逐次授权。",
  understoodSeparateServiceStart: "安装、后台服务启动、开机自启需分别选择与批准，不能捆绑。",
  understoodDataPrivacy: "联网、系统信息采集、自动恢复和远控保持关闭，安装不产生这些权限。",
};

export function InstallReviewPanel({
  host, popos,
}: Readonly<{ host: HostSnapshot | null; popos: PoposDisplay }>) {
  // Only local component state, never stored, signed, submitted to a broker
  // or interpreted as authorization. Unmount or changing host evidence resets.
  const [acknowledged, setAcknowledged] = useState<ReviewConsent>({
    ...EMPTY_REVIEW_CONSENT,
  });
  useEffect(() => {
    setAcknowledged({ ...EMPTY_REVIEW_CONSENT });
  }, [host, popos]);
  const plan = reviewOnlyInstallPlan(host, popos, acknowledged);

  return (
    <div className="space-y-5">
      <div className="rounded-2xl border border-slate-200 bg-slate-50 p-5">
        <p className="flex items-center gap-2 text-sm font-semibold text-slate-900">安装操作已锁定 · 独立安全审核</p>
        <p className="mt-2 text-xs leading-6 text-slate-600">这是仅供查看的实际检查状态，不是安装请求、签名发行证明或 Polkit 授权。后台服务和开机自启都不会开启。</p>
      </div>
      <section aria-label="Agent 安装必要条件" className="divide-y divide-slate-100 rounded-2xl border border-slate-200 bg-white px-4">
        {plan.gates.map(item => (
          <div key={item.id} className="flex flex-col gap-1 py-3 sm:flex-row sm:items-start sm:justify-between sm:gap-4">
            <div>
              <h3 className="text-sm font-semibold text-slate-800">{item.title}</h3>
              <p className="mt-1 text-xs leading-6 text-slate-500">{item.detail}</p>
            </div>
            <span className="shrink-0 text-xs font-semibold text-amber-800">
              {item.status === "metadata_only" ? "仅有元数据" :
                item.status === "not_checked" ? "尚未核验" :
                  item.status === "blocked" ? "安全阻断" : "独立审批未完成"}
            </span>
          </div>
        ))}
      </section>
      <fieldset className="space-y-3 rounded-2xl border border-slate-200 bg-slate-50 p-4">
        <legend className="px-1 text-sm font-semibold text-slate-800">请分别阅读权限与安装限制（非授权）</legend>
        {REVIEW_CONSENT_FIELDS.map(field => (
          <label key={field} className="flex cursor-pointer items-start gap-3 text-xs leading-6 text-slate-700">
            <input type="checkbox" className="mt-1.5 size-4 shrink-0 accent-sky-700"
              checked={acknowledged[field]}
              onChange={event => setAcknowledged(previous => ({
                ...previous, [field]: event.target.checked,
              }))} />
            <span>{explanations[field]}</span>
          </label>
        ))}
        <p role="status" aria-live="polite" className="text-xs leading-6 text-amber-800">
          {plan.noticeReviewed
            ? "全部说明已阅；但真实发行、Polkit、L3 门禁仍未批准，无法安装或启动 Agent。"
            : "阅读说明不会启动 Agent，也不会向系统索取管理员权限。"}
        </p>
      </fieldset>
    </div>
  );
}
