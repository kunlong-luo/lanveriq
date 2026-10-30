# Nonfunctional targets

**Design targets, not actual measured performance.** Pilot scope: 1–10 endpoints and one LAN host.

| Quality | Initial target | Verification |
|---|---|---|
| Agent overhead | idle <150 MiB RAM and <2% of one CPU core, excludes video/GUI | 60-min manual benchmark |
| Heartbeat | every 30 s; stale after 90 s; offline after 300 s | unit clock tests |
| LAN event latency | p95 <15 s when connected | targeted manual sampling |
| Transport durability | no silent loss of committed events through restart | unit fault/replay + manual reboot |
| Endpoint event storage | bounded by configurable bytes and age; report gaps | unit quota tests |
| Disk | provide free-space warning, no unbounded recordings | manual disk pressure |
| Network | never require external SaaS to run | offline LAN validation |
| RBAC | authorize each endpoint and data category server-side | unit negative tests |
| Recovery | pilot RPO 24 h, RTO 4 h | manual database restore drill |
| Upgrades | migrations with backup, compatible API contract | unit migration tests |
| UX | keyboard-accessible management; clear error/empty states | manual review |

**Sizing example:** at an average 2 Mbit/s, uninterrupted 24-h recording creates about 21.6 GB per device/day. Ten devices for fourteen days need roughly 3.0 TB raw, excluding replicas, logs and capacity headroom. Actual capacity depends on frame rate, encoder, actual hours and screen content.

**Status:** OS session recording reliability, Wayland support and specific Linux distributions are unverified; the MVP must expose capability flags instead of promising universal coverage.
