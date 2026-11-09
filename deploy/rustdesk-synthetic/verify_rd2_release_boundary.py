#!/usr/bin/env python3
"""Source-only RD-2 shipping guard; does NOT attest network-level authorization.

While RustDesk's native connection path cannot be bound to Lanveriq RBAC,
remote control MUST be non-routable in Spring, Next.js and reference Caddy.
This gate is intentionally conservative and must not become an enable switch.
Any future legitimate remote API/UI requires explicit review and replacing
this check with real server-enforced authorized/denied session tests.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re

REMOTE_ROUTE = re.compile(
    r"(?:remote[-_/]?sessions?|remote[-_/]?control|remote[-_/]?desktop)",
    re.IGNORECASE,
)
JAVA_MAPPING = re.compile(
    r"@(?:Request|Get|Post|Put|Patch|Delete)Mapping\s*\(([^)]{0,2500})\)",
    re.DOTALL,
)
CADDY_LISTEN = re.compile(r"(?<!\d)2111[4-9](?!\d)")


def verify(root: Path) -> list[str]:
    problems: list[str] = []
    app = root / "apps/server/src/main/resources/application.yml"
    if not app.is_file():
        problems.append("application.yml missing: cannot prove remote default-deny")
    else:
        source = app.read_text(encoding="utf-8")
        # Literal setting, never an env override such as ${LANVERIQ_REMOTE_ENABLED:true}.
        if source.count("  remote:\n    enabled: false\n") != 1:
            problems.append("remote.enabled must be a single literal false, not an override")
        if re.search(r"(?m)^\s+enabled:\s*\$\{[^}]*REMOTE", source):
            problems.append("remote feature may not be enabled through environment variables")

    runtime_lock = root / "apps/server/src/main/java/com/lanveriq/server/security/RemoteAccessReleaseLock.java"
    if not runtime_lock.is_file():
        problems.append("Spring environment-aware remote release lock missing")
    else:
        runtime = runtime_lock.read_text(encoding="utf-8")
        for evidence in ("implements EnvironmentAware", "environment.getProperty(PROPERTY)",
                         "throw new IllegalStateException"):
            if evidence not in runtime:
                problems.append("Spring remote release lock is missing a required fail-closed check")
                break

    java = root / "apps/server/src/main"
    if not java.is_dir():
        problems.append("server source tree missing")
    else:
        for src in sorted(java.rglob("*")):
            if src.suffix not in (".java", ".kt"):
                continue
            code = src.read_text(encoding="utf-8")
            # Catch live annotation route declarations; ignore documentation
            # comments and ordinary backend references to RustDesk.
            for mapping in JAVA_MAPPING.finditer(code):
                if REMOTE_ROUTE.search(mapping.group(1)):
                    problems.append(f"remote session Spring mapping present: {src.relative_to(root)}")
                    break

    appdir = root / "apps/web-next/src/app"
    if not appdir.is_dir():
        problems.append("Next.js App Router source tree missing")
    else:
        for src in sorted(appdir.rglob("*")):
            if src.name not in ("page.tsx", "route.ts", "page.jsx", "route.js"):
                continue
            if any(REMOTE_ROUTE.fullmatch(part) for part in src.relative_to(appdir).parts[:-1]):
                problems.append(f"remote session Next.js route present: {src.relative_to(root)}")

    caddy = root / "deploy/caddy/Caddyfile.reference"
    if not caddy.is_file():
        problems.append("Caddy reference missing")
    else:
        active_lines = [
            line.split("#", 1)[0] for line in caddy.read_text(encoding="utf-8").splitlines()
        ]
        # A reference for production reverse-proxying RustDesk is forbidden
        # until the backend session admission/enforcement is actually tested.
        if any(CADDY_LISTEN.search(line) for line in active_lines):
            problems.append("reference ingress exposes a RustDesk port 21114-21119")

    return problems


def main() -> None:
    cli = argparse.ArgumentParser()
    cli.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = cli.parse_args()
    issues = verify(args.root)
    if issues:
        for reason in issues:
            print("FAIL:", reason)
        raise SystemExit(1)
    print("PASS: literal remote default and Spring effective-property startup rejection present")
    print("PASS: no remote session Spring/Next.js route or Caddy RustDesk port in source")
    print("SCOPE: only repo-level negative release gate; not live RBAC/Pro/desktop evidence")


if __name__ == "__main__":
    main()
