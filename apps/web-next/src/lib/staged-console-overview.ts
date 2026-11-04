import type { DirectoryState } from '../components/device-directory';

/** Validate the exact server-derived, group-scoped health counters.
 * Never convert disconnected/malformed responses into zero devices.
 * All values are operational health metadata, not employee activity.
 */
export type ScopedConsoleOverview = Readonly<{
  asOf: string;
  total: number;
  online: number;
  stale: number;
  offline: number;
  revoked: number;
  pending: number;
  retired: number;
}>;

const keys = ['online', 'stale', 'offline', 'revoked', 'pending', 'retired'] as const;

export function parseScopedConsoleOverview(raw: unknown): ScopedConsoleOverview | null {
  if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) return null;
  const data = raw as Record<string, unknown>;
  if (typeof data.as_of !== 'string' ||
      !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|[+-]\d{2}:\d{2})$/.test(data.as_of) ||
      !Number.isFinite(Date.parse(data.as_of))) return null;
  const numbers = ['total', ...keys] as const;
  if (numbers.some(key => typeof data[key] !== 'number' ||
      !Number.isSafeInteger(data[key]) || (data[key] as number) < 0)) return null;
  const total = data.total as number;
  const parts = keys.map(key => data[key] as number);
  if (!Number.isSafeInteger(parts.reduce((sum, value) => sum + value, 0)) ||
      parts.reduce((sum, value) => sum + value, 0) !== total) return null;
  return {
    asOf: data.as_of,
    total,
    online: data.online as number,
    stale: data.stale as number,
    offline: data.offline as number,
    revoked: data.revoked as number,
    pending: data.pending as number,
    retired: data.retired as number,
  };
}

/**
 * The directory and overview are separate real HTTP reads. Reject a torn
 * snapshot instead of showing rows from a previous authorization scope with
 * newer zero-valued monitoring totals. With no cursor, the first page covers
 * the entire authorized group; with a cursor, more authorized rows exist.
 *
 * This is a frontend safety check, not a replacement for server-side RBAC
 * or transactionally consistent multi-endpoint snapshots.
 */
export function isConsistentScopedDirectoryPage(
  directory: DirectoryState, overview: ScopedConsoleOverview,
): boolean {
  if (directory.kind !== 'ready') return false;
  const visible = directory.rows.length;
  if (!Number.isSafeInteger(visible) || overview.total < visible) return false;
  if (directory.hasMore) return overview.total > visible;
  return overview.total === visible;
}
