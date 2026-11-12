# Pop!_OS 24.04 Linux-first Agent: offline identity and custody acceptance

**Owner decision (2026-11-11): Pop!_OS 24.04 LTS with COSMIC is the primary operating system.** Linux-first systemd, Rustls, secure local Agent operation and Pop!_OS installation/consent come before any new Windows SID/DPAPI/CNG service-account work. Windows synthetic crypto proofs remain historical, optional compatibility evidence, not critical-path blockers for the first Linux release.

## Approved baseline and scope

System76's Pop!_OS 24.04 release ships the COSMIC desktop and `systemd`. The repository's Linux `apps/agent/deploy/systemd/lanveriq-agent.service` is a **template only**, not a deployed service. It runs the existing **idle, no-collection** Rust binary only after a separately approved operator installation of the binary and root-managed `/etc/lanveriq-agent/agent.toml`. Repository checkout and CI do not call `systemctl enable` or `start`. COSMIC user-session permission/capture/remote-control is deliberately not part of this work.

The template isolates the Agent with `DynamicUser=yes`, `StateDirectory=lanveriq-agent` and `StateDirectoryMode=0700`, `UMask=0077`, no Linux capabilities, read-only OS filesystem, no home access, no elevated privileges or device access. **`PrivateNetwork=yes`, `IPAddressDeny=any`, `SocketBindDeny=any`, and `RestrictAddressFamilies=AF_UNIX` deliberately prevent IP network activity.** These cannot be loosened or the service enabled until separate privacy, enrollment, issuer, TLS, network and policy approval. `DynamicUser` uses a managed state directory; do not assume its numeric UID survives a reboot or manually `chown` that directory to a user. Service certificate/OS access remains a separate acceptance gate.

## Automated synthetic checks (no real endpoint)

The path-scoped `.github/workflows/agent-linux-popos24-custody.yml` uses **Ubuntu 24.04** CI because it shares Linux DAC and systemd conventions with Pop!_OS 24.04; it does **not** claim to execute on an actual Pop!_OS or System76 computer.

- Validate all offline policy directives and systemd syntax without installing any unit
- Build a synthetic-only Rust example that explicitly calls `create_offline_p256_csr` as a temporary, nonprivileged Linux UID; the production Agent never calls it
- Confirm that the freshly signed SHA-256/P-256 PKCS#10 CSR is valid and private bundle/key retain `0700` / `0600`
- Verify that a second **process under the same dedicated UID** can reopen the private key, whereas a **different unprivileged UID** cannot traverse the private parent, read or modify the private key
- Prove existing bundle cannot be overwritten and a `0755` parent is rejected; remove test accounts and keys in a cleanup trap

All private keys are ephemeral **exportable Linux PEM file keys** in this test. They do not demonstrate non-exportability, TPM 2.0, hardware-backed certificates, a reboot-persistent systemd DynamicUser state mapping, a real operating system service, full-directory TOCTOU defense, org authorization, actual Caddy/Spring/Tomcat mTLS, PostgreSQL 18 commit or Agent telemetry. Avoid confusing these security tiers. A later, approved Linux TPM2-backed key-provider integration should use maintained libraries such as `tpm2-pkcs11`/PKCS#11 and demonstrate Rustls signing without private-key export, if hardware and deployment policy require it; do not hand-roll crypto or expose the user's keys.

## Next release gates

1. **Linux service identity:** real disposable systemd-run/installed-unit (never on user hardware without permission), dynamic UID state mapping, persistence across restart, independent UID denial and key rotation/revocation.
2. **Linux mTLS:** controlled end-to-end Agent Reqwest → Caddy → Tomcat two-hop TLS 1.3 with negative issuer/SNI/revocation cases and a reviewed CA-signed client leaf.
3. **Database & business ACK:** disposable PostgreSQL 18 actual credential activation/heartbeat, immutable audit, exact request-ID ACK and failure/replay controls.
4. **Pop!_OS pilot:** only after notice, consent, signed package, rollback, OS privacy and explicit authorized endpoint approval. Remote desktop/COSMIC Wayland access is separately gated by RD-2.

**Safety decision:** batch A stays OPEN; real monitoring/upload and RustDesk remote are default-OFF. The Linux CI result alone does not authorize installation on the user's Pop!_OS.

References: [Pop!_OS 24.04 official](https://system76.com/download-pop/), [systemd service hardening](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec).

## 2026-11-11 observed CI evidence

**Exact source commit:** [`98b76be485a973c1fb947706c0ba2a7b0a3fe725`](https://github.com/kunlong-luo/lanveriq/commit/98b76be485a973c1fb947706c0ba2a7b0a3fe725). The first CI attempt failed at systemd's requirement for an already-installed binary, and initial Rustfmt changed the synthetic example. The systemd syntax checker now substitutes `/usr/bin/true` **only in an ephemeral verification copy** while the independent policy parser checks the real exact ExecStart; no Agent binary is installed. The final exact-source checks succeeded:

- [Linux state/DAC + OpenSSL P-256 synthetic L2 #43009141284 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43009141284), including two disposable Unix UID read/write/traverse negatives, private 0700/0600 key, signed CSR, same-UID separate process reopen, unsafe parent rejection, no overwrite and systemd policy/syntax
- [Agent Rustfmt, locked unit tests, Clippy #43009141303 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43009141303)
- [RD-2 fail-closed remote guard #43009141351 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43009141351)

**Acceptance is limited to the disposable Ubuntu 24.04 runner.** It is not a true Pop!_OS 24.04/COSMIC, installed service, systemd DynamicUser reboot, TPM-backed key, operational TLS or real device proof. Batch A remains OPEN and all real monitoring/remote features remain OFF.


## 2026-11-12 isolated systemd DynamicUser lifecycle restart gate (SOURCE ONLY UNTIL CI)

New CI-only scripts `apps/agent/tools/linux/verify-systemd-dynamic-restart.sh` and `systemd-csr-dynamic-stage.sh` use the GitHub-hosted Ubuntu 24.04 systemd manager to launch a **transient, temporary unit**, with `DynamicUser=yes`, `StateDirectoryMode=0700`, `PrivateNetwork=yes`, no IP sockets, no capabilities, no home access, `ProtectSystem=strict`, `UMask=0077` and a fixed temporary StateDirectory prefixed `lanveriq-linux-csr-ci.`. The first invocation calls the real Rust offline CSR helper as the transient unprivileged service identity. A **second invocation of the same transient unit name** proves the retained 0600 P-256 PEM file can still be reopened, its SHA-256/PKCS#10 CSR signature and public-key match remain valid, and `INVOCATION_ID` differs. An unrelated unprivileged UID is denied access before and after, and the sandbox must reject actual IPv4 socket creation.

The helper and stage script are **copied to a disposable `/run` path**, so the real Agent binary is never installed or started. The service retains only a synthetic public CSR digest and private key in systemd-managed test state; its cleanup removes the scoped transient unit, test StateDirectory and temporary files. No real Agent service, actual Pop!_OS device, telemetry or network access is used. The runner's temporary systemd service is a genuine systemd execution context, distinct from a file-permission test using `runuser`.

**Confirmed 2026-11-12 at exact source commit `00ba1bdc26cab32b20a9a6734748d09aea6305aa`:** The [Ubuntu 24.04 systemd DynamicUser L2 CI #43140369914](https://github.com/kunlong-luo/lanveriq/actions/runs/43140369914) completed **SUCCESS**. The log records the two actual transient systemd service invocations, a distinct `INVOCATION_ID`, the retained 0600 P-256 key and matching signed CSR, unrelated-UID denial before/after, and rejected IPv4 socket creation. This proves across **service executions only**, not across a machine reboot. The same SHA also passed [Rust Agent Rustfmt/locked unit tests/Clippy #43140369870](https://github.com/kunlong-luo/lanveriq/actions/runs/43140369870) and [RD-2 default-OFF guard #43140369976](https://github.com/kunlong-luo/lanveriq/actions/runs/43140369976). It does not prove real Pop!_OS/COSMIC implementation, TPM-backed non-exportable key, production service installation, Caddy→Tomcat TLS, PostgreSQL, legal consent, or approved use of device privileges. UID numbers are deliberately not treated as stable: systemd may use ID-mapped storage. The retained PEM remains exportable to the authorized service account. Follow systemd's [DynamicUser/StateDirectory documentation](https://www.freedesktop.org/software/systemd/man/latest/systemd.exec.html).


## 2026-11-12 next Linux PKI interoperability milestone

The separate Linux-first [Rust CSR → temporary OpenSSL client certificate → Java PKIX acceptance](linux-pki-spki-interop.md) now passed at source `33e939f5d8334a4f81edd0cf04daae6e5d08d22d`, [CI #43157407314](https://github.com/kunlong-luo/lanveriq/actions/runs/43157407314). The **same Rust helper's real CSR** is checked against a 30-day clientAuth-only/URI-SAN certificate with dedicated synthetic root, and negative wrong-issuer/SPKI/SAN/EKU and tampered-signature cases are not skipped. Still no actual Pop!_OS host, production CA approval, Caddy→Tomcat live mTLS or PostgreSQL18 Agent activation. This is a separate offline PKI L2 subset, not Batch A completion.


## 2026-11-12 live Linux synthetic mTLS/PG18 milestone

The Linux-first [Rust CSR → temporary clientAuth leaf → Caddy → Spring/Tomcat TLS1.3 → PostgreSQL18](linux-caddy-spring-pg18-rust-csr.md) milestone passed at exact main source `5444eec68db6c2b4b460d15c61a44717bf5d9cf6` in [Linux Rust CSR → live Caddy/Tomcat mTLS + PG18, CI #43186315505 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43186315505). Both Caddy/Tomcat health/mTLS and separately isolated eight-suite PostgreSQL18/≥60 transaction evidence jobs succeeded, with Agent business API still 403/default-OFF. The **tested client is curl using a synthetic Rust-generated key**, not an installed Agent Reqwest service; Ubuntu 24.04 is not real Pop!_OS/COSMIC. Registration/activation/heartbeat/ACK across a single HTTPS business transaction and revocation/issuer review remain separately gated.
