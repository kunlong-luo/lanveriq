# Screen recording pipeline — later milestone, explicitly authorized

**Not in v0.1 or v0.2.** Screen capture is more sensitive than process/security telemetry. No recording code should begin until disclosure, approval, platform-specific permission and retention policies are documented and reviewed.

## States
`disabled` → `policy-approved` → `awaiting OS permission` → `recording` → `finalizing` → `uploaded` / `failed` / `unsupported`. UI shows state honestly. No service-level workaround for a denied OS grant.

## Capture architecture
A signed and visible **user-session companion** obtains Windows.Graphics.Capture or Linux X11/Wayland (PipeWire + xdg-desktop-portal) permissions as appropriate. Native dialogs, system indicators and protected-content restrictions are respected. A background audit service cannot capture a session without permission merely because the organization owns the PC.

## Segments and manifests
- Conservative default: H.264 video, 1080p at 10–15 FPS, audio off, 30-minute target segments; configure based on benchmark.
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
