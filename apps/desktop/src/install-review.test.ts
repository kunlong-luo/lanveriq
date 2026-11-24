import { describe, expect, it } from "vitest";
import { EMPTY_REVIEW_CONSENT, reviewOnlyInstallPlan } from "./install-review";
import type { HostSnapshot } from "./safety";
import type { PoposDisplay } from "./preflight";

const host: HostSnapshot = {
  osName: "Pop!_OS 24.04",
  architecture: "x86_64",
  targetSupported: true,
  packageState: "not_installed",
  serviceState: "inactive",
  unitFileState: "disabled",
  overrideState: "none_reported",
  managerReloadState: "not_required",
  legacyBinaryState: "not_detected",
  installationAllowed: false,
  runtimeActivationAllowed: false,
};
const unverified: PoposDisplay = { status: "idle", code: "", data: null };
const allAcknowledged = {
  understoodPackageSource: true,
  understoodAdminAuthorization: true,
  understoodSeparateServiceStart: true,
  understoodDataPrivacy: true,
} as const;

describe("L2B-21 Tauri graphical install review is not an installation API", () => {
  it("rejects empty, browser preview and unchecked hosts without inventing approvals", () => {
    for (const view of [
      reviewOnlyInstallPlan(null, unverified, EMPTY_REVIEW_CONSENT),
      reviewOnlyInstallPlan(null, { status: "web-preview", code: "", data: null },
        allAcknowledged),
    ]) {
      expect(view.gates.find(x => x.id === "host")?.status).toBe("not_checked");
      expect(view.gates.find(x => x.id === "publisher")?.status).toBe("not_approved");
      expect(view.privilegedBrokerAvailable).toBe(false);
      expect(view.installAllowed).toBe(false);
    }
  });

  it("shows only metadata, NEVER a trusted install when an apparently clean host is reported", () => {
    const view = reviewOnlyInstallPlan(host, unverified, allAcknowledged);
    expect(view.gates.find(x => x.id === "host")?.status).toBe("metadata_only");
    expect(view.gates.find(x => x.id === "package-state")?.status).toBe("metadata_only");
    expect(view.gates.find(x => x.id === "service-state")?.status).toBe("metadata_only");
    expect(view.noticeReviewed).toBe(true);
    expect(Object.entries(view).filter(([key]) => key.endsWith("Allowed") ||
      key === "bootEnabled" || key === "privilegedBrokerAvailable")
      .every(([, value]) => value === false)).toBe(true);
  });

  it("client-forged installationAllowed and runtimeActivationAllowed grant no authority", () => {
    const forged = { ...host, installationAllowed: true, runtimeActivationAllowed: true };
    const view = reviewOnlyInstallPlan(forged, unverified, allAcknowledged);
    expect(view.installAllowed).toBe(false);
    expect(view.startAllowed).toBe(false);
    expect(view.bootEnabled).toBe(false);
    expect(view.gates.find(x => x.id === "broker")?.status).toBe("not_approved");
  });

  it("independently rejects active, enabled, overridden, legacy and unknown service states", () => {
    const scenarios: Partial<HostSnapshot>[] = [
      { serviceState: "active" }, { serviceState: "failed" },
      { unitFileState: "enabled" }, { unitFileState: "masked" },
      { overrideState: "present_unreviewed" },
      { managerReloadState: "reload_required" },
      { legacyBinaryState: "present_unreviewed" },
      { legacyBinaryState: "unverified" },
    ];
    for (const scenario of scenarios) {
      const view = reviewOnlyInstallPlan({ ...host, ...scenario }, unverified, allAcknowledged);
      expect(view.gates.find(x => x.id === "service-state")?.status).toBe("blocked");
      expect(view.installAllowed).toBe(false);
    }
  });

  it("rejects unknown and already installed package states", () => {
    for (const state of ["unverified", "unrecognized_version", "preview_candidate"] as const) {
      const view = reviewOnlyInstallPlan({ ...host, packageState: state }, unverified,
        allAcknowledged);
      expect(view.gates.find(x => x.id === "package-state")?.status).toBe("blocked");
    }
  });

  it("refuses another architecture and a forged target", () => {
    for (const change of [{ architecture: "aarch64" }, { targetSupported: false }]) {
      expect(reviewOnlyInstallPlan({ ...host, ...change }, unverified,
        allAcknowledged).gates.find(x => x.id === "host")?.status).toBe("blocked");
    }
  });

  it("local metadata cannot be upgraded to real L3, Polkit or publisher approval", () => {
    const forged: PoposDisplay = {
      status: "checked", code: "",
      data: { release_approved: true, install_authorized: true,
        signed_package_provenance: "TRUSTED" } as unknown as NonNullable<PoposDisplay["data"]>,
    };
    const view = reviewOnlyInstallPlan(host, forged, allAcknowledged);
    expect(view.gates.find(x => x.id === "compatibility")?.status).toBe("metadata_only");
    expect(view.gates.filter(x => x.status === "not_approved")).toHaveLength(3);
    expect(view.installAllowed).toBe(false);
  });

  it("every notice checkbox is separate and cannot authorize installation", () => {
    for (const field of Object.keys(allAcknowledged) as (keyof typeof allAcknowledged)[]) {
      const no = { ...allAcknowledged, [field]: false };
      const view = reviewOnlyInstallPlan(host, unverified, no);
      expect(view.noticeReviewed).toBe(false);
      expect(view.installAllowed).toBe(false);
    }
  });
});
