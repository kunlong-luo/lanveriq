# Lanveriq architecture drawings

**Looking for colorful customer-facing graphics?** See the separate [eight-image product gallery](../visuals/README.md). The Mermaid pages below remain the editable engineering diagrams.

**All drawings are conceptual, not executable architecture or evidence of implementation.** Mermaid is embedded in Markdown for direct GitHub rendering. Source-of-truth explanations live in the linked technical specifications; keep diagrams synchronized when contracts change.

| ID | Drawing | Canonical specification |
|---|---|---|
| D-01 | [System context](system-context.md) | [System overview](../architecture/system-overview.md) |
| D-02 | [Components](component-architecture.md) | [Component boundaries](../architecture/components.md) |
| D-03 | [LAN deployment](deployment-topology.md) | [Deployment](../architecture/deployment.md) |
| D-04 | [Enrollment sequence](enrollment-sequence.md) | [Bootstrap/enrollment](../architecture/enrollment-bootstrap.md) |
| D-05 | [Event delivery and ACK](event-delivery-sequence.md) | [Atomic delivery](../architecture/event-delivery.md) |
| D-06 | [WebSocket messages and resync](websocket-flow.md) | [WS protocol](../architecture/websocket-contract.md) |
| D-07 | [Recording pipeline (v0.3)](recording-pipeline.md) | [Capture restrictions](../architecture/recording.md) |
| D-08 | [RBAC decisions](rbac-matrix.md) | [Permission matrix](../security/authorization-matrix.md) |
| D-09 | [Server entity relations](database-er.md) | [Data model](../architecture/data-model.md) |
| D-10 | [Agent outbox state](agent-outbox-state.md) | [SQLite persistence](../architecture/agent-persistence.md) |
| D-11 | [Health and coverage states](health-coverage-states.md) | [Health model](../architecture/health-and-gap-semantics.md) |
| D-12 | [Trust boundaries](trust-boundaries.md) | [Threat model](../security/threat-model.md) |
| D-13 | [Data retention lifecycle](retention-lifecycle.md) | [Retention](../security/data-retention.md) |
| D-14 | [Agent SQLite entity model](agent-sqlite-model.md) | [Agent persistence](../architecture/agent-persistence.md) |
| D-15 | [Dashboard navigation](dashboard-navigation.md) | [Dashboard UX](../development/dashboard-ux.md) |
| D-16 | [Browser WSS lifecycle](websocket-lifecycle.md) | [WebSocket contract](../architecture/websocket-contract.md) |

## Conventions
- **Solid arrows** are intended data/control flows; label with transport/authorization where needed.
- Application data must cross the authenticated Spring Boot API, not directly from browser to PostgreSQL/NAS.
- Screen capture/video only appears as an **optional v0.3, disclosed, OS-authorized** component.
- Agent REST event batches remain durable; Dashboard WSS notifications are **ephemeral hints**.
- Test examples must not contain actual employee data.
