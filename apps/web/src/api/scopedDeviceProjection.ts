import type { ConsoleReadResult } from './consoleReadTransport';
import type {
  ScopedDeviceDirectoryState,
  ScopedDeviceDisplayRow,
} from '../components/ScopedDeviceDirectory';

/**
 * FE-02-C fail-closed wire-to-display boundary.
 *
 * This is NOT a copied Java DTO or a second source of truth for the API:
 * Springdoc → Orval owns its generated request/response types. We inspect
 * only the fields the read-only UI actually displays and the keyset
 * pagination invariants; the contract build additionally checks the real
 * generated model. No raw events, IP/MAC, credentials or secret properties
 * are copied into the view.
 *
 * This module has no browser APIs, network requests or synthetic fallback.
 */
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const states = new Set(['pending_certificate', 'active', 'revoked', 'retired']);
const own = (value: Record<string, unknown>, key: string) =>
  Object.prototype.hasOwnProperty.call(value, key);
const record = (value: unknown): value is Record<string, unknown> =>
  typeof value === 'object' && value !== null && !Array.isArray(value);
const requiredText = (value: unknown): value is string =>
  typeof value === 'string' && value.trim().length > 0 && value.length <= 255;
const optionalText = (value: unknown) =>
  value === null || value === undefined ||
  (typeof value === 'string' && value.length <= 255);

function optionalTimestamp(value: unknown): value is string | null | undefined {
  if (value === null || value === undefined) return true;
  if (typeof value !== 'string' ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(value)) {
    return false;
  }
  return Number.isFinite(Date.parse(value));
}

function rowFromUnknown(value: unknown): ScopedDeviceDisplayRow | null {
  if (!record(value) ||
      !requiredText(value.device_id) || !uuid.test(value.device_id) ||
      !requiredText(value.group_id) || !uuid.test(value.group_id) ||
      !requiredText(value.group_name) || !requiredText(value.label) ||
      typeof value.enrollment_state !== 'string' ||
      !states.has(value.enrollment_state) ||
      !optionalText(value.os_name) || !optionalText(value.agent_version) ||
      !optionalTimestamp(value.heartbeat_received_at) ||
      (value.queue_count !== null && value.queue_count !== undefined &&
       (typeof value.queue_count !== 'number' ||
        !Number.isSafeInteger(value.queue_count) || value.queue_count < 0))) {
    return null;
  }
  const enrollment = value.enrollment_state;
  const connectivityLabel = enrollment === 'revoked'
    ? '证书已撤销' : enrollment === 'retired'
      ? '已退役' : enrollment === 'pending_certificate'
        ? '待完成注册' : '连接状态待确认';
  // A stored enrollment state, heartbeat timestamp, or local wall clock
  // alone does not prove that a managed device is online right now.
  const heartbeat = value.heartbeat_received_at;
  const lastSeenLabel = typeof heartbeat === 'string'
    ? new Date(heartbeat).toISOString()
    : '尚无已接收心跳';
  const queue = value.queue_count;
  return {
    deviceId: value.device_id,
    title: value.label,
    subtitle: `${value.os_name || '系统未上报'} · Agent ${value.agent_version || '未上报'}`,
    groupLabel: value.group_name,
    connectivityLabel,
    lastSeenLabel,
    queueLabel: typeof queue === 'number' ? `${queue} 条` : '未上报',
  };
}

export type ScopedPageProjection = ScopedDeviceDirectoryState & {
  readonly nextCursor?: string | null;
};

/**
 * A 200 is considered "ready" only after all required wire and pagination
 * invariants are checked. Invalid or partially decoded pages fail as a
 * whole; no rows are leaked alongside an error state.
 */
export function projectScopedDevicePage(
  raw: unknown,
  options: Readonly<{ limit?: number; after?: string }> = {},
): ScopedPageProjection {
  const limit = options.limit ?? 50;
  if (!Number.isSafeInteger(limit) || limit < 1 || limit > 100 ||
      (options.after !== undefined && !uuid.test(options.after))) {
    return { kind: 'unavailable' };
  }
  if (!record(raw) || !Array.isArray(raw.items) ||
      raw.items.length > limit || !own(raw, 'next_cursor')) {
    return { kind: 'unavailable' };
  }
  const cursor = raw.next_cursor;
  if (cursor !== null && (typeof cursor !== 'string' || !uuid.test(cursor))) {
    return { kind: 'unavailable' };
  }
  const rows: ScopedDeviceDisplayRow[] = [];
  let previous = options.after?.toLowerCase();
  for (const source of raw.items) {
    const row = rowFromUnknown(source);
    if (!row) return { kind: 'unavailable' };
    const normalized = row.deviceId.toLowerCase();
    // PostgreSQL returns ascending UUID keyset order. Duplicate/out-of-order
    // IDs and cross-page repeats are invalid, never silently de-duplicated.
    if (previous !== undefined && normalized <= previous) {
      return { kind: 'unavailable' };
    }
    previous = normalized;
    rows.push(row);
  }
  if (cursor !== null &&
      (rows.length !== limit || cursor.toLowerCase() !== previous)) {
    return { kind: 'unavailable' };
  }
  return { kind: 'ready', rows, hasMore: cursor !== null, nextCursor: cursor };
}

/** Clear previous rows on 401/403/throttle/outage, rather than returning demos. */
export function projectScopedDeviceRead(
  result: ConsoleReadResult,
  options: Readonly<{ limit?: number; after?: string }> = {},
): ScopedPageProjection {
  switch (result.kind) {
    case 'signed_out':
    case 'forbidden':
    case 'throttled':
    case 'unavailable':
      return { kind: result.kind };
    case 'not_found': // only valid on a detail route, never on the list
      return { kind: 'unavailable' };
    case 'ok':
      return projectScopedDevicePage(result.data, options);
  }
}
