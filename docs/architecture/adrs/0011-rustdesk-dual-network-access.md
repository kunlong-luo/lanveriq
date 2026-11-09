# ADR-0011: RustDesk reuse + dual-network access (LAN and WAN)

- Date: 2026-11-08
- Status: **APPROVED DIRECTION / IMPLEMENTATION IN PROGRESS; NOT PRODUCTION ENABLEMENT**
- Supersedes: the *LAN-only network scope* in `docs/architecture/deployment.md`, but not the existing security, device identity, privacy, default-OFF, or v0.1 A–E correctness gates.
- Related: `docs/architecture/device-communication-stack.md`, `docs/architecture/adrs/0009-agent-transport-selection.md`, `docs/operations/dual-network-acceptance.md`.

## Decision

**Use RustDesk as a separately deployed remote-access plane**, not as the source of Lanveriq device identity, inventory truth or business ACK. We own the Lanveriq Next.js interface and Spring Boot/JPA/API control plane, and preserve the existing Rust Agent's mTLS identity, SQLite durable outbox and business transaction ACK. No RustDesk protocol reimplementation, custom NAT/ICE/relay, or wholesale cloning of its Flutter UI. Start with the supported external application/client handoff; an embedded browser session requires separate Web Client/API, origin, license and RBAC evaluation.

**LAN + WAN:** a single canonical, real, owned FQDN for Console (e.g. `console.example.com`, **placeholder only**) with split-horizon DNS where available. LAN DNS resolves to the private Caddy ingress; WAN operators initially access the *same FQDN* through an approved WireGuard-class VPN/zero-trust private network with explicit DNS routing. If a public Web ingress is approved later, its public A/AAAA resolves to a hardened edge forwarding only HTTPS 443 to Caddy. Sessions/Host/Origin/CSRF stay on the same Console origin; do not implement two independent browser logins or use IP literals as the authenticated URL.

**Separate network/security planes:**
1. Browser: `console.<owned-domain>` HTTPS/WSS only -> Caddy -> Spring session/RBAC authorization of HTML/RSC, API and WebSocket -> private Next.js/Spring; database remains private.
2. Enrollment: `enroll.<owned-domain>` separate HTTPS/vhost, **OFF until CSR review, key custody, anti-replay and pairing tests pass**; do not route enrollment via Console.
3. Agent ingestion: `agent.<owned-domain>` on 443 with *device client-cert required* -> Caddy -> dedicated Caddy-to-Spring mTLS -> active device credential lookup in PostgreSQL. **OFF until Batch A/PKI and TLS1.3 tests pass.** Agent FQDN can use LAN private resolution or approved outbound-to-public ingress for WAN devices; no open Agent endpoints without mTLS. TLSv1.3-only for both hops is a release target, not a currently proven guarantee (Rust Agent source in main is now TLS1.3-only, but actual external TLS and the full mTLS/PG18 handshake remain NOT_RUN).
4. Remote access: independently deployed RustDesk `hbbs`/ `hbbr`, each on their documented TCP/UDP ports, with server key pinning, versioned client configuration, audit and per-device human authorization. Prefer LAN direct/P2P, then WAN ICE/P2P, then relay. **Do not proxy the RustDesk native protocol through Lanveriq API or expose native clients via a generic HTTP reverse proxy.** If direct/P2P fails, Relay provides fallback *only if independently reachable*; availability is not guaranteed without network and relay acceptance.
5. Internal services: Next.js/Java upstream, PostgreSQL 5432, Redis 6379, Caddy upstream TLS 8443, private RustDesk administrative APIs **must not** be bound/exposed on public or untrusted LAN interfaces.

## Reference topology

```text
LAN browser --private split DNS---+
WAN operator --approved VPN------+--> console.<owned-domain>:443 -> Caddy
[future approved public HTTPS edge]+                           |-> Spring auth/REST/WSS
                                                                |-> private Next.js HTML/RSC
                                                                |-> private PG18 (not public)

LAN Agent -> agent.<owned-domain>:443 -> client mTLS -> Caddy -> upstream mTLS -> Spring/JPA/PG18
WAN Agent -> approved VPN OR explicitly approved public mTLS ingress (not enabled today)
LAN/WAN remote client -> RustDesk hbbs ID/rendezvous -> direct ICE/TCP/UDP OR hbbr relay
Remote endpoint runs RustDesk Client separately from the Lanveriq Agent.
```

## RustDesk adoption boundary

- Source projects: https://github.com/rustdesk/rustdesk and https://github.com/rustdesk/rustdesk-server . Check pinned tag/SHA, all direct and transitive licenses, CVEs, build reproducibility and target Windows/Linux compatibility before shipping. The primary RustDesk client is AGPL-3.0; Server Pro and Web Client feature/API licenses require a separate procurement/legal review. Independent binaries/services and a documented integration interface are preferred; **do not assume client code can be copied into proprietary binaries**.
- RustDesk 1.5.0 WebRTC direct path needs compatible peers/server and explicit self-hosted `enable-webrtc=Y` (only after controlled acceptance): https://rustdesk.com/blog/rustdesk-1-5-0-webrtc-qos/?lang=en . TCP/UDP paths and relay remain mandatory fallbacks.
- OSS server provides hbbs/hbbr, not the enterprise management API/control plane promised by Pro. A browser Web Console is **not** automatically an embeddable Web Client. We will not fake a public web remote-control route until API/SSO/session-bound launch and license are verified.
- Deployment guidance: https://rustdesk.com/docs/en/self-host/rustdesk-server-oss/install/ . For a native client pilot, restrict ingress to the documented required hbbs/hbbr ports (e.g. hbbs 21115/TCP, 21116/TCP+UDP; hbbr 21117/TCP) and egress needed for legitimate peers. Only expose additional web/admin ports after feature-specific review. **RustDesk docs warn the web-client WebSocket listener ports 21118/21119 trust unvalidated forwarded IP headers**; never expose them directly to untrusted networks; put behind a properly sanitizing reverse proxy or keep closed.
- Each Lanveriq `device_id` maps to a separately verified RustDesk peer ID. A peer ID or online flag is *never* a device credential or a business heartbeat. Human remote-control permission must be checked against live Spring group RBAC/session before generating any handoff. Deny/revoke/grant changes must invalidate launches; audit both sides with a correlation ID and without exposing RustDesk secrets.
- License and Web/API integration is a **go/no-go gate**, not a promise. If it fails, keep RustDesk as an approved external client handoff for v0.1; reconsider managed/embedded access separately, without modifying protocol internals.

## Release gates and non-goals

- **L1:** network contract verifier/unit tests (no external calls in CI), secret scan, optional Caddy static/synthetic test; ordinary source CI remains path-filtered.
- **L2 (explicit synthetic environment):** two independent network vantage points (LAN and genuine WAN/VPN), split DNS / same-host certificate/SNI, HTTPS Console success, direct-origin denial, anonymous API denial, Spring RBAC/session/CSRF, WebSocket Origin, timeout and revocation; agent wrong/missing/revoked cert fail-closed including TLS1.2 negative; RustDesk client P2P+forced Relay fallback and reconnect. Never infer real WAN reachability from a CI runner.
- **L3:** approved operators/devices, real OS credential storage, restore/rollback, public edge controls if enabled, remote-session audit and privacy notices, tested ISP/NAT/firewall scenarios, license review.
- No public database/Redis/Java/Next.js ports; no `tls_insecure_skip_verify`, unsigned client keys, wildcard identity-header trust, blanket `/api/v1/**` allow, real data in GitHub Pages or default-ON collection.
- **Do not modify `spark-db`, its volumes, or existing production DNS/firewall/ports** as part of this architecture decision.
- The previous LAN-only `*.lanveriq.internal` names remain historical **synthetic test fixtures only**, not final publicly trusted hostnames. Do not point real clients at placeholder `example.com` names.

## Implementation order

1. **This ADR, the operational acceptance plan and an isolated read-only network probe** (no runtime flags, credentials, DNS or firewall changes).
2. Pin RustDesk server/client versions and prove Windows/Linux native remote control over LAN, then WAN/VPN and relay, in disposable infrastructure with anonymous/no real employee data.
3. Create separate RustDesk integration boundary in Spring only after API/permission/license approval; keep Next.js login and device routes disconnected until Batch C.
4. Complete A–E v0.1 gates, deploy private approved services and then perform independent LAN/WAN acceptance; public WAN Console is a separate reviewed expansion.
