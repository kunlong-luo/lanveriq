# A11-D4-C13 — GlobalSign R46 public root reference candidate (not trusted)

**Date: 2026-11-22. Single development branch: `main`. 5/5 exact-functional-SHA synthetic CI: SUCCESS. v0.1 NOT RELEASE READY.**

## Verified public information and audit references

FIDO Alliance's [MDS overview](https://fidoalliance.org/metadata/) identifies **GlobalSign Root R46** as the current signing trust-chain root. The [2026-08-31 FIDO changelog](https://fidoalliance.org/mds-changelog/) explains the new x5c[2] R3→R46 cross-certificate (legacy transition), which is **not** the self-signed R46 root. Public root identity is corroborated by the [GlobalSign root certificate page](https://support.globalsign.com/ca-certificates/root-certificates/globalsign-root-certificates) and [Mozilla CCADB root inclusion record R00000947](https://ccadb.my.salesforce-sites.com/mozilla/PrintViewForCase?CaseNumber=00000469).

Published **self-signed R46 SHA-256 DER fingerprint**:
`4FA3126D8D3A11D1C4855A4F807CBAD6CF919D3A5A88B03BEA2C6372D93C40C9`.

Published **SHA-1 reference thumbprint** (not a security decision):
`53A2B04BCA6BD645E6398A8EC40DD2BF77C3A290`.

Published serial: `11D2BBB9D723189E405F0A9D2DD0DF2567D1`. Subject/issuer: `CN=GlobalSign Root R46,O=GlobalSign nv-sa,C=BE`. RSA-4096 / SHA384withRSA / expires 2046-03-20. Hash evidence is public information; there is **no real organizational or production trust-anchor approval**.

## What changed

- `StagedFidoMdsR46PublicReferenceScreen`: package-private, offline-only, deterministic **public certificate identity test** for DER X.509v3, exact published SHA-256, subject=issuer, serial, RSA-4096 SHA384 signature, CA/keyCertSign/cRLSign, critical-extension policy, self-signature, canonical DER and caller-supplied test time. Returns only `UntrustedPublishedRootReference` containing the SHA-256; **not an X509 TrustAnchor, issuer, Spring bean, authorization or allowlist for devices**.
- `StagedFidoMdsSourceVerifier.inspectWithPublishedR46ReferenceOfflineOnly`: new optional, unmounted offline lane validates that exact published DER identity matches the claimed root's actual encoded bytes **before** invoking C12 independent two-key review and existing JWS/PKIX/CRL/signed-time checks. A fake CA cannot substitute merely because its own JWS, CRL, claimed root hash and two disposable review signatures are consistent.
- `apps/server/src/test/resources/security/untrusted-reference-only/globalsign-root-r46.pem` is a **public certificate copied from GlobalSign's page** solely to test reference parsing offline. It is explicitly NOT configured as Spring Security, Java truststore, PKIX production anchor, WebAuthn4J metadata truststore or issuing CA.
- Tests assert the true published R46 reference produces an *untrusted* candidate, mutated/truncated/extra-byte/expired candidates reject, and genuinely signed disposable ES256 and RS256 fake-root JWS with two valid review signatures **both reject** through the R46-protected lane; even a real R46 root paired with a synthetically issued signer must reject at PKIX.
- Java 25's real Gradle JUnit XML checker now requires two new verifier methods (**12 mandatory methods total**). Also require PostgreSQL 18.6 Testcontainers synthetic transactions, PostgreSQL integration-source compile, Next.js fast regression and RD-2 OFF at the **functional SHA**.

## Acceptance: 5/5 exact-functional-SHA SUCCESS

[functional SHA `e73e6824`](https://github.com/kunlong-luo/lanveriq/commit/e73e6824a1ad09cc9d389f5d6392409078408e58) passed **5/5 exact-SHA synthetic CI**: [Java 25 real Gradle + 12 mandatory executed FIDO JUnit methods #45011880180](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880180), [PostgreSQL 18.6 real Testcontainers synthetic transactions #45011880254](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880254), [PostgreSQL integration-source compile #45011880190](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880190), [Next.js fast source/typecheck/build #45011880239](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880239), [RD-2 fail-closed default-OFF #45011880178](https://github.com/kunlong-luo/lanveriq/actions/runs/45011880178).

The actual server-unit log contains `BUILD SUCCESSFUL in 39s` and `PASS: real Gradle JUnit evidence includes all 12 required synthetic offline source-time tests`. The PostgreSQL 18.6 workflow ran actual Testcontainers transactions and its mandatory XML check. The **public R46 test certificate** was parsed and matched by code against the independently published SHA-256; simulated ES256/RS256 impostor roots failed the stricter optional offline verifier path. All checks are tied ONLY to source SHA `e73e6824` and do not establish trusted official signer provenance.

The published SHA-256 reference matches GlobalSign/Mozilla CCADB records; this is an **offline public root-identity check only**, NOT an organizationally approved FIDO signing trust anchor, independently authenticated CRL retrieval/source, licensed official MDS production feed, trusted production time, real two-person WebAuthn MFA, HSM/CA issuance, or Pop!_OS 24.04 L3. Production network, collection, automatic recovery, remote control and certificate issuing remain OFF; v0.1 NOT RELEASE READY.

## What this still does NOT establish

The candidate DER SHA-256 is public **identity evidence, not evidence of how it was downloaded**, of who accepted a trust root, of approved distribution, of fresh externally obtained CRLs or a trusted time authority. No FIDO BLOB is downloaded; no PKI root is provisioned or trusted by Spring/WebAuthn4J. No independent organizational root approval, reviewer custody/real MFA, hardened fetch with ETag and monotonic registry, CRL custody/signature source, actual production trusted clock, status admission, CA/KMS signing or Pop!_OS 24.04 L3 exists yet. **Production networking, device collection, auto-recovery, remote control and certificate issuance remain OFF. v0.1 NOT RELEASE READY.**
