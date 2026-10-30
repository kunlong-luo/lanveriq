# Identity, device enrollment and authorization

## Administrator access

**AUTH-01 partial implementation (2026-10-30):** `AdminIdentityUserDetailsService` loads existing admins through Spring Data JPA for Spring Security's `DaoAuthenticationProvider`; Argon2id password hashes use a reviewed Spring Security `PasswordEncoder`, without a plaintext/default account. `AdminScopeService` checks current active account, role and assigned group on each operation, and Flyway V4 makes `internal_operational` a separate non-seeded grant for IP/MAC network details. `AuthenticatedEnrollmentTokenIssuer` takes its principal from the authenticated server context, not a caller-supplied admin ID. [Unit CI](https://github.com/kunlong-luo/lanveriq/actions/runs/41066726762) is green. **No HTTP login handler or browser session is enabled**; the startup bootstrap, login throttling, TLS origin isolation, CSRF, session invalidation, real PostgreSQL migrations, role revocation integration and WSS path are pending. Do not treat the new provider bean as authorization to open login routes.

Start with a single administrator created via a secure first-run bootstrap (no hardcoded/default password). Require password hashing using a maintained password hashing function, secure cookie/session handling, CSRF defenses as appropriate, rate limiting and session expiration. Design optional MFA for operations; enforce MFA for recording review before production rollout.

## Roles
- Owner/security admin: device-group policy, roles, retention, revoke keys.
- IT operator: enroll, manage and diagnose devices, **not automatic recording access**.
- Security auditor: bounded event queries in permitted device groups.
- Recording reviewer: permitted approved time windows and case-specific video access.
- Device user: local status/policy view without admin API access.

Enforce **role + device group + data category + time window** at the server API, not by hiding links in React. **V0.1 already requires** admin login and group-scoped inventory/monitoring/WSS reads; V0.2 extends audit event and export permissions. IP/MAC/asset-tag details are sensitive operational data, not public dashboard badges. Log reads/denials/exports, especially recording. No superuser bypass outside documented emergency process.

## Enrollment
1. Authenticated admin creates expiring single-use token hashed in PostgreSQL; chooses device group and published policy.
2. Installer verifies LAN server certificate via explicitly installed internal CA and sees organization/device policy notice.
3. Device generates unique identity key; token proves approval only for first pairing.
4. In the proposed D-07 pilot flow, server atomically consumes token and validates a CSR, creates UUIDv7 device identity in **pending_certificate**, and does **not** immediately activate a client credential.
5. An authorized operator signs/reviews the CSR through a dedicated client CA and securely delivers the leaf certificate; registration binds the fingerprint to the pending device. Agent keeps its key in service-protected local storage, not in SQLite; never logs/uploads the private key.
6. Agent proves possession on the mandatory mTLS ingress; Spring activates only the exact verified, unrevoked credential. Re-enrollment, expiry, abnormal pending state, recovery and rotation require audited operator action. See [candidate bootstrap](../architecture/enrollment-bootstrap.md).

## WebSocket authorization
The dashboard opens `wss://<lan-host>/ws` using its existing Secure/HttpOnly same-origin session cookie; the server validates authentication, the exact approved `Origin`, device-group subscription scope and session revocation. No API key in URL/query, no generic wildcard subscriptions, no anonymous video notifications, and no WS channel for policy mutations. See [WebSocket contract](../architecture/websocket-contract.md) and [RBAC drawing](../diagrams/rbac-matrix.md).

## Wire protections
Use TLS even on LAN; never set 'insecure skip verify'. Limit enrollment endpoint rate/size and payload. Revoked credentials are denied. Replays/different payload same event sequence cause a conflict; do not overwrite previously recorded evidence.

## Security tests before usable pilot
Credential revocation, token replay, expiry, wrong-device ID, cross-scope read/exports, case approval denial, oversized requests, path traversal and insecure transport. Unit-only CI is not a substitute for targeted manual integration checks on real devices.
