import { describe, expect, it } from 'vitest';
import { parseScopedConsoleOverview } from './staged-console-overview';

const valid = {
  as_of: '2026-11-04T09:00:00.001Z',
  total: 3, online: 0, stale: 0, offline: 2,
  revoked: 0, pending: 1, retired: 0,
};

describe('staged Console overview allowlist (source-only)', () => {
  it('accepts an actual group-scoped counter partition, including verified empty', () => {
    expect(parseScopedConsoleOverview(valid)).toEqual({
      asOf: valid.as_of, total: 3, online: 0, stale: 0,
      offline: 2, revoked: 0, pending: 1, retired: 0,
    });
    expect(parseScopedConsoleOverview({
      ...valid, total: 0, offline: 0, pending: 0,
    })?.total).toBe(0);
  });

  it('never treats connection errors, invalid or contradictory counters as zero', () => {
    for (const invalid of [
      null, undefined, [], {}, 'offline', false, 0,
      {...valid, as_of: 'not-a-date'},
      {...valid, as_of: '2026-11-04T09:00:00'},
      {...valid, total: '3'},
      {...valid, total: 0},
      {...valid, total: 4},
      {...valid, online: -1},
      {...valid, revoked: 0.5},
      {...valid, pending: Number.NaN},
      {...valid, retired: Number.POSITIVE_INFINITY},
      {...valid, offline: Number.MAX_SAFE_INTEGER},
      {...valid, total: Number.MAX_SAFE_INTEGER + 1},
    ]) {
      expect(parseScopedConsoleOverview(invalid)).toBeNull();
    }
  });

  it('ignores unapproved raw fields even if server adds them', () => {
    const result = parseScopedConsoleOverview({
      ...valid, employee_name: 'should never display',
      mac_address: 'not part of health summary',
    });
    expect(result).not.toHaveProperty('employee_name');
    expect(result).not.toHaveProperty('mac_address');
  });
});
