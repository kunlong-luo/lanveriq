# D-06 — Dashboard WSS authentication, notification and resync

~~~mermaid
sequenceDiagram
  autonumber
  participant Browser as React Dashboard
  participant Caddy as Caddy TLS
  participant WS as Spring WebSocket handler
  participant API as Spring REST API
  participant PG as PostgreSQL
  Browser->>Caddy: WSS /ws upgrade + secure session cookie
  Caddy->>WS: Forward upgrade, original host/Origin
  WS->>WS: Authenticate session + exact Origin + scopes
  alt Authorized
    WS-->>Browser: system.ready
    Browser->>API: REST fetch scoped device snapshot
    API->>PG: Scope-filtered query
    PG-->>API: Current authorized state
    API-->>Browser: Initial device IDs (may already be stale)
    Browser->>WS: subscribe (authorized device_ids)
    WS-->>Browser: subscription.accepted (scope intersection)
    Browser->>API: REST refetch AFTER subscription active
    API->>PG: Scope-filtered authoritative snapshot
    PG-->>API: Latest committed state
    API-->>Browser: Reconciled snapshot
    Note over Browser,WS: Buffer/coalesce hints during REST refetch
    API->>PG: Durable heartbeat/event transaction
    PG-->>API: COMMIT
    API->>WS: After-commit status change
    WS-->>Browser: device.status.changed (small metadata)
    Browser->>API: Refetch affected REST resource
    Note over Browser,WS: WS can drop or reorder hints; REST stays authoritative
    opt Disconnection or server restart
      Browser->>Browser: Backoff with jitter
      Browser->>WS: Reconnect (authenticate again)
      WS-->>Browser: system.ready
      Browser->>API: Fetch device IDs
      Browser->>WS: Resubscribe authorized IDs
      WS-->>Browser: subscription.accepted
      Browser->>API: Refetch REST snapshot after subscription
    end
  else Unauthorized
    WS-->>Browser: Reject upgrade (401/403)
  end
~~~

No raw audit payload, employee screen frame or video file travels over the dashboard WebSocket. See [protocol contract](../architecture/websocket-contract.md).
