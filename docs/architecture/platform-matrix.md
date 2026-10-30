# Windows/Linux feature matrix and limitations

**Status:** planned support, none yet verified in Lanveriq. Distinguish host version, session protocol and permissions.

| Feature | Windows 11 | Pop!_OS 24.04 / KDE KWin on X11 | Pop!_OS 24.04 / KDE KWin on Wayland |
|---|---|---|---|
| Background agent, heartbeat | Windows Service planned | systemd planned | systemd planned |
| OS event audit | Event Log / optional Sysmon source | auditd / journald | auditd / journald |
| Foreground activity metadata | Win32 foreground-window APIs | X11 window data | desktop-specific/unsupported until verified |
| Recording screen (v0.3) | Windows.Graphics.Capture where appropriate; obey system UX/consent | approved session capture API | xdg-desktop-portal ScreenCast + PipeWire authorization |
| System tray/settings | Tauri companion | Tauri companion | Tauri, depends on desktop |
| Locked/logged-out screen recording | not guaranteed | not guaranteed | not guaranteed |
| Auto-update | deferred | deferred | deferred |

## D-02 pilot selection and evidence candidate (NOT YET CONFIRMED)

**Candidate:** Pop!_OS 24.04 LTS x86_64 with the specifically reported KDE Plasma/KWin configuration. Windows 11 x64 is a **separate** minimum acceptance target, not automatically validated when Linux succeeds. Do not claim X11 and Wayland both supported until actual sessions are tested.

On the actual **interactive user desktop session**, capture the following as *sanitized environment metadata only* (do not upload or commit usernames, IP/MAC, paths or tokens):
~~~sh
printf 'session=%s\n' "${XDG_SESSION_TYPE:-unknown}"
printf 'desktop=%s\n' "${XDG_CURRENT_DESKTOP:-unknown}"
plasmashell --version
loginctl show-session "${XDG_SESSION_ID}" -p Type -p Desktop 2>/dev/null || true
~~~
If `XDG_SESSION_ID` is absent/empty or commands run from SSH/headless systemd service, classify the **interactive** session type as `unknown`; do not infer X11 or Wayland from KDE/KWin alone. A desktop-session probe may be unavailable to the privileged background service, so encode a capability/status rather than escalating privileges or inspecting arbitrary logged-in users.

**Prior to D0 decision:** maintainer confirms which exact host/session is the first **pilot target**, and whether the planned V0.1 support claim is for basic health/inventory only. Runtime service/CPU/RAM/NIC and credential-store compatibility remain post-D0 real-device tests. Foreground application activity is postponed to V0.2; desktop recording, portal/encoder permissions and visible capture UX are postponed to V0.3.

**Evidence checklist after implementation:** record date, OS build, kernel, service identity, actual session protocol, relevant desktop/portal versions, Agent binary architecture, CPU/RAM/NIC capability results and synthetic enrollment/heartbeat/offline-replay outcomes. Mark unmeasured cells `NOT TESTED`, unsupported capabilities `unavailable` or `unsupported`—not `supported`.

Owner approval and real-device test evidence are different milestones. See [D-02](../governance/open-decisions.md), [test matrix](../testing/test-matrix.md) and [D0 gate](../governance/documentation-gate.md).

## Support policy
- **Initial Linux pilot machine supplied by the maintainer:** Pop!_OS 24.04 LTS x86_64, KDE Plasma 5.27.12 / KWin, kernel 7.2.6-76070206-generic, NVIDIA RTX 4050 Laptop. These are **reported environment details, not certification**. KDE Plasma 5.27 + KWin does **not** identify whether the actual session uses X11 or Wayland.
- Before claiming a supported session, record `echo $XDG_SESSION_TYPE`, `echo $XDG_CURRENT_DESKTOP`, `plasmashell --version`, portal backend/version and PipeWire status. Test the **actual** session first; test the other session separately if available.
- Windows 11 x64 remains a second v0.1 pilot target requiring a real test device. Ubuntu/GNOME/COSMIC and other distributions/compositors remain **experimental**, rather than replacing the user's Pop!_OS/KWin baseline.
- Report capability per source: `supported`, `requires_permission`, `permission_denied`, `unavailable`, `degraded`.
- Avoid silently substituting mouse/keystroke recording for unsupported window metadata.
- Unit tests can cover adapters with mocks; OS capabilities still require device testing. Do not present a checked mark as broad distro certification. The supplied GPU does not establish availability of a particular encoder or Wayland capture portal.

## Wayland session persistence is not universal
The [ScreenCast portal interface](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html) supports optional `persist_mode` and single-use `restore_token` in implementations that provide them. A restore attempt may still prompt again (permissions revoked, monitor changed, unsupported implementation) and must never be treated as unconditional silent permission. The initial Linux support claim must identify exact distro, desktop/compositor, portal version and the result of a real permission test.

## OS privilege boundaries
Windows service runs with least required permissions; desktop capture in user session. Linux daemon uses dedicated service identity and only required audit access; no assumption that root can bypass Wayland portal. Tauri status UX must remain visible and accurate.
