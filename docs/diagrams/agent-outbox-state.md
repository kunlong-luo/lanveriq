# D-10 — Agent SQLite outbox state machine

~~~mermaid
stateDiagram-v2
  [*] --> Collected
  Collected --> Persisting: Redact and validate allowed event
  Persisting --> Pending: SQLite transaction commits event + sequence + cursor
  Persisting --> Gap: Local transaction fails / quota exceeded
  Pending --> Sending: Select immutable bounded batch
  Sending --> Acknowledged: Server commits entire batch / accepted_sequences
  Sending --> Pending: Timeout / 5xx / 429, bounded retry
  Sending --> Quarantined: 409 or invalid schema
  Acknowledged --> Pruned: Mark only acknowledged rows
  Pruned --> [*]
  Quarantined --> Reported: Health degraded + operator diagnostics
  Gap --> Reported: Persist loss interval if possible
~~~

The server returns **all-or-none batch ACK** after PostgreSQL commit; retry of the same immutable event must be idempotent. Do not delete a row based on a higher acknowledged sequence. See [event delivery](../architecture/event-delivery.md) and [local DB persistence](../architecture/agent-persistence.md).
