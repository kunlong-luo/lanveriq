import type { DirectoryRow, DirectoryState } from '../components/device-directory';
import { isCanonicalUuid } from './uuid';

/**
 * NXT-03 source-generated DTO -> display-only boundary, intentionally
 * unmounted. Springdoc/Orval is the source of truth for wire schema; this
 * validator checks only security-critical fields and displayed values, and
 * discards additional fields such as MAC/IP/user IDs.
 *
 * A successful HTTP response is not proof of an authentic Spring session:
 * that is validated by the backend on every request, with independent
 * PostgreSQL, Caddy TLS and browser acceptance required before wiring.
 */
const enrollments = new Set(['pending_certificate', 'active', 'revoked', 'retired']);
const isObject = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const boundedText = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0 && value.length <= 255;
const optionalText = (value: unknown): value is string | null | undefined =>
  value === null || value === undefined ||
  (typeof value === 'string' && value.length <= 255);
const hasOwn = (object: Record<string, unknown>, key: string) =>
  Object.prototype.hasOwnProperty.call(object, key);

const isoDateTime =
  /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/;

function receivedAt(value: unknown): value is string | null | undefined {
  return value === null || value === undefined ||
    (typeof value === 'string' && isoDateTime.test(value) &&
     Number.isFinite(Date.parse(value)));
}

function parseRow(raw: unknown): DirectoryRow | null {
  if (!isObject(raw) ||
      !isCanonicalUuid(raw.device_id) || !isCanonicalUuid(raw.group_id) ||
      !boundedText(raw.label) || !boundedText(raw.group_name) ||
      typeof raw.enrollment_state !== 'string' ||
      !enrollments.has(raw.enrollment_state) ||
      !optionalText(raw.os_name) || !optionalText(raw.agent_version) ||
      !receivedAt(raw.heartbeat_received_at) ||
      (raw.queue_count !== null && raw.queue_count !== undefined &&
       (typeof raw.queue_count !== 'number' ||
        !Number.isSafeInteger(raw.queue_count) || raw.queue_count < 0))) {
    return null;
  }
  return {
    id: raw.device_id,
    label: raw.label,
    group: raw.group_name,
    os: raw.os_name || null,
    enrollment: raw.enrollment_state as DirectoryRow['enrollment'],
    // The current REST projection includes enrollment + last heartbeat
    // evidence but NO authoritative connectivity status. Do not infer
    // online/stale/offline from a client-side clock or enrollment state.
    connectivity: 'unknown',
    heartbeat: typeof raw.heartbeat_received_at === 'string'
      ? new Date(raw.heartbeat_received_at).toISOString() : null,
    queue: typeof raw.queue_count === 'number' ? raw.queue_count : null,
  };
}

export type ValidatedDirectoryPage =
  | Readonly<{ kind: 'ready'; rows: readonly DirectoryRow[]; hasMore: boolean; nextCursor: string | null }>
  | Readonly<{ kind: 'unavailable' }>;

export function validateScopedDirectoryPage(
  data: unknown,
  options: Readonly<{ limit?: number; after?: string }> = {},
): ValidatedDirectoryPage {
  const limit = options.limit ?? 50;
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100 ||
      (options.after !== undefined && !isCanonicalUuid(options.after))) {
    return { kind: 'unavailable' };
  }
  if (!isObject(data) || !Array.isArray(data.items) ||
      !hasOwn(data, 'next_cursor') || data.items.length > limit) {
    return { kind: 'unavailable' };
  }
  const cursor = data.next_cursor;
  if (cursor !== null && !isCanonicalUuid(cursor)) return { kind: 'unavailable' };

  const rows: DirectoryRow[] = [];
  let previousId = options.after?.toLowerCase();
  for (const entry of data.items) {
    const row = parseRow(entry);
    if (!row) return { kind: 'unavailable' };
    const id = row.id.toLowerCase();
    if (previousId !== undefined && id <= previousId) return { kind: 'unavailable' };
    rows.push(row);
    previousId = id;
  }
  if (cursor !== null &&
      (rows.length !== limit || cursor.toLowerCase() !== previousId)) {
    return { kind: 'unavailable' };
  }
  return { kind: 'ready', rows, hasMore: cursor !== null, nextCursor: cursor };
}

export type StagedConsoleResponse =
  | Readonly<{ kind: 'ok'; data: unknown }>
  | Readonly<{ kind: 'signed_out' | 'forbidden' | 'throttled' | 'unavailable' | 'not_found' }>;

export function projectConsoleList(
  response: StagedConsoleResponse,
  options: Readonly<{ limit?: number; after?: string }> = {},
): DirectoryState {
  switch (response.kind) {
    case 'ok':
      return validateScopedDirectoryPage(response.data, options);
    case 'signed_out':
    case 'forbidden':
    case 'throttled':
    case 'unavailable':
      return { kind: response.kind };
    case 'not_found':
      return { kind: 'unavailable' };
  }
}
