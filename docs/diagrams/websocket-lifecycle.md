# D-16 — Browser WebSocket client lifecycle

~~~mermaid
stateDiagram-v2
  [*] --> Disconnected
  Disconnected --> Connecting: Authenticated dashboard opens
  Connecting --> Ready: WSS system.ready
  Connecting --> Reauth: 401 / 403 / forbidden Origin
  Ready --> Resyncing: Fetch scoped device IDs
  Resyncing --> Reconciling: Subscribe and receive acceptance
  Reconciling --> Subscribed: Refetch authoritative REST snapshot
  Subscribed --> Subscribed: Notification invalidates REST query
  Subscribed --> Subscribed: Visible-page periodic REST reconcile / tab resume
  Subscribed --> Backoff: Network loss / server restart
  Resyncing --> Backoff: Connection lost
  Reconciling --> Backoff: Connection lost
  Connecting --> Backoff: Transient failure
  Backoff --> Connecting: Bounded retry + jitter
  Subscribed --> Reauth: Session revoked / permissions invalid
  Reauth --> Disconnected: User signs out
  Subscribed --> Disconnected: Explicit close / logout
~~~

**No durable WS replay:** initial connection and reconnect require fresh authorization, device-ID lookup, accepted subscriptions and a **REST snapshot after subscription** to close the race window. Agent events continue over durable HTTPS batches, independent of browser connection state. See [WS protocol](../architecture/websocket-contract.md).
