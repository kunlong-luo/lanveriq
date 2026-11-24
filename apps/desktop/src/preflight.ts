/** Fail-closed view contract for the untrusted local Pop!_OS metadata probe.
 * This report NEVER grants installer, signing, L3, or Agent-start rights.
 * No browser demo data is ever synthesized to satisfy this contract.
 */
export const ABI_PACKAGE_NAMES = [
  "libc6", "libcairo2", "libdbus-1-3", "libgcc-s1",
  "libgdk-pixbuf-2.0-0", "libglib2.0-0t64", "libgtk-3-0t64",
  "libjavascriptcoregtk-4.1-0", "libsoup-3.0-0", "libwebkit2gtk-4.1-0",
] as const;
export const COSMIC_PACKAGE_NAMES = [
  "cosmic-session", "cosmic-comp", "xdg-desktop-portal-cosmic",
] as const;

export type CandidatePackage = {
  package: string;
  minimum: string | null;
  cached_candidate: string;
  installed: boolean;
};
export type CosmicPackage = {
  package: string;
  installed_version: string;
};
export type PoposMetadata = {
  schema: "lanveriq.desktop.untrusted-popos-local-preflight.v1";
  host_os: { id: "pop"; version: "24.04" };
  architecture: "amd64";
  session: {
    environment: "COSMIC_WAYLAND_CLAIM_ONLY";
    compositor_running_proven: false;
    display_socket_saved: false;
  };
  reviewed_abi_cache: CandidatePackage[];
  cosmic_components: CosmicPackage[];
  apt_sources: {
    file_count: number;
    contents_sha256: string;
    apt_repository_signature_trust: "NOT_VERIFIED";
  };
  source_config_checked_on_host: false;
  apt_cache_updated: false;
  popos_install_solver: "NOT_RUN";
  physical_l3: "NOT_APPROVED";
  gui_actual_launch: "NOT_TESTED";
  signed_package_provenance: "NOT_VERIFIED";
  license_approval: "OPEN";
  release_approved: false;
  install_authorized: false;
  agent_start_authorized: false;
};

export type PoposState = "idle" | "reading" | "blocked" | "checked" | "web-preview";
export type PoposDisplay = {
  status: PoposState;
  code: string;
  data: PoposMetadata | null;
};

const safeVersion = (x: unknown): x is string =>
  typeof x === "string" && /^[A-Za-z0-9.+:~_-]{1,180}$/.test(x);
const object = (x: unknown): x is Record<string, unknown> =>
  !!x && typeof x === "object" && !Array.isArray(x);

export function parseLocalPreflight(raw: string): PoposMetadata {
  if (typeof raw !== "string" || raw.length > 16384 || raw.length < 64) {
    throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
  }
  let result: unknown;
  try {
    result = JSON.parse(raw);
  } catch {
    throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
  }
  if (!object(result) || !object(result.host_os) ||
      result.schema !== "lanveriq.desktop.untrusted-popos-local-preflight.v1" ||
      result.host_os.id !== "pop" || result.host_os.version !== "24.04" ||
      result.architecture !== "amd64" || !object(result.session) ||
      result.session.environment !== "COSMIC_WAYLAND_CLAIM_ONLY" ||
      result.session.compositor_running_proven !== false ||
      result.session.display_socket_saved !== false ||
      result.apt_cache_updated !== false ||
      result.source_config_checked_on_host !== false ||
      result.popos_install_solver !== "NOT_RUN" ||
      result.physical_l3 !== "NOT_APPROVED" ||
      result.gui_actual_launch !== "NOT_TESTED" ||
      result.signed_package_provenance !== "NOT_VERIFIED" ||
      result.license_approval !== "OPEN" ||
      result.release_approved !== false ||
      result.install_authorized !== false ||
      result.agent_start_authorized !== false ||
      !object(result.apt_sources) ||
      result.apt_sources.apt_repository_signature_trust !== "NOT_VERIFIED" ||
      typeof result.apt_sources.file_count !== "number" ||
      !Number.isInteger(result.apt_sources.file_count) ||
      !(Number(result.apt_sources.file_count) > 0 && Number(result.apt_sources.file_count) <= 64) ||
      typeof result.apt_sources.contents_sha256 !== "string" ||
      !/^[0-9a-f]{64}$/.test(result.apt_sources.contents_sha256)) {
    throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
  }
  if (!Array.isArray(result.reviewed_abi_cache) ||
      result.reviewed_abi_cache.length !== ABI_PACKAGE_NAMES.length ||
      !Array.isArray(result.cosmic_components) ||
      result.cosmic_components.length !== COSMIC_PACKAGE_NAMES.length) {
    throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
  }
  for (const [index, expected] of ABI_PACKAGE_NAMES.entries()) {
    const row: unknown = result.reviewed_abi_cache[index];
    if (!object(row) || row.package !== expected ||
        !(row.minimum === null || safeVersion(row.minimum)) ||
        !safeVersion(row.cached_candidate) ||
        typeof row.installed !== "boolean") {
      throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
    }
  }
  for (const [index, expected] of COSMIC_PACKAGE_NAMES.entries()) {
    const row: unknown = result.cosmic_components[index];
    if (!object(row) || row.package !== expected || !safeVersion(row.installed_version)) {
      throw new Error("UNTRUSTED_PREFLIGHT_RESULT");
    }
  }
  return result as PoposMetadata;
}

const BLOCKERS: Record<string, string> = {
  NOT_POP_OS_TARGET: "当前不是经过要求的 Pop!_OS 24.04 系统。",
  PRIVILEGED_GUI_PREFLIGHT_FORBIDDEN: "预检禁止以 root 身份运行，请使用普通桌面用户。",
  UNSUPPORTED_POP_OS_ARCH: "当前设备架构不在 amd64 验收范围内。",
  NOT_COSMIC_WAYLAND_SESSION: "未检测到符合要求的 COSMIC / Wayland 会话环境。",
  SYSTEM_PYTHON_APT_UNAVAILABLE: "系统缺少发行版自带的 python3-apt 只读查询能力。",
  DPKG_ARCH_UNAVAILABLE: "系统软件包架构暂时无法核验。",
  COSMIC_COMPONENT_NOT_INSTALLED: "COSMIC 桌面必需组件缺失。",
  NO_LOCAL_ABI_APT_CANDIDATE: "本机缓存中缺少必要的系统库候选软件包。",
  ABI_CANDIDATE_VERSION_TOO_OLD: "本机缓存的软件包版本低于要求。",
  MISSING_OR_EXCESS_APT_SOURCES: "APT 软件源配置不存在或超出受控范围。",
  UNTRUSTED_APT_SOURCE_PATH: "APT 软件源配置包含未审核的符号链接。",
  EXCESS_APT_SOURCES_BYTES: "APT 软件源配置异常或超出大小限制。",
  INVALID_APT_SOURCES_DIRECTORY: "APT 软件源目录无法安全读取。",
  REVIEWED_ABI_BASELINE_DRIFT: "桌面程序的 ABI 安全基线与当前审核不一致。",
  INVALID_APT_RELATION: "系统库版本表达式无法通过官方解析器验证。",
  UNSAFE_APT_PACKAGE_VERSION: "软件包版本元数据异常。",
  PREFLIGHT_UNAVAILABLE: "只读兼容性检查无法完成，保持未经核验。",
};
export function blockerMessage(value: unknown): string {
  if (typeof value !== "string") return BLOCKERS.PREFLIGHT_UNAVAILABLE;
  return BLOCKERS[value] ?? BLOCKERS.PREFLIGHT_UNAVAILABLE;
}

export function poposSummary(view: PoposDisplay): string {
  if (view.status === "checked" && view.data) {
    return "本机兼容性元数据已读取；仅为预检，不代表可安装、已签名或 L3 验收。";
  }
  if (view.status === "reading") return "正在只读核对 COSMIC 会话和本机软件包缓存…";
  if (view.status === "blocked") return blockerMessage(view.code);
  if (view.status === "web-preview") return "浏览器预览无法检查本机，未获得任何 Pop!_OS 兼容性证据。";
  return "尚未执行 Pop!_OS/COSMIC 只读预检。";
}
