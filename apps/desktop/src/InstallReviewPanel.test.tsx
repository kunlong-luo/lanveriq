import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { InstallReviewPanel } from "./InstallReviewPanel";
import type { HostSnapshot } from "./safety";

const safeHost: HostSnapshot = {
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

describe("installation preparation visibly remains review-only", () => {
  it("renders every unmet independent gate and no executing controls", () => {
    const markup = renderToStaticMarkup(<InstallReviewPanel
      host={safeHost} popos={{ status: "idle", code: "", data: null }} />);
    for (const item of [
      "可信软件源与正式签名", "逐次管理员授权与受限 Polkit",
      "Pop!_OS COSMIC 实机与法务批准", "独立审批未完成",
      "安装、升级、卸载分别需要", "阅读说明不会启动 Agent",
    ]) {
      expect(markup).toContain(item);
    }
    expect((markup.match(/type="checkbox"/g) ?? []).length).toBe(4);
    expect(markup).not.toContain("install-agent");
    expect(markup).not.toContain("pkexec");
    expect(markup).not.toContain("href=");
  });

  it("unknown host and web preview cannot display a trusted installation", () => {
    const markup = renderToStaticMarkup(<InstallReviewPanel
      host={null} popos={{ status: "web-preview", code: "", data: null }} />);
    expect(markup).toContain("尚未核验");
    expect(markup).toContain("安全阻断");
    expect(markup).not.toContain("已获得安装许可");
  });
});
