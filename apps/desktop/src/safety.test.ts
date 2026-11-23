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
  legacyBinaryState: "not_detected",
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
    expect(describeReadiness(host).kind).toBe("unverified");
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
  it("prioritizes legacy manual installs even when candidate is present", () => {
    const host = { ...safeHost, packageState: "preview_candidate" as const, legacyBinaryState: "present_unreviewed" as const };
    expect(describeReadiness(host).label).toContain("人工迁移");
    expect(nativeDetails(host).find(([label]) => label === "旧版安装位置")?.[1]).toContain("旧版");
  });

  it("rejects active service, enabled unit and unsafe drop-ins ahead of candidate", () => {
    const candidate = { ...safeHost, packageState: "preview_candidate" as const };
    for (const changed of [
      { serviceState: "active" as const },
      { unitFileState: "enabled" as const },
      { overrideState: "present_unreviewed" as const },
      { managerReloadState: "reload_required" as const },
    ]) {
      expect(describeReadiness({ ...candidate, ...changed }).kind).toBe("unverified");
    }
  });

  it("does not treat an unreadable legacy location as a clean host", () => {
    const host = { ...safeHost, legacyBinaryState: "unverified" as const, packageState: "preview_candidate" as const };
    expect(describeReadiness(host).label).toContain("无法核验");
  });

  it("still labels a clean advisory candidate without granting install access", () => {
    const host = { ...safeHost, packageState: "preview_candidate" as const };
    expect(describeReadiness(host).kind).toBe("candidate");
    expect(RELEASE_GATE.installEnabled).toBe(false);
  });

});
