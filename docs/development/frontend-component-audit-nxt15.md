# NXT-15 — Mainstream SaaS layout and reusable component audit

**Date:** 2026-11-07  
**Target:** Canonical `apps/web-next` Next.js 16.4.5 / React 19.2 / TypeScript 7 / Tailwind 4, public disconnected design preview only.

## Architecture decision

The B+C design is mainstream for premium enterprise SaaS: persistent navigation, global toolbar, task-based routes, bento overview metrics and compact read-only data surfaces. **Mainstream does not mean optimal by default.** Operational screens should prioritize asset tables, meaningful filters, status freshness and permissions; marketing-style hero illustration is suitable as a restrained entry-point, not every long-running operations view.

Use battle-tested **shadcn/ui source-owned component conventions** and **Base UI** for focus-sensitive interactions. The shadcn/ui project declared Base UI its default for new projects in July 2026, while continuing to support Radix. We selected **`@base-ui/react@1.9.0` pinned**, not `@latest`, and retained existing Next.js/React/Lucide versions. Sources:

- https://ui.shadcn.com/docs/changelog/2026-07-base-ui-default
- https://base-ui.com/react/components/dialog
- https://base-ui.com/react/components/tabs
- https://base-ui.com/react/components/button
- https://ui.shadcn.com/docs/components/base/table

The exact pnpm 12 lockfile was generated via a disposable, write-enabled, **package.json-triggered GitHub Actions workflow** and committed at `326cf02`; that temporary workflow was then deleted. No local hand-written lock data and no unpinned runtime packages.

## Component audit and outcome

| Surface | Previous implementation | NXT-15 replacement |
|---|---|---|
| Mobile navigation | Hand-written backdrop, `inert`, `matchMedia`, Escape handler, refs and focus restoration | **Base UI Dialog** controlling modal focus trap, background inertness, Escape and trigger focus restore; shared desktop/mobile typed nav links |
| Device detail tabs | Local state, refs, custom arrow-key/Home/End handler and custom role wiring | **Base UI Tabs** with `activateOnFocus`, built-in roving focus/ARIA and read-only panels |
| Asset directory text search | Raw input | Reusable **shadcn/ui-style Input** preserving disabled-by-default behavior |
| Asset group selector | Raw select with one-off styling | Reusable **shadcn/ui NativeSelect**, recommended for simple short native selection |
| Authorized read-only device table | Repeated ad hoc table markup | Reusable semantic **shadcn/ui Table** components with caption, column headings and row/cell structure |
| Public disabled login fields | Raw inputs | **shadcn/ui-style Input** with `disabled`; still no form, credentials or submission |
| Card composition | Partial shadcn-compatible five-part component | Standardized Card/Header/Title/Description/Action/Content/Footer source-owned API |
| Presentation primitives (links, headings, labels, lists, disabled buttons) | Semantic HTML | **Remain native intentionally.** Native DOM is what headless libraries ultimately render; extra abstraction has no benefit here |
| Server-authorized list query, sorting and pagination | Explicit `DirectoryState` read-only + local filtering of **only supplied authorized page** | **Keep existing authorization boundary.** Adopt TanStack Table once actual server-scoped pagination/sorting contract is separately approved, not a synthetic client-side replacement for RBAC |

**Important:** shadcn/ui is primarily a source-owned design-system approach, not a precompiled card npm package. Component files under `src/components/ui` are normal and expected. Base UI provides library-owned keyboard/focus semantics; source files only style and compose them.

## Non-negotiable safety boundary

- Public GitHub Pages and normal Next.js remain disconnected; no real credential inputs, cookie issuance, storage, proxy, RSC device reads, Agent requests, or fake live metrics.
- Login markup remains an inert visual preview. `#login-username` and `#login-password` are disabled; login is a disabled button and there is no submit form.
- Directory `unconnected`, `loading`, `signed_out`, `forbidden` etc. cannot display previous rows. Service-level RBAC remains the authority. Group selector and search disabled unless the state is `ready` with caller-provided authorized rows.
- Dialog navigation never displays authenticated device data.
- Existing Caddy/Spring/PG18 Chromium integration tests are synthetic and do not authorize production identity or Agent functions.

## Acceptance requirements

1. `pnpm install --frozen-lockfile`, `verify:routes`, Vitest, TypeScript 7 and Next.js production build succeed from exact lock.
2. Real Playwright Chromium checks 11 URLs and validates Base UI mobile dialog opening, modal focus, Escape/focus restoration, client navigation and 320–1440px layout.
3. Base UI tab arrows activate correct panel while preserving disabled operations.
4. Live Pages disconnected preview remains safe. The synthetic isolated Caddy/Spring/PG18 boundary must stay green after UI changes.
5. Remove obsolete custom keyboard utilities. Do not delete native table/button semantics simply to satisfy a superficial "no HTML" rule.
6. Future components are evaluated against existing Base UI, shadcn/ui and TanStack patterns before authoring any new interactive widget.

## Follow-up design quality

Optimize visual density and readability across real day-to-day asset workflows, consolidate three temporary CSS layers, review 390/768/1440px screenshots, test actual screen-reader behavior, and design authenticated server-side filtering/pagination **only after explicit release approval**.
