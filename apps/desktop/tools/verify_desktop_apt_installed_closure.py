#!/usr/bin/env python3
"""L2B-7: read-only python-apt installed mandatory-dependency satisfier graph.

Uses the maintained python-apt API; NEVER installs, marks or updates packages.
All installed alternatives are retained, so this graph is an overapproximation,
not proof of which dlopen/runtime libraries the app actually loads.
"""
import argparse
import hashlib
import importlib.util
import json
import pathlib
import sys

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "ubuntu_owner", HERE / "verify_desktop_ubuntu_runtime_packages.py"
)
owner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(owner)
require = owner.require

MAX_NODES = 1024
MAX_EDGES = 8192
MAX_ALTERNATIVES = 2000
LICENSE_OPEN = "OPEN_NOT_APPROVED"

def name_version(version):
    package = version.package
    name = package.fullname
    require(isinstance(name, str) and owner.PKG_NAME.fullmatch(name) is not None,
            "invalid APT installed package name")
    v = version.version
    require(isinstance(v, str) and owner.VERSION.fullmatch(v) is not None,
            "invalid APT installed version")
    require(package.installed is not None
            and package.installed.version == v, "APT target is not actually installed")
    return name, v

def installed_satisfiers(or_group):
    """Never choose a preferred alternative; return all actual installed targets."""
    require(isinstance(or_group, (tuple, list)) and 1 <= len(or_group) <= 32,
            "invalid APT alternative group")
    labels = []
    targets = {}
    kind = None
    for dep in or_group:
        require(dep.rawtype in ("Depends", "PreDepends"),
                "unsupported APT dependency type")
        if kind is None:
            kind = dep.rawtype
        require(kind == dep.rawtype, "mixed dependency group")
        label = dep.rawstr
        require(isinstance(label, str) and 0 < len(label) <= 1024,
                "unbounded APT dependency expression")
        labels.append(label)
        for target in dep.installed_target_versions:
            key, version = name_version(target)
            if key in targets:
                require(targets[key] == version, "contradictory installed dependency target")
            targets[key] = version
    require(targets, "missing installed satisfier for mandatory APT dependency: "
            + " | ".join(labels)[:220])
    return {
        "type": kind, "alternatives": labels,
        "installed_targets": [{"name": n, "version": targets[n]} for n in sorted(targets)],
        "ambiguous_installed_choices": len(targets) > 1,
    }

def inspect_installed(cache, root_versions):
    """Traverse read-only APT installed Depends/PreDepends satisfiers, bounded."""
    require(isinstance(root_versions, dict) and root_versions,
            "missing initial L2B-6 package roots")
    for name, version in root_versions.items():
        require(owner.PKG_NAME.fullmatch(name) and owner.VERSION.fullmatch(version),
                "invalid direct package name or version")
    work = sorted(root_versions)
    nodes = {}
    edges = []
    ambiguous = 0
    while work:
        name = work.pop(0)
        if name in nodes:
            continue
        require(len(nodes) < MAX_NODES, "installed dependency graph node bound exceeded")
        require(name in cache and cache[name].installed is not None,
                "required installed root or dependency absent: " + name)
        pkg = cache[name]
        version = pkg.installed
        resolved_name, resolved_version = name_version(version)
        require(resolved_name == name, "APT package architecture/name changed")
        if name in root_versions:
            require(resolved_version == root_versions[name],
                    "APT installed version differs from L2B-6 evidence")
        groups = version.get_dependencies("PreDepends", "Depends")
        require(isinstance(groups, list) and len(groups) <= 300,
                "invalid or huge APT dependency groups")
        for group in groups:
            alternatives = installed_satisfiers(group.or_dependencies)
            for target in alternatives["installed_targets"]:
                if target["name"] not in nodes and target["name"] not in work:
                    work.append(target["name"])
            edges.append({"from": name, **alternatives})
            ambiguous += int(alternatives["ambiguous_installed_choices"])
            require(len(edges) <= MAX_EDGES
                    and ambiguous <= MAX_ALTERNATIVES,
                    "installed dependency graph edge bound exceeded")
        nodes[name] = {"name": name, "version": resolved_version}
        work.sort()
    return {
        "packages": [nodes[n] for n in sorted(nodes)],
        "mandatory_dependency_groups": sorted(
            edges, key=lambda e: (e["from"], e["type"], e["alternatives"])
        ),
        "installed_alternative_ambiguities": ambiguous,
    }

def copyright_input(pkg):
    """Copyright material presence is evidence; does not confer redistribution rights."""
    try:
        record = owner.copyright_receipt(pkg)
    except (ValueError, OSError):
        return {"copyright_review": "MISSING_MATERIAL_LEGAL_REVIEW_OPEN"}
    return {"copyright_sha256": record["copyright_sha256"],
            "copyright_review": "MATERIAL_PRESENT_LEGAL_REVIEW_OPEN"}

def check_prior(original, source_sha, run, attempt):
    require(original.get("schema") == "lanveriq.desktop.untrusted-ubuntu-package-map.v1"
            and original.get("source_sha") == source_sha
            and original.get("run_id") == run and original.get("attempt") == attempt
            and original.get("ubuntu_release") == "24.04"
            and original.get("architecture") == "amd64"
            and original.get("license_review") == LICENSE_OPEN
            and original.get("transitive_runtime_closure") == "NOT_VERIFIED"
            and original.get("signed_provenance") == "NOT_AVAILABLE"
            and original.get("release_approved") is False
            and original.get("install_authorized") is False,
            "forged or mismatched L2B-6 package input")
    roots = original.get("installed_owner_packages")
    sonames = original.get("soname_owner_map")
    require(isinstance(roots, list) and len(roots) == 10
            and isinstance(sonames, list) and len(sonames) == 15,
            "wrong actual L2B-6 SONAME or package roots")
    require(len({p["name"] for p in roots}) == 10
            and len({p["soname"] for p in sonames}) == 15
            and {r["owner"] for r in sonames} == {p["name"] for p in roots},
            "L2B-6 owner-to-direct-SONAME mismatch")
    return {p["name"]: p["version"] for p in roots}

def evidence(owner_report, runtime_report, binary, source_sha, run_id, attempt,
             cache):
    prior = owner.verify(owner_report, runtime_report, binary,
                         source_sha, run_id, attempt)
    require(prior.startswith("PASS:"), "L2B-6 owner mapping verification failed")
    original = owner.elfguard.payload.base.json_read(owner_report, 49152)
    roots = check_prior(original, source_sha, run_id, attempt)
    graph = inspect_installed(cache, roots)
    packages = graph["packages"]
    for entry in packages:
        entry.update(copyright_input(entry["name"]))
    missing = sum(p["copyright_review"] == "MISSING_MATERIAL_LEGAL_REVIEW_OPEN"
                  for p in packages)
    payload = json.dumps(graph, sort_keys=True, separators=(",", ":")).encode()
    return {
        "schema": "lanveriq.desktop.untrusted-apt-installed-satisfier-v1",
        "source_sha": source_sha, "run_id": run_id, "attempt": attempt,
        "owner_report_sha256": owner.elfguard.payload.base.digest(owner_report),
        "actual_elf_sha256": original["elf_sha256"],
        "apt_graph_sha256": hashlib.sha256(payload).hexdigest(),
        "apt_installed_package_count": len(packages),
        "apt_dependency_or_group_count": len(graph["mandatory_dependency_groups"]),
        "apt_ambiguous_installed_choices": graph["installed_alternative_ambiguities"],
        "copyright_missing_count": missing,
        "graph": graph,
        "scope": "APT_INSTALLED_MANDATORY_SATISFIERS_MAY_OVERAPPROXIMATE_RUNTIME",
        "optional_dlopen_and_plugins": "NOT_IN_SCOPE",
        "pop_os24_runtime": "NOT_TESTED",
        "license_review": LICENSE_OPEN,
        "release_approved": False, "install_authorized": False,
    }

def verify(report, *args):
    actual = owner.elfguard.payload.base.json_read(report, 4000000)
    require(actual == evidence(*args), "APT installed graph drift or forged release")
    return "PASS: read-only APT installed mandatory satisfiers; LICENSE/RELEASE NOT APPROVED"

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("mode", choices=("create", "verify"))
    for field in ("owner-report", "runtime-report", "binary", "output"):
        parser.add_argument("--" + field, type=pathlib.Path, required=True)
    parser.add_argument("--source-sha", required=True)
    parser.add_argument("--run-id", type=int, required=True)
    parser.add_argument("--attempt", type=int, required=True)
    x = parser.parse_args()
    try:
        import apt
    except ImportError as exc:
        raise ValueError("distro python3-apt unavailable") from exc
    cache = apt.Cache()
    args = (x.owner_report, x.runtime_report, x.binary,
            x.source_sha, x.run_id, x.attempt, cache)
    if x.mode == "create":
        require(not x.output.exists(), "refuse overwrite APT evidence")
        x.output.write_text(json.dumps(evidence(*args), sort_keys=True, indent=2) + "\n")
        print("PASS: untrusted local APT satisfier graph created; NOT legal/release approval")
    else:
        print(verify(x.output, *args))

if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError, KeyError,
            RuntimeError) as exc:
        print("NOT_APPROVED: " + str(exc), file=sys.stderr)
        sys.exit(2)
