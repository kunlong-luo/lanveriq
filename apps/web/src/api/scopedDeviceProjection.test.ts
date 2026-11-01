import { describe, expect, it } from 'vitest';
import {
  projectScopedDevicePage,
  projectScopedDeviceRead,
} from './scopedDeviceProjection';

const first = '00000000-0000-7000-8000-000000000001';
const second = '00000000-0000-7000-8000-000000000002';
const third = '00000000-0000-7000-8000-000000000003';

function device(device_id = first) {
  return {
    device_id, group_id: '00000000-0000-7000-8000-000000000100',
    group_name: '研发组', label: '合成测试终端',
    enrollment_state: 'active', heartbeat_received_at: null,
    queue_count: null, os_name: null, agent_version: null,
    // Deliberately injected sensitive extra values must never be displayed.
    mac_address: 'SECRET-MAC', ip_address: 'SECRET-IP',
    employee_id: 'SECRET-USER',
  };
}
const page = (items: unknown[], next_cursor: string | null = null) =>
  ({ items, next_cursor });

describe('FE-02-C fail-closed scoped page projection (no live network)', () => {
  it('represents a truly empty authorized database page without demo rows', () => {
    expect(projectScopedDevicePage(page([]))).toEqual({
      kind: 'ready', rows: [], hasMore: false, nextCursor: null,
    });
    expect(projectScopedDevicePage({ items: [] })).toEqual({kind: 'unavailable'});
    expect(projectScopedDevicePage({ next_cursor: null })).toEqual({kind: 'unavailable'});
  });

  it('never turns an active enrollment or missing heartbeat into online', () => {
    const result = projectScopedDevicePage(page([device()]));
    expect(result.kind).toBe('ready');
    if (result.kind !== 'ready') return;
    expect(result.rows).toHaveLength(1);
    expect(result.rows[0]).toMatchObject({
      deviceId: first, title: '合成测试终端', groupLabel: '研发组',
      connectivityLabel: '连接状态待确认',
      lastSeenLabel: '尚无已接收心跳',
      queueLabel: '未上报',
      subtitle: '系统未上报 · Agent 未上报',
    });
    expect(JSON.stringify(result)).not.toMatch(/SECRET-MAC|SECRET-IP|SECRET-USER/);
    expect(JSON.stringify(result)).not.toContain('在线');
  });

  it('distinguishes an authenticated heartbeat receipt from actual connectivity', () => {
    const item = {
      ...device(), heartbeat_received_at: '2026-11-01T07:05:09.123Z',
      queue_count: 0, os_name: 'Windows 11', agent_version: '0.1.0',
    };
    const result = projectScopedDevicePage(page([item]));
    expect(result.kind).toBe('ready');
    if (result.kind !== 'ready') return;
    expect(result.rows[0]).toMatchObject({
      connectivityLabel: '连接状态待确认',
      lastSeenLabel: '2026-11-01T07:05:09.123Z',
      queueLabel: '0 条', subtitle: 'Windows 11 · Agent 0.1.0',
    });
  });

  it.each([
    ['pending_certificate', '待完成注册'],
    ['revoked', '证书已撤销'],
    ['retired', '已退役'],
  ] as const)('displays %s enrollment as %s without interpreting a heartbeat', (state,label) => {
    const result = projectScopedDevicePage(page([{...device(),enrollment_state: state}]));
    expect(result.kind).toBe('ready');
    if (result.kind === 'ready') expect(result.rows[0].connectivityLabel).toBe(label);
  });

  it('rejects missing or wrong-typed required fields instead of filling defaults', () => {
    for (const key of ['device_id','group_id','group_name','label','enrollment_state']) {
      const missing = { ...device() } as Record<string, unknown>;
      delete missing[key];
      expect(projectScopedDevicePage(page([missing]))).toEqual({kind:'unavailable'});
    }
    for (const patch of [
      {device_id:'../../auth/logout'}, {group_id:'bad'}, {group_name:' '},
      {label:23}, {enrollment_state:'online'}, {heartbeat_received_at:'yesterday'},
      {queue_count:-1}, {queue_count:1.5}, {queue_count:Number.MAX_SAFE_INTEGER+1},
      {queue_count:'0'}, {os_name:{secret:'oops'}},
      {agent_version:['0.1']},
    ]) {
      expect(projectScopedDevicePage(page([{...device(),...patch}])))
        .toEqual({kind:'unavailable'});
    }
  });

  it('rejects duplicate, out-of-order, oversize and inconsistent cursor pages', () => {
    for (const payload of [
      page([device(second),device(first)]),
      page([device(first),device(first)]),
      page([device(first)],first), // has-more only when a full page was served
      page([],first),
      page([device(first),device(second)],first),
      page([device(first),device(second),device(third)]),
      {items:[device(first)],next_cursor:'not-a-uuid'},
      {items:[device(first)],next_cursor:undefined},
    ]) {
      expect(projectScopedDevicePage(payload,{limit:2})).toEqual({kind:'unavailable'});
    }
    expect(projectScopedDevicePage(page([device(first),device(second)],second),{limit:2}))
      .toMatchObject({kind:'ready',hasMore:true,nextCursor:second});
    expect(projectScopedDevicePage(page([device(second)]),{after:first}))
      .toMatchObject({kind:'ready',hasMore:false});
    expect(projectScopedDevicePage(page([device(first)]),{after:first}))
      .toEqual({kind:'unavailable'});
  });

  it('rejects malicious cursor, limit and malformed payload before rendering', () => {
    for (const opts of [
      {limit:0}, {limit:101}, {limit:1.2}, {limit:NaN},
      {after:'../api/v1/auth/logout'}, {after:'not-a-uuid'},
    ]) {
      expect(projectScopedDevicePage(page([]),opts)).toEqual({kind:'unavailable'});
    }
    for (const raw of [null,[],true,0,'{}',{items:null,next_cursor:null}]) {
      expect(projectScopedDevicePage(raw)).toEqual({kind:'unavailable'});
    }
  });

  it('clears any previous data on 401, 403, 429, 404 or outage', () => {
    for (const [kind,out] of [
      ['signed_out','signed_out'],['forbidden','forbidden'],
      ['throttled','throttled'],['unavailable','unavailable'],
      ['not_found','unavailable'],
    ] as const) {
      expect(projectScopedDeviceRead({kind})).toEqual({kind:out});
    }
    expect(projectScopedDeviceRead({kind:'ok',data:{items:[]}}))
      .toEqual({kind:'unavailable'});
  });
});
