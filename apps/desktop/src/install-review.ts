import type { PoposDisplay } from "./preflight";
import type { HostSnapshot } from "./safety";

/**
 * L2B-21 unprivileged install-review view model.
 *
 * Neither a browser checkbox nor a forged Tauri HostSnapshot is a Polkit
 * authorization, signed release, valid source or approved Linux host.
 * This function is for displaying precisely what evidence is MISSING;
 * it cannot produce an install transaction, command line, URL or privilege.
 */
export type ReviewGate = Readonly<{
  id: "host" | "package-state" | "service-state" | "compatibility" |
      "publisher" | "broker" | "real-host-l3";
  title: string;
  status: "not_checked" | "blocked" | "metadata_only" | "not_approved";
  detail: string;
}>;

export type ReviewConsent = Readonly<{
  understoodPackageSource: boolean;
  understoodAdminAuthorization: boolean;
  understoodSeparateServiceStart: boolean;
  understoodDataPrivacy: boolean;
}>;

export const EMPTY_REVIEW_CONSENT: ReviewConsent = Object.freeze({
  understoodPackageSource: false,
  understoodAdminAuthorization: false,
  understoodSeparateServiceStart: false,
  understoodDataPrivacy: false,
});

export const REVIEW_CONSENT_FIELDS = [
  "understoodPackageSource", "understoodAdminAuthorization",
  "understoodSeparateServiceStart", "understoodDataPrivacy",
] as const;

export function reviewOnlyInstallPlan(
  host: HostSnapshot | null,
  popos: PoposDisplay,
  consent: ReviewConsent,
): Readonly<{
  gates: readonly ReviewGate[];
  noticeReviewed: boolean;
  installAllowed: false;
  updateAllowed: false;
  removeAllowed: false;
  startAllowed: false;
  bootEnabled: false;
  collectionAllowed: false;
  networkAllowed: false;
  privilegedBrokerAvailable: false;
}> {
  const stableHost = host?.targetSupported === true &&
    host.architecture === "x86_64";
  const packageSafe = host?.packageState === "not_installed";
  const serviceSafe = host?.serviceState === "inactive" &&
    host.unitFileState === "disabled" &&
    host.overrideState === "none_reported" &&
    host.managerReloadState === "not_required" &&
    host.legacyBinaryState === "not_detected";

  const gates: ReviewGate[] = [
    {
      id: "host", title: "系统与架构",
      status: !host ? "not_checked" : stableHost ? "metadata_only" : "blocked",
      detail: stableHost
        ? "仅核对 Pop!_OS 24.04 / x86_64 系统标识，非实机安全批准"
        : "必须在受信任的 Pop!_OS 24.04 设备上完成独立核验",
    },
    {
      id: "package-state", title: "既有 Agent 包",
      status: !host ? "not_checked" : packageSafe ? "metadata_only" : "blocked",
      detail: packageSafe
        ? "系统报告目标离线候选包未安装；不等于可进行新安装"
        : "已有包、未知版本或检测失败时不得覆盖安装",
    },
    {
      id: "service-state", title: "后台服务与旧版文件",
      status: !host ? "not_checked" : serviceSafe ? "metadata_only" : "blocked",
      detail: serviceSafe
        ? "只读查询未发现启用、运行或未审核覆盖；不代表授权"
        : "服务、旧版文件、覆盖或 systemd 状态不明时禁止修改",
    },
    {
      id: "compatibility", title: "COSMIC 与系统库",
      status: popos.status === "checked" && popos.data
        ? "metadata_only" : popos.status === "idle" || popos.status === "reading"
          ? "not_checked" : "blocked",
      detail: popos.status === "checked" && popos.data
        ? "已获只读元数据，仍未执行安装求解及桌面实机验收"
        : "无可信的 COSMIC/ABI 元数据，不允许预设为兼容",
    },
    {
      id: "publisher", title: "可信软件源与正式签名",
      status: "not_approved",
      detail: "未独立预置信任根、发行身份、撤销和 Signed-By 正式仓库",
    },
    {
      id: "broker", title: "逐次管理员授权与受限 Polkit",
      status: "not_approved",
      detail: "未提供受审 broker；GUI 不得获得 root 或调用任意命令",
    },
    {
      id: "real-host-l3", title: "Pop!_OS COSMIC 实机与法务批准",
      status: "not_approved",
      detail: "无正式的设备所有人同意、Pop!_OS 实机 L3 与法务/运维签署",
    },
  ];

  // Acknowledging a NOTICE is not consenting to installation, service
  // start, autostart, collection, network, removal or release rights.
  // This type deliberately offers no success outcome or executable plan.
  const noticeReviewed = REVIEW_CONSENT_FIELDS.every(key => consent[key] === true);
  return {
    gates, noticeReviewed,
    installAllowed: false,
    updateAllowed: false,
    removeAllowed: false,
    startAllowed: false,
    bootEnabled: false,
    collectionAllowed: false,
    networkAllowed: false,
    privilegedBrokerAvailable: false,
  };
}
