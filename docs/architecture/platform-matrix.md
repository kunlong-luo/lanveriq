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
