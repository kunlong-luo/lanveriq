import { describe, expect, it } from 'vitest';
import { projectConsoleList, validateScopedDirectoryPage } from './console-device-projection';

const one = '00000000-0000-7000-8000-000000000001';
const two = '00000000-0000-7000-8000-000000000002';
const device = (device_id = one) => ({
  device_id, group_id: '00000000-0000-7000-8000-000000000100',
  label: '合成单测终端', group_name: '安全测试组',
  enrollment_state: 'active', heartbeat_received_at: null,
  queue_count: null, os_name: null, agent_version: null,
  mac_address: 'SECRET-MAC', ip_address: 'SECRET-IP',
  employee_id: 'SECRET-USER',
});
const page = (items: unknown[], next_cursor: string | null = null) => ({ items, next_cursor });

describe('NXT-03 authorized REST response -> Next.js display state', () => {
  it('keeps an authenticated empty page distinct from missing or invalid payload', () => {
    expect(validateScopedDirectoryPage(page([]))).toEqual({
      kind: 'ready', rows: [], hasMore: false, nextCursor: null,
    });
    for (const payload of [null, [], {}, { items: [] }, { next_cursor: null },
      { items: null, next_cursor: null }, page([], one)]) {
      expect(validateScopedDirectoryPage(payload)).toEqual({ kind: 'unavailable' });
    }
  });

  it('never infers online from an active credential or an old heartbeat', () => {
    const result = validateScopedDirectoryPage(page([
      { ...device(), heartbeat_received_at: '2026-11-01T07:05:09.123Z',
        os_name: 'Pop!_OS', queue_count: 0 },
    ]));
    expect(result.kind).toBe('ready');
    if (result.kind !== 'ready') return;
    expect(result.rows).toHaveLength(1);
    expect(result.rows[0]).toMatchObject({
      id: one, label: '合成单测终端', group: '安全测试组',
      connectivity: 'unknown', heartbeat: '2026-11-01T07:05:09.123Z',
      queue: 0, os: 'Pop!_OS',
    });
    expect(JSON.stringify(result)).not.toMatch(/SECRET-MAC|SECRET-IP|SECRET-USER/);
  });

  it('refuses missing mandatory fields, invented status and invalid counters', () => {
    for (const key of ['device_id', 'group_id', 'label', 'group_name', 'enrollment_state']) {
      const altered: Record<string, unknown> = { ...device() };
      delete altered[key];
      expect(validateScopedDirectoryPage(page([altered]))).toEqual({ kind: 'unavailable' });
    }
    for (const patch of [
      { device_id: '../api/v1/auth/logout' }, { group_id: '' },
      { enrollment_state: 'online' }, { heartbeat_received_at: 'yesterday' },
      { queue_count: -1 }, { queue_count: Number.MAX_SAFE_INTEGER + 1 },
      { queue_count: '0' }, { os_name: { nested: 'bad' } },
    ]) {
      expect(validateScopedDirectoryPage(page([{ ...device(), ...patch }])))
        .toEqual({ kind: 'unavailable' });
    }
  });

  it('enforces bounded ascending UUID keyset pagination without duplicates', () => {
    expect(validateScopedDirectoryPage(page([device(one), device(two)],two),{limit:2}))
      .toMatchObject({kind:'ready',hasMore:true,nextCursor:two});
    for (const payload of [
      page([device(two),device(one)]), page([device(one),device(one)]),
      page([device(one)],one), page([device(one),device(two)],one),
      page([device(one),device(two),device('00000000-0000-7000-8000-000000000003')]),
      { items:[device(one)], next_cursor: undefined },
    ]) {
      expect(validateScopedDirectoryPage(payload, {limit:2}))
        .toEqual({kind:'unavailable'});
    }
    expect(validateScopedDirectoryPage(page([device(one)]), {after:one}))
      .toEqual({kind:'unavailable'});
    expect(validateScopedDirectoryPage(page([]), {after:'bad'}))
      .toEqual({kind:'unavailable'});
    expect(validateScopedDirectoryPage(page([]), {limit:101}))
      .toEqual({kind:'unavailable'});
  });

  it('never preserves rows for denied, throttled, signed-out or failed requests', () => {
    for (const kind of ['signed_out','forbidden','throttled','unavailable'] as const) {
      expect(projectConsoleList({kind})).toEqual({kind});
    }
    expect(projectConsoleList({kind:'not_found'})).toEqual({kind:'unavailable'});
    expect(projectConsoleList({kind:'ok',data:{items:[]}})).toEqual({kind:'unavailable'});
  });
});
