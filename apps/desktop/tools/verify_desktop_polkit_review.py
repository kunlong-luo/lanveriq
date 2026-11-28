#!/usr/bin/env python3
"""L2B-22: review uninstalled Polkit action defaults. NEVER grants authority.

This verifies an inert source fixture, NOT the installed polkit/rules database,
subject identity, APT origin, publisher key, signed Agent, privileged broker,
or an actual authorization result. NO installation, subprocess or network.
"""
import pathlib
import re
import stat
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[3]
FOLDER = ROOT / "apps/desktop/polkit"
POLICY = FOLDER / "org.lanveriq.agent.package.policy"

# Important: operation IDs are separate; no action covers start, boot, network,
# collection, remote control, certificates or a generic command executor.
ACTIONS = {
    "install": (
        "Review Lanveriq Agent installation",
        "Lanveriq Agent install is not approved",
    ),
    "upgrade": (
        "Review Lanveriq Agent upgrade",
        "Lanveriq Agent upgrade is not approved",
    ),
    "remove": (
        "Review Lanveriq Agent removal",
        "Lanveriq Agent removal is not approved",
    ),
}
ALLOW_FIELDS = ("allow_any", "allow_inactive", "allow_active")
ALLOWED_REVIEW_FILES = frozenset({POLICY.name, "README.md"})
RESULT = "PASS: inert Polkit action XML is DENY-ALL; NOT INSTALLED, NO BROKER OR INSTALL AUTHORITY"


def require(condition, message):
    if not condition:
        raise ValueError(message)


def safe_text(node):
    """No nested tags, attributes or nonliteral payloads in user prompts."""
    require(len(node) == 0 and not node.attrib, "unexpected policy prompt markup")
    require(node.text is not None and node.text.strip() == node.text,
            "noncanonical policy prompt")
    return node.text


def validate_xml(raw):
    """Strict allowlist, not a general-purpose Polkit policy validator."""
    require(type(raw) is bytes and 0 < len(raw) < 8192, "policy size invalid")
    require(raw.startswith(b'<?xml version="1.0" encoding="UTF-8"?>\n'),
            "policy must have reviewed UTF-8 XML header")
    require(not re.search(rb"<!\s*(?:DOCTYPE|ENTITY)\b", raw, re.I),
            "XML external entity / DTD declarations forbidden")
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as exc:
        raise ValueError("invalid Polkit XML") from exc
    require(root.tag == "policyconfig" and not root.attrib,
            "unsupported Polkit root or root attributes")
    children = list(root)
    require([n.tag for n in children] == ["vendor", *["action"] * len(ACTIONS)],
            "unexpected policy action, annotation or helper")
    require(safe_text(children[0]) == "Lanveriq", "publisher label drift")
    seen = set()
    for action in children[1:]:
        require(set(action.attrib) == {"id"}, "unexpected action attributes")
        identifier = action.attrib["id"]
        require(identifier.startswith("org.lanveriq.agent.package."),
                "unreviewed Polkit namespace")
        opname = identifier.removeprefix("org.lanveriq.agent.package.")
        require(opname in ACTIONS and opname not in seen,
                "unknown, missing or duplicate privileged action")
        seen.add(opname)
        nodes = list(action)
        require([n.tag for n in nodes] == ["description", "message", "defaults"],
                "unexpected action annotation, imply, or helper")
        description, prompt = ACTIONS[opname]
        require(safe_text(nodes[0]) == description
                and safe_text(nodes[1]) == prompt,
                "Polkit action text/identity mismatch")
        defaults = nodes[2]
        require(not defaults.attrib and
                [n.tag for n in list(defaults)] == list(ALLOW_FIELDS),
                "missing or unexpected Polkit default")
        for setting in defaults:
            require(safe_text(setting) == "no",
                    "Polkit grant or cached authorization forbidden")
    require(seen == set(ACTIONS), "missing reviewed Polkit action")
    return RESULT


def validate_folder(folder=FOLDER):
    """Never allow executable helpers or deployable rules alongside fixture."""
    require(not folder.is_symlink() and folder.is_dir(),
            "unexpected policy source directory")
    names = {child.name for child in folder.iterdir()}
    require(names == ALLOWED_REVIEW_FILES,
            "unreviewed policy helper, rule or deployment artifact")
    file = folder / POLICY.name
    info = file.lstat()
    require(stat.S_ISREG(info.st_mode) and not file.is_symlink(),
            "policy must be a regular, non-symlink file")
    require(not (info.st_mode & 0o111), "policy must not be executable")
    return validate_xml(file.read_bytes())


def validate_context(root=ROOT):
    """Source audit only. GUI and bundle must remain fully offline/readonly."""
    desktop = root / "apps/desktop"
    from verify_gpui_readonly import validate
    require(not (desktop / "src-tauri").exists()
            and not (desktop / "gpui-candidate").exists(),
            "retired desktop source must not reappear")
    try:
        validate(desktop)
    except (AssertionError, ValueError, KeyError, OSError, RuntimeError) as exc:
        raise ValueError("native GUI source boundary rejected") from exc
    manifest = (desktop / "Cargo.toml").read_text(encoding="utf-8")
    require("publish = false" in manifest, "native GUI publication forbidden")
    return RESULT


if __name__ == "__main__":
    try:
        validate_folder()
        validate_context()
        print(RESULT)
    except (ValueError, OSError, TypeError, KeyError, ET.ParseError) as exc:
        print("DENIED: Polkit review contract invalid: " + str(exc), file=sys.stderr)
        sys.exit(1)
