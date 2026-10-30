# LAN bootstrap and device enrollment — v0.1 design

**Status:** documented proposal, no runnable installer. Device enrollment occurs only on managed, notified organization equipment. For operational simplicity, select one verified internal hostname; never infer trust merely from a local IP address.

## Server TLS design
- One Caddy HTTPS entry point, e.g. `lanveriq.internal` (example only, organization must ensure it resolves on its LAN).
- **Initial pilot option:** Caddy `tls internal` as a private CA. Install the CA root into the operating-system trust store on each managed endpoint/admin workstation **through a verified, authorized channel** and verify its fingerprint out-of-band.
- Caddy in Docker may not install its root into the **host/endpoint** trust stores automatically. Treat that as a provisioning task, **never bypass verification** with insecure TLS flags.
- Protect and back up CA private keys securely; establish rotation and disaster-recovery procedure. Production may replace Caddy internal CA with an organization-managed PKI.
- Public network ports: HTTPS only for authorized LAN/VPN segments. Never expose PostgreSQL or internal management endpoints.

See [Caddy internal TLS documentation](https://caddyserver.com/docs/caddyfile/directives/tls) for limits of automatic trust installation.

## Bootstrap and credentials
1. Start server with a **single-use admin setup secret generated locally**, constrained to a private host console or similarly controlled setup. No shipped universal password.
2. Administrator enrolls with protected credentials; other accounts created only through authenticated role-managed flows. Configure password storage, sessions and lockout/rate limiting.
3. Admin creates a short-lived, one-use enrollment token associated with a device group and immutable disclosed policy version. PostgreSQL stores only a verifier/hash.
4. On the endpoint, the trusted admin installer checks server hostname and certificate chain, displays the organization notice and obtains pairing approval.
5. Agent generates a unique asymmetric key and performs one-time exchange using the token; server atomically consumes token and registers a new `device_id` bound to credential thumbprint.
6. Thereafter agent requests prove possession of the device key. Exact mTLS/proof-of-possession mechanism must be chosen before implementation; do not fall back to a long-lived shared API password.
7. Device credential revocation blocks heartbeat and upload. Key rotation, re-enrollment, server CA rotation and lost-identity cases are documented and audited.

## Identity lifecycle
New install -> pending -> enrolled -> active -> revoked/retired. If local identity and SQLite are lost, a new enrollment creates a **new device_id**. Administrators may record an explicit replacement link to the old record; never stitch together two event sequence histories.

## Failure behavior
Invalid/missing trust root: refuse connection. Used/expired token: refuse pairing. Duplicated device public key or enrollment conflict: require explicit administrator action. Server unreachable: keep previously enrolled and buffered events in bounded SQLite, without collecting newly unauthorized data.

## Open sign-off
Choose the initial hostname, CA trust distribution, device-key storage, and per-request authentication binding before writing the v0.1 enrollment code. These are open in [D-07](../governance/open-decisions.md).
