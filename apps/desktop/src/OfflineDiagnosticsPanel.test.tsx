import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { OfflineDiagnosticsPanel } from "./OfflineDiagnosticsPanel";

describe("offline operator diagnostics presentation", () => {
  it("provides select-only report with visible release blockers and no send/installer action", () => {
    const html = renderToStaticMarkup(
      <OfflineDiagnosticsPanel host={null} inspection="web-preview"
        popos={{ status: "web-preview", code: "", data: null }} />,
    );
    expect(html).toContain("本机诊断与安装阻塞报告");
    expect(html).toContain("inspection_source=web_preview_no_host_access");
    expect(html).toContain("release_status=NOT_RELEASE_READY");
    expect(html).toContain("readOnly");
    expect(html).toContain("只在本窗口生成");
    expect(html).not.toContain("install-agent");
    expect(html).not.toContain("href=");
    expect(html).not.toContain("type=\"submit\"");
  });

  it("does not put raw blocked error paths into native diagnostic output", () => {
    const html = renderToStaticMarkup(
      <OfflineDiagnosticsPanel host={null} inspection="error"
        popos={{ status: "blocked", code: "/home/operator/.ssh/id_rsa", data: null }} />,
    );
    expect(html).toContain("inspection_source=native_inspection_failed");
    expect(html).not.toContain("/home/operator");
  });
});
