import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { buildOfflineIdleConfig, IDLE_INTERVAL_OPTIONS } from "./idle-config";

describe("offline GUI config exactly matches the current Rust AgentConfig v1", () => {
  it("uses the committed Agent example as the canonical default", () => {
    const example = readFileSync(
      new URL("../../agent/config/agent.example.toml", import.meta.url), "utf8",
    );
    const withoutComments = example.split("\n")
      .filter(line => !line.startsWith("#")).join("\n");
    expect(buildOfflineIdleConfig(30)).toBe(withoutComments);
  });

  it("every reviewed choice has only two Rust parser-supported scalar fields", () => {
    expect(IDLE_INTERVAL_OPTIONS.map(option => option.seconds))
      .toEqual([15, 30, 60, 120, 300]);
    for (const option of IDLE_INTERVAL_OPTIONS) {
      const config = buildOfflineIdleConfig(option.seconds);
      expect(config).toBe(`schema_version = 1\n\n[runtime]\ncycle_seconds = ${option.seconds}\n`);
      expect(config).not.toMatch(/token|password|endpoint|capture|network|remote|key|issuer/i);
    }
  });

  it("refuses arbitrary values, coercion, an injection payload and unsupported intervals", () => {
    for (const value of [
      null, undefined, "", 0, 5, 301, 60.5, "30", Number.NaN,
      Number.POSITIVE_INFINITY, {}, [], "30\n[network]\nenabled = true",
    ]) {
      expect(() => buildOfflineIdleConfig(value)).toThrow("UNAPPROVED_IDLE_INTERVAL");
    }
  });
});
