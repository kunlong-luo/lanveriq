#!/usr/bin/env python3
"""Verify Springdoc-generated V0.1 console health contract; never invent it.

The only accepted input is JSON emitted from the actual controller through
Springdoc's GET /v3/api-docs in an isolated synthetic Spring Boot test context.
This script is a fail-closed *contract* gate, NOT a DB/TLS/production test.
"""

import json
import sys
from pathlib import Path


GET_PATHS = {
    "/api/v1/devices", "/api/v1/devices/{deviceId}",
    "/api/v1/monitoring/overview", "/api/v1/auth/session",
    "/api/v1/auth/csrf",
}
POST_PATHS = {"/api/v1/auth/login", "/api/v1/auth/logout"}
EXPECTED_METHODS = {
    **{path: "get" for path in GET_PATHS},
    **{path: "post" for path in POST_PATHS},
}
SESSION_SECURITY_PATHS = GET_PATHS - {"/api/v1/auth/csrf"} | {"/api/v1/auth/logout"}
EXPECTED_FIELDS = {
    "device_id", "group_id", "group_name", "label", "enrollment_state",
    "last_seen_at", "heartbeat_received_at", "agent_version",
    "queue_count", "queue_bytes", "os_family", "os_name", "cpu_model",
    "memory_total_bytes", "inventory_received_at",
}
PAGE_FIELDS = {"items", "next_cursor"}
SESSION_FIELDS = {"login"}
CSRF_FIELDS = {"csrfToken"}
OVERVIEW_FIELDS = {
    "as_of", "total", "online", "stale", "offline", "revoked", "pending", "retired",
}


def validate(spec: object) -> list[str]:
    errors = []
    if not isinstance(spec, dict):
        return ["Generated OpenAPI root is not an object"]

    if not str(spec.get("openapi", "")).startswith("3."):
        errors.append("Expected generated OpenAPI 3.x version")
    paths = spec.get("paths")
    if not isinstance(paths, dict):
        return errors + ["OpenAPI paths absent"]
    if set(paths) != set(EXPECTED_METHODS):
        errors.append("OpenAPI paths changed or include unauthorized routes")

    for path, verb in EXPECTED_METHODS.items():
        entry = paths.get(path)
        if not isinstance(entry, dict):
            errors.append(f"Missing generated route {path}")
            continue
        verbs = {key.lower() for key in entry if key.lower() in
                 {"get", "post", "put", "patch", "delete", "head", "options", "trace"}}
        if verbs != {verb}:
            errors.append(f"Only {verb.upper()} is allowed in contract: {path}")
        op = entry.get(verb, {})
        if not isinstance(op, dict):
            errors.append(f"Invalid {verb.upper()} operation: {path}")
            continue
        security = op.get("security", spec.get("security", []))
        requires_cookie = path in SESSION_SECURITY_PATHS
        has_cookie = isinstance(security, list) and any(
            isinstance(item, dict) and "consoleSession" in item
            for item in security)
        if requires_cookie and not has_cookie:
            errors.append(f"Missing consoleSession authentication: {path}")
        if not requires_cookie and security:
            errors.append(f"Pre-login CSRF/login route must not require a session: {path}")
        expected_status = "204" if verb == "post" else "200"
        response = op.get("responses", {}).get(expected_status, {})
        if not isinstance(response, dict) or not response:
            errors.append(f"Missing {expected_status} success response: {path}")
            continue
        if verb == "get":
            schema = response.get("content", {}).get("application/json", {}).get("schema")
            if not isinstance(schema, dict):
                errors.append(f"Missing generated 200 application/json schema: {path}")
        elif response.get("content"):
            errors.append(f"Authentication {verb.upper()} must have no success body: {path}")
        if verb == "post":
            params = op.get("parameters", [])
            if not isinstance(params, list):
                params = []
            for name in ("Origin", "X-CSRF-TOKEN"):
                matches = [parameter for parameter in params
                           if isinstance(parameter, dict)
                           and parameter.get("in") == "header"
                           and parameter.get("name") == name
                           and parameter.get("required") is True]
                if len(matches) != 1:
                    errors.append(f"Missing mandatory header {name}: {path}")
            request = op.get("requestBody")
            if path == "/api/v1/auth/login":
                form = (request or {}).get("content", {}).get(
                    "application/x-www-form-urlencoded", {}).get("schema", {})
                properties = form.get("properties") if isinstance(form, dict) else None
                required = form.get("required", []) if isinstance(form, dict) else []
                if (not isinstance(properties, dict)
                        or set(properties) != {"username", "password"}
                        or set(required) != {"username", "password"}
                        or request.get("required") is not True):
                    errors.append("Login must require exactly two form fields, no URL secrets")
            elif request:
                errors.append("Logout request must not have a body")

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

    csrf = schemas.get("ConsoleCsrfToken")
    if not isinstance(csrf, dict) or not isinstance(
            csrf.get("properties"), dict) or set(
                csrf["properties"]) != CSRF_FIELDS:
        errors.append("CSRF bootstrap must expose only csrfToken")

    session = schemas.get("ConsoleSessionInfo")
    if not isinstance(session, dict) or not isinstance(
            session.get("properties"), dict) or set(
                session["properties"]) != SESSION_FIELDS:
        errors.append("Session response must reveal only normalized login")

    overview = schemas.get("ScopedMonitoringOverview")
    if not isinstance(overview, dict) or not isinstance(
            overview.get("properties"), dict) or set(
                overview["properties"]) != OVERVIEW_FIELDS:
        errors.append("Monitoring overview fields drifted or leaked sensitive data")

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
    print("Source-generated Springdoc console contract verified: five GETs,"
          " two filter-owned POSTs, CSRF/form security and strict wire schemas.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
