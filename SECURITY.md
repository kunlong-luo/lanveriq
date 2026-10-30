# Security policy (pre-release)

**Status:** Design repository; no supported production software or releases. This document does not certify safety or offer production support.

## Reporting
Avoid public issues containing exploitable vulnerabilities, private employee data, tokens, recordings or credentials. Contact the repository maintainer through a private GitHub vulnerability report / private channel if enabled; otherwise request a secure reporting channel without disclosing the sensitive details publicly. No response-time SLA is promised.

## Scope
Security principles apply even in fast-development mode:
- Never implement hidden installation, covert recording, keylogging or OS permission bypass.
- Default deny anonymous access, insecure LAN transport and broad administrator viewing privileges.
- Use unique device identities, server-side RBAC and scoped data categories.
- Minimize recorded data; keep app/user-session capture notice visible and meaningful.
- Test data-loss, retention and revocation paths before real-device use.

## Development status
Unit-only CI does not replace manual auth, recovery, permission, secure deployment or eventually integration/E2E validation. This project must not be deployed with real employee data before legal/security review, configuration hardening and verified backups.
