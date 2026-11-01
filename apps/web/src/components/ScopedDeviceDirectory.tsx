import { ArrowRight, LockKeyhole, ShieldAlert, WifiOff } from 'lucide-react';

/**
 * FE-02-B: a presentation model, NOT a handwritten backend response DTO.
 * The eventual Orval-generated, group-authorized server response must be
 * mapped to these *display-only* strings by a separately accepted adapter.
 * Do not place network identifiers, raw inventory or employee data here.
 */
export type ScopedDeviceDisplayRow = Readonly<{
  deviceId: string;
  title: string;
  subtitle: string;
  groupLabel: string;
  connectivityLabel: string;
  lastSeenLabel: string;
  queueLabel: string;
}>;

export type ScopedDeviceDirectoryState =
  | Readonly<{ kind: 'loading' }>
  | Readonly<{ kind: 'signed_out' }>
  | Readonly<{ kind: 'forbidden' }>
  | Readonly<{ kind: 'throttled' }>
  | Readonly<{ kind: 'unavailable' }>
  | Readonly<{ kind: 'ready'; rows: readonly ScopedDeviceDisplayRow[]; hasMore: boolean }>;

const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

export function safeScopedDeviceHref(deviceId: string): string | null {
  return uuidPattern.test(deviceId) ? `/devices/${deviceId}` : null;
}

/**
 * No requests, demo fixture imports, timers or mutations. In particular,
 * 401/403/503 must never cause a fallback to synthetic rows. The parent will
 * supply an authenticated, Orval-decoded state only after security acceptance.
 */
export function ScopedDeviceDirectory({ state }: { state: ScopedDeviceDirectoryState }) {
  if (state.kind !== 'ready') {
    const detail = {
      loading: {
        icon: null,
        heading: '正在加载已授权设备',
        body: '仅显示当前管理员有权限查看的设备。',
      },
      signed_out: {
        icon: <LockKeyhole size={20} aria-hidden="true" />,
        heading: '登录状态已失效',
        body: '请重新登录后查看设备。当前不会展示旧数据或演示数据。',
      },
      forbidden: {
        icon: <ShieldAlert size={20} aria-hidden="true" />,
        heading: '没有设备访问权限',
        body: '服务器未授予当前会话访问此设备列表的权限。',
      },
      throttled: {
        icon: <ShieldAlert size={20} aria-hidden="true" />,
        heading: '请求受到限制',
        body: '请稍后手动重试；不会自动反复请求。',
      },
      unavailable: {
        icon: <WifiOff size={20} aria-hidden="true" />,
        heading: '设备服务暂不可用',
        body: '无法安全取得设备信息。不会使用合成数据代替。',
      },
    }[state.kind];
    return <section className="panel" aria-label="授权设备列表状态">
      <div className="info" role="status" aria-live="polite">
        {detail.icon}
        <div><strong>{detail.heading}</strong><p>{detail.body}</p></div>
      </div>
    </section>;
  }

  const { rows, hasMore } = state;
  return <section className="panel" aria-label="已授权设备列表">
    <div className="panel-header">
      <div>
        <h2>已授权设备</h2>
        <p>仅显示服务端按当前登录账号和设备组筛选的结果。</p>
      </div>
      <span className="pill">只读</span>
    </div>
    {rows.length === 0 ? (
      <p className="empty" role="status">当前授权范围内没有设备。这里不会填充演示终端。</p>
    ) : (
      <div className="table-scroll">
        <table>
          <caption className="sr-only">当前管理员授权范围内的设备健康摘要</caption>
          <thead><tr>
            <th scope="col">终端设备</th>
            <th scope="col">设备组</th>
            <th scope="col">连接状态</th>
            <th scope="col">最后心跳</th>
            <th scope="col">队列</th>
            <th scope="col">详情</th>
          </tr></thead>
          <tbody>{rows.map(row => {
            const detailHref = safeScopedDeviceHref(row.deviceId);
            return <tr key={row.deviceId}>
              <td><div className="device-title"><div>
                <strong>{row.title}</strong><small>{row.subtitle}</small>
              </div></div></td>
              <td>{row.groupLabel}</td>
              <td>{row.connectivityLabel}</td>
              <td>{row.lastSeenLabel}</td>
              <td>{row.queueLabel}</td>
              <td>{detailHref ? (
                <a className="row-link" href={detailHref}
                  aria-label={`查看${row.title}的授权只读详情`}>
                  <ArrowRight size={18} aria-hidden="true" />
                </a>
              ) : <span>不可用</span>}</td>
            </tr>;
          })}</tbody>
        </table>
      </div>
    )}
    {hasMore && <p className="table-foot">还有后续设备页，尚未加载；不会自动请求。</p>}
  </section>;
}
