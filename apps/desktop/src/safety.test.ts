import { describe, expect, it } from "vitest";
import { describeReadiness, nativeDetails, RELEASE_GATE, type HostSnapshot } from "./safety";

const safeHost: HostSnapshot = {
  osName: "Pop!_OS 24.04",
  architecture: "x86_64",
  targetSupported: true,
  packageState: "unverified",
  serviceState: "unverified",
  unitFileState: "unverified",
  overrideState: "unverified",
  managerReloadState: "unverified",
  installationAllowed: false,
  runtimeActivationAllowed: false,
};

describe("desktop always refuses unapproved operations", () => {
  it("never enables install/start/recovery/network/remote/CA", () => {
    expect(Object.values(RELEASE_GATE).every(value => value === false)).toBe(true);
  });

  it("does not treat no evidence as a clean install", () => {
    expect(describeReadiness(null).kind).toBe("unchecked");
    expect(nativeDetails(null).every(([, value]) => value === "未核验")).toBe(true);
    expect(describeReadiness(safeHost).kind).toBe("unverified");
  });

  it("rejects unsupported systems even with a package", () => {
    expect(describeReadiness({
      ...safeHost,
      targetSupported: false,
      packageState: "preview_candidate",
    }).kind).toBe("unsupported");
  });

  it("never promotes a preview package or running service to approved", () => {
    const host = {
      ...safeHost,
      packageState: "preview_candidate" as const,
      serviceState: "active" as const,
      unitFileState: "enabled" as const,
      installationAllowed: true,
      runtimeActivationAllowed: true,
    };
    expect(describeReadiness(host).kind).toBe("candidate");
    expect(describeReadiness(host).label).toContain("未批准");
    expect(nativeDetails(host).find(([label]) => label === "后台服务")?.[1]).toContain("未授权");
    expect(RELEASE_GATE.installEnabled).toBe(false);
  });

  it("states unknown version and unexpected overrides", () => {
    const detail = nativeDetails({
      ...safeHost,
      packageState: "unrecognized_version",
      overrideState: "present_unreviewed",
      managerReloadState: "reload_required",
    });
    expect(detail.find(([label]) => label === "Agent 包")?.[1]).toContain("未知");
    expect(detail.find(([label]) => label === "覆盖配置")?.[1]).toContain("未审核");
  });
});
