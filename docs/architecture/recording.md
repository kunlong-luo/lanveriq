# Screen recording pipeline — later milestone, explicitly authorized

**Not in v0.1 or v0.2.** Screen capture is more sensitive than process/security telemetry. No recording code should begin until disclosure, approval, platform-specific permission and retention policies are documented and reviewed.

## States
`disabled` → `policy-approved` → `awaiting OS permission` → `recording` → `finalizing` → `uploaded` / `failed` / `unsupported`. UI shows state honestly. No service-level workaround for a denied OS grant.

## Capture architecture
A signed and visible **user-session companion** obtains Windows.Graphics.Capture or Linux X11/Wayland (PipeWire + xdg-desktop-portal) permissions as appropriate. Native dialogs, system indicators and protected-content restrictions are respected. On Linux Wayland, portal `persist_mode` and `restore_token` are conditional; tokens are single-use and a new token must replace the previous one after successful restoration. No promise that reopening a session skips the user prompt, and no bypass on permission denial. See the [official ScreenCast interface](https://flatpak.github.io/xdg-desktop-portal/docs/doc-org.freedesktop.portal.ScreenCast.html). A background audit service cannot capture a session without permission merely because the organization owns the PC.

## Permission and recorder state transitions
Grant is bound to **session, source and OS implementation**; a new login session, display change or portal denial may invalidate it. Explicit transitions: `disabled → awaiting_permission → recording → finalizing → uploading → available`, with `permission_denied/unsupported/failed` exits and a separate coverage-gap record. Stop collection when policy is revoked or the desktop permission is withdrawn, even if the service remains online. See [health/coverage states](health-and-gap-semantics.md).

## Segments and manifests
- **Benchmark proposal, not a fixed policy:** H.264, up to 1080p at 10–15 FPS, audio off, **roughly 1–5 minute finalized segments** with bounded encoded bytes; tune by storage budget, consent, encoder stability and real Windows/Linux validation. Avoid 30-minute open segments: a crash or disk-full event would delay upload and enlarge unavailable intervals.
- Use appropriate local encoder, e.g., tested FFmpeg integration, with protected local temporary directory.
- On normal segment close, compute SHA-256, length and UTC interval. Upload **only fully finalized files**.
- Assign server-side opaque recording ID and idempotent chunk/manifest IDs; verify upload hash and size; record final manifest in PostgreSQL. Never let client supply arbitrary server storage paths.
- Recording bytes on separate encrypted-at-rest disk/NAS where feasible; range-based playback with authenticated server authorization and detailed view/export audit.
- TLS for transport; limited-size upload; quotas; deduplicate repeated upload.

## Access control
A viewer must have device-group permission and an approved case/purpose/time range. Create review/access record before playback or download, log denied attempts as well. No direct NAS URL.

## Gaps
Examples: logoff, restart, lock, session switch, Wayland capture denial, unsupported compositor, paused recording, full disk, encoder failure or power outage. Mark as `capture_gap` with reason/time; **no assertion of flawless 24-hour footage**.

## Retention
Default proposed 14 days for video, with explicit organization policy and adjustable limits. Ingestion/log retention differs. See [retention](../security/data-retention.md).

## Unit-first test obligations
State transitions; segment idempotency; checksum handling; upload retry; authorization filter; retention. Full real-desktop capture validation is **manual** during early MVP and must be completed before recording is offered as usable.
