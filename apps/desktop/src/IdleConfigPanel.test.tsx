import { describe, expect, it } from "vitest";
import { renderToStaticMarkup } from "react-dom/server";
import { IdleConfigPanel } from "./IdleConfigPanel";

describe("GUI local idle config preparation never edits or authorizes the device", () => {
  it("renders editable radio choices but strictly read-only TOML output", () => {
    const markup = renderToStaticMarkup(<IdleConfigPanel />);
    expect(markup).toContain("离线 Agent 空闲配置预览");
    expect(markup).toContain("schema_version = 1");
    expect(markup).toContain("cycle_seconds = 30");
    expect((markup.match(/type="radio"/g) ?? []).length).toBe(5);
    expect(markup).toContain("readOnly");
    expect(markup).toContain("不是 Polkit 授权");
    for (const forbidden of ["href=", "type=\"submit\"", "install-agent", "pkexec", "systemctl"]) {
      expect(markup).not.toContain(forbidden);
    }
  });
});
