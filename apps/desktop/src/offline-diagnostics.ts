import type { HostSnapshot } from "./safety";
import type { PoposDisplay } from "./preflight";

// A local operator-readable diagnostic summary, NOT an authority token or an
// evidence bundle. Do not serialize raw osName, package versions, apt sources,
// errors, file paths, credentials, machine IDs, user IDs, or hostnames.
type Inspection = "idle" | "reading" | "loaded" | "error" | "web-preview";

const PACKAGE: Record<HostSnapshot["packageState"], string> = {
  preview_candidate: "unapproved_preview_candidate",
  not_installed: "not_installed",
  unrecognized_version: "unknown_version",
  unverified: "unverified",
};
const SERVICE: Record<HostSnapshot["serviceState"], string> = {
  active: "running_unapproved",
  inactive: "inactive",
  failed: "failed",
  unverified: "unverified",
};
const UNIT: Record<HostSnapshot["unitFileState"], string> = {
  enabled: "enabled_unapproved",
  disabled: "disabled",
  masked: "masked",
  unverified: "unverified",
};
const OVERRIDE: Record<HostSnapshot["overrideState"], string> = {
  none_reported: "none_reported_not_verified",
  present_unreviewed: "present_unreviewed",
  unverified: "unverified",
};
const RELOAD: Record<HostSnapshot["managerReloadState"], string> = {
  reload_required: "reload_required",
  not_required: "not_required",
  unverified: "unverified",
};
const LEGACY: Record<HostSnapshot["legacyBinaryState"], string> = {
  present_unreviewed: "present_unreviewed",
  not_detected: "not_detected_not_proven",
  unverified: "unverified",
};
function listed<T extends string>(mapping: Record<T, string>, value: T): string {
  return Object.hasOwn(mapping, value) ? mapping[value] : "unverified";
}
function hostValue<K extends keyof HostSnapshot>(
  loaded: boolean, host: HostSnapshot | null,
  key: K, options: Record<string, string>,
): string {
  if (!loaded || !host) return "unverified";
  const value = host[key];
  return typeof value === "string" && Object.hasOwn(options, value)
    ? options[value] : "unverified";
}

export function createOfflineDiagnostics(
  host: HostSnapshot | null, inspection: Inspection, popos: PoposDisplay,
): string {
  const loaded = inspection === "loaded" && host !== null;
  const checkSource = loaded ? "native_readonly" :
    inspection === "web-preview" ? "web_preview_no_host_access" :
      inspection === "error" ? "native_inspection_failed" : "not_verified";
  const platform = loaded
    ? host.targetSupported === true ? "popos_24_04_x86_64_metadata_only" : "unverified_or_unsupported"
    : "unverified";
  const preflight = popos.status === "checked" && popos.data !== null
    ? "metadata_checked_not_l3"
    : popos.status === "blocked" ? "blocked_not_verified"
      : popos.status === "web-preview" ? "web_preview_no_host_access" : "not_verified";

  return [
    "LANVERIQ DESKTOP - LOCAL REDACTED DIAGNOSTIC v1",
    "Local text only; NOT a signed report, installer authorization, or physical L3 evidence.",
    "This window does not upload, save or send the report.",
    `inspection_source=${checkSource}`,
    `os_target=${platform}`,
    `agent_package=${hostValue(loaded, host, "packageState", PACKAGE)}`,
    `systemd_service=${hostValue(loaded, host, "serviceState", SERVICE)}`,
    `systemd_unit=${hostValue(loaded, host, "unitFileState", UNIT)}`,
    `systemd_dropins=${hostValue(loaded, host, "overrideState", OVERRIDE)}`,
    `systemd_reload=${hostValue(loaded, host, "managerReloadState", RELOAD)}`,
    `legacy_binary=${hostValue(loaded, host, "legacyBinaryState", LEGACY)}`,
    `cosmic_preflight=${preflight}`,
    "official_signed_agent_package=NOT_APPROVED",
    "independent_publisher_root_and_highwater=NOT_VERIFIED",
    "reviewed_polkit_installer_broker=NOT_IMPLEMENTED",
    "popos_physical_l3=NOT_RUN",
    "production_mfa_ca_and_worm=NOT_APPROVED",
    "legal_and_sre_signoff=NOT_RUN",
    "install_authorized=false",
    "agent_start_authorized=false",
    "production_network_collection_recovery_remote=OFF",
    "release_gates_closed=0/10",
    "release_status=NOT_RELEASE_READY",
    "Manual inspection of real host/session evidence and independent approvals still required.",
  ].join("\n") + "\n";
}
