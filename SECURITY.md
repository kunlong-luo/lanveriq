# Lanveriq security policy (pre-release)

**2026-11-22 · v0.1 NOT RELEASE READY.** There are actual Java/Rust/Next.js modules and real **synthetic CI** for PostgreSQL 18.6, Caddy/Spring TLS1.3, SQLite/ACK and Chromium. These are NOT supported production security claims, legal authorization, or a response-time SLA.

## Report vulnerabilities privately
Never post live keys, credentials, employee data, exploitable endpoints, recordings or sensitive exploit steps publicly. Use private GitHub vulnerability reporting if enabled; otherwise request a secure channel without exposing secrets. A dedicated security contact/SLA has not been independently confirmed.

## Boundaries
- Production networking, device/employee collection, automatic recovery, remote control RD-2, live admin login, CA issuance and official FIDO trust are **default OFF / unapproved**.
- Client identity needs PKIX/mTLS, scoped server RBAC and session/Host/Origin checks. IP, headers and HTTP 200 are not identity or durable ACK. Expired/revoked/wrong-CA, replay and audit suppression must fail closed.
- D4-C9–C16 FIDO stages only inspect **untrusted synthetic** signed blobs, R46 public reference, acquisition declarations, PG V30 and offline witnesses. Public R46 identity is not authorized root; a signed TLS/CRL claim is not proof of an actual official download; a PG audit/digest is not externally immutable; off-DB WORM/KMS/trusted time have not been deployed. [FIDO boundaries](docs/security/fido-trust-boundaries.md).
- No covert monitoring, OS privilege bypass, real staff data, hidden recordings or unauthorized WAN exposure. Pop!_OS24 physical L3, legal/privacy approval, true WebAuthn reviewer identity, key custody, restore and license audits remain OPEN.

Read [current fact sheet](docs/CURRENT_STATUS.md), [release gaps](docs/planning/release-gap-register.md), [testing strategy](docs/testing/strategy.md) and [engineering playbook](docs/governance/engineering-playbook.md). Preserve failed SHAs; only a real newly executed same-SHA test is evidence of a fix.
