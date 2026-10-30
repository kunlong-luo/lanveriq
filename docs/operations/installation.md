# LAN installation and maintenance runbook (planned)

**No installable software or compose files exist yet.** This document describes the desired procedure and verification steps.

## Server prerequisites
Dedicated supported Linux host; local/NAS disk sized for media; Docker Engine and Compose installed; internal DNS or static hostname; trusted internal CA; reserved LAN IP; backup location. Firewall: only HTTPS for authorized subnets, SSH restricted to administrators, PostgreSQL private to Compose network.

## Planned operator workflow
1. Verify OS, network, free disk, synchronized clock, backups and intended server name.
2. Install trusted local CA certificate to authorized devices. Refuse insecure TLS.
3. Configure server secrets with restrictive host permissions (database user/password, first-run admin setup, application secrets); never put real secrets in repository.
4. Start PostgreSQL/Caddy/Spring Boot/dashboard via `docker compose up -d` (once deployed); run Flyway migration and health checks.
5. Complete secure first-run admin bootstrap; enforce no shared default passwords.
6. Configure device groups, collection policy, allowed events/directories, retention and user notice.
7. Issue short-lived one-use pairing token for each endpoint.
8. For **V0.1 pilot**, install/start the native service through an approved manual test procedure and verify trust, notice, device enrollment, heartbeat, inventory freshness and access scope. **MSI/DEB installers are later V0.4 distribution targets**; do not imply packages already exist.
9. Validate offline/reconnect replay with **synthetic test events** and current inventory (multiple NICs/IPs). Real application/OS audit ingestion is V0.2; screen recording V0.3 is not installed in the early MVP.
10. Back up DB and secrets in separately restricted storage; test restore before real deployment.

## Local-only operation
Application runtime must not depend on GitHub, CDN, public DNS, SaaS analytics, AI APIs or Internet licensing. Record OS distro/compositor and capability support for every device.

## Troubleshooting checklist
TLS chain/hostname; enrollment expiry; device credential revocation; time drift; SQLite queue saturation; PostgreSQL migrations; API ingestion limits; filesystem permissions; audit source allowlists; video OS grant and storage volume (later). Never disable TLS or OS capture protections for troubleshooting.

## Upgrades/rollback
Small versioned releases, recorded schema compatibility, pre-upgrade verified backup and safe rollback plan. Never overwrite incompatible SQLite store without migration; never claim silent uninstall/installation.
