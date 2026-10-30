# D-16 — Browser WebSocket client lifecycle

~~~mermaid
stateDiagram-v2
  [*] --> Disconnected
  Disconnected --> Connecting: Authenticated dashboard opens
  Connecting --> Ready: WSS system.ready
  Connecting --> Reauth: 401 / 403 / forbidden Origin
  Ready --> Resyncing: Load scoped REST snapshot
  Resyncing --> Subscribed: Authorized device subscriptions
  Subscribed --> Subscribed: Notification invalidates REST query
  Subscribed --> Backoff: Network loss / server restart
  Resyncing --> Backoff: Connection lost
  Connecting --> Backoff: Transient failure
  Backoff --> Connecting: Bounded retry + jitter
  Subscribed --> Reauth: Session revoked / permissions invalid
  Reauth --> Disconnected: User signs out
  Subscribed --> Disconnected: Explicit close / logout
~~~

**No durable WS replay:** reconnect requires fresh authorization, scoped REST refetch, and subscription renewal. Agent events continue over durable HTTPS batches, independent of browser connection state. See [WS protocol](../architecture/websocket-contract.md).
