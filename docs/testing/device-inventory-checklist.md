# Device inventory test checklist — planned, not executed

**Unit-only CI in early MVP; manual OS/LAN validation separately.** Synthetic fixtures only.

| ID | Case | Expected result |
|---|---|---|
| INV-01 | Linux and Windows OS inventory | Normalized OS family/version/kernel or build + architecture; unsupported fields null |
| INV-02 | CPU model, physical/logical cores and total RAM | Correct units; no negative values; physical count may be null |
| INV-03 | Device has wired + Wi-Fi + VPN adapters | One `device_id`, many interface/address records; multiple IPv4/IPv6 supported |
| INV-04 | DHCP or MAC randomization changes | Device identity stays the same; inventory changes only |
| INV-05 | Distinct server-observed IP vs Agent-local IP | Both stored with independent source labels; untrusted forwarding ignored |
| INV-06 | Agent off/on and OS upgrade | Existing device ID persists; inventory refreshes |
| INV-07 | Agent credentials + local identity lost | New enrollment and ID, optional audited asset replacement link |
| INV-08 | Duplicate snapshot retry | No duplicate/change-history spam; same payload idempotently accepted |
| INV-09 | Older snapshot after newer accepted | Current snapshot cannot roll back |
| INV-10 | Malformed/oversized snapshot | Whole update rejected; no partial NIC or volume wipe |
| INV-11 | Missing RAM/CPU/volume data | Capability unavailable/unknown, never invented as zero |
| INV-12 | Device group permission boundary | Unauthorized operator cannot retrieve IP/MAC, even by direct ID |
| INV-13 | 30s heartbeat vs 24h inventory refresh | No full hardware upload on each heartbeat |
| INV-14 | 7-day metric and network history cleanup | Configurable deletion/retention and audit without indefinite metadata history |
| INV-15 | Pop!_OS 24.04 KDE/KWin | OS/kernel/CPU/RAM/NIC/disk supported as verified; X11/Wayland unknown until checked |
| INV-16 | Windows 11 x64 | Same common fields, OS build included; synthetic data used |

For manual checks record actual OS build, Agent version, test date, reviewer, supported/unsupported fields and sanitized evidence. No real LAN addresses or hardware serials in public issue logs. See [manual acceptance](manual-acceptance.md) and [cross-platform matrix](test-matrix.md).
