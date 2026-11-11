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
