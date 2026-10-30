# Identity, device enrollment and authorization

## Administrator access
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
4. Server marks token consumed atomically and issues per-device credentials bound to key/device, with expiration.
5. Agent stores private material using host-protected credential storage; never logs or uploads secrets.
6. Re-enrollment, recovery and rotation are explicit operations with audit trail and data continuity plan.

## WebSocket authorization
The dashboard opens `wss://<lan-host>/ws` using its existing Secure/HttpOnly same-origin session cookie; the server validates authentication, the exact approved `Origin`, device-group subscription scope and session revocation. No API key in URL/query, no generic wildcard subscriptions, no anonymous video notifications, and no WS channel for policy mutations. See [WebSocket contract](../architecture/websocket-contract.md) and [RBAC drawing](../diagrams/rbac-matrix.md).

## Wire protections
Use TLS even on LAN; never set 'insecure skip verify'. Limit enrollment endpoint rate/size and payload. Revoked credentials are denied. Replays/different payload same event sequence cause a conflict; do not overwrite previously recorded evidence.

## Security tests before usable pilot
Credential revocation, token replay, expiry, wrong-device ID, cross-scope read/exports, case approval denial, oversized requests, path traversal and insecure transport. Unit-only CI is not a substitute for targeted manual integration checks on real devices.
