#!/usr/bin/env python3
"""CI-only real Chromium proof for one Rust-origin device on the SAME PG18.

No dummy API, screenshots, traces, deployed UI route, TLS bypass or real user
data. The temporary test page renders the existing StagedConsoleWorkspace.
"""
import json
import os
import re
import subprocess
import sys
import uuid
from pathlib import Path

from playwright.sync_api import sync_playwright

ORIGIN = "https://console.lanveriq.internal"
DEVICE = "00000000-0000-7000-8000-000000000001"
UNSCOPED = "00000000-0000-7000-8000-000000000099"


class BrowserGateFailure(Exception):
    """Only fixed source-authored gate names; never underlying request details."""


def require(value, name):
    if not value:
        raise BrowserGateFailure(name)


def main():
    required = [
        "LANVERIQ_SYNTHETIC_CADDY_BRIDGE",
        "LANVERIQ_SYNTHETIC_LINUX_CSR",
        "LANVERIQ_SYNTHETIC_AGENT_BUSINESS_LOOP",
        "LANVERIQ_SYNTHETIC_CONSOLE_SESSION",
        "LANVERIQ_SYNTHETIC_CHAIN_READ",
        "LANVERIQ_SYNTHETIC_CHAIN_UI",
        "LANVERIQ_SYNTHETIC_BROWSER_PROBE",
        "LANVERIQ_SYNTHETIC_NEXTJS_PROBE",
    ]
    require(os.environ.get("GITHUB_ACTIONS") == "true" and
            os.environ.get("RUNNER_OS") == "Linux" and
            all(os.environ.get(key) == "1" for key in required) and
            os.environ.get("LANVERIQ_SYNTHETIC_AUTH_UI_PROBE") == "0" and
            os.environ.get("LANVERIQ_SYNTHETIC_MULTI_ADMIN_PROBE") == "0",
            "disposable_scope")
    home = os.environ.get("HOME", "")
    require(home and os.path.isdir(os.path.join(home, ".pki/nssdb")),
            "private_chromium_nss_trust_missing")
    inventory_mode = os.environ.get("LANVERIQ_SYNTHETIC_INVENTORY_CHAIN", "0")
    require(inventory_mode in ("0", "1"), "inventory_mode_is_bounded")
    sample_mode = os.environ.get("LANVERIQ_SYNTHETIC_LINUX_READONLY_SAMPLE", "0")
    require(sample_mode in ("0", "1") and
            (sample_mode == "0" or inventory_mode == "1"),
            "real_linux_inventory_sample_requires_explicit_chain")
    inventory_source = None
    recovered_source = None
    recovery_snapshot_id = None
    expected_inventory_revision = 1
    if inventory_mode == "1":
        inventory_path = Path(os.environ.get(
            "LANVERIQ_SYNTHETIC_INVENTORY_BODY_FILE", ""))
        require(inventory_path.is_absolute() and
                str(inventory_path).startswith("/tmp/") and
                inventory_path.name == "synthetic-inventory-request.json" and
                inventory_path.is_file(), "private_rust_inventory_wire_source")
        inventory_source = json.loads(inventory_path.read_text(encoding="utf-8"))
        require(inventory_source.get("device_id") == DEVICE and
                inventory_source.get("inventory_revision") == 1 and
                inventory_source.get("interfaces") == [] and
                inventory_source.get("volumes") == [],
                "bound_rust_inventory_wire_source")
        if sample_mode == "1":
            # A different, exact SQLite-origin wire request is delivered
            # AFTER actual Caddy disconnection and transport recovery.
            recovered_path = Path(os.environ.get(
                "LANVERIQ_SYNTHETIC_INVENTORY_RECOVERED_BODY_FILE", ""))
            require(recovered_path.is_absolute() and
                    str(recovered_path).startswith("/tmp/") and
                    recovered_path.name == "synthetic-inventory-recovered.json" and
                    recovered_path.is_file(),
                    "private_recovered_rust_inventory_wire_source")
            recovered_source = json.loads(recovered_path.read_text(encoding="utf-8"))
            require(recovered_source.get("device_id") == DEVICE and
                    recovered_source.get("inventory_revision") == 2 and
                    recovered_source.get("interfaces") == [] and
                    recovered_source.get("volumes") == [] and
                    recovered_source.get("schema_version") == 1,
                    "bound_recovered_rust_inventory_wire_source")
            try:
                recovery_snapshot_id = str(uuid.UUID(recovered_source["snapshot_id"]))
            except (ValueError, KeyError, TypeError, AttributeError):
                raise BrowserGateFailure("valid_recovered_snapshot_uuid") from None
            require(recovery_snapshot_id != inventory_source.get("snapshot_id"),
                    "recovered_inventory_has_new_snapshot")
            # This is still the same actual CI Linux host. The release
            # request only adds an explicitly CI-only capability probe; no
            # sensitive NIC, disk, user, or process values may appear.
            for key in ("os",):
                require(recovered_source.get(key) == inventory_source.get(key),
                        "recovered_os_source_matches_original_linux_host")
            for key in ("cpu", "memory"):
                require(recovered_source.get("hardware", {}).get(key) ==
                        inventory_source.get("hardware", {}).get(key),
                        "recovered_hardware_source_matches_original_linux_host")
            expected_inventory_revision = 2
    container = os.environ.get("LANVERIQ_SYNTHETIC_CHAIN_DB_CID", "")
    require(bool(re.fullmatch(r"[0-9a-f]{12,64}", container)), "ephemeral_database_id")
    # Password is transferred by a private inherited FD. Never argv/env/log.
    with os.fdopen(3, "r", encoding="utf-8") as secret:
        password = secret.readline().rstrip("\r\n")
    require(bool(re.fullmatch(r"[0-9a-f]{48}", password)), "one_time_password")

    # Prove a real Rust request was committed before the browser reads the DB.
    # The source-side Rust/ACK and revocation probes run before this function.
    sql = ("SELECT count(*) FROM device_heartbeat_latest h "
           "JOIN devices d ON d.id=h.device_id "
           f"WHERE d.id='{DEVICE}' AND d.enrollment_state='active' "
           "AND h.agent_status='degraded' AND h.policy_version=2 "
           "AND h.integrity_degraded IS TRUE AND h.received_at IS NOT NULL "
           "AND d.last_seen_at IS NOT NULL "
           "AND (SELECT count(*) FROM device_heartbeat_request_ids r "
           "WHERE r.device_id=d.id)=2")
    check = subprocess.run(
        ["docker", "exec", container, "psql", "-U", "lanveriq", "-d", "lanveriq",
         "-XAtqc", sql],
        capture_output=True, text=True, timeout=15, check=False)
    require(check.returncode == 0 and check.stdout.strip() == "1",
            "rust_postgres_committed_identity")
    print("STAGE: fixed-source PG18 receipt confirmed", flush=True)
    if inventory_mode == "1":
        inventory_sql = (
            "SELECT count(*) FROM device_inventory_current "
            f"WHERE device_id='{DEVICE}' AND inventory_revision={expected_inventory_revision} "
            "AND snapshot_id IS NOT NULL AND received_at IS NOT NULL "
            "AND os_family='linux' AND os_name IS NOT NULL "
            "AND cpu_model IS NOT NULL AND memory_total_bytes > 0 "
            "AND (SELECT count(*) FROM device_inventory_current)=1"
            + (f" AND snapshot_id='{recovery_snapshot_id}'"
               if recovery_snapshot_id else "")) 
        inventory_check = subprocess.run(
            ["docker", "exec", container, "psql", "-U", "lanveriq",
             "-d", "lanveriq", "-XAtqc", inventory_sql],
            capture_output=True, text=True, timeout=15, check=False)
        require(inventory_check.returncode == 0 and
                inventory_check.stdout.strip() == "1",
                "rust_inventory_pg18_source_row")
        print("STAGE: exact Rust inventory PG18 source and latest revision verified", flush=True)

    rejected = []
    api_responses = []
    with sync_playwright() as playwright:
        # Chromium's own resolver must stay on the same loopback-only Caddy
        # used by real curl. Without this it tries public DNS or a runner proxy
        # and can fail before the TLS and UI checks begin.
        chromium_args = [
            "--host-resolver-rules=MAP console.lanveriq.internal 127.0.0.1",
            "--no-proxy-server",
        ]
        browser = playwright.chromium.launch(headless=True, args=chromium_args)
        print("STAGE: Chromium launched with isolated NSS trust", flush=True)
        try:
            # Actual certificate validation; no certificate bypass and no
            # browser proxy, downloads or installed desktop Agent.
            context = browser.new_context(
                ignore_https_errors=False, accept_downloads=False,
                service_workers="block")
            page = context.new_page()

            # Block external origins ONLY. Never intercept/rewrite a trusted
            # first-party request: Chromium owns Origin and Sec-Fetch-* on the
            # wire, and Spring's actual request guard must see those headers.
            outside_console = re.compile(
                r"^(?!https://console\.lanveriq\.internal(?::443)?(?:/(?!/)|$)[^#]*$).+"
            )
            def deny_external(route):
                rejected.append("outbound_origin")
                route.abort()

            context.route(outside_console, deny_external)
            page.on("websocket", lambda _: rejected.append("websocket"))
            page.on("response", lambda response: api_responses.append(
                (response.url, response.status)) if
                "/api/v1/devices" in response.url else None)
            initial = page.goto(ORIGIN + "/synthetic-rust-chain",
                                wait_until="domcontentloaded", timeout=30000)
            require(initial is not None and initial.status == 200, "real_nextjs_tls_page")
            require(page.get_by_role(
                "heading", name="只读管理控制台候选").count() == 1,
                "shared_console_component")
            require(page.locator("main[data-candidate-status='signed_out']").count() == 1,
                    "initial_no_session")
            require(page.locator(".device-table tbody tr").count() == 0,
                    "no_prelogin_device")
            print("STAGE: real Next.js anonymous view verified", flush=True)
            page.locator("#candidate-username").fill("synthetic-owner")
            page.locator("#candidate-password").fill(password)
            page.get_by_role("button", name="安全登录并获取授权设备").click()
            page.locator("main[data-candidate-status='ready']").wait_for(timeout=30000)
            print("STAGE: Spring session and React authorized render ready", flush=True)

            # Network facts come from real Spring JSON, and DOM identity must
            # exactly match the same source UUID and server scoped total.
            snapshot = page.evaluate("""async () => {
              const one = async path => {
                const response = await fetch(path, {
                  credentials: 'same-origin', cache: 'no-store',
                  redirect: 'error', mode: 'same-origin'
                });
                const cache = response.headers.get('cache-control') || '';
                const body = response.status === 200 ? await response.json() : null;
                return {status: response.status, cache, body};
              };
              return {
                list: await one('/api/v1/devices?limit=50'),
                detail: await one('/api/v1/devices/00000000-0000-7000-8000-000000000001'),
                other: await one('/api/v1/devices/00000000-0000-7000-8000-000000000099'),
                overview: await one('/api/v1/monitoring/overview')
              };
            }""")
            for key in ("list", "detail", "overview"):
                require(snapshot[key]["status"] == 200 and
                        "no-store" in snapshot[key]["cache"].lower(),
                        key + "_authorized_no_store")
            rows = snapshot["list"]["body"].get("items")
            require(isinstance(rows, list) and len(rows) == 1 and
                    rows[0].get("device_id") == DEVICE and
                    rows[0].get("label") == "RustToConsoleSynthetic" and
                    rows[0].get("heartbeat_received_at") and
                    rows[0].get("last_seen_at"), "auth_list_matches_rust_pg")
            require(snapshot["list"]["body"].get("next_cursor") is None,
                    "auth_list_excludes_other_groups")
            require(snapshot["detail"]["body"].get("device_id") == DEVICE,
                    "authorized_detail_matches_rust_pg")
            require(snapshot["other"]["status"] == 404, "cross_group_detail_denied")
            require(snapshot["overview"]["body"].get("total") == 1,
                    "overview_server_scope_count")
            if inventory_mode == "1":
                require(inventory_source is not None, "missing_rust_inventory_source")
                source_os = inventory_source["os"]["name"]
                source_cpu = inventory_source["hardware"]["cpu"]["model"]
                source_memory = inventory_source["hardware"]["memory"]["total_bytes"]
                require(rows[0].get("os_family") == "linux" and
                        rows[0].get("os_name") == source_os and
                        rows[0].get("cpu_model") == source_cpu and
                        rows[0].get("memory_total_bytes") == source_memory and
                        bool(rows[0].get("inventory_received_at")),
                        "spring_inventory_projection_matches_rust_pg")
                detail = snapshot["detail"]["body"]
                require(detail.get("os_name") == source_os and
                        detail.get("cpu_model") == source_cpu and
                        detail.get("memory_total_bytes") == source_memory,
                        "spring_scoped_inventory_detail_consistency")
            require(page.locator("main").get_attribute("data-candidate-principal") ==
                    "synthetic-owner", "real_session_principal")
            require(page.locator(".device-table tbody tr").count() == 1,
                    "one_rust_dom_row")
            require(page.locator(".device-table tbody th[scope='row'] strong")
                    .all_text_contents() == ["RustToConsoleSynthetic"],
                    "rust_db_label_matches_next_dom")
            require(page.get_by_test_id("candidate-metric-已授权设备总数")
                    .text_content() == "1", "rust_overview_matches_next_dom")
            if inventory_mode == "1":
                details = page.locator(
                    ".device-table tbody th[scope='row'] small").all_text_contents()
                expected_ram = page.evaluate(
                    """n => (n / 1000000000).toLocaleString('zh-CN',
                      {maximumFractionDigits: 1}) + ' GB'""", source_memory)
                require(len(details) == 2 and
                        details[0] == source_os and
                        source_cpu in details[1] and
                        expected_ram in details[1],
                        "rust_inventory_os_cpu_memory_visible_in_nextjs_dom")
            require(any(url.endswith("/api/v1/devices?limit=50") and status == 200
                        for url, status in api_responses),
                    "actual_chromium_scoped_http_read")
            require(not rejected, "no_external_network_or_websocket")
            print("STAGE: PG18/Spring JSON matches Next.js DOM", flush=True)
            if sample_mode == "1":
                # Confirm the product-shaped UI refresh is a real, explicit
                # HTTP read of the UPDATED PG18 inventory (no hidden polling).
                with page.expect_response(
                    lambda response:
                        response.url.endswith("/api/v1/devices?limit=50")
                        and response.status == 200,
                    timeout=30000,
                ):
                    page.get_by_role("button", name="手动刷新").click()
                page.locator("main[data-candidate-status='ready']").wait_for(
                    timeout=30000)
                require(page.locator(".device-table tbody tr").count() == 1 and
                        page.locator(".device-table tbody th[scope='row'] strong")
                        .all_text_contents() == ["RustToConsoleSynthetic"] and
                        page.get_by_test_id("candidate-metric-已授权设备总数")
                        .text_content() == "1",
                        "manual_refresh_keeps_only_latest_scoped_rust_device")
                require(not rejected, "no_external_after_manual_refresh")
                print("STAGE: actual operator-triggered Next.js refresh reread PG18",
                      flush=True)

            page.get_by_role("button", name="登出并清除数据").click()
            page.locator("main[data-candidate-status='signed_out']").wait_for(timeout=30000)
            require(page.locator(".device-table tbody tr").count() == 0,
                    "logout_must_erase_device_dom")
            denied = page.evaluate("""async () => {
              const r = await fetch('/api/v1/devices?limit=50', {
                credentials:'same-origin', cache:'no-store', mode:'same-origin'
              });
              return {status:r.status, cache:r.headers.get('cache-control') || ''};
            }""")
            require(denied["status"] == 401 and
                    "no-store" in denied["cache"].lower(),
                    "logged_out_browser_api_denied")
            require(not rejected, "no_external_after_logout")
            context.close()
        finally:
            browser.close()

    print("PASS: same Rust request -> committed PG18 row -> Spring scoped API -> "
          "real TLS/Chromium/Next.js rendered exactly one device and scoped total; "
          "cross-group 404, logout clears DOM and API returns 401.")
    if inventory_mode == "1":
        if sample_mode == "1":
            print("PASS: independently corroborated Linux runner OS/CPU/RAM -> "
                  "SQLite pending revision 2 retained on Caddy outage -> restored "
                  "mTLS exact ACK -> PostgreSQL 18 current revision 2 -> "
                  "Spring scoped API -> operator-triggered Chromium Next.js refresh.")
        else:
            print("PASS: Rust SQLite outbox -> mTLS inventory POST/ACK -> PG18 current "
                  "snapshot -> authorized Spring projection -> Chromium Next.js "
                  "OS, CPU and memory DOM, without actual OS collection.")
    print("NOT PROVEN: normal production Next.js route, authorized Pop!_OS "
          "installation, real employees/devices, acquisition or release approval.")


if __name__ == "__main__":
    try:
        main()
    except BrowserGateFailure as exc:
        # require() failures have fixed, source-authored nonsecret gate names.
        # Never print raw browser exceptions, HTTP bodies or database values.
        print("FAIL: disposable Rust-to-Next.js gate: " + str(exc),
              file=sys.stderr)
        raise SystemExit(1) from None
    except Exception:
        # Deliberately no traceback: browser exception objects may contain
        # private cookies, synthetic passwords, headers and response bodies.
        print("FAIL: disposable Rust-to-Next.js Chromium runtime/trust exception",
              file=sys.stderr)
        raise SystemExit(1) from None
