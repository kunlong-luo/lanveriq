# Windows/Linux feature matrix and limitations

**Status:** planned support, none yet verified in Lanveriq. Distinguish host version, session protocol and permissions.

| Feature | Windows 11 | Ubuntu/Debian X11 | Linux Wayland |
|---|---|---|---|
| Background agent, heartbeat | Windows Service planned | systemd planned | systemd planned |
| OS event audit | Event Log / optional Sysmon source | auditd / journald | auditd / journald |
| Foreground activity metadata | Win32 foreground-window APIs | X11 window data | desktop-specific/unsupported until verified |
| Recording screen (v0.3) | Windows.Graphics.Capture where appropriate; obey system UX/consent | approved session capture API | xdg-desktop-portal ScreenCast + PipeWire authorization |
| System tray/settings | Tauri companion | Tauri companion | Tauri, depends on desktop |
| Locked/logged-out screen recording | not guaranteed | not guaranteed | not guaranteed |
| Auto-update | deferred | deferred | deferred |

## Support policy
- Initial validated target: recent Windows 11 x64; one Ubuntu LTS desktop configuration, desktop version and X11/Wayland session **recorded**.
- Other Linux distributions, GNOME/KDE/COSMIC environments and ARM are **experimental** until tested.
- Report capability per source: `supported`, `requires_permission`, `permission_denied`, `unavailable`, `degraded`.
- Avoid silently substituting mouse/keystroke recording for unsupported window metadata.
- Unit tests can cover adapters with mocks; OS capabilities still require device testing. Do not present a checked mark as broad distro certification.

## Wayland session persistence is not universal
The [ScreenCast portal interface](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html) supports optional `persist_mode` and single-use `restore_token` in implementations that provide them. A restore attempt may still prompt again (permissions revoked, monitor changed, unsupported implementation) and must never be treated as unconditional silent permission. The initial Linux support claim must identify exact distro, desktop/compositor, portal version and the result of a real permission test.

## OS privilege boundaries
Windows service runs with least required permissions; desktop capture in user session. Linux daemon uses dedicated service identity and only required audit access; no assumption that root can bypass Wayland portal. Tauri status UX must remain visible and accurate.
