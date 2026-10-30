# LAN bootstrap and device enrollment — v0.1 design

**Status:** documented proposal, no runnable installer. Device enrollment occurs only on managed, notified organization equipment. For operational simplicity, use a verified set of purpose-separated LAN hostnames in the D-07 candidate below; never infer trust merely from a local IP address.

## Server TLS design
- One Caddy service with purpose-separated HTTPS hosts (e.g. console/enroll/agent; exact LAN DNS names and TLS site policies remain D-07 decisions).
- **Initial pilot option:** Caddy `tls internal` as a private CA. Install the CA root into the operating-system trust store on each managed endpoint/admin workstation **through a verified, authorized channel** and verify its fingerprint out-of-band.
- Caddy in Docker may not install its root into the **host/endpoint** trust stores automatically. Treat that as a provisioning task, **never bypass verification** with insecure TLS flags.
- Protect and back up CA private keys securely; establish rotation and disaster-recovery procedure. Production may replace Caddy internal CA with an organization-managed PKI.
- Public network ports: HTTPS only for authorized LAN/VPN segments. Never expose PostgreSQL or internal management endpoints.

See [Caddy internal TLS documentation](https://caddyserver.com/docs/caddyfile/directives/tls) for limits of automatic trust installation.

## D-07 concrete pilot proposal for maintainer review (NOT APPROVED)

**Ingress partition and DNS (example names, not provisioned):**
- `console.lanveriq.internal:443` serves the same-origin browser SPA, session-authenticated REST and `/ws` WSS. No client certificate requirement; strict Origin, session, CSRF and role/group checks.
- `enroll.lanveriq.internal:443` serves **only** bounded initial enrollment/certificate-pickup operations. TLS server identity is mandatory, but a not-yet-enrolled device cannot be required to supply a client certificate. One-use token, CSR proof-of-possession, authorization/notice binding, throttling and short expiry apply.
- `agent.lanveriq.internal:443` serves **only** enrolled Agent heartbeat, event batches, inventory and approved agent endpoints. Its TLS listener/site requires `client_auth { mode require_and_verify ... }` against a **dedicated client-identity CA**. Other site hosts/paths must not proxy the protected Agent handlers. Separate site names do not by themselves prove correct SNI/TLS policy isolation; validate Caddy's adapted configuration, actual browser/Agent TLS handshakes and connection reuse.

**Preferred pilot certificate issuance (low-volume, human-operated; no bespoke CA service):**
1. An authorized operator provisions the **server-trust root** out-of-band with the real hostnames/fingerprint; Caddy internal PKI may be used for *server* TLS only. Keep a separate **client-identity signing CA**, with encrypted/offline signing key and a backed-up recovery procedure.
2. Agent locally generates a per-device private key and CSR. The first-time installer submits the CSR with the short-lived one-use enrollment token and acknowledged notice to the **enrollment** host. PostgreSQL atomically consumes the token, assigns canonical UUIDv7 `device_id` and records **pending_certificate**, not `active`. Validate CSR signature, key algorithm, length, size and rate limits.
3. Admin reviews pending device/group/CSR fingerprint and **manually signs the CSR** with a reviewed `step` CLI workflow and clientAuth-restricted certificate template. The certificate identity is bound to the server-issued `device_id`; an untrusted CSR-supplied name is never authoritative. Admin imports the signed leaf/chain and fingerprint into the server's credential record, then delivers the certificate to the authorized installer over a verified provisioning channel. The private key never leaves the Agent and the CA signing key never moves to the public web service.
4. Agent makes its first **mTLS** request to the Agent host; Caddy validates the client certificate chain, while Spring matches a **proxy-asserted, verified certificate fingerprint** to the unrevoked `device_credentials` row, exact `device_id`, status and allowed route on **every request**. Only then mark enrollment `active`. A lost enrollment response/token requires a new admin-approved attempt; do not silently reuse consumed tokens.
5. For 1–10 endpoints, operator-mediated certificate installation is an explicit UX/operational cost. An automatic online CA (e.g. reviewed step-ca) is an **alternative requiring separate risk and deployment review**, not secretly introduced in V0.1. No hand-rolled X.509 issuer.

**Caddy → Spring assertion boundary:** strip all client-supplied `X-Lanveriq-Verified-Client-Fingerprint` and similar identity headers at ingress. Only the Agent TLS site may overwrite that exact header with Caddy's *verified* leaf fingerprint after successful client-auth. Spring listens on an isolated non-public container/Unix-socket network (or independently authenticated upstream transport), rejects absent/ambiguous identities, validates the active credential record and refuses direct access from the LAN. Confirm actual Caddy placeholder/header behavior with `caddy adapt` and requests, not documentation guesses. Certificate chain validation alone is **not** per-device revocation: Spring DB revocation must take effect immediately, including when TLS connections are reused.

**Key custody and rotation candidate:** Windows: service-identity-restricted key material protected by OS ACL and, where compatible with reqwest/rustls, DPAPI encryption at rest; Linux: service-account-only mode 0600/0700 local storage with host disk encryption where available. Do **not** promise non-exportable Windows CNG keys work with rustls until explicitly demonstrated. No private keys in SQLite, logs, repo, browser storage or CLI arguments. Pilot leaf lifetime proposal: 90 days with operator-led rotation before expiry; overlap old/new credentials only during audited transition, and a revoked old credential must fail immediately. Record recovery from compromised issuer/key, damaged Agent database, expiration and isolated CA backup restoration. Final validity/key-storage choices remain subject to D-07 sign-off.

**Required negative evidence after implementation:** browser/pre-enrollment unaffected by Agent mTLS; unauthenticated, wrong-CA, forged forwarded header, wrong device ID, expired, rotated/revoked and cross-group credentials rejected; direct Spring route bypass impossible; stolen/reused enrollment token rejected; revoked active connection cannot continue uploading; trust-root mismatch fails closed. Document dates, Caddy version, OS/crypto backend and observed results.

Official reference: [Caddy TLS client authentication](https://caddyserver.com/docs/caddyfile/directives/tls), [Caddy reverse-proxy header controls](https://caddyserver.com/docs/caddyfile/directives/reverse_proxy), [Smallstep CSR signing](https://smallstep.com/docs/step-cli/reference/certificate/sign/). This is an **architecture proposal**, not a tested Caddyfile or approved PKI operation.

## Bootstrap and credentials
1. Start server with a **single-use admin setup secret generated locally**, constrained to a private host console or similarly controlled setup. No shipped universal password.
2. Administrator enrolls with protected credentials; other accounts created only through authenticated role-managed flows. Configure password storage, sessions and lockout/rate limiting.
3. Admin creates a short-lived, one-use enrollment token associated with a device group and immutable disclosed policy version. PostgreSQL stores only a verifier/hash.
4. On the endpoint, the trusted admin installer checks server hostname and certificate chain, displays the organization notice and obtains pairing approval.
5. Agent generates a unique asymmetric key and valid CSR; the server **atomically consumes the token and allocates `device_id` in `pending_certificate` state**. There is no client certificate or active credential thumbprint yet.
6. An authorized operator reviews and signs the CSR using a reviewed client-CA workflow, registers the resulting certificate fingerprint against that device ID, and delivers only the signed certificate/chain to the approved installer. Keep a **bounded expiry and administrator cleanup** for abandoned `pending_certificate` enrollments. This human step is an explicit proposed V0.1 pilot workflow, not an implemented certificate-issuance service.
7. Agent proves possession over **mTLS** on the separate Agent ingress. Spring validates the Caddy-verified certificate fingerprint and exact active device credential binding before transitioning to `active` and accepting heartbeats. Revocation, key rotation, expired certificates and lost key/DB require audited repair; no fallback long-lived shared API password.

## Identity lifecycle
New install -> token-validated `pending_certificate` -> signed credential registered -> first verified mTLS proof -> `active` -> revoked/retired. Incomplete/expired pending requests remain unable to upload. If local identity and SQLite are lost, a new enrollment creates a **new device_id**. Administrators may record an explicit replacement link to the old record; never stitch together two event sequence histories.

## Failure behavior
Invalid/missing trust root: refuse connection. Used/expired token: refuse pairing. Duplicated device public key, duplicate CSR/credential binding, abandoned pending enrollment or enrollment conflict: require explicit administrator action. Server unreachable: keep previously enrolled and buffered events in bounded SQLite, without collecting newly unauthorized data.

## Open sign-off
Choose browser/enrollment/Agent ingress hostnames and route boundaries, Caddy client-auth policy, CA trust distribution, certificate issuance/renewal, OS-protected key storage, revocation behavior and per-request authenticated identity mapping before writing V0.1 enrollment code. A setup diagram is not a deployed PKI. These are open in [D-07](../governance/open-decisions.md).
