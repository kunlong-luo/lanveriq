import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DeviceDirectory, filterDirectoryRows, type DirectoryRow, type DirectoryState } from './device-directory';

const render = (state: DirectoryState) => renderToStaticMarkup(<DeviceDirectory state={state} />);

const rows: readonly DirectoryRow[] = [
  { id: '00000000-0000-7000-8000-000000000001', label: '合成测试设备 A',
    os: 'Windows 11', group: '授权测试组', enrollment: 'active',
    connectivity: 'unknown', heartbeat: null, queue: null },
  { id: '00000000-0000-7000-8000-000000000002', label: '合成测试设备 B',
    os: 'Linux', group: '另一个测试组', enrollment: 'pending_certificate',
    connectivity: 'offline', heartbeat: null, queue: 0 },
];

describe('NXT-02 directory display model (all records test-only)', () => {
  it('keeps the default unconnected state distinct from an authorized empty page', () => {
    const disconnected = render({ kind: 'unconnected' });
    expect(disconnected).toContain('设备数据尚未连接');
    expect(disconnected).toContain('不代表数据库中没有设备');
    expect(disconnected).toContain('disabled');
    expect(disconnected).not.toContain('0 台');
    expect(disconnected).not.toContain('合成测试设备 A');
    const empty = render({kind:'ready',rows:[],hasMore:false});
    expect(empty).toContain('当前授权范围没有设备');
    expect(empty).not.toContain('设备数据尚未连接');
  });

  it.each([
    ['loading','正在加载已授权设备'],
    ['signed_out','管理员登录状态已失效'],
    ['forbidden','当前账号无权查看设备'],
    ['unavailable','设备服务暂不可用'],
  ] as const)('renders %s without retaining any previous rows', (kind,title) => {
    const html = render({ kind });
    expect(html).toContain(title);
    expect(html).not.toContain('合成测试设备');
    expect(html).not.toContain('<tbody');
  });

  it('filters only the caller-provided authorized page by group, OS or label', () => {
    expect(filterDirectoryRows(rows,'windows','')).toHaveLength(1);
    expect(filterDirectoryRows(rows,'linux','')).toHaveLength(1);
    expect(filterDirectoryRows(rows,'设备 B','')).toHaveLength(1);
    expect(filterDirectoryRows(rows,'', '授权测试组')).toHaveLength(1);
    expect(filterDirectoryRows(rows,'Windows','另一个测试组')).toHaveLength(0);
    expect(filterDirectoryRows([], '', '')).toEqual([]);
    expect(rows).toHaveLength(2);
  });

  it('renders only authorized test rows, never exposes fake online counts', () => {
    const html = render({ kind: 'ready', rows, hasMore:true });
    expect(html).toContain('合成测试设备 A');
    expect(html).toContain('合成测试设备 B');
    expect(html).toContain('待确认');
    expect(html).toContain('尚无已接收心跳');
    expect(html).toContain('未上报');
    expect(html).toContain('0 条');
    expect(html).toContain('后续分页待授权接入');
    expect(html).not.toContain('设备操作（暂不可用）');
    expect(html).not.toContain('发起注册');
  });

  it('never creates a navigation link from a malicious row ID', () => {
    const html = render({kind:'ready',rows:[{...rows[0],id:'../auth/logout'}],hasMore:false});
    expect(html).toContain('不可用');
    expect(html).not.toContain('href="/devices/../');
  });
});
