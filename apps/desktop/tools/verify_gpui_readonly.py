#!/usr/bin/env python3
"""Reuse the reviewed GPUI nonprivileged source guard outside the build job.

Static source policy only, not a sandbox, dependency review or release approval.
The existing native build guard remains in place during the staged migration.
"""
import pathlib
import sys
import tomllib

DESKTOP = pathlib.Path(__file__).resolve().parents[1] / "gpui-candidate"


def validate(root=DESKTOP):
    if not __debug__:
        raise RuntimeError("source guard requires Python assertions enabled")
    root = pathlib.Path(root)
    p = tomllib.loads((root / "Cargo.toml").read_text())
    assert p["package"]["publish"] is False
    assert set(p["dependencies"]) == {"gpui-kit", "sysinfo"}
    assert p["dependencies"]["sysinfo"] == {"version": "=0.31.4", "default-features": False, "features": ["system"]}
    d = p["dependencies"]["gpui-kit"]
    assert d["version"] == "=0.11.0" and d["default-features"] is False
    assert set(d["features"]) == {"component", "assets", "x11"}
    assert not (root / "build.rs").exists()
    summary = (root / "src/system_summary.rs").read_text()
    assert "RefreshKind::new()" in summary and ".with_ram()" in summary
    assert all(token not in summary for token in ("new_all", "refresh_all", "refresh_processes", "with_processes", "Networks::", "Disks::", "Users::", "host_name", "Components::"))
    for path in (root / "src").rglob("*.rs"):
        data = path.read_text()
        path = path.relative_to(root)
        for token in ("std::net", "unsafe {", "reqwest::", "zbus::", "tokio::", "include_bytes!", "include_str!", "OpenOptions", "File::create", "std::io::Write"):
            assert token not in data, (path, token)
        if path == pathlib.Path("src/host_identity.rs"):
            assert data.count("std::fs") == 1 and "std::fs::File::open(path)" in data
            assert '"/proc/self/status"' in data and '"/etc/os-release"' in data
            assert "file.take(16_385)" in data and "ordinary_user" in data
        elif path == pathlib.Path("src/runtime_metadata.rs"):
            assert data.count("std::process") == 1 and data.count("Command::new") == 1
            assert 'Command::new("/usr/bin/timeout")' in data
            assert '"/usr/bin/dpkg-query"' in data and '"/usr/bin/systemctl"' in data
            assert '"--kill-after=1s", "3s"' in data and "out.take(4097)" in data
            assert ".env_clear()" in data and ".stderr(Stdio::null())" in data
            assert data.count("std::fs") == 1 and 'std::fs::symlink_metadata("/usr/local/bin/lanveriq-agent")' in data
            assert not any(t in data for t in ('"/bin/sh"', '"/bin/bash"', '"sudo"', '"start"', '"stop"', '"enable"', '"install"', '"daemon-reload"'))
        else:
            assert "std::fs" not in data, path
        if path != pathlib.Path("src/runtime_metadata.rs"):
            assert "std::process" not in data and "Command::new" not in data and "std::env" not in data, path
        else:
            assert data.count("std::env") == 3
            for name in ("XDG_CURRENT_DESKTOP", "XDG_SESSION_TYPE", "WAYLAND_DISPLAY"):
                assert f'std::env::var("{name}")' in data
    assert ".disabled(true)" in (root / "src/main.rs").read_text()
    return "PASS: GPUI read-only source boundary (nonrelease)"


if __name__ == "__main__":
    try:
        if len(sys.argv) != 1:
            raise ValueError("unexpected arguments")
        print(validate())
    except (AssertionError, ValueError, KeyError, OSError, RuntimeError) as exc:
        print(f"FAIL: GPUI source boundary: {exc}", file=sys.stderr)
        sys.exit(1)
