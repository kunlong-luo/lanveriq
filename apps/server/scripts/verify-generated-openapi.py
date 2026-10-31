#!/usr/bin/env python3
"""Verify Springdoc-generated V0.1 console health contract; never invent it.

The only accepted input is JSON emitted from the actual controller through
Springdoc's GET /v3/api-docs in an isolated synthetic Spring Boot test context.
This script is a fail-closed *contract* gate, NOT a DB/TLS/production test.
"""

import json
import sys
from pathlib import Path


EXPECTED_PATHS = {"/api/v1/devices", "/api/v1/devices/{deviceId}"}
EXPECTED_FIELDS = {
    "device_id", "group_id", "group_name", "label", "enrollment_state",
    "last_seen_at", "heartbeat_received_at", "agent_version",
    "queue_count", "queue_bytes", "os_family", "os_name", "cpu_model",
    "memory_total_bytes", "inventory_received_at",
}
PAGE_FIELDS = {"items", "next_cursor"}


def validate(spec: object) -> list[str]:
    errors = []
    if not isinstance(spec, dict):
        return ["Generated OpenAPI root is not an object"]

    if not str(spec.get("openapi", "")).startswith("3."):
        errors.append("Expected generated OpenAPI 3.x version")
    paths = spec.get("paths")
    if not isinstance(paths, dict):
        return errors + ["OpenAPI paths absent"]
    if set(paths) != EXPECTED_PATHS:
        errors.append("OpenAPI paths changed or include unauthorized routes")

    for path in EXPECTED_PATHS:
        entry = paths.get(path)
        if not isinstance(entry, dict):
            errors.append(f"Missing generated route {path}")
            continue
        verbs = {key.lower() for key in entry if key.lower() in
                 {"get", "post", "put", "patch", "delete", "head", "options", "trace"}}
        if verbs != {"get"}:
            errors.append(f"Only GET is authorized in contract: {path}")
        op = entry.get("get", {})
        if not isinstance(op, dict):
            errors.append(f"Invalid GET operation: {path}")
            continue
        security = op.get("security", spec.get("security", []))
        if not (isinstance(security, list)
                and any(isinstance(item, dict) and "consoleSession" in item
                        for item in security)):
            errors.append(f"Missing consoleSession authentication: {path}")
        response = op.get("responses", {}).get("200", {})
        schema = response.get("content", {}).get("application/json", {}).get("schema")
        if not isinstance(schema, dict):
            errors.append(f"Missing generated 200 application/json schema: {path}")

    schemes = spec.get("components", {}).get("securitySchemes", {})
    cookie = schemes.get("consoleSession") if isinstance(schemes, dict) else None
    if not (isinstance(cookie, dict) and cookie.get("type") == "apiKey"
            and cookie.get("in") == "cookie"
            and cookie.get("name") == "__Host-lanveriq-session"):
        errors.append("Missing exact secure console cookie declaration")

    schemas = spec.get("components", {}).get("schemas", {})
    if not isinstance(schemas, dict):
        return errors + ["Missing generated schema components"]
    item = schemas.get("DeviceHealthSummary")
    page = schemas.get("ScopedDeviceDirectoryPage")
    if not isinstance(item, dict):
        errors.append("Missing generated DeviceHealthSummary record")
    else:
        fields = item.get("properties", {})
        if not isinstance(fields, dict) or set(fields) != EXPECTED_FIELDS:
            errors.append("Device health projection fields drifted or leaked sensitive data")
    if not isinstance(page, dict):
        errors.append("Missing generated ScopedDeviceDirectoryPage record")
    else:
        fields = page.get("properties", {})
        if not isinstance(fields, dict) or set(fields) != PAGE_FIELDS:
            errors.append("Device directory page fields drifted")
        elif not isinstance(fields["items"], dict) or not isinstance(
                fields["next_cursor"], dict):
            errors.append("Missing typed page members")

    return errors


def main() -> int:
    if len(sys.argv) != 2:
        print("Usage: verify-generated-openapi.py <Springdoc openapi.json>",
              file=sys.stderr)
        return 2
    source = Path(sys.argv[1])
    try:
        spec = json.loads(source.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, ValueError) as error:
        print("OPENAPI CONTRACT BLOCKED: unreadable or invalid Springdoc JSON",
              file=sys.stderr)
        return 1
    errors = validate(spec)
    for error in errors:
        print("OPENAPI CONTRACT BLOCKED: " + error, file=sys.stderr)
    if errors:
        return 1
    print("Source-generated Springdoc 3.x device list/detail verified: "
          "GET-only, session cookie security, strict health-only response schema.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
