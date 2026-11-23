export type HostSnapshot = {
  osName: string;
  architecture: string;
  targetSupported: boolean;
  packageState: "preview_candidate" | "not_installed" | "unrecognized_version" | "unverified";
  serviceState: "active" | "inactive" | "failed" | "unverified";
  unitFileState: "enabled" | "disabled" | "masked" | "unverified";
  overrideState: "none_reported" | "present_unreviewed" | "unverified";
  managerReloadState: "reload_required" | "not_required" | "unverified";
  legacyBinaryState: "present_unreviewed" | "not_detected" | "unverified";
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
  // Refuse a reassuring summary when old manually installed software,
  // active/enabled units or unreviewed manager overrides are present.
  if (host.legacyBinaryState === "present_unreviewed") {
    return { kind: "unverified", label: "发现旧版 /usr/local 安装文件 · 需要人工迁移审查" };
  }
  if (host.serviceState === "active" || host.unitFileState === "enabled") {
    return { kind: "unverified", label: "检测到正在运行或已开机启用的服务 · 未获授权" };
  }
  if (host.overrideState === "present_unreviewed" || host.managerReloadState === "reload_required") {
    return { kind: "unverified", label: "服务存在未审核覆盖配置或缓存尚未更新" };
  }
  if (host.legacyBinaryState === "unverified") {
    return { kind: "unverified", label: "旧版安装位置无法核验 · 不可视为安全" };
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
    ["旧版安装位置", "未核验"],
    ["systemd 缓存", "未核验"],
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
    ["旧版安装位置", { present_unreviewed: "发现旧版文件，需要人工迁移审查", not_detected: "未发现（不代表完整性通过）", unverified: "无法核验" }[host.legacyBinaryState]],
    ["systemd 缓存", { reload_required: "需要重新加载（仅提醒，不会自动操作）", not_required: "无需重新加载（状态仅供参考）", unverified: "无法核验" }[host.managerReloadState]],
  ] as const;
}
