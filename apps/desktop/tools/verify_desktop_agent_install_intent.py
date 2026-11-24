#!/usr/bin/env python3
"""L2B-17: offline, never-authorizing draft for a future polkit install intent.

The submitted bytes are untrusted input, not an approval, installer, signer,
policy file, privileged helper, or executable command. This module contains
no execution/install/privilege escalation path. Even a valid proposal is DENIED.
"""
import argparse
import json
import pathlib
import re
import stat
import sys

SCHEMA = "lanveriq.desktop.agent-install-intent.v0.DENY_ALL"
MAX_BYTES = 8192
ALLOWED_FIELDS = frozenset({
    "schema", "action", "agent_package", "host_target", "notice_ack",
    "install_opt_in", "service_start_opt_in", "boot_enable_opt_in",
    "network_opt_in", "collection_opt_in", "remote_control_opt_in",
    "publisher_signature", "authorized_artifact", "polkit_grant",
    "external_approvals", "request_arguments",
})
TRUST_FIELDS = (
    "publisher_signature", "authorized_artifact", "polkit_grant", "external_approvals",
)
OPT_INS = (
    "notice_ack", "install_opt_in", "service_start_opt_in",
    "boot_enable_opt_in", "network_opt_in", "collection_opt_in",
    "remote_control_opt_in",
)
DENIED = "DENIED: design-only Agent install intent, no publisher, signed package, polkit approval or PopOS L3"
NAME = re.compile(r"[a-z][a-z0-9-]{0,63}\Z", re.ASCII)


def require(ok, message):
    if not ok:
        raise ValueError(message)


def unique_pairs(pairs):
    output = {}
    for key, value in pairs:
        require(key not in output, "duplicate install-intent field")
        output[key] = value
    return output


def reject_json_nonstandard(constant):
    raise ValueError("nonstandard install-intent JSON number")


def read_intent(path):
    file = pathlib.Path(path)
    info = file.lstat()
    require(stat.S_ISREG(info.st_mode), "install-intent input is not a regular file")
    require(0 < info.st_size <= MAX_BYTES, "install-intent size invalid")
    payload = file.read_bytes()
    require(len(payload) == info.st_size, "install-intent file changed while reading")
    return json.loads(
        payload.decode("utf-8"),
        object_pairs_hook=unique_pairs,
        parse_constant=reject_json_nonstandard,
    )


def validate_intent(data):
    """Check the canonical, deliberately inert proposal. Always return DENIED.

    No proposal can turn on installation. A future implementation must have
    independent trusted-artifact provenance, publisher authorization, host
    consent and narrow privileged broker review; those are NOT implemented.
    """
    require(type(data) is dict, "install-intent root must be an object")
    require(set(data) == ALLOWED_FIELDS, "missing or unknown install-intent field")
    require(data["schema"] == SCHEMA, "unsupported install-intent schema")
    require(data["action"] == "propose-agent-install-only",
            "install-intent cannot invoke privileged operations")
    require(type(data["agent_package"]) is dict, "agent package proposal must be object")
    pkg = data["agent_package"]
    require(set(pkg) == {"name", "version", "sha256", "source"},
            "unknown or missing package detail")
    require(pkg["name"] == "lanveriq-agent" and NAME.fullmatch(pkg["name"]) is not None,
            "unreviewed package identity")
    require(pkg["version"] is None and pkg["sha256"] is None and pkg["source"] is None,
            "no signed or distributable Agent package is approved")
    require(data["host_target"] == "pop-24.04-cosmic", "unsupported target host")
    for field in OPT_INS:
        require(data[field] is False, f"unapproved install intent consent: {field}")
    for field in TRUST_FIELDS:
        require(data[field] is None, f"unapproved trusted authority: {field}")
    require(type(data["request_arguments"]) is list
            and len(data["request_arguments"]) == 0,
            "arbitrary command, path, service or package arguments forbidden")
    return DENIED


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--intent", type=pathlib.Path, required=True)
    args = parser.parse_args()
    print(validate_intent(read_intent(args.intent)))


if __name__ == "__main__":
    try:
        main()
    except (OSError, UnicodeError, ValueError, TypeError, json.JSONDecodeError) as exc:
        print(f"DENIED: {exc}", file=sys.stderr)
        sys.exit(1)
