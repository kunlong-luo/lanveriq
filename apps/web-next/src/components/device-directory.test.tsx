import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DeviceDirectory, filterDirectoryRows, normalizeAuthorizedGroupSelection, sortAuthorizedDirectoryPage, type DirectoryRow, type DirectoryState } from './device-directory';

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
    ['throttled','请求受到限制'],
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

  it('drops a stale group on a newly authorized page without hiding valid rows', () => {
    const oldGroup = normalizeAuthorizedGroupSelection(['授权测试组'], '授权测试组');
    expect(oldGroup).toBe('授权测试组');
    const newGroup = normalizeAuthorizedGroupSelection(['另一个测试组'], oldGroup);
    expect(newGroup).toBe('');
    expect(filterDirectoryRows(rows.slice(1), '', newGroup)).toHaveLength(1);
    expect(normalizeAuthorizedGroupSelection([], '授权测试组')).toBe('');
    expect(normalizeAuthorizedGroupSelection(['授权测试组'], '')).toBe('');
    expect(normalizeAuthorizedGroupSelection(['授权测试组'], '../admin')).toBe('');
  });

  it('sorts only the supplied authorized page, naturally and without mutating source rows', () => {
    const source: readonly DirectoryRow[] = [
      { ...rows[0], label: 'CI设备10', group: 'CI组乙' },
      { ...rows[1], label: 'CI设备2', group: 'CI组甲' },
      { ...rows[0], id: '00000000-0000-7000-8000-000000000003',
        label: 'CI设备2', group: 'CI组甲' },
    ];
    expect(sortAuthorizedDirectoryPage(source, 'label-asc').map(row => row.label))
      .toEqual(['CI设备2', 'CI设备2', 'CI设备10']);
    expect(sortAuthorizedDirectoryPage(source, 'label-desc').map(row => row.label))
      .toEqual(['CI设备10', 'CI设备2', 'CI设备2']);
    expect(sortAuthorizedDirectoryPage(source, 'group-asc').map(row => row.group))
      .toEqual(['CI组甲', 'CI组甲', 'CI组乙']);
    expect(sortAuthorizedDirectoryPage(source, 'server').map(row => row.label))
      .toEqual(['CI设备10', 'CI设备2', 'CI设备2']);
    expect(source.map(row => row.label))
      .toEqual(['CI设备10', 'CI设备2', 'CI设备2']);
    expect(sortAuthorizedDirectoryPage(source.slice(1), 'label-desc')).toHaveLength(2);
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

  it('labels totals as counts only within the supplied authorized page and keeps native table semantics', () => {
    const html = render({ kind: 'ready', rows, hasMore: false });
    expect(html).toContain('仅本次授权页：匹配 2 / 2 条');
    expect(html).toContain('仅本次已授权页面');
    expect(html).toContain('搜索、设备组筛选与排序不会查询或推断其他页面');
    expect(html).toContain('data-slot="table-container"');
    expect(html).toContain('role="region"');
    expect(html).toContain('tabindex="0"');
    expect(html).toContain('aria-label="设备目录表格（可横向滚动）"');
    expect(html).toContain('排序当前授权页');
    expect(html).toContain('窄屏可横向滑动');
    expect(html).toContain('scope="row"');
    expect(html).toContain('当前管理员已授权的设备健康列表');
    expect(html).toContain('<th');
    expect(html).toContain('scope="col"');
    expect(html).not.toContain('全部设备数');
    expect(html).not.toContain('总设备量：2');
  });

  it('hides all table rows, authorized-page totals and filters for missing permissions', () => {
    for (const kind of ['unconnected','signed_out','forbidden','unavailable'] as const) {
      const html = render({ kind });
      expect(html).not.toContain('仅本次授权页：匹配');
      expect(html).not.toContain('合成测试设备');
      expect(html).not.toContain('data-slot="table-container"');
      expect(html).toContain('disabled');
      expect(html).not.toContain('role="region"');
      expect(html).not.toContain('设备目录表格（可横向滚动）');
    }
  });

  it('never creates a navigation link from a malicious row ID', () => {
    const html = render({kind:'ready',rows:[{...rows[0],id:'../auth/logout'}],hasMore:false});
    expect(html).toContain('不可用');
    expect(html).not.toContain('href="/devices/../');
  });
});
