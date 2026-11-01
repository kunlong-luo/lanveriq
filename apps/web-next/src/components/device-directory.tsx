'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { ArrowRight, LockKeyhole, Search, ShieldAlert, WifiOff, RefreshCcw } from 'lucide-react';
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from './ui/card';

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
  | Readonly<{ kind: 'unconnected' | 'loading' | 'signed_out' | 'forbidden' | 'unavailable' }>
  | Readonly<{ kind: 'ready'; rows: readonly DirectoryRow[]; hasMore: boolean }>;

const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
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

const feedback = {
  unconnected: { title: '设备数据尚未连接', message: '未建立安全管理员会话，不代表数据库中没有设备。', Icon: WifiOff },
  loading: { title: '正在加载已授权设备', message: '请以服务端权限过滤结果为准。', Icon: RefreshCcw },
  signed_out: { title: '管理员登录状态已失效', message: '请重新登录。不会展示旧设备数据或演示记录。', Icon: LockKeyhole },
  forbidden: { title: '当前账号无权查看设备', message: '设备组权限由后端验证，前端筛选不能代替授权。', Icon: ShieldAlert },
  unavailable: { title: '设备服务暂不可用', message: '无法安全读取服务端响应，不会回退到合成数据。', Icon: WifiOff },
} as const;

export function DeviceDirectory({ state }: Readonly<{ state: DirectoryState }>) {
  const [search, setSearch] = useState('');
  const [group, setGroup] = useState('');
  const isReady = state.kind === 'ready';
  const rows = isReady ? state.rows : [];
  const groups = useMemo(() => [...new Set(rows.map(row => row.group))].sort(), [rows]);
  const visible = useMemo(() => filterDirectoryRows(rows, search, group), [rows, search, group]);

  return <Card className="directory-panel" aria-label="终端资产列表">
    <CardHeader className="directory-toolbar">
      <div>
        <CardTitle>设备列表</CardTitle>
        <CardDescription>{isReady ? `当前已授权页：${visible.length} 条` : '仅支持已授权的只读设备查询'}</CardDescription>
      </div>
      <div className="directory-controls">
        <label className="directory-search">
          <Search size={16} aria-hidden="true" />
          <span className="sr-only">搜索终端名称、系统或设备组</span>
          <input aria-label="搜索终端名称、系统或设备组"
            value={search} onChange={event => setSearch(event.target.value)}
            placeholder="搜索设备或系统" disabled={!isReady} />
        </label>
        <label className="directory-select">
          <span className="sr-only">筛选设备组</span>
          <select aria-label="筛选设备组" disabled={!isReady}
            value={group} onChange={event => setGroup(event.target.value)}>
            <option value="">全部授权设备组</option>
            {groups.map(value => <option value={value} key={value}>{value}</option>)}
          </select>
        </label>
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
        <table className="device-table">
          <caption className="sr-only">当前管理员已授权的设备健康列表</caption>
          <thead><tr><th scope="col">设备</th><th scope="col">设备组</th>
            <th scope="col">注册状态</th><th scope="col">连接状态</th>
            <th scope="col">最后心跳</th><th scope="col">队列</th><th scope="col">详情</th></tr></thead>
          <tbody>{visible.map(row => <tr key={row.id}>
            <td><strong>{row.label}</strong><small>{row.os ?? '操作系统未上报'}</small></td>
            <td>{row.group}</td><td>{enrollmentLabel[row.enrollment]}</td>
            <td><span className={`health-pill health-${row.connectivity}`}>
              {statusLabel[row.connectivity]}</span></td>
            <td>{row.heartbeat ?? '尚无已接收心跳'}</td>
            <td>{row.queue === null ? '未上报' : `${row.queue} 条`}</td>
            <td>{uuid.test(row.id)
              ? <Link href={`/devices/${row.id}`} aria-label={`查看${row.label}的详情`}
                  className="device-detail-link"><ArrowRight size={18} /></Link>
              : <span>不可用</span>}</td>
          </tr>)}</tbody>
        </table>
      </CardContent>
    )}
    <div className="directory-foot">
      <span>{isReady ? '数据范围由 Spring Boot 授权控制' : '等待 PostgreSQL 18 / HTTPS / 浏览器会话验收'}</span>
      {isReady && state.hasMore && <span>后续分页待授权接入</span>}
    </div>
  </Card>;
}
