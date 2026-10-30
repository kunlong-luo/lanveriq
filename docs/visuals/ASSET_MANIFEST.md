# Visual asset manifest

**Canonical path:** `docs/visuals/assets/`. **Format:** editable self-contained SVG 1600×900. **Repository:** single `main` branch. No raster generation pipeline or external storage needed for the canonical images.

| ID | Source file | Audience / meaning | Capability stage | Associated authoritative doc |
|---|---|---|---|---|
| 01 | [01-overview.svg](assets/01-overview.svg) | Three-system overview, endpoint ↔ server ↔ browser | V0.1 + explicit future mention | [Architecture](../architecture/system-overview.md) |
| 02 | [02-data-flow.svg](assets/02-data-flow.svg) | Onboarding, SQLite queue, upload/ACK and WSS | V0.1 | [Event delivery](../architecture/event-delivery.md) |
| 03 | [03-technology-stack.svg](assets/03-technology-stack.svg) | Frontend, backend and endpoint components | V0.1 design and optional future | [Technology](../development/technology.md) |
| 04 | [04-feature-map.svg](assets/04-feature-map.svg) | Feature availability by milestone | V0.1/V0.2/V0.3 | [Roadmap](../planning/roadmap.md) |
| 05 | [05-enrollment.svg](assets/05-enrollment.svg) | Secure enrollment/identity | V0.1 | [Enrollment](../architecture/enrollment-bootstrap.md) |
| 06 | [06-dashboard-concept.svg](assets/06-dashboard-concept.svg) | Illustrative React dashboard (fake sample data) | V0.1 design, **not screenshot** | [Dashboard UX](../development/dashboard-ux.md) |
| 07 | [07-offline-recovery.svg](assets/07-offline-recovery.svg) | Bounded SQLite outbox and PostgreSQL ACK | V0.1 | [Outbox](../architecture/agent-persistence.md) |
| 08 | [08-security-and-privacy.svg](assets/08-security-and-privacy.svg) | Disclosed policy, RBAC and OS recording permission | V0.1 core; recording V0.3 | [Privacy](../security/privacy-and-consent.md) |

## Versioning
- Filenames are stable and numbered for easy ordering; do not silently swap purpose.
- Visual identity is frozen by [STYLE_GUIDE.md](STYLE_GUIDE.md) for this 8-poster series.
- When a feature changes, update its diagram and related wording in the same commit. If a distinct major visual redesign is approved, create a new manifest revision rather than mixing palettes.
- Never claim the screenshots or data are real. Do not store employee private information in illustrations.
- Avoid font binaries and external image hotlinks; all assets must be locally previewable after cloning.

## Current status
All eight illustrations are **design concept files**, separate from the **20** Mermaid engineering drawings. No PNG replicas are claimed to have been committed; source/preview asset is the SVG itself.
