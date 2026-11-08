'use client';

import { useEffect, useId, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { ArrowRight, LockKeyhole, Search, ShieldAlert, WifiOff, RefreshCcw, X } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from './ui/card';
import { Input } from './ui/input';
import { NativeSelect } from './ui/native-select';
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from './ui/table';
import { isCanonicalUuid } from '../lib/uuid';

/**
 * NXT-02 display-only table. No network requests, demo data or browser
 * storage; actual server-scoped rows may only arrive after separate NXT-03
 * adapter and PG18/HTTPS/browser/RBAC acceptance.
 */
export type DirectoryRow = Readonly<{
  id: string;
  label: string;
  os: string | null;
  group: string;
  enrollment: 'pending_certificate' | 'active' | 'revoked' | 'retired';
  connectivity: 'online' | 'stale' | 'offline' | 'unknown';
  heartbeat: string | null;
  queue: number | null;
}>;

export type DirectoryState =
  | Readonly<{ kind: 'unconnected' | 'loading' | 'signed_out' | 'forbidden' | 'throttled' | 'unavailable' }>
  | Readonly<{ kind: 'ready'; rows: readonly DirectoryRow[]; hasMore: boolean }>;


const statusLabel: Record<DirectoryRow['connectivity'], string> = {
  online: '在线', stale: '延迟', offline: '离线', unknown: '待确认',
};
const enrollmentLabel: Record<DirectoryRow['enrollment'], string> = {
  pending_certificate: '待注册', active: '已注册', revoked: '证书已撤销', retired: '已退役',
};

export function filterDirectoryRows(rows: readonly DirectoryRow[], search: string, group: string) {
  const needle = search.trim().toLocaleLowerCase();
  return rows.filter(row =>
    (group === '' || row.group === group) &&
    (!needle || [row.label, row.os ?? '', row.group].some(
      value => value.toLocaleLowerCase().includes(needle))));
}

/** A selection is meaningful only within this server-authorized page's groups. */
export function normalizeAuthorizedGroupSelection(
  availableGroups: readonly string[], selected: string,
): string {
  return selected !== '' && availableGroups.includes(selected) ? selected : '';
}

/** Sorting is *only* over the single server-authorized page in memory. */
export type DirectorySort = 'server' | 'label-asc' | 'label-desc' | 'group-asc';

export function sortAuthorizedDirectoryPage(
  rows: readonly DirectoryRow[], sort: DirectorySort,
): DirectoryRow[] {
  if (sort === 'server') return [...rows];
  const collator = new Intl.Collator('zh-CN', { numeric: true, sensitivity: 'base' });
  return rows.map((row, index) => ({ row, index })).sort((a, b) => {
    const left = sort === 'group-asc' ? a.row.group : a.row.label;
    const right = sort === 'group-asc' ? b.row.group : b.row.label;
    const direction = sort === 'label-desc' ? -1 : 1;
    return direction * collator.compare(left, right) || a.index - b.index;
  }).map(item => item.row);
}

const feedback = {
  unconnected: { title: '设备数据尚未连接', message: '未建立安全管理员会话，不代表数据库中没有设备。', Icon: WifiOff },
  loading: { title: '正在加载已授权设备', message: '请以服务端权限过滤结果为准。', Icon: RefreshCcw },
  signed_out: { title: '管理员登录状态已失效', message: '请重新登录。不会展示旧设备数据或演示记录。', Icon: LockKeyhole },
  forbidden: { title: '当前账号无权查看设备', message: '设备组权限由后端验证，前端筛选不能代替授权。', Icon: ShieldAlert },
  throttled: { title: '请求受到限制', message: '请稍后手动重试；不会自动高频请求，也不会显示缓存的设备行。', Icon: ShieldAlert },
  unavailable: { title: '设备服务暂不可用', message: '无法安全读取服务端响应，不会回退到合成数据。', Icon: WifiOff },
} as const;

export function DeviceDirectory({
  state, allowDetailNavigation = true,
}: Readonly<{ state: DirectoryState; allowDetailNavigation?: boolean }>) {
  const [search, setSearch] = useState('');
  const [group, setGroup] = useState('');
  const [sortMode, setSortMode] = useState<DirectorySort>('server');
  const searchInputRef = useRef<HTMLInputElement>(null);
  const scrollHintId = useId();
  const isReady = state.kind === 'ready';

  // Never retain the previous user's search terms or device group filter when
  // the authorization state becomes signed out, forbidden or disconnected.
  // Actual authorized rows are always controlled by the server-side caller.
  useEffect(() => {
    if (!isReady) {
      setSearch('');
      setGroup('');
      setSortMode('server');
    }
  }, [isReady]);
  const rows = isReady ? state.rows : [];
  const groups = useMemo(() => [...new Set(rows.map(row => row.group))].sort(), [rows]);

  // A refreshed authorized page may no longer contain the previously selected
  // group. Never hide new authorized rows behind an invalid stale selection.
  const activeGroup = normalizeAuthorizedGroupSelection(groups, group);
  useEffect(() => {
    if (group !== '' && activeGroup === '') setGroup('');
  }, [group, activeGroup]);

  const visible = useMemo(
    () => sortAuthorizedDirectoryPage(
      filterDirectoryRows(rows, search, activeGroup), sortMode,
    ),
    [rows, search, activeGroup, sortMode],
  );
  const hasFilters = isReady && (search.trim().length > 0 || activeGroup !== '');
  function resetFilters() {
    setSearch('');
    setGroup('');
    // The clear button is removed after reset; focus a stable enabled field.
    searchInputRef.current?.focus();
  }

  return <Card className="directory-panel" aria-label="终端资产列表">
    <CardHeader className="directory-toolbar">
      <div>
        <CardTitle>设备列表</CardTitle>
        <CardDescription aria-live={isReady ? 'polite' : 'off'}>
          {isReady ? `仅本次授权页：匹配 ${visible.length} / ${rows.length} 条` :
            '仅支持已授权的只读设备查询'}
        </CardDescription>
      </div>
      <div className="directory-controls">
        <label className="directory-search">
          <Search size={16} aria-hidden="true" />
          <span className="sr-only">搜索终端名称、系统或设备组</span>
          <Input aria-label="搜索终端名称、系统或设备组" ref={searchInputRef}
            value={search} onChange={event => setSearch(event.target.value)}
            placeholder="搜索设备或系统" disabled={!isReady} />
        </label>
        <div className="directory-select">
          <NativeSelect aria-label="筛选设备组" disabled={!isReady}
            value={activeGroup} onChange={event => setGroup(event.target.value)}>
            <option value="">全部授权设备组</option>
            {groups.map(value => <option value={value} key={value}>{value}</option>)}
          </NativeSelect>
        </div>
        <div className="directory-sort">
          <NativeSelect aria-label="排序当前授权页" disabled={!isReady}
            value={sortMode}
            onChange={event => setSortMode(event.target.value as DirectorySort)}>
            <option value="server">服务器返回顺序</option>
            <option value="label-asc">设备名称 A–Z</option>
            <option value="label-desc">设备名称 Z–A</option>
            <option value="group-asc">设备组 A–Z</option>
          </NativeSelect>
        </div>
        {hasFilters && <button type="button" onClick={resetFilters}
          className="directory-clear-filters" aria-label="清除设备搜索和设备组筛选">
          <X size={15} aria-hidden="true"/> 清除筛选
        </button>}
      </div>
    </CardHeader>
    {state.kind !== 'ready' ? (
      <CardContent className="directory-feedback" role="status" aria-live="polite">
        {(() => {
          const { title, message, Icon } = feedback[state.kind];
          return <><span className="state-icon"><Icon size={23} aria-hidden="true" /></span>
            <strong>{title}</strong><p>{message}</p></>;
        })()}
      </CardContent>
    ) : visible.length === 0 ? (
      <CardContent className="directory-feedback" role="status">
        <span className="state-icon"><Search size={23} aria-hidden="true" /></span>
        <strong>{rows.length === 0 ? '当前授权范围没有设备' : '没有匹配的设备'}</strong>
        <p>{rows.length === 0
          ? '这是已验证的空列表，不等于服务断开；不会填充示例设备。'
          : '请调整本页的搜索词或设备组筛选条件。'}</p>
      </CardContent>
    ) : (
      <CardContent className="directory-table-wrap">
        <p id={scrollHintId} className="directory-scroll-hint">
          表格有七列。窄屏可横向滑动；键盘用户可聚焦表格区域，按左右方向键浏览。
        </p>
        <Table className="device-table"
          scrollRegionLabel="设备目录表格（可横向滚动）"
          scrollRegionDescriptionId={scrollHintId}>
          <TableCaption className="sr-only">当前管理员已授权的设备健康列表</TableCaption>
          <TableHeader><TableRow><TableHead scope="col">设备</TableHead><TableHead scope="col">设备组</TableHead>
            <TableHead scope="col">注册状态</TableHead><TableHead scope="col">连接状态</TableHead>
            <TableHead scope="col">最后心跳</TableHead><TableHead scope="col">队列</TableHead><TableHead scope="col">详情</TableHead></TableRow></TableHeader>
          <TableBody>{visible.map(row => <TableRow key={row.id}>
            <TableHead scope="row" className="device-row-heading">
              <strong>{row.label}</strong><small>{row.os ?? '操作系统未上报'}</small>
            </TableHead>
            <TableCell>{row.group}</TableCell><TableCell>{enrollmentLabel[row.enrollment]}</TableCell>
            <TableCell><span className={`health-pill health-${row.connectivity}`}>
              {statusLabel[row.connectivity]}</span></TableCell>
            <TableCell>{row.heartbeat ?? '尚无已接收心跳'}</TableCell>
            <TableCell>{row.queue === null ? '未上报' : `${row.queue} 条`}</TableCell>
            <TableCell>{isCanonicalUuid(row.id) && allowDetailNavigation
              ? <Link href={`/devices/${row.id}`} aria-label={`查看${row.label}的详情`}
                  className="device-detail-link"><ArrowRight size={18} /></Link>
              : <span>{isCanonicalUuid(row.id) ? '详情未开放' : '不可用'}</span>}</TableCell>
          </TableRow>)}</TableBody>
        </Table>
      </CardContent>
    )}
    <div className="directory-foot">
      <span>{isReady ? '仅本次已授权页面：搜索、设备组筛选与排序不会查询或推断其他页面' :
        '等待 PostgreSQL 18 / HTTPS / 浏览器会话验收'}</span>
      {isReady && state.hasMore && <span>后续分页待授权接入</span>}
    </div>
  </Card>;
}
