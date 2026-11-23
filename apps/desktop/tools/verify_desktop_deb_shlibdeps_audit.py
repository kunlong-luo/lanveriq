#!/usr/bin/env python3
"""L2B-8: read-only official dpkg-shlibdeps vs GUI-only Debian Depends.

NEVER execute, install, update or publish the inspected candidate. The official
dpkg-dev symbols/shlibs minimum from Ubuntu 24 is not a Pop!_OS validation.
"""
import argparse
import importlib.util
import json
import pathlib
import subprocess
import sys
import tempfile

HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "desktop_apt", HERE / "verify_desktop_apt_installed_closure.py"
)
chain = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(chain)
require = chain.require

# Only a disposable synthetic dpkg-dev worktree is generated. This control file
# is never packaged, installed, uploaded, or used to claim publication rights.
SYNTHETIC_CONTROL = """Source: lanveriq-synthetic-readonly-audit
Section: utils
Priority: optional
Maintainer: Lanveriq CI <noreply@example.invalid>
Standards-Version: 4.7.0

Package: lanveriq-desktop
Architecture: any
Depends: @DOLLAR@{shlibs:Depends}, @DOLLAR@{misc:Depends}
Description: untrusted GUI-only ABI audit
 This is not a release.
""".replace("@DOLLAR@", "$")


def parse_relations(raw, apt_pkg):
    """Use distro apt_pkg instead of reimplementing Debian dependency grammar."""
    require(isinstance(raw, str) and 0 < len(raw) <= 32768
            and "\r" not in raw and "\n" not in raw,
            "empty or multiline Debian dependency expression")
    try:
        groups = apt_pkg.parse_depends(raw, False)
    except (ValueError, TypeError, SystemError) as exc:
        raise ValueError("APT refused dependency grammar: " + str(exc)[:120]) from exc
    require(isinstance(groups, list) and 0 < len(groups) <= 512,
            "invalid dependency-group count")
    result = []
    for group in groups:
        require(isinstance(group, list) and 1 <= len(group) <= 32,
                "missing or excessive APT OR group")
        items = []
        for triple in group:
            require(isinstance(triple, tuple) and len(triple) == 3,
                    "invalid parsed APT relation")
            name, version, op = triple
            require(isinstance(name, str)
                    and chain.APT_PKG_NAME.fullmatch(name) is not None
                    and isinstance(version, str) and len(version) <= 160
                    and isinstance(op, str) and op in ("", ">=", "=", "<=", ">>", "<<")
                    and ((not version and not op) or (version and op)),
                    "unsafe parsed APT relation")
            items.append({"name": name, "version": version, "op": op})
        result.append(items)
    return result


def parse_generated(stdout):
    require(isinstance(stdout, bytes) and 20 <= len(stdout) < 32768,
            "empty/oversized official shlibdeps output")
    try:
        rows = stdout.decode("utf-8").strip().splitlines()
    except UnicodeDecodeError as exc:
        raise ValueError("non-UTF8 official shlibdeps output") from exc
    require(len(rows) == 1 and rows[0].startswith("shlibs:Depends="),
            "unexpected official shlibdeps format")
    value = rows[0][len("shlibs:Depends="):]
    require(value and "$" not in value, "unresolved official shlibs dependencies")
    return value


def official_deps(binary):
    """Compute from real ELF via dpkg-dev without LD_LIBRARY_PATH or ignoring errors."""
    binary = pathlib.Path(binary)
    require(binary.is_file() and not binary.is_symlink()
            and 1024 <= binary.stat().st_size < 120 * 1024 * 1024,
            "unapproved executable for shlibdeps")
    with tempfile.TemporaryDirectory(prefix="lanveriq-abi-readonly-") as tmp:
        root = pathlib.Path(tmp)
        (root / "debian").mkdir(mode=0o700)
        (root / "debian" / "control").write_text(SYNTHETIC_CONTROL)
        try:
            result = subprocess.run(
                ["/usr/bin/dpkg-shlibdeps", "-O", "-e" + str(binary.resolve(strict=True))],
                cwd=root, capture_output=True, check=False, timeout=90, shell=False,
                env={"LC_ALL": "C", "LANG": "C", "PATH": "/usr/bin:/bin",
                     "DPKG_COLORS": "never", "DPKG_NLS": "0"}
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise ValueError("dpkg-shlibdeps failed to start or timed out") from exc
        require(result.returncode == 0,
                "official dpkg-shlibdeps failed to compute versioned ABI requirements")
        require(len(result.stderr) <= 16000, "excessive dpkg-dev diagnostics")
        return parse_generated(result.stdout), result.stderr.decode("utf-8", "replace")


def covers(required, declared, apt_pkg):
    if required["name"] != declared["name"]:
        return False
    if not required["op"]:
        return True
    if required["op"] not in (">=", "=") or declared["op"] not in (">=", "="):
        return False
    comparison = apt_pkg.version_compare(declared["version"], required["version"])
    if required["op"] == "=":
        return comparison == 0 and declared["op"] == "="
    return comparison >= 0


def compare_requirements(required_groups, declared_groups, apt_pkg):
    missing, too_weak = [], []
    for group in required_groups:
        if any(covers(needed, declared, apt_pkg)
               for needed in group for archived in declared_groups for declared in archived):
            continue
        known = {p["name"] for archived in declared_groups for p in archived}
        (too_weak if any(x["name"] in known for x in group) else missing).append(group)
    return {
        "calculated_direct_group_count": len(required_groups),
        "archive_declared_group_count": len(declared_groups),
        "not_directly_declared_groups": missing,
        "declared_but_insufficient_constraint_groups": too_weak,
        "direct_declaration_gap_count": len(missing) + len(too_weak),
    }


def validate_prior(input_report, runtime, source_sha, run_id, attempt):
    require(input_report.get("schema") ==
            "lanveriq.desktop.untrusted-apt-installed-satisfier-v1"
            and input_report.get("source_sha") == source_sha
            and input_report.get("run_id") == run_id
            and input_report.get("attempt") == attempt
            and input_report.get("actual_elf_sha256") == runtime.get("elf_sha256")
            and input_report.get("release_approved") is False
            and input_report.get("install_authorized") is False
            and input_report.get("license_review") == "OPEN_NOT_APPROVED",
            "forged or changed L2B-7 evidence authority")


def evidence(package, extraction, runtime_report, owner_report, apt_report,
             sha, run_id, attempt, apt_pkg):
    import apt
    package = pathlib.Path(package)
    binary = pathlib.Path(extraction) / chain.owner.elfguard.GUI
    require(chain.verify(apt_report, owner_report, runtime_report, binary,
                         sha, run_id, attempt, apt.Cache()).startswith("PASS:"),
            "L2B-7 unsigned receipt verification failed")
    loader = chain.owner.elfguard.payload.base
    native = loader.json_read(runtime_report, 16384)
    prior = loader.json_read(apt_report, 4000000)
    validate_prior(prior, native, sha, run_id, attempt)
    require(native.get("deb_sha256") == loader.digest(package)
            and native.get("release_approved") is False,
            "Debian/ELF byte identity or release claim changed")
    ctrl = chain.owner.elfguard.payload.deb.exact_control(
        chain.owner.elfguard.payload.deb.run("dpkg-deb", "--field", str(package)))
    require(ctrl.get("Architecture") == "amd64"
            and ctrl.get("Package") == "lanveriq-desktop",
            "wrong synthetic Debian package identity")
    raw_declared = ctrl.get("Depends", "")
    calculated_raw, warnings = official_deps(binary)
    computed = parse_relations(calculated_raw, apt_pkg)
    declared = parse_relations(raw_declared, apt_pkg)
    comparison = compare_requirements(computed, declared, apt_pkg)
    return {
        "schema": "lanveriq.desktop.untrusted-dpkg-shlibdeps-v1",
        "source_sha": sha, "run_id": run_id, "attempt": attempt,
        "deb_sha256": native["deb_sha256"], "elf_sha256": native["elf_sha256"],
        "apt_report_sha256": loader.digest(apt_report),
        "official_generated_shlibs_depends": calculated_raw,
        "official_calculated_groups": computed,
        "official_dpkg_shlibdeps_diagnostics": warnings,
        "archive_actual_depends": raw_declared,
        "archive_actual_groups": declared,
        "comparison": comparison,
        "direct_package_metadata": "GAPS_FOUND" if
            comparison["direct_declaration_gap_count"] else "NO_GAP_DETECTED_UNAPPROVED",
        "scope": "UBUNTU_24_04_SYSTEM_SYMBOLS_NOT_POP_OS_ABI",
        "ignored_missing_library_metadata": False,
        "installability_on_pop_os": "NOT_TESTED",
        "license_review": "OPEN_NOT_APPROVED",
        "trusted_release_provenance": "NOT_AVAILABLE",
        "release_approved": False, "install_authorized": False,
    }


def verify(output, *args):
    loader = chain.owner.elfguard.payload.base
    require(loader.json_read(output, 131072) == evidence(*args),
            "shlibdeps evidence changed or forged release approval")
    return "PASS: local ABI dependency comparison only; RELEASE/INSTALL NOT APPROVED"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("mode", choices=("create", "verify"))
    for field in ("package", "extraction", "runtime-report", "owner-report",
                  "apt-report", "output"):
        p.add_argument("--" + field, type=pathlib.Path, required=True)
    p.add_argument("--source-sha", required=True)
    p.add_argument("--run-id", type=int, required=True)
    p.add_argument("--attempt", type=int, required=True)
    x = p.parse_args()
    try:
        import apt_pkg
    except ImportError as exc:
        raise ValueError("distro python3-apt unavailable") from exc
    apt_pkg.init_system()
    args = (x.package, x.extraction, x.runtime_report, x.owner_report,
            x.apt_report, x.source_sha, x.run_id, x.attempt, apt_pkg)
    if x.mode == "create":
        require(not x.output.exists(), "refuse overwriting unapproved shlibdeps report")
        x.output.write_text(json.dumps(evidence(*args), indent=2, sort_keys=True) + "\n")
        print("PASS: read-only official shlibdeps comparison; never approved for install")
    else:
        print(verify(x.output, *args))


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, UnicodeError, json.JSONDecodeError, KeyError,
            RuntimeError, subprocess.SubprocessError) as exc:
        print("NOT_APPROVED: " + str(exc), file=sys.stderr)
        sys.exit(2)
