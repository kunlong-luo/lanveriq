#!/usr/bin/env python3
"""Temporary, read-only MVP-08 cutover inventory; does not change tracked files.

Enumerate tracked GPUI moves, Tauri-only removals and affected references on
the actual CI checkout. Historic contract fixtures and generic security labs
are not removals. No network, installation, build, authority or distribution.
"""
import pathlib
import posixpath
import re
import subprocess
import sys

DESKTOP = "apps/desktop/"
CANDIDATE = DESKTOP + "gpui-candidate/"
OLD_ROOT = {
    DESKTOP + name for name in (
        ".node-version", "index.html", "package.json", "pnpm-lock.yaml",
        "tsconfig.json", "vite.config.ts", "README.md",
        "tools/verify_desktop_readonly.py", "tools/test_verify_desktop_readonly.py",
        "tools/paired_desktop_performance.py", "tools/test_paired_desktop_performance.py",
    )
}
OLD_WORKFLOWS = {
    ".github/workflows/" + name + ".yml" for name in (
        "desktop-tauri-preview", "desktop-deb-candidate",
        "desktop-paired-performance", "desktop-ubuntu-apt-prefetch",
        "desktop-ubuntu-apt-plan-diagnostic",
    )
}
REFERENCE = re.compile(
    r"gpui-candidate[/\\]|src-tauri[/\\]|verify_desktop_readonly\.py"
    r"|test_verify_desktop_readonly\.py|paired_desktop_performance\.py"
    r"|desktop-tauri-preview\.yml|desktop-deb-candidate\.yml"
    r"|desktop-paired-performance\.yml|desktop-ubuntu-apt-prefetch\.yml"
    r"|desktop-ubuntu-apt-plan-diagnostic\.yml"
    r"|apps/desktop/(?:pnpm-lock\.yaml|package\.json|src/[^\s)]+\.tsx?)"
)
LINK = re.compile(r"\]\(([^)]+)\)")
TEXT = {".md", ".yml", ".yaml", ".py", ".rs", ".js", ".mjs", ".ts",
        ".tsx", ".sh", ".toml", ".json", ".txt", ".gradle", ".css"}

def tracked():
    raw = subprocess.check_output(["git", "ls-files", "-z"])
    paths = [p.decode("utf-8") for p in raw.split(bytes([0])) if p]
    if len(paths) != len(set(paths)):
        raise ValueError("duplicate tracked paths")
    required = {CANDIDATE + "Cargo.toml", DESKTOP + "src-tauri/Cargo.toml"}
    if not required <= set(paths):
        raise ValueError("missing expected tracked source roots")
    return set(paths)

def main():
    paths = tracked()
    moves = {p: DESKTOP + p[len(CANDIDATE):] for p in paths if p.startswith(CANDIDATE)}
    retired = {p for p in paths if p in OLD_ROOT | OLD_WORKFLOWS
               or p.startswith(DESKTOP + "src-tauri/")
               or (p.startswith(DESKTOP + "src/") and pathlib.PurePosixPath(p).suffix
                   in {".ts", ".tsx", ".css"})}
    if not moves or not retired:
        raise ValueError("expected both source trees at the precutover baseline")
    collisions = set(moves.values()) & (paths - retired - set(moves))
    if collisions:
        raise ValueError("unexpected move collisions: " + ", ".join(sorted(collisions)))
    removed = set(moves) | retired
    post = paths - removed | set(moves.values())
    old_dirs = {str(parent) for p in paths for parent in pathlib.PurePosixPath(p).parents}
    post_dirs = {str(parent) for p in post for parent in pathlib.PurePosixPath(p).parents}
    print("MOVE_COUNT", len(moves), "RETIRE_COUNT", len(retired))
    for p in sorted(moves):
        print("MOVE", p, "=>", moves[p])
    for p in sorted(retired):
        print("RETIRE", p)
    refs = set()
    links = set()
    for p in sorted(paths - retired):
        path = pathlib.Path(p)
        if path.suffix not in TEXT and path.name not in {".gitignore", "AGENTS.md", "Makefile"}:
            continue
        if path.is_symlink() or not path.is_file() or path.stat().st_size > 4 * 1024 * 1024:
            raise ValueError("unexpected tracked text size/type: " + p)
        content = path.read_text(encoding="utf-8")
        if REFERENCE.search(content):
            refs.add(p)
        if path.suffix != ".md":
            continue
        for match in LINK.finditer(content):
            target = match[1].strip().split("#", 1)[0]
            if not target or re.match(r"^(?:[a-z][a-z\d+.-]*:|/)", target, re.I):
                continue
            dest = posixpath.normpath(posixpath.join(posixpath.dirname(p), target))
            if dest not in paths and dest not in old_dirs:
                continue
            after = posixpath.normpath(posixpath.join(posixpath.dirname(moves.get(p, p)), target))
            if after not in post and after not in post_dirs:
                links.add((p, dest))
    for p in sorted(refs):
        print("REFERENCE", p)
    for p, target in sorted(links):
        print("LINK_REWRITE", p, "=>", target)
    print("REFERENCE_COUNT", len(refs), "LINK_REWRITE_COUNT", len(links))
    print("PASS: read-only inventory; cutover, CI and release approval not performed")

if __name__ == "__main__":
    try:
        main()
    except (OSError, ValueError, subprocess.SubprocessError) as exc:
        print("FAIL:", exc, file=sys.stderr)
        sys.exit(1)
