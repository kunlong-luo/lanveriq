import { describe, expect, it } from "vitest";
import {
  ABI_PACKAGE_NAMES, ABI_MINIMUM_VERSIONS, COSMIC_PACKAGE_NAMES,
  blockerMessage, parseLocalPreflight, poposSummary,
} from "./preflight";

function record(): Record<string, unknown> {
  return {
    schema: "lanveriq.desktop.untrusted-popos-local-preflight.v1",
    host_os: { id: "pop", version: "24.04" },
    architecture: "amd64",
    session: {
      environment: "COSMIC_WAYLAND_CLAIM_ONLY",
      compositor_running_proven: false,
      display_socket_saved: false,
    },
    reviewed_abi_cache: ABI_PACKAGE_NAMES.map((name, index) => ({
      package: name, minimum: ABI_MINIMUM_VERSIONS[index], cached_candidate: "999.0", installed: false,
    })),
    cosmic_components: COSMIC_PACKAGE_NAMES.map(name => ({
      package: name, installed_version: "1.0",
    })),
    apt_sources: {
      file_count: 2,
      contents_sha256: "a".repeat(64),
      apt_repository_signature_trust: "NOT_VERIFIED",
    },
    source_config_checked_on_host: false,
    apt_cache_updated: false,
    popos_install_solver: "NOT_RUN",
    physical_l3: "NOT_APPROVED",
    gui_actual_launch: "NOT_TESTED",
    signed_package_provenance: "NOT_VERIFIED",
    license_approval: "OPEN",
    release_approved: false,
    install_authorized: false,
    agent_start_authorized: false,
  };
}

const parse = (x: Record<string, unknown>) => parseLocalPreflight(JSON.stringify(x));

describe("Pop!_OS local metadata panel cannot grant installer or Agent authority", () => {
  it("accepts a strictly structured synthetic metadata record, not L3 approval", () => {
    const result = parse(record());
    expect(result.reviewed_abi_cache).toHaveLength(10);
    expect(result.cosmic_components).toHaveLength(3);
    expect(result.physical_l3).toBe("NOT_APPROVED");
    expect(result.release_approved).toBe(false);
    expect(result.install_authorized).toBe(false);
    expect(result.agent_start_authorized).toBe(false);
  });

  it("rejects Ubuntu and other OS versions despite plausible libraries", () => {
    for (const badOs of [{ id: "ubuntu", version: "24.04" }, { id: "pop", version: "22.04" }]) {
      expect(() => parse({ ...record(), host_os: badOs })).toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    }
  });

  it("never trusts client-side approval toggles", () => {
    for (const flag of ["release_approved", "install_authorized", "agent_start_authorized"]) {
      expect(() => parse({ ...record(), [flag]: true })).toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    }
  });

  it("refuses forged compositor, GUI launch and L3 approval claims", () => {
    for (const changed of [
      { physical_l3: "PASS" },
      { gui_actual_launch: "PASS" },
      { signed_package_provenance: "TRUSTED" },
      { popos_install_solver: "PASS" },
      { source_config_checked_on_host: true },
      { apt_cache_updated: true },
    ]) {
      expect(() => parse({ ...record(), ...changed })).toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    }
  });

  it("rejects damaged/oversized JSON and missing package rows", () => {
    expect(() => parseLocalPreflight("{}")).toThrow();
    expect(() => parseLocalPreflight("not-json")).toThrow();
    expect(() => parseLocalPreflight("x".repeat(18000))).toThrow();
    expect(() => parse({ ...record(), reviewed_abi_cache: [] })).toThrow();
    expect(() => parse({ ...record(), cosmic_components: [] })).toThrow();
  });

  it("rejects unrecognized, missing or invalid package version evidence", () => {
    const rows = (record().reviewed_abi_cache as Record<string, unknown>[]);
    rows[0] = { package: "malicious-extra-package", minimum: ABI_MINIMUM_VERSIONS[0], cached_candidate: "999.0", installed: false };
    expect(() => parse({ ...record(), reviewed_abi_cache: rows })).toThrow();
    const versions = (record().reviewed_abi_cache as Record<string, unknown>[]);
    versions[0] = { package: ABI_PACKAGE_NAMES[0], minimum: ABI_MINIMUM_VERSIONS[0], cached_candidate: "/home/secret", installed: false };
    expect(() => parse({ ...record(), reviewed_abi_cache: versions })).toThrow();
  });


  it("rejects downgraded or deleted minimum ABI constraints, including implicit floor removal", () => {
    const rows = (record().reviewed_abi_cache as Record<string, unknown>[]);
    rows[0] = { ...rows[0], minimum: null };
    expect(() => parse({ ...record(), reviewed_abi_cache: rows })).toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    const noMinimumRow = (record().reviewed_abi_cache as Record<string, unknown>[]);
    noMinimumRow[7] = { ...noMinimumRow[7], minimum: "0.1" };
    expect(() => parse({ ...record(), reviewed_abi_cache: noMinimumRow })).toThrow("UNTRUSTED_PREFLIGHT_RESULT");
  });

  it("rejects undeclared result fields that could smuggle local paths or forged privileges", () => {
    const sample = record();
    expect(() => parse({ ...sample, home_directory: "/home/private-user" }))
      .toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    expect(() => parse({ ...sample, host_os: { id: "pop", version: "24.04", machine_id: "private" } }))
      .toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    const rows = sample.reviewed_abi_cache as Record<string, unknown>[];
    rows[0] = { ...rows[0], source_uri: "https://private.invalid/" };
    expect(() => parse({ ...sample, reviewed_abi_cache: rows }))
      .toThrow("UNTRUSTED_PREFLIGHT_RESULT");
    const cosmic = (record().cosmic_components as Record<string, unknown>[]);
    cosmic[0] = { ...cosmic[0], extra_field: "unexpected" };
    expect(() => parse({ ...record(), cosmic_components: cosmic }))
      .toThrow("UNTRUSTED_PREFLIGHT_RESULT");
  });

  it("does not expose raw errors, usernames or paths as failure messages", () => {
    expect(blockerMessage("COSMIC_COMPONENT_NOT_INSTALLED")).toContain("COSMIC");
    expect(blockerMessage("NOT_POP_OS_TARGET")).toContain("Pop!_OS");
    expect(blockerMessage("/home/user/private")).toContain("无法完成");
    expect(blockerMessage({ error: "root secret" })).toContain("无法完成");
  });

  it("browser preview and unknown remain unchecked rather than compatible", () => {
    expect(poposSummary({ status: "web-preview", data: null, code: "" })).toContain("未获得");
    expect(poposSummary({ status: "idle", data: null, code: "" })).toContain("尚未");
    expect(poposSummary({ status: "checked", data: parse(record()), code: "" })).toContain("不代表可安装");
  });
});
