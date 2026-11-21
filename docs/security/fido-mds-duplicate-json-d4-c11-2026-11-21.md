# A11-D4-C11 — FIDO MDS compact JWS duplicate-JSON denial

**Date:** 2026-11-21 · **Branch:** `main` only · **State:** implementation submitted, exact functional-SHA CI PENDING · **Release:** **v0.1 NOT RELEASE READY**.

## Why this is distinct from D4-C10

D4-C10 prevents multiple FIDO metadata **entries** from claiming the same AAGUID, AAID or U2F attestation key identifier. However, a single otherwise valid ES256/RS256-signed JWS may contain duplicate **JSON object member names**, including `no`, `entries`, `alg`, `status` or nested future extension fields. JSON object parsers can discard one occurrence (first-wins or last-wins), making later status interpretation ambiguous. Even an otherwise valid signed BLOB cannot be treated as unambiguous when its raw JSON duplicates a key.

## Implementation

- New **package-private, non-Bean** `StagedFidoMdsStrictJsonScreen` invokes existing Spring Boot 4 **Jackson 3** streaming `StreamReadFeature.STRICT_DUPLICATE_DETECTION`. No custom JSON tokenizer, dependency, service or network.
- Parse the **original Base64URL-protected compact header and payload segments** from exactly three JWS segments, rather than a Nimbus-reserialized Map which might already have discarded duplicate keys. Reject duplicate field names **at any nested depth**, escaped-key collisions, non-object roots, trailing second root and malformed/oversized compact input.
- Bound header to 128 KiB and decoded payload to 24 MiB (existing compact JWS upper bound 32 MiB still applies). These are *project-local denial limits*, not official FIDO requirements; legitimate upstream metadata beyond these bounds remains rejected until separately reviewed.
- Call screen in unmounted `StagedFidoMdsSourceVerifier.inspectOfflineOnly` before Nimbus header/payload interpretation; **leave ES256/RS256 signature, signer SAN/EKU, caller-supplied root pin, PKIX/CRL revocation, domain-separated signed-time transcript, sequential blob/time and D4-C9/C10 entry validation intact**.
- Add direct malformed/escaped/trailing-key unit tests, plus valid **disposable ES256 and RS256 JWS** carrying duplicate top-level, entry, status and optional-extension member names. Re-sign time with the exact JWS/CRL/claimed root for each case; even with valid synthetic crypto, all ambiguous BLOBs MUST throw `AccessDeniedException`. Valid unique synthetic entries still return `UntrustedSourceEvidence`, never a grant.
- Update real Gradle JUnit XML evidence gate to require `signedJwsWithDuplicateJsonFieldsAtAnyDepthRejectsEvenWithValidTimeAndCrl` (nine mandatory verifier tests total).

## Acceptance gate (do not mark PASS until evidence)

- JDK 25 `server-unit.yml`: real Gradle unit tests + nine mandatory executed JUnit verifier methods.
- PG18.6 Testcontainers: independent synthetic database transactions and negative backend checks; this does **not** test an official MDS chain.
- Next.js fast and RD-2 OFF gates: non-regression of default-deny and disconnected frontend.
- The source SHA, job links and conclusions must be filled **only after successful actual CI**, not inherited from D4-C10.

## What is NOT solved

**No independently approved actual FIDO MDS signing root, signer provenance, source CRL distribution/custody, production trusted time, licensed official metadata acquisition, persistent and approved anti-rollback, authenticator status admission, live administrator WebAuthn MFA, KMS/issuer authorization, or Pop!_OS 24.04 L3.** Nothing is mounted into Spring Security, WebAuthn4J, HTTP, the Agent, runtime scheduler or CA signer. Production networking, collection, automatic recovery, remote control and certificate issuance remain **OFF**; v0.1 **NOT RELEASE READY**.

Technology references: [Spring Boot 4 migration / Jackson 3](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide) · [Jackson `STRICT_DUPLICATE_DETECTION`](https://javadoc.io/static/tools.jackson.core/jackson-core/3.1.7/tools.jackson.core/tools/jackson/core/StreamReadFeature.html). This source check is deliberately stricter than permissive JSON parsing and is **not** a claim of official FIDO licensing or acceptance.
