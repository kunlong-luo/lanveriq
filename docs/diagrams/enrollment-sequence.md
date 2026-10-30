# D-04 — Certificate trust and device enrollment

~~~mermaid
sequenceDiagram
  autonumber
  actor Operator as Authorized IT operator
  participant UI as React Dashboard
  participant Server as Spring Boot
  participant PG as PostgreSQL
  participant Agent as Rust Agent
  participant Key as Endpoint secure key store
  Operator->>UI: Request one-use enrollment
  UI->>Server: POST /api/v1/enrollments (session + CSRF)
  Server->>PG: Store hashed token, group, expiry, policy
  PG-->>Server: Commit
  Server-->>UI: Display short-lived token once
  Operator->>Agent: Provide LAN hostname and token
  Agent->>Agent: Validate configured server CA and target hostname during TLS handshake
  Agent->>Agent: Display approved policy notice
  Agent->>Key: Create unique device keypair
  Agent->>Server: POST /api/v1/devices/enroll (token + public key)
  Server->>PG: Atomically consume token and insert devices.id = uuidv7()
  alt Valid and unused
    PG-->>Server: Commit device identity
    Server-->>Agent: device_id + bound credential information
    Agent->>Key: Protect device credential
    Agent->>Server: Authenticated heartbeat over HTTPS
    Server-->>Agent: Accepted
  else Expired, reused or invalid
    PG-->>Server: Reject, no enrollment
    Server-->>Agent: Error (no credential)
  end
~~~

Reinstallation after loss of key and SQLite storage creates a new device identity; old event sequences are not reused. See [bootstrap contract](../architecture/enrollment-bootstrap.md).
