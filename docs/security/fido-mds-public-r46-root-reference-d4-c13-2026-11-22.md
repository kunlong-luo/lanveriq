# A11-D4-C13 — GlobalSign R46 public root reference candidate (not trusted)

**Date: 2026-11-22. Single development branch: `main`. New exact-source CI: PENDING. v0.1 NOT RELEASE READY.**

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

## What this still does NOT establish

The candidate DER SHA-256 is public **identity evidence, not evidence of how it was downloaded**, of who accepted a trust root, of approved distribution, of fresh externally obtained CRLs or a trusted time authority. No FIDO BLOB is downloaded; no PKI root is provisioned or trusted by Spring/WebAuthn4J. No independent organizational root approval, reviewer custody/real MFA, hardened fetch with ETag and monotonic registry, CRL custody/signature source, actual production trusted clock, status admission, CA/KMS signing or Pop!_OS 24.04 L3 exists yet. **Production networking, device collection, auto-recovery, remote control and certificate issuance remain OFF. v0.1 NOT RELEASE READY.**
