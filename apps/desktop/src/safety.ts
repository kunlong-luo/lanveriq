export type HostSnapshot = {
  osName: string;
  architecture: string;
  targetSupported: boolean;
  packageState: "preview_candidate" | "not_installed" | "unrecognized_version" | "unverified";
  serviceState: "active" | "inactive" | "failed" | "unverified";
  unitFileState: "enabled" | "disabled" | "masked" | "unverified";
  overrideState: "none_reported" | "present_unreviewed" | "unverified";
  managerReloadState: "reload_required" | "not_required" | "unverified";
  installationAllowed: boolean;
  runtimeActivationAllowed: boolean;
};

export type Readiness =
  | { kind: "unchecked"; label: string }
  | { kind: "unsupported"; label: string }
  | { kind: "unverified"; label: string }
  | { kind: "candidate"; label: string };

export const RELEASE_GATE = Object.freeze({
  installEnabled: false,
  serviceStartEnabled: false,
  bootEnableEnabled: false,
  collectionEnabled: false,
  networkEnabled: false,
  remoteControlEnabled: false,
  certificateIssuanceEnabled: false,
});

export function describeReadiness(host: HostSnapshot | null): Readiness {
  if (!host) return { kind: "unchecked", label: "尚未读取真实主机状态" };
  if (!host.targetSupported) {
    return { kind: "unsupported", label: "尚未核验 Pop!_OS 24.04 x86_64 兼容性" };
  }
  if (host.packageState === "preview_candidate") {
    return { kind: "candidate", label: "检测到未批准的离线候选包 · 不代表验收通过" };
  }
  return { kind: "unverified", label: "正式 Agent 未验收或未安装" };
}

export function nativeDetails(host: HostSnapshot | null) {
  if (!host) return [
    ["操作系统", "未核验"],
    ["设备架构", "未核验"],
    ["Agent 包", "未核验"],
    ["后台服务", "未核验"],
    ["开机自启", "未核验"],
    ["覆盖配置", "未核验"],
  ] as const;

  const service = {
    active: "运行中（未授权候选，不等于通过）",
    inactive: "已停止",
    failed: "发生故障",
    unverified: "无法核验",
  }[host.serviceState];
  const unit = {
    enabled: "已启用（需要独立审批）",
    disabled: "未启用",
    masked: "已屏蔽",
    unverified: "无法核验",
  }[host.unitFileState];
  const packageValue = {
    preview_candidate: "发现仅用于测试的预览包",
    not_installed: "未安装",
    unrecognized_version: "检测到未知版本",
    unverified: "无法核验",
  }[host.packageState];
  const overrides = {
    none_reported: "未报告覆盖配置（非安全背书）",
    present_unreviewed: "存在未审核覆盖配置",
    unverified: "无法核验",
  }[host.overrideState];
  return [
    ["操作系统", host.osName],
    ["设备架构", host.architecture],
    ["Agent 包", packageValue],
    ["后台服务", service],
    ["开机自启", unit],
    ["覆盖配置", overrides],
  ] as const;
}
