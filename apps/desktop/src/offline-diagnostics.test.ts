import { describe, expect, it } from "vitest";
import { createOfflineDiagnostics } from "./offline-diagnostics";
import type { HostSnapshot } from "./safety";
import type { PoposDisplay } from "./preflight";

const host: HostSnapshot = {
  osName: "Pop!_OS 24.04 /home/private-user TOKEN=do-not-leak",
  architecture: "x86_64",
  targetSupported: true,
  packageState: "preview_candidate",
  serviceState: "active",
  unitFileState: "enabled",
  overrideState: "present_unreviewed",
  managerReloadState: "reload_required",
  legacyBinaryState: "present_unreviewed",
  installationAllowed: true, // a forged native field MUST NOT be reflected
  runtimeActivationAllowed: true,
};
const preflight: PoposDisplay = {
  status: "blocked",
  code: "/home/private-user/apt/private.list?password=abc",
  data: null,
};

describe("desktop local installation diagnostics (only redacted metadata)", () => {
  it("produces a useful operator report without sensitive host strings or fake authority", () => {
    const text = createOfflineDiagnostics(host, "loaded", preflight);
    for (const line of [
      "inspection_source=native_readonly",
      "os_target=popos_24_04_x86_64_metadata_only",
      "agent_package=unapproved_preview_candidate",
      "systemd_service=running_unapproved",
      "systemd_unit=enabled_unapproved",
      "systemd_dropins=present_unreviewed",
      "systemd_reload=reload_required",
      "legacy_binary=present_unreviewed",
      "cosmic_preflight=blocked_not_verified",
      "install_authorized=false",
      "agent_start_authorized=false",
      "release_gates_closed=0/10",
      "release_status=NOT_RELEASE_READY",
    ]) expect(text).toContain(line);
    for (const sensitive of [
      "private-user", "password=abc", "TOKEN=", "do-not-leak",
      "/home/", "Pop!_OS 24.04 /home",
      "install_authorized=true", "agent_start_authorized=true",
    ]) expect(text).not.toContain(sensitive);
  });

  it("never turns browser preview, missing host, or stale refreshed host into a verified scan", () => {
    const web = createOfflineDiagnostics(null, "web-preview", {
      status: "web-preview", code: "", data: null,
    });
    expect(web).toContain("inspection_source=web_preview_no_host_access");
    expect(web).toContain("agent_package=unverified");
    expect(web).toContain("cosmic_preflight=web_preview_no_host_access");
    const stale = createOfflineDiagnostics(host, "reading", preflight);
    expect(stale).toContain("inspection_source=not_verified");
    expect(stale).toContain("systemd_service=unverified");
    expect(createOfflineDiagnostics(null, "error", preflight))
      .toContain("inspection_source=native_inspection_failed");
  });

  it("cannot reflect unknown untrusted enum values or arbitrary blocked errors", () => {
    const forged = {
      ...host,
      packageState: "SIGNED_PRODUCTION_AGENT",
      serviceState: "running_allowed",
      unitFileState: "administrator",
    } as unknown as HostSnapshot;
    const text = createOfflineDiagnostics(forged, "loaded", {
      status: "blocked",
      code: "private:session-token-abc",
      data: null,
    });
    expect(text).toContain("agent_package=unverified");
    expect(text).toContain("systemd_service=unverified");
    expect(text).toContain("systemd_unit=unverified");
    expect(text).not.toContain("SIGNED_PRODUCTION_AGENT");
    expect(text).not.toContain("session-token");
  });

  it("checked PopOS metadata is still not L3, and no report grants a release", () => {
    const text = createOfflineDiagnostics(host, "loaded", {
      status: "checked", code: "", data: {} as NonNullable<PoposDisplay["data"]>,
    });
    expect(text).toContain("cosmic_preflight=metadata_checked_not_l3");
    expect(text).toContain("popos_physical_l3=NOT_RUN");
    expect(text).toContain("official_signed_agent_package=NOT_APPROVED");
    expect(text).toContain("legal_and_sre_signoff=NOT_RUN");
  });
});
