# Linux Rust/OpenSSL CSR → Java 25 trust-profile interoperability (synthetic Batch A L2)

**Scope:** Pop!_OS 24.04 is the first-release target. This acceptance runs on **disposable Ubuntu 24.04 GitHub CI**, not the user's Pop!_OS device, and does not install/run a real Agent service. It extends [Linux-first Agent acceptance](linux-popos24-agent-acceptance.md).

## Contract under test

The existing Rust `apps/agent/src/enrollment_linux.rs` helper generates a fresh P-256 ECDSA/SHA-256 PKCS#10 CSR from a mode-0600 **exportable** private PEM inside a 0700 directory owned by a short-lived unprivileged Linux test UID. The **CSR subject is not authoritative identity**. An offline synthetic OpenSSL 3 CA, using a different root-only 0700 directory, signs a leaf with the reviewed test device's *canonical* URI SAN `urn:uuid:71d3df4a-c892-47c7-9d44-83666a3cb3ab`, only `clientAuth` EKU, `digitalSignature` key usage, `CA:FALSE`, and 30-day maximum expiry. The CA private key is never passed to Java, the Agent or an artifact. The local issuer is only a **disposable test fixture**, not an approved or deployable CA.

Only PUBLIC PEMs cross from the Rust/OpenSSL step to Java. The real `CertificateSigningRequestValidator` (Bouncy Castle PKCS#10 signature) derives the CSR SPKI SHA-256 digest. The real `ClientCertificateProfileValidator` (JDK PKIX + Bouncy Castle) validates the issuer, P-256 key/CSR-SPKI binding, exact UUID URI SAN, clientAuth-only EKU, digitalSignature-only key usage, bounded validity and public certificate fingerprint.

**Negative matrix:** wrong SAN, wrong device UUID, serverAuth-only certificate, separately generated Linux CSR/public key under the same issuer, wrong root CA, incorrect supplied CSR-SPKI digest, and a bit-tampered CSR signature must all be denied. Java JUnit XML must prove that **three methods ran and none were skipped**; a successful Gradle exit code alone is insufficient.

## Execution boundary

The path-scoped `.github/workflows/agent-linux-csr-leaf-interop.yml` runs only when the relevant Linux CSR/Java verifier/harness files change or on explicit dispatch. It builds the genuine Rust synthetic-only example with the locked Agent dependencies, executes `verify-linux-csr-issued-leaf.sh` under a disposable Ubuntu runner with a new unprivileged account and temporary CA, then runs `LinuxOpenSslCsrInteropTest` with Java 25. The script erases its CA signing keys, Agent private keys and disposable Unix user upon exit. A separate always-run CI step erases the public PEMs.

**Source status: ACCEPTED for the isolated cross-language PKI L2 subset only; see exact-SHA evidence below.** This does not demonstrate an authorized administrator review, actual client certificate registration in PostgreSQL 18, CRL/OCSP/credential revocation, a non-exportable Linux TPM key, real Caddy→Tomcat two-hop TLS, end-to-end activation/heartbeat ACK, or deployment on Pop!_OS. The Java pure validator explicitly disables PKIX revocation checks; runtime credential revocation needs a separate accepted gate. Real Agent networking is still forbidden by the idle systemd unit; remote access stays default-OFF.

Relevant standards and maintained tooling: [OpenSSL 3 `x509 -req -extfile`](https://docs.openssl.org/3.0/man1/openssl-x509/), [X.509 v3 extensions](https://docs.openssl.org/3.0/man5/x509v3_config/). Never accept issuer, CA key or user identity from the CSR's untrusted subject/SAN.

## 2026-11-12 observed acceptance

**Final exact source main SHA:** [`33e939f5d8334a4f81edd0cf04daae6e5d08d22d`](https://github.com/kunlong-luo/lanveriq/commit/33e939f5d8334a4f81edd0cf04daae6e5d08d22d).

- [Ubuntu 24.04 Linux Rust CSR → actual short-lived OpenSSL CA/clientAuth leaf → Java 25 validators, #43157407314 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43157407314). The native Linux CSR build, unprivileged generation/signing stage, actual Spring Bouncy Castle CSR and JDK PKIX client leaf checks, strict mandatory **three JUnit methods / zero skips / zero failures** XML assertion, and public PEM cleanup all succeeded.
- [RD-2 fail-closed remote release guard, #43157407475 — SUCCESS](https://github.com/kunlong-luo/lanveriq/actions/runs/43157407475). Real device collection, upload, production activation and remote access remained unchanged/default-OFF.

**Diagnostic history preserved:** Earlier attempts failed before the PKI validator because GitHub's runner checkout and runner temp ancestors blocked the disposable Unix account from executing the compiled example. Using an executable-only staging copy under `/tmp` preserved 0700/0600 Agent and CA custody. A subsequent attempt successfully signed the leaf but failed all three Java tests because a literal backslash preceded the GitHub `runner.temp` environment expression. That path was fixed; **only the final exact SHA run is accepted**. None of these failures required relaxing issuer/SPKI/SAN/EKU assertions.

**Still NOT_ACCEPTED:** this is a test CA and pure Java validators, not an approved device-issuer process, CA key provisioning, credential registration, CRL/OCSP or app revocation, live TLS1.3 Caddy→Tomcat, real PostgreSQL 18 activation/heartbeat/ACK, real Pop!_OS installation, or production Agent network. Keep Batch A open. The next Linux-first integration must prove those separately rather than promoting the synthetic issuer.
