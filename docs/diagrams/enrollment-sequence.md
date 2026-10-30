# D-04 — Certificate trust and device enrollment (D-07 PILOT CANDIDATE ONLY)

**Not approved, not executable:** three purpose-separated TLS hostnames and human-operated client-CA signing must receive D0 sign-off. This flow deliberately adds a pending certificate state before an Agent can become active.

~~~mermaid
sequenceDiagram
  autonumber
  actor Operator as Authorized IT operator
  participant UI as Browser console host
  participant Server as Spring Boot (protected upstream)
  participant PG as PostgreSQL
  participant Agent as Rust Agent
  participant Key as Agent key store
  participant CA as Offline client CA (step CLI)
  Operator->>UI: Create approved one-use pairing token
  UI->>Server: POST /api/v1/enrollments (session + CSRF)
  Server->>PG: Hash token; bind group, expiry and notice
  PG-->>Server: Commit
  Server-->>UI: Display token once
  Operator->>Agent: Provision trusted server CA root + pairing token
  Agent->>Agent: Verify enrollment-host TLS and prior notice
  Agent->>Key: Generate private key and signed CSR
  Agent->>Server: POST enrollment-host /api/v1/devices/enroll (token + CSR)
  Server->>PG: Atomic consume token + uuidv7() + pending_certificate
  alt Token valid and CSR acceptable
    PG-->>Server: Commit pending identity
    Server-->>Agent: 202 pending_certificate + device_id
    Operator->>Server: Review authorized pending CSR, device and group
    Operator->>CA: Sign reviewed CSR with clientAuth certificate
    CA-->>Operator: Leaf certificate and CA chain only
    Operator->>Server: Register certificate fingerprint for device
    Operator->>Agent: Deliver leaf/chain over trusted channel
    Agent->>Key: Install cert with protected private key
    Agent->>Server: Agent-host HTTPS mTLS first proof (verified Caddy)
    Server->>PG: Match verified fingerprint and device_id; activate
    PG-->>Server: Commit active status
    Server-->>Agent: Authorized heartbeat/response
  else Token invalid, expired, reused or CSR rejected
    PG-->>Server: Reject and audit, no active enrollment
    Server-->>Agent: No credential, require admin remediation
  end
~~~

Caddy's separate browser/enrollment/Agent TLS sites and Spring's per-request verified fingerprint lookup are not bypassed by a token or any client-supplied identity header. Reinstall after loss of key and SQLite creates a new device identity, never reuses previous sequences. See [D-07 proposal](../architecture/enrollment-bootstrap.md), [API review fixtures](../architecture/api-contract.md) and [D0 approval gate](../governance/documentation-gate.md).
