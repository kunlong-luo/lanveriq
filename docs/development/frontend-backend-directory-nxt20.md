# NXT-20 — Bounded server-side directory filtering and pagination contract

**Date:** 2026-11-08. **Scope:** additive backend contract for the already opt-in, health-only `GET /api/v1/devices` path; no production business data, public Next.js API connection, login or Agent operations. This does not turn on any existing feature flag.

## Existing architecture (do not reinvent it)

The canonical server uses Java 25, Spring Boot 4.1.2, Hibernate/JPA and PostgreSQL 18.6. Existing `DeviceDirectoryRestController` is registered only when **all three** properties are `true`:

- `lanveriq.console.auth.enabled`
- `lanveriq.device-directory.enabled`
- `lanveriq.device-directory.http-enabled`

The Spring Security chain is **exactly** `GET /api/v1/devices` and `GET /api/v1/devices/{deviceId}`, with session revalidation and strict console Host/Origin rules. The JPA repository uses an existing, named-parameterized native projection whose `WHERE EXISTS` predicate **rechecks the live admin account, allowed health roles and exact device-group membership for every query**. The projection intentionally excludes network addresses, MAC, raw inventory, event payloads and certificate material. Nothing in NXT-20 changes those gates.

The query is bounded to a single role-scoped page, with a server UUID keyset and `limit + 1` fetched internally only for `next_cursor`. No SQL `OFFSET` or unbounded/ambiguous total count.

## NXT-20 additive request contract

`GET /api/v1/devices?cursor=...&limit=50&sort=device_id_asc&group_id=...&label_prefix=...`

| Query param | Required | Bounds and meaning |
| --- | --- | --- |
| `cursor` | No | last returned canonical UUID (exclusive). Nil UUID rejected. Not a bearer secret |
| `limit` | No | default 50, integer range 1–100. Service fetches max 101 rows before trimming |
| `sort` | No | **only `device_id_asc`**. Arbitrary column, direction and SQL strings fail with 400 |
| `group_id` | No | additional exact UUID group intersection, never a new permission. Nil UUID rejected; an out-of-scope group yields an empty page |
| `label_prefix` | No | 1–64 Unicode code points after trimming, case-insensitive literal prefix of the device label; whitespace-only, controls, unpaired surrogates and overlong text rejected; SQL `LIKE` metacharacters `%`, `_` and escape `!` are safely escaped as literals |

The literal prefix applies to `COALESCE(NULLIF(device_label,''), 'Unlabeled device')` so it follows the existing displayed fallback. It is **not** full-text search over OS, IP, MAC, asset tags or device-group names; such searches require separate authorization/performance review. Filters are always `AND` conditions appended to the existing live scope query, with values bound through Hibernate/JPA. Input never supplies a SQL identifier or an `ORDER BY` expression.

**Response:** unchanged `{items:[...], next_cursor:uuid|null}`, `snake_case` and `Cache-Control: no-store`. `items` includes only the existing allowlisted health projection. Errors for invalid bounded input return 400 with no exception details; unauthenticated and out-of-scope behavior continues to use the prior Spring Security and JPA gates.

## Pagination, accuracy and threat model

- **Never change filters while reusing an old cursor.** The caller resets the cursor on any change to `group_id`, `label_prefix`, `sort` or session identity. Raw UUID cursor is not a grant and may be replayed, but the repository checks authorization afresh.
- `next_cursor` is present only when an additional **authorized and filtered** row exists. There is no total across hidden groups, no claim of a globally sorted name/group set, and no total beyond the page's returned items.
- UUID keyset is not a snapshot: inserts/deletes/renames/group changes between requests can move the live result set. Consumers must describe it as a **live feed**, not an exact historical snapshot or an export.
- Explicitly reject `sort=label_asc`, `sort=group_asc` and reverse UUID sort: global sorting by anything other than ascending UUID would require a separately versioned, composite keyset contract `(normalized sort value, device UUID)`, index review, filter binding and negative tests.
- `label_prefix` is anchored. It can still require a scan over authorized devices; before production high-cardinality deployments, review `EXPLAIN (ANALYZE, BUFFERS)`, rate limits, P95 targets and appropriate index strategy. No new database migration or unreviewed performance promise is made here.
- Distinguish empty authorized pages from disconnected/forbidden states. Filtered absence is **not** proof a hidden device does not exist.
- A candidate API client must use the **generated Springdoc/Orval contract**, not duplicate hand-maintained TypeScript response types. Existing `DirectoryState`/React Query session caches must clear on logout/scope change before showing new rows.

## Implementation and verification approach

- New immutable `DeviceDirectoryListQuery` normalizes/rejects parameters before any repository execution, and escapes literal prefix characters.
- Existing unfiltered REST `list(cursor,limit)` dispatch is deliberately preserved to avoid breaking prior Spring MVC/security semantics; filtered calls share the same authenticated service and SQL predicate.
- New unit tests cover: bounds, UUID-only sorting, Unicode/control rejection, SQL pattern escaping, authentication, `limit+1` cursor, JPA bound params, no extra sensitive fields, and MockMvc no-store/400 behavior.
- New **synthetic-only PG18** integration fixture verifies: scope intersections, cross-admin access denial, group/prefix filter, exact wildcard search and filtered two-page keyset behavior.
- Existing Caddy/Tomcat/PG18/Chromium two-admin acceptance continues to run automatically on server query changes, without enabling real production credentials or opening a public endpoint.
- The PostgreSQL 18 **full database integration workflow remains explicitly manually dispatched**, per existing repository safety policy. A compiled Testcontainers test is not the same thing as successful Docker execution. Do not mark the new PG18 filter integration *executed/passed* unless the actual workflow is run with evidence.

## Next release prerequisites

Before enabling any real web adapter, complete independent decisions on authenticated server-data delivery, UI page reset/caching, traffic limits, production query plan/indexing, fresh/session-scoped read permission semantics and 401/403/404 error envelopes. The public GitHub Pages site stays a **disconnected design preview**, not a backend client. This is a **default-off staged contract implementation**, not production authorization or employee monitoring approval.
