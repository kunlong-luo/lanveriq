import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { DesktopNavigation, DesktopOverview, PlannedSection, sectionLabels, type Section } from "./DesktopWorkspace";
import { RELEASE_GATE } from "./safety";

describe("MVP-08 desktop workspace is navigation and read-only presentation", () => {
  it("exposes all seven named destinations and one current page", () => {
    for (const section of Object.keys(sectionLabels) as Section[]) {
      const html = renderToStaticMarkup(<DesktopNavigation section={section} onNavigate={() => {}} />);
      expect((html.match(/aria-current="page"/g) ?? []).length).toBe(1);
      expect((html.match(/<button/g) ?? []).length).toBe(7);
      for (const label of Object.values(sectionLabels)) expect(html).toContain('aria-label="' + label + '"');
    }
  });
  it("does not invent host, registration or connection evidence", () => {
    const html = renderToStaticMarkup(<DesktopOverview host={null} inspection="web-preview" onNavigate={() => {}} onInspect={() => {}} />);
    expect(html).toContain("浏览器预览无法读取本机状态");
    expect(html).toContain("尚未检测");
    expect(html).toContain("未核验");
    expect(html).not.toContain("Pop!_OS 24.04");
    expect(html).not.toContain("已连接");
  });
  it("keeps all future sensitive controls natively disabled", () => {
    for (const kind of ["config", "enrollment"] as const) {
      const html = renderToStaticMarkup(<PlannedSection kind={kind} />);
      expect((html.match(/<button/g) ?? []).length).toBe(2);
      expect((html.match(/disabled=""/g) ?? []).length).toBe(2);
      expect(html).not.toMatch(/<input|<form|href=|type="submit"/);
    }
    expect(Object.values(RELEASE_GATE).every(value => value === false)).toBe(true);
  });
});
